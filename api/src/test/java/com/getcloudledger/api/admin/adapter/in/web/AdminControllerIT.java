package com.getcloudledger.api.admin.adapter.in.web;

import com.getcloudledger.api.account.domain.event.MoneyDeposited;
import com.getcloudledger.api.shared.adapter.in.PostgresContainerBase;
import com.getcloudledger.api.shared.adapter.in.RedisContainerBase;
import com.getcloudledger.api.shared.adapter.out.sqs.SqsEventBus;
import com.getcloudledger.api.shared.domain.bus.event.BaseEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AdminController (integration)")
class AdminControllerIT {

    private static final String ADMIN_AUTHORITY = "SCOPE_https://api.getcloudledger.com/admin";

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainerBase.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainerBase.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresContainerBase.POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.data.redis.host", RedisContainerBase.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> RedisContainerBase.REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "sqsEventBus")
    private SqsEventBus sqsEventBus;

    @Test
    @DisplayName("rebuild | account-scoped replay completes and republishes every event with balance_after")
    void rebuild_account_scoped_completes_and_republishes_with_balance_after() throws Exception {
        var accountId = UUID.randomUUID();
        var ownerId = "admin-rebuild-owner";
        openAccountWithDeposit(accountId, ownerId);

        // Only capture the republish (reset drops the open/deposit write-path invocations).
        Mockito.reset(sqsEventBus);

        var location = mockMvc.perform(post("/v1/admin/projections/rebuild")
                        .param("account_id", accountId.toString())
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY)))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.total_events").value(2))
                .andReturn();

        var jobId = readJobId(location);
        awaitDone(jobId);

        mockMvc.perform(get("/v1/admin/projections/rebuild/{jobId}", jobId)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.total_events").value(2))
                .andExpect(jsonPath("$.processed_events").value(2))
                .andExpect(jsonPath("$.finished_at").isNotEmpty());

        // The reconstructed MoneyDeposited must carry the recomputed balance_after (it lives only on
        // the SQS wire format, never in the stored payload) so the projector can rebuild BALANCE.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends BaseEvent>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(sqsEventBus).publish(eq("account"), captor.capture());
        var deposit = captor.getValue().stream()
                .filter(MoneyDeposited.class::isInstance)
                .map(MoneyDeposited.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("100.00", deposit.getDynamicAttributes().get("balance_after"));
    }

    @Test
    @DisplayName("rebuild | full rebuild (no account_id) completes and processes every event")
    void rebuild_full_completes() throws Exception {
        openAccountWithDeposit(UUID.randomUUID(), "full-rebuild-owner");
        Mockito.reset(sqsEventBus);

        var result = mockMvc.perform(post("/v1/admin/projections/rebuild")
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY)))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.account_id").doesNotExist())
                .andReturn();

        var jobId = readJobId(result);
        awaitDone(jobId);

        var body = statusBody(jobId);
        assertTrue(body.contains("\"status\":\"DONE\""), body);
        // total_events reflects the whole shared event log; assert the job drained it fully.
        var total = body.replaceAll(".*\"total_events\":(\\d+).*", "$1");
        assertTrue(body.contains("\"processed_events\":" + total),
                "processed_events should equal total_events: " + body);
    }

    @Test
    @DisplayName("rebuild | returns 403 when the caller lacks the api/admin scope")
    void rebuild_returns_403_without_admin_scope() throws Exception {
        mockMvc.perform(post("/v1/admin/projections/rebuild")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_https://api.getcloudledger.com/write")))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("status | returns 404 rebuild_job_not_found for an unknown job id")
    void status_returns_404_for_unknown_job() throws Exception {
        mockMvc.perform(get("/v1/admin/projections/rebuild/{jobId}", UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("rebuild_job_not_found"));
    }

    private void openAccountWithDeposit(UUID accountId, String ownerId) throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(jwt().jwt(b -> b.subject(ownerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": "USD"}
                                """.formatted(accountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/accounts/{id}/deposits", accountId)
                        .with(jwt().jwt(b -> b.subject(ownerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"amount": "100.00"}
                                """))
                .andExpect(status().isCreated());
    }

    private String readJobId(MvcResult result) throws Exception {
        var body = result.getResponse().getContentAsString();
        // {"job_id":"<uuid>",...}
        int idx = body.indexOf("\"job_id\":\"") + "\"job_id\":\"".length();
        return body.substring(idx, idx + 36);
    }

    private void awaitDone(String jobId) throws Exception {
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            var body = statusBody(jobId);
            return !body.contains("\"status\":\"RUNNING\"");
        });
        var finalBody = statusBody(jobId);
        assertTrue(finalBody.contains("\"status\":\"DONE\""), "Job did not complete: " + finalBody);
    }

    private String statusBody(String jobId) throws Exception {
        return mockMvc.perform(get("/v1/admin/projections/rebuild/{jobId}", jobId)
                        .with(jwt().authorities(new SimpleGrantedAuthority(ADMIN_AUTHORITY))))
                .andReturn().getResponse().getContentAsString();
    }
}
