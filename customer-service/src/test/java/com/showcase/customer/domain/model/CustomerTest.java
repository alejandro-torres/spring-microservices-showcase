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
    @DisplayName("create and update")
    class CreateAndUpdate {

        @Test
        @DisplayName("normalizes the email and trims the names")
        void create_shouldNormalizeFields() {
            var customer = Customer.create(null, " Ana ", "García", "Ana@Example.COM", null);

            assertThat(customer.getEmail()).isEqualTo("ana@example.com");
            assertThat(customer.fullName()).isEqualTo("Ana García");
            assertThat(customer.isActive()).isTrue();
        }

        @Test
        @DisplayName("rejects an empty required field")
        void create_withBlankName_shouldThrow() {
            assertThatThrownBy(() -> Customer.create(null, " ", "García", "a@b.com", null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.INVALID_CUSTOMER_DATA);
        }

        @Test
        @DisplayName("update changes the contact data")
        void update_shouldChangeData() {
            var customer = ana();

            customer.update("Ana María", "García", "ANA.M@example.com", "699000000");

            assertThat(customer.getFirstName()).isEqualTo("Ana María");
            assertThat(customer.getEmail()).isEqualTo("ana.m@example.com");
            assertThat(customer.getPhone()).isEqualTo("699000000");
        }
    }

    @Nested
    @DisplayName("addresses")
    class Addresses {

        @Test
        @DisplayName("the first address becomes the primary one and the country is stored in uppercase")
        void addAddress_first_shouldBeMain() {
            var customer = Customer.create(null, "Ana", "García", "a@b.com", null);

            customer.addAddress(newAddress());
            customer.addAddress(newAddress());

            assertThat(customer.getAddresses()).extracting(Address::isMain).containsExactly(true, false);
            assertThat(customer.getAddresses().getFirst().getCountry()).isEqualTo("ES");
        }

        @Test
        @DisplayName("does not allow more than 5 addresses")
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
        @DisplayName("deleting the primary makes the next one primary")
        void removeAddress_main_shouldPromoteNext() {
            var customer = ana();

            customer.removeAddress(10L);

            assertThat(customer.getAddresses()).extracting(Address::getId, Address::isMain)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, true));
        }

        @Test
        @DisplayName("deleting a secondary leaves the primary unchanged")
        void removeAddress_secondary_shouldKeepMain() {
            var customer = ana();

            customer.removeAddress(11L);

            assertThat(customer.getAddresses()).extracting(Address::getId, Address::isMain)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, true));
        }

        @Test
        @DisplayName("deleting all the addresses leaves the customer with none")
        void removeAddress_all_shouldLeaveEmpty() {
            var customer = ana();

            customer.removeAddress(10L);
            customer.removeAddress(11L);

            assertThat(customer.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("setMainAddress leaves a single primary address")
        void setMainAddress_shouldLeaveOnlyOneMain() {
            var customer = ana();

            customer.setMainAddress(11L);

            assertThat(customer.getAddresses()).extracting(Address::isMain).containsExactly(false, true);
        }

        @Test
        @DisplayName("throws ADDRESS_NOT_FOUND if the address does not belong to the customer")
        void setMainAddress_unknown_shouldThrow() {
            assertThatThrownBy(() -> ana().setMainAddress(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CustomerErrorCode.ADDRESS_NOT_FOUND);
        }
    }
}
