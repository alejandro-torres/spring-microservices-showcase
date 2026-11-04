package com.showcase.product.domain.model;

import static com.showcase.product.testdata.ProductMother.laptop;
import static com.showcase.product.testdata.ProductMother.laptops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.InvalidRequestException;
import com.showcase.product.domain.exception.InsufficientStockException;
import com.showcase.product.domain.exception.ProductErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("creates an active product with the clean name and the price with 2 decimals")
        void create_shouldNormalize() {
            var product = Product.create("  Laptop   Air 13 ", "Light", new BigDecimal("999"), 3, laptops());

            assertThat(product.getName()).isEqualTo("Laptop Air 13");
            assertThat(product.getPrice()).isEqualByComparingTo("999.00").hasScaleOf(2);
            assertThat(product.getStock()).isEqualTo(3);
            assertThat(product.isActive()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"A", " B "})
        @DisplayName("rejects names shorter than 2 characters with INVALID_PRODUCT_NAME")
        void create_withInvalidName_shouldThrow(String name) {
            assertThatThrownBy(() -> Product.create(name, null, BigDecimal.TEN, 1, laptops()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5", "10.999", "1000000.00"})
        @DisplayName("rejects non-positive prices, with more than 2 decimals or too high")
        void create_withInvalidPrice_shouldThrow(String price) {
            assertThatThrownBy(() -> Product.create("Laptop", null, new BigDecimal(price), 1, laptops()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_PRICE);
        }

        @Test
        @DisplayName("rejects a negative initial stock")
        void create_withNegativeStock_shouldThrow() {
            assertThatThrownBy(() -> Product.create("Laptop", null, BigDecimal.TEN, -1, laptops()))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("stock")
    class Stock {

        @Test
        @DisplayName("reserves and releases units")
        void reserveAndRelease_shouldUpdateStock() {
            var product = laptop();

            product.reserveStock(3);
            assertThat(product.getStock()).isEqualTo(2);

            product.releaseStock(1);
            assertThat(product.getStock()).isEqualTo(3);
        }

        @Test
        @DisplayName("does not reserve more than available and does not change the stock on failure")
        void reserve_moreThanAvailable_shouldThrowWithoutChanges() {
            var product = laptop();

            assertThatThrownBy(() -> product.reserveStock(6))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);
            assertThat(product.getStock()).isEqualTo(5);
            assertThat(product.hasStock(5)).isTrue();
            assertThat(product.hasStock(6)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -2})
        @DisplayName("rejects non-positive quantities when reserving or releasing")
        void reserveOrRelease_withInvalidQuantity_shouldThrow(int quantity) {
            var product = laptop();
            assertThatThrownBy(() -> product.reserveStock(quantity))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
            assertThatThrownBy(() -> product.releaseStock(quantity))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("adjusts the stock up or down without taking it below 0")
        void adjustStock_shouldNeverGoNegative() {
            var product = laptop();

            product.adjustStock(10);
            product.adjustStock(-15);
            assertThat(product.getStock()).isZero();

            assertThatThrownBy(() -> product.adjustStock(-1)).isInstanceOf(InsufficientStockException.class);
            assertThatThrownBy(() -> product.adjustStock(0))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("update and Category")
    class Update {

        @Test
        @DisplayName("updates data and category without touching the stock")
        void update_shouldKeepStock() {
            var product = laptop();
            var accessories = new Category(2L, "Accessories", null, true, 0L, null, null);

            product.update("Laptop Pro 16", "New model", new BigDecimal("1699.5"), accessories);

            assertThat(product.getName()).isEqualTo("Laptop Pro 16");
            assertThat(product.getPrice()).isEqualByComparingTo("1699.50");
            assertThat(product.getCategory().getName()).isEqualTo("Accessories");
            assertThat(product.getStock()).isEqualTo(5);
        }

        @Test
        @DisplayName("the category validates and cleans its name")
        void category_shouldValidateName() {
            var category = Category.create("  Sports   clothing ", "Textile");
            assertThat(category.getName()).isEqualTo("Sports clothing");

            category.update("Sports", null);
            assertThat(category.getName()).isEqualTo("Sports");

            assertThatThrownBy(() -> Category.create("x", null))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_CATEGORY_NAME);
        }
    }
}
