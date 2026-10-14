package com.showcase.customer.application.service.impl;

import static com.showcase.customer.testdata.CustomerMother.ana;
import static com.showcase.customer.testdata.CustomerMother.newAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.exception.DuplicateResourceException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private void givenSaveReturnsArgument() {
        given(customerRepository.save(any(Customer.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenAna() {
        given(customerRepository.findById(1L)).willReturn(Optional.of(ana()));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("guarda el cliente con sus direcciones")
        void create_shouldSaveWithAddresses() {
            given(customerRepository.existsByUserId(7L)).willReturn(false);
            given(customerRepository.existsByEmail("ana@example.com")).willReturn(false);
            givenSaveReturnsArgument();

            var created = customerService.create(7L, "Ana", "García", "ANA@example.com", null,
                    List.of(newAddress(), newAddress()));

            assertThat(created.getEmail()).isEqualTo("ana@example.com");
            assertThat(created.getAddresses()).hasSize(2);
        }

        @Test
        @DisplayName("sin userId no comprueba el usuario")
        void create_withoutUser_shouldSkipUserCheck() {
            given(customerRepository.existsByEmail("ana@example.com")).willReturn(false);
            givenSaveReturnsArgument();

            var created = customerService.create(null, "Ana", "García", "ana@example.com", null, null);

            assertThat(created.getAddresses()).isEmpty();
            verify(customerRepository, never()).existsByUserId(any());
        }

        @Test
        @DisplayName("lanza USER_ALREADY_HAS_CUSTOMER si el usuario ya tiene perfil")
        void create_withUserTaken_shouldThrow() {
            given(customerRepository.existsByUserId(7L)).willReturn(true);

            assertThatThrownBy(() -> customerService.create(7L, "Ana", "García", "ana@example.com", null, null))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.USER_ALREADY_HAS_CUSTOMER);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("lanza EMAIL_ALREADY_EXISTS si el email está repetido")
        void create_withEmailTaken_shouldThrow() {
            given(customerRepository.existsByEmail("ana@example.com")).willReturn(true);

            assertThatThrownBy(() -> customerService.create(null, "Ana", "García", "ana@example.com", null, null))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("consultas")
    class Queries {

        @Test
        @DisplayName("findById lanza CUSTOMER_NOT_FOUND si no existe")
        void findById_missing_shouldThrow() {
            assertThatThrownBy(() -> customerService.findById(9L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }

        @Test
        @DisplayName("findByUserId devuelve el cliente del usuario")
        void findByUserId_shouldReturnCustomer() {
            given(customerRepository.findByUserId(7L)).willReturn(Optional.of(ana()));

            assertThat(customerService.findByUserId(7L).getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("findByUserId lanza CUSTOMER_NOT_FOUND si el usuario no tiene perfil")
        void findByUserId_missing_shouldThrow() {
            assertThatThrownBy(() -> customerService.findByUserId(8L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("findAll delega en el repositorio con filtros y página")
        void findAll_shouldDelegate() {
            var query = new PageQuery(0, 10, "id", SortDirection.ASC);
            var page = PageResult.of(List.of(ana()), 0, 10, 1);
            given(customerRepository.findAll(List.of(), query, false)).willReturn(page);

            assertThat(customerService.findAll(List.of(), query, false)).isSameAs(page);
        }
    }

    @Nested
    @DisplayName("update y delete")
    class UpdateAndDelete {

        @Test
        @DisplayName("update cambia los datos si la versión coincide")
        void update_shouldSave() {
            givenAna();
            given(customerRepository.existsByEmailAndIdNot("nueva@example.com", 1L)).willReturn(false);
            givenSaveReturnsArgument();

            var updated = customerService.update(1L, "Ana", "Pérez", "nueva@example.com", null, 0L);

            assertThat(updated.getLastName()).isEqualTo("Pérez");
        }

        @Test
        @DisplayName("update lanza CONCURRENT_MODIFICATION con una versión antigua")
        void update_withStaleVersion_shouldThrow() {
            givenAna();

            assertThatThrownBy(() -> customerService.update(1L, "Ana", "Pérez", "ana@example.com", null, 3L))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.CONCURRENT_MODIFICATION);
        }

        @Test
        @DisplayName("update lanza EMAIL_ALREADY_EXISTS si el email es de otro cliente")
        void update_withEmailTaken_shouldThrow() {
            givenAna();
            given(customerRepository.existsByEmailAndIdNot("otro@example.com", 1L)).willReturn(true);

            assertThatThrownBy(() -> customerService.update(1L, "Ana", "García", "otro@example.com", null, 0L))
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.EMAIL_ALREADY_EXISTS);
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("delete hace un borrado lógico")
        void delete_shouldDeactivate() {
            givenAna();
            givenSaveReturnsArgument();

            customerService.delete(1L);

            verify(customerRepository).save(argThat(customer -> !customer.isActive()));
        }
    }

    @Nested
    @DisplayName("direcciones")
    class Addresses {

        @Test
        @DisplayName("addAddress añade la dirección y guarda")
        void addAddress_shouldSave() {
            givenAna();
            givenSaveReturnsArgument();

            assertThat(customerService.addAddress(1L, newAddress()).getAddresses()).hasSize(3);
        }

        @Test
        @DisplayName("removeAddress quita la dirección y guarda")
        void removeAddress_shouldSave() {
            givenAna();
            givenSaveReturnsArgument();

            assertThat(customerService.removeAddress(1L, 11L).getAddresses()).hasSize(1);
        }

        @Test
        @DisplayName("setMainAddress cambia la principal y guarda")
        void setMainAddress_shouldSave() {
            givenAna();
            givenSaveReturnsArgument();

            var customer = customerService.setMainAddress(1L, 11L);

            assertThat(customer.getAddresses().get(1).isMain()).isTrue();
        }
    }
}
