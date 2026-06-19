package com.getcloudledger.api.account.adapter.in.web;

import com.getcloudledger.api.shared.adapter.in.PostgresContainerBase;
import com.getcloudledger.api.shared.adapter.in.RedisContainerBase;
import com.getcloudledger.api.shared.adapter.out.repository.JpaDomainEventRepository;
import com.getcloudledger.api.shared.domain.bus.event.EventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TransferController (integration)")
class TransferControllerIT {

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

    @Autowired
    private JpaDomainEventRepository jpaDomainEventRepository;

    @MockitoBean(name = "sqsEventBus")
    private EventBus sqsEventBus;

    @Test
    @WithMockUser
    @DisplayName("transfer | returns 201 and persists TransferDebited + TransferCredited events when both accounts exist")
    void transfer_returns_201_and_persists_debit_and_credit_events() throws Exception {
        var sourceId = openAccount("USD");
        deposit(sourceId, "500.00");
        var destinationId = openAccount("USD");
        var transferId = UUID.randomUUID();

        mockMvc.perform(post("/v1/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 200.00,
                                  "transferId": "%s"
                                }
                                """.formatted(sourceId, destinationId, transferId)))
                .andExpect(status().isCreated());

        var sourceEvents = jpaDomainEventRepository.findAllByAggregateId(sourceId, Sort.by("version"));
        var destEvents = jpaDomainEventRepository.findAllByAggregateId(destinationId, Sort.by("version"));

        assertEquals("TransferDebited", sourceEvents.getLast().getEventName());
        assertEquals("TransferCredited", destEvents.getLast().getEventName());
    }

    @Test
    @WithMockUser
    @DisplayName("transfer | returns 400 when amount is missing")
    void transfer_returns_400_when_amount_is_missing() throws Exception {
        mockMvc.perform(post("/v1/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "transferId": "%s"
                                }
                                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID openAccount(String currency) throws Exception {
        var accountId = UUID.randomUUID();
        mockMvc.perform(post("/v1/accounts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-User-Id", UUID.randomUUID().toString())
                .content("""
                        {"accountId": "%s", "currency": "%s"}
                        """.formatted(accountId, currency)));
        return accountId;
    }

    private void deposit(UUID accountId, String amount) throws Exception {
        mockMvc.perform(post("/v1/accounts/{id}/deposits", accountId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .content("""
                        {"amount": %s}
                        """.formatted(amount)));
    }
}
