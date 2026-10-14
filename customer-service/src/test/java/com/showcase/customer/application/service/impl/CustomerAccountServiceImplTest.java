package com.showcase.customer.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.showcase.common.domain.exception.DuplicateResourceException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProcessedEventRegistry processedEvents;

    @InjectMocks
    private CustomerAccountServiceImpl accountService;

    @Nested
    @DisplayName("createForUser")
    class CreateForUser {

        @Test
        @DisplayName("crea el cliente del usuario, sin direcciones y con el email normalizado")
        void createForUser_shouldCreateCustomer() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.registered")).willReturn(true);
            given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            accountService.createForUser(EVENT_ID, 10L, "Ana@Example.com", "Ana", "García");

            // then
            var captor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(10L);
            assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
            assertThat(captor.getValue().fullName()).isEqualTo("Ana García");
            assertThat(captor.getValue().getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("ignora un evento duplicado")
        void createForUser_duplicateEvent_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.registered")).willReturn(false);

            // when
            accountService.createForUser(EVENT_ID, 10L, "ana@example.com", "Ana", "García");

            // then
            verifyNoInteractions(customerRepository);
        }

        @Test
        @DisplayName("no crea otro cliente si el usuario ya tiene uno")
        void createForUser_existingCustomer_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.registered")).willReturn(true);
            given(customerRepository.existsByUserId(10L)).willReturn(true);

            // when
            accountService.createForUser(EVENT_ID, 10L, "ana@example.com", "Ana", "García");

            // then
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("lanza EMAIL_ALREADY_EXISTS si otro cliente usa el email (el mensaje irá a la DLQ)")
        void createForUser_emailInUse_shouldThrow() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.registered")).willReturn(true);
            given(customerRepository.existsByEmail("ana@example.com")).willReturn(true);

            // when / then
            assertThatThrownBy(() -> accountService.createForUser(EVENT_ID, 10L, "ana@example.com", "Ana", "García"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.EMAIL_ALREADY_EXISTS);
            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deactivateForUser")
    class DeactivateForUser {

        @Test
        @DisplayName("marca como inactivo el cliente del usuario")
        void deactivateForUser_shouldDeactivateCustomer() {
            // given
            var customer = Customer.create(10L, "Ana", "García", "ana@example.com", null);
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.disabled")).willReturn(true);
            given(customerRepository.findByUserId(10L)).willReturn(Optional.of(customer));

            // when
            accountService.deactivateForUser(EVENT_ID, 10L);

            // then
            assertThat(customer.isActive()).isFalse();
            verify(customerRepository).save(customer);
        }

        @Test
        @DisplayName("un usuario sin cliente no es un error")
        void deactivateForUser_withoutCustomer_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.disabled")).willReturn(true);
            given(customerRepository.findByUserId(10L)).willReturn(Optional.empty());

            // when
            accountService.deactivateForUser(EVENT_ID, 10L);

            // then
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("ignora un evento duplicado")
        void deactivateForUser_duplicateEvent_shouldDoNothing() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "user.disabled")).willReturn(false);

            // when
            accountService.deactivateForUser(EVENT_ID, 10L);

            // then
            verifyNoInteractions(customerRepository);
        }
    }

    @Nested
    @DisplayName("recordAccountLocked")
    class RecordAccountLocked {

        @Test
        @DisplayName("registra el evento sin modificar el cliente")
        void recordAccountLocked_shouldOnlyRegisterEvent() {
            // given
            given(processedEvents.registerIfAbsent(EVENT_ID, "account.locked")).willReturn(true);

            // when
            accountService.recordAccountLocked(EVENT_ID, 10L, Instant.parse("2026-10-14T10:15:00Z"));

            // then
            verify(processedEvents).registerIfAbsent(EVENT_ID, "account.locked");
            verifyNoInteractions(customerRepository);
        }
    }
}
