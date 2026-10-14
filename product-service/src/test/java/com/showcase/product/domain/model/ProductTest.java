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
        @DisplayName("crea un producto activo con el nombre limpio y el precio con 2 decimales")
        void create_shouldNormalize() {
            var product = Product.create("  Portátil   Air 13 ", "Ligero", new BigDecimal("999"), 3, laptops());

            assertThat(product.getName()).isEqualTo("Portátil Air 13");
            assertThat(product.getPrice()).isEqualByComparingTo("999.00").hasScaleOf(2);
            assertThat(product.getStock()).isEqualTo(3);
            assertThat(product.isActive()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"A", " B "})
        @DisplayName("rechaza nombres de menos de 2 caracteres con INVALID_PRODUCT_NAME")
        void create_withInvalidName_shouldThrow(String name) {
            assertThatThrownBy(() -> Product.create(name, null, BigDecimal.TEN, 1, laptops()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_PRODUCT_NAME);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-5", "10.999", "1000000.00"})
        @DisplayName("rechaza precios no positivos, con más de 2 decimales o demasiado altos")
        void create_withInvalidPrice_shouldThrow(String price) {
            assertThatThrownBy(() -> Product.create("Portátil", null, new BigDecimal(price), 1, laptops()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_PRICE);
        }

        @Test
        @DisplayName("rechaza un stock inicial negativo")
        void create_withNegativeStock_shouldThrow() {
            assertThatThrownBy(() -> Product.create("Portátil", null, BigDecimal.TEN, -1, laptops()))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
        }
    }

    @Nested
    @DisplayName("stock")
    class Stock {

        @Test
        @DisplayName("reserva y libera unidades")
        void reserveAndRelease_shouldUpdateStock() {
            var product = laptop();

            product.reserveStock(3);
            assertThat(product.getStock()).isEqualTo(2);

            product.releaseStock(1);
            assertThat(product.getStock()).isEqualTo(3);
        }

        @Test
        @DisplayName("no reserva más de lo disponible y no modifica el stock al fallar")
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
        @DisplayName("rechaza cantidades no positivas al reservar o liberar")
        void reserveOrRelease_withInvalidQuantity_shouldThrow(int quantity) {
            var product = laptop();
            assertThatThrownBy(() -> product.reserveStock(quantity))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
            assertThatThrownBy(() -> product.releaseStock(quantity))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("ajusta el stock en positivo o negativo sin dejarlo por debajo de 0")
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
    @DisplayName("update y Category")
    class Update {

        @Test
        @DisplayName("actualiza datos y categoría sin tocar el stock")
        void update_shouldKeepStock() {
            var product = laptop();
            var accessories = new Category(2L, "Accesorios", null, true, 0L, null, null);

            product.update("Portátil Pro 16", "Nuevo modelo", new BigDecimal("1699.5"), accessories);

            assertThat(product.getName()).isEqualTo("Portátil Pro 16");
            assertThat(product.getPrice()).isEqualByComparingTo("1699.50");
            assertThat(product.getCategory().getName()).isEqualTo("Accesorios");
            assertThat(product.getStock()).isEqualTo(5);
        }

        @Test
        @DisplayName("la categoría valida y limpia su nombre")
        void category_shouldValidateName() {
            var category = Category.create("  Ropa   deportiva ", "Textil");
            assertThat(category.getName()).isEqualTo("Ropa deportiva");

            category.update("Deporte", null);
            assertThat(category.getName()).isEqualTo("Deporte");

            assertThatThrownBy(() -> Category.create("x", null))
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INVALID_CATEGORY_NAME);
        }
    }
}
