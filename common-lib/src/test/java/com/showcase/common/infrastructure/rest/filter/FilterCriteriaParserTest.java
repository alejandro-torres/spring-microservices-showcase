package com.showcase.common.infrastructure.rest.filter;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.IS_NOT_NULL;
import static com.showcase.common.domain.filter.FilterOperator.IS_NULL;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static com.showcase.common.domain.filter.FilterOperator.LTE;
import static com.showcase.common.domain.filter.FilterOperator.NE;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.InvalidFilterException;
import com.showcase.common.domain.filter.FilterCriterion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FilterCriteriaParserTest {

    enum Status { PENDING, CONFIRMED, CANCELLED }

    private static final FilterFields FIELDS = FilterFields.of(
            FilterableField.of("name", String.class, EQ, LIKE, STARTS_WITH),
            FilterableField.of("price", BigDecimal.class, EQ, GTE, LTE, BETWEEN),
            FilterableField.of("categoryId", Long.class, EQ, IN),
            FilterableField.of("stock", Integer.class, GTE),
            FilterableField.of("createdAt", Instant.class, GTE, LTE, BETWEEN),
            FilterableField.of("birthDate", LocalDate.class, EQ),
            FilterableField.of("active", Boolean.class, EQ),
            FilterableField.of("status", Status.class, EQ, NE, IN),
            FilterableField.of("phone", String.class, IS_NULL, IS_NOT_NULL),
            FilterableField.of("externalId", UUID.class, EQ));

    private final FilterCriteriaParser parser = new FilterCriteriaParser();

    private FilterCriterion parseOne(String raw) {
        return parser.parse(List.of(raw), FIELDS).getFirst();
    }

    private void assertFails(String raw, CommonErrorCode expected) {
        assertThatThrownBy(() -> parser.parse(List.of(raw), FIELDS))
                .isInstanceOf(InvalidFilterException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Nested
    @DisplayName("parse with valid filters")
    class ValidFilters {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("returns an empty list if there are no filters")
        void parse_withoutFilters_shouldReturnEmpty(List<String> raw) {
            assertThat(parser.parse(raw, FIELDS)).isEmpty();
        }

        @Test
        @DisplayName("converts several filters to criteria with typed values")
        void parse_withSeveralFilters_shouldReturnTypedCriteria() {
            // when
            var criteria = parser.parse(
                    List.of("name:like:laptop", "price:between:100,500", "categoryId:in:1,2,3"), FIELDS);

            // then
            assertThat(criteria).containsExactly(
                    FilterCriterion.of("name", LIKE, "laptop"),
                    FilterCriterion.of("price", BETWEEN, new BigDecimal("100"), new BigDecimal("500")),
                    FilterCriterion.of("categoryId", IN, 1L, 2L, 3L));
        }

        @Test
        @DisplayName("allows ':' inside the value (dates with time)")
        void parse_withColonInValue_shouldKeepIt() {
            assertThat(parseOne("createdAt:gte:2026-10-01T10:30:00Z"))
                    .isEqualTo(FilterCriterion.of("createdAt", GTE, Instant.parse("2026-10-01T10:30:00Z")));
        }

        @Test
        @DisplayName("converts each supported type")
        void parse_shouldConvertEachSupportedType() {
            var uuid = UUID.randomUUID();
            assertThat(parseOne("stock:gte:5").value()).isEqualTo(5);
            assertThat(parseOne("birthDate:eq:1990-05-17").value()).isEqualTo(LocalDate.of(1990, 5, 17));
            assertThat(parseOne("active:eq:TRUE").value()).isEqualTo(true);
            assertThat(parseOne("status:eq:confirmed").value()).isEqualTo(Status.CONFIRMED);
            assertThat(parseOne("externalId:eq:" + uuid).value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("accepts valueless operators")
        void parse_withNoValueOperator_shouldReturnEmptyValues() {
            assertThat(parseOne("phone:isNull")).isEqualTo(FilterCriterion.of("phone", IS_NULL));
            assertThat(parseOne("phone:isNotNull:")).isEqualTo(FilterCriterion.of("phone", IS_NOT_NULL));
        }

        @Test
        @DisplayName("ignores spaces around the field, the operator and the value")
        void parse_withSpaces_shouldTrimThem() {
            assertThat(parseOne(" price : gte : 100 ")).isEqualTo(FilterCriterion.of("price", GTE, new BigDecimal("100")));
        }

        @Test
        @DisplayName("accepts a range with both ends equal")
        void parse_withEqualRangeBounds_shouldAcceptIt() {
            assertThat(parseOne("price:between:100,100").values()).hasSize(2);
        }

        @Test
        @DisplayName("accepts exactly the maximum number of filters")
        void parse_withMaxFilters_shouldAcceptThem() {
            var raw = Collections.nCopies(FilterCriteriaParser.DEFAULT_MAX_FILTERS, "price:gte:1");
            assertThat(parser.parse(raw, FIELDS)).hasSize(FilterCriteriaParser.DEFAULT_MAX_FILTERS);
        }
    }

    @Nested
    @DisplayName("parse with invalid filters")
    class InvalidFilters {

        @ParameterizedTest
        @ValueSource(strings = {"price", ":gte:10", "price::10", " ", "price:"})
        @DisplayName("rejects a format other than field:operator:value with INVALID_FILTER_FORMAT")
        void parse_withBadFormat_shouldThrowInvalidFormat(String raw) {
            assertFails(raw, CommonErrorCode.INVALID_FILTER_FORMAT);
        }

        @Test
        @DisplayName("rejects a field not on the whitelist with FILTER_FIELD_NOT_ALLOWED")
        void parse_withUnknownField_shouldThrowFieldNotAllowed() {
            assertThatThrownBy(() -> parser.parse(List.of("passwordHash:eq:x"), FIELDS))
                    .isInstanceOf(InvalidFilterException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.FILTER_FIELD_NOT_ALLOWED)
                    .satisfies(e -> assertThat(((InvalidFilterException) e).getArgs()).first().isEqualTo("passwordHash"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"price:like:10", "name:gte:a", "price:equals:10", "active:in:true"})
        @DisplayName("rejects an unknown operator or one not allowed for the field with FILTER_OPERATOR_NOT_ALLOWED")
        void parse_withOperatorNotAllowed_shouldThrow(String raw) {
            assertFails(raw, CommonErrorCode.FILTER_OPERATOR_NOT_ALLOWED);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "price:gte:abc", "price:gte: ", "categoryId:in:1,,3", "price:between:100",
                "price:between:1,2,3", "price:between:500,100", "active:eq:yes", "status:eq:SHIPPED",
                "createdAt:gte:ayer", "birthDate:eq:17/05/1990", "externalId:eq:123", "phone:isNull:x",
                "stock:gte:99999999999"
        })
        @DisplayName("rejects values that cannot be converted or do not fit the operator with INVALID_FILTER_VALUE")
        void parse_withInvalidValue_shouldThrowInvalidValue(String raw) {
            assertFails(raw, CommonErrorCode.INVALID_FILTER_VALUE);
        }

        @Test
        @DisplayName("rejects more filters than the maximum with TOO_MANY_FILTERS")
        void parse_withTooManyFilters_shouldThrow() {
            var raw = Collections.nCopies(FilterCriteriaParser.DEFAULT_MAX_FILTERS + 1, "price:gte:1");
            assertThatThrownBy(() -> parser.parse(raw, FIELDS))
                    .isInstanceOf(InvalidFilterException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.TOO_MANY_FILTERS);
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("allows configuring the maximum filters and rejects values below 1")
        void constructor_shouldValidateMaxFilters() {
            var strict = new FilterCriteriaParser(1);
            assertThatThrownBy(() -> strict.parse(List.of("price:gte:1", "price:lte:2"), FIELDS))
                    .hasFieldOrPropertyWithValue("errorCode", CommonErrorCode.TOO_MANY_FILTERS);
            assertThatThrownBy(() -> new FilterCriteriaParser(0)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
