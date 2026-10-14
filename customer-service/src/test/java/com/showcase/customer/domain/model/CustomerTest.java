package com.showcase.customer.domain.model;

import static com.showcase.customer.testdata.CustomerMother.ana;
import static com.showcase.customer.testdata.CustomerMother.newAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.BusinessRuleException;
import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CustomerTest {

    @Nested
    @DisplayName("create y update")
    class CreateAndUpdate {

        @Test
        @DisplayName("normaliza el email y recorta los nombres")
        void create_shouldNormalizeFields() {
            var customer = Customer.create(null, " Ana ", "García", "Ana@Example.COM", null);

            assertThat(customer.getEmail()).isEqualTo("ana@example.com");
            assertThat(customer.fullName()).isEqualTo("Ana García");
            assertThat(customer.isActive()).isTrue();
        }

        @Test
        @DisplayName("rechaza un campo obligatorio vacío")
        void create_withBlankName_shouldThrow() {
            assertThatThrownBy(() -> Customer.create(null, " ", "García", "a@b.com", null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.INVALID_CUSTOMER_DATA);
        }

        @Test
        @DisplayName("update cambia los datos de contacto")
        void update_shouldChangeData() {
            var customer = ana();

            customer.update("Ana María", "García", "ANA.M@example.com", "699000000");

            assertThat(customer.getFirstName()).isEqualTo("Ana María");
            assertThat(customer.getEmail()).isEqualTo("ana.m@example.com");
            assertThat(customer.getPhone()).isEqualTo("699000000");
        }
    }

    @Nested
    @DisplayName("direcciones")
    class Addresses {

        @Test
        @DisplayName("la primera dirección pasa a ser la principal y el país se guarda en mayúsculas")
        void addAddress_first_shouldBeMain() {
            var customer = Customer.create(null, "Ana", "García", "a@b.com", null);

            customer.addAddress(newAddress());
            customer.addAddress(newAddress());

            assertThat(customer.getAddresses()).extracting(Address::isMain).containsExactly(true, false);
            assertThat(customer.getAddresses().getFirst().getCountry()).isEqualTo("ES");
        }

        @Test
        @DisplayName("no admite más de 5 direcciones")
        void addAddress_overLimit_shouldThrow() {
            var customer = ana();
            for (int i = 0; i < 3; i++) {
                customer.addAddress(newAddress());
            }

            assertThatThrownBy(() -> customer.addAddress(newAddress()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.TOO_MANY_ADDRESSES);
        }

        @Test
        @DisplayName("al borrar la principal, la siguiente pasa a serlo")
        void removeAddress_main_shouldPromoteNext() {
            var customer = ana();

            customer.removeAddress(10L);

            assertThat(customer.getAddresses()).extracting(Address::getId, Address::isMain)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, true));
        }

        @Test
        @DisplayName("al borrar una secundaria, la principal no cambia")
        void removeAddress_secondary_shouldKeepMain() {
            var customer = ana();

            customer.removeAddress(11L);

            assertThat(customer.getAddresses()).extracting(Address::getId, Address::isMain)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, true));
        }

        @Test
        @DisplayName("al borrar todas las direcciones el cliente se queda sin ninguna")
        void removeAddress_all_shouldLeaveEmpty() {
            var customer = ana();

            customer.removeAddress(10L);
            customer.removeAddress(11L);

            assertThat(customer.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("setMainAddress deja una única dirección principal")
        void setMainAddress_shouldLeaveOnlyOneMain() {
            var customer = ana();

            customer.setMainAddress(11L);

            assertThat(customer.getAddresses()).extracting(Address::isMain).containsExactly(false, true);
        }

        @Test
        @DisplayName("lanza ADDRESS_NOT_FOUND si la dirección no es del cliente")
        void setMainAddress_unknown_shouldThrow() {
            assertThatThrownBy(() -> ana().setMainAddress(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.ADDRESS_NOT_FOUND);
        }
    }
}
