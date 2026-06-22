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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AccountController (integration)")
class AccountControllerIT {

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
    @DisplayName("openAccount | returns 201 and persists AccountOpened event when request is valid")
    void openAccount_returns_201_and_persists_AccountOpened_event() throws Exception {
        var accountId = UUID.randomUUID();
        var ownerId = "cognito-client-id-abc123";

        mockMvc.perform(post("/v1/accounts")
                        .with(jwt().jwt(b -> b.subject(ownerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": "USD"}
                                """.formatted(accountId)))
                .andExpect(status().isCreated());

        var events = jpaDomainEventRepository.findAllByAggregateId(accountId, Sort.by("version"));
        assertEquals(1, events.size());
        assertEquals("AccountOpened", events.getFirst().getEventName());
        assertEquals(accountId, events.getFirst().getAggregateId());
    }

    @Test
    @DisplayName("deposit | returns 201 when JWT subject matches account owner")
    void deposit_returns_201_when_caller_is_owner() throws Exception {
        var accountId = UUID.randomUUID();
        var ownerId = "owner-deposit-ok";

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

    @Test
    @DisplayName("deposit | returns 403 when JWT subject does not match account owner")
    void deposit_returns_403_when_caller_is_not_owner() throws Exception {
        var accountId = UUID.randomUUID();

        mockMvc.perform(post("/v1/accounts")
                        .with(jwt().jwt(b -> b.subject("real-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": "USD"}
                                """.formatted(accountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/accounts/{id}/deposits", accountId)
                        .with(jwt().jwt(b -> b.subject("different-caller")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"amount": "100.00"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("openAccount | returns 400 when currency is blank")
    void openAccount_returns_400_when_currency_is_blank() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": ""}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("openAccount | returns 400 when Idempotency-Key header is missing")
    void openAccount_returns_400_when_idempotency_key_is_missing() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "currency": "USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("openAccount | returns 400 when accountId is missing")
    void openAccount_returns_400_when_accountId_is_missing() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("""
                                {"currency": "USD"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
