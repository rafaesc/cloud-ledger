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
    @WithMockUser
    @DisplayName("openAccount | returns 201 and persists AccountOpened event when request is valid")
    void openAccount_returns_201_and_persists_AccountOpened_event() throws Exception {
        var accountId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-User-Id", userId.toString())
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
    @WithMockUser
    @DisplayName("openAccount | returns 400 when currency is blank")
    void openAccount_returns_400_when_currency_is_blank() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": ""}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("openAccount | returns 400 when Idempotency-Key header is missing")
    void openAccount_returns_400_when_idempotency_key_is_missing() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .content("""
                                {"accountId": "%s", "currency": "USD"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("openAccount | returns 400 when accountId is missing")
    void openAccount_returns_400_when_accountId_is_missing() throws Exception {
        mockMvc.perform(post("/v1/accounts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .content("""
                                {"currency": "USD"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
