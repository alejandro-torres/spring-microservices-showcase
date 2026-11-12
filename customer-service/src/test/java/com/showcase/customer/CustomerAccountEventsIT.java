package com.showcase.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.customer.application.service.CustomerService;
import com.showcase.customer.domain.repository.CustomerRepository;
import com.showcase.customer.testdata.TestJwt;
import com.showcase.events.EventEnvelopeDTO;
import com.showcase.events.auth.UserDisabledMessageDTO;
import com.showcase.events.auth.UserRegisteredMessageDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * auth-service events through real RabbitMQ: this test plays auth-service, publishing to {@code auth.events}.
 * It also walks through the DLQ administration over HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerAccountEventsIT {

    private static final String USER_REGISTERED_DLQ = "customer-service.user-registered.dlq";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicLong USER_IDS = new AtomicLong(50_000);

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(USER_REGISTERED_DLQ, false);
    }

    @Nested
    @DisplayName("user.registered")
    class UserRegistered {

        @Test
        @DisplayName("creates the user's customer profile only once even if the message arrives repeatedly")
        void userRegistered_shouldCreateCustomerOnce() {
            // given
            long userId = USER_IDS.incrementAndGet();
            UUID eventId = UUID.randomUUID();
            var user = new UserRegisteredMessageDTO(userId, "user" + userId, "user" + userId + "@example.com",
                    "Ana", "García");

            // when
            publishFromAuth("user.registered", eventId, user);
            publishFromAuth("user.registered", eventId, user);

            // then
            await().atMost(TIMEOUT).untilAsserted(() ->
                    assertThat(customerService.findByUserId(userId).fullName()).isEqualTo("Ana García"));
            assertThat(rabbitTemplate.receive(USER_REGISTERED_DLQ, 1_000)).isNull();
        }

        @Test
        @DisplayName("if the email already belongs to another customer the message goes to the DLQ, and an ADMIN can view and re-send it")
        void userRegistered_withEmailInUse_shouldGoToDeadLetterQueue() throws Exception {
            // given
            long firstUser = USER_IDS.incrementAndGet();
            String email = "repeated" + firstUser + "@example.com";
            publishFromAuth("user.registered", UUID.randomUUID(),
                    new UserRegisteredMessageDTO(firstUser, "u" + firstUser, email, "Ana", "García"));
            await().atMost(TIMEOUT).until(() -> customerRepository.existsByUserId(firstUser));
            long secondUser = USER_IDS.incrementAndGet();

            // when
            publishFromAuth("user.registered", UUID.randomUUID(),
                    new UserRegisteredMessageDTO(secondUser, "u" + secondUser, email, "Bea", "López"));

            // then: the DLQ shows it, only to an ADMIN
            await().atMost(TIMEOUT).untilAsserted(() ->
                    mockMvc.perform(get("/api/v1/admin/dead-letters").with(TestJwt.admin()))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$[?(@.name == '" + USER_REGISTERED_DLQ + "')].messageCount")
                                    .value(1)));
            mockMvc.perform(get("/api/v1/admin/dead-letters").with(TestJwt.manager()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/dead-letters/" + USER_REGISTERED_DLQ + "/messages")
                            .with(TestJwt.admin()))
                    .andExpect(jsonPath("$[0].eventType").value("user.registered"))
                    .andExpect(jsonPath("$[0].reason").value("rejected"));

            // when: the data is fixed (the first customer changes email) and it is re-sent
            var first = customerService.findByUserId(firstUser);
            customerService.update(first.getId(), "Ana", "García", "other" + firstUser + "@example.com", null,
                    first.getVersion());
            mockMvc.perform(post("/api/v1/admin/dead-letters/" + USER_REGISTERED_DLQ + "/replay")
                            .with(TestJwt.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.replayed").value(1));

            // then
            await().atMost(TIMEOUT).until(() -> customerRepository.existsByUserId(secondUser));
        }
    }

    @Nested
    @DisplayName("user.disabled")
    class UserDisabled {

        @Test
        @DisplayName("marks the user's customer as inactive")
        void userDisabled_shouldDeactivateCustomer() {
            // given
            long userId = USER_IDS.incrementAndGet();
            publishFromAuth("user.registered", UUID.randomUUID(), new UserRegisteredMessageDTO(userId,
                    "user" + userId, "user" + userId + "@example.com", "Carlos", "Ruiz"));
            await().atMost(TIMEOUT).until(() -> customerRepository.existsByUserId(userId));

            // when
            publishFromAuth("user.disabled", UUID.randomUUID(), new UserDisabledMessageDTO(userId, "user" + userId));

            // then: inactive customers are not returned
            await().atMost(TIMEOUT).until(() -> customerRepository.findByUserId(userId).isEmpty());
        }
    }

    @Test
    @DisplayName("a DLQ that does not belong to this service returns 404")
    void unknownDeadLetterQueue_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dead-letters/other.queue.dlq/messages").with(TestJwt.admin()))
                .andExpect(status().isNotFound());
    }

    private void publishFromAuth(String routingKey, UUID eventId, Object payload) {
        var envelope = new EventEnvelopeDTO<>(eventId, routingKey, 1, Instant.now(), "auth-service", payload);
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setType(routingKey)
                .build();
        rabbitTemplate.send("auth.events", routingKey, message);
    }
}
