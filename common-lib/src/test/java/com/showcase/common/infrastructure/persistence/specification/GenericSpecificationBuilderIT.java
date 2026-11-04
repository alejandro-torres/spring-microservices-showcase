package com.showcase.common.infrastructure.persistence.specification;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GT;
import static com.showcase.common.domain.filter.FilterOperator.GTE;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.IS_NOT_NULL;
import static com.showcase.common.domain.filter.FilterOperator.IS_NULL;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static com.showcase.common.domain.filter.FilterOperator.LT;
import static com.showcase.common.domain.filter.FilterOperator.LTE;
import static com.showcase.common.domain.filter.FilterOperator.NE;
import static com.showcase.common.domain.filter.FilterOperator.STARTS_WITH;
import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.infrastructure.persistence.specification.model.CategoryEntity;
import com.showcase.common.infrastructure.persistence.specification.model.LabelEntity;
import com.showcase.common.infrastructure.persistence.specification.model.ProductEntity;
import com.showcase.common.infrastructure.persistence.specification.model.ProductRepository;
import com.showcase.common.infrastructure.persistence.specification.model.ProductStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GenericSpecificationBuilderIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withInitScript("unaccent.sql");

    private static final FilterMapping MAPPING = FilterMapping.of(Map.of(
            "categoryId", "category.id",
            "categoryName", "category.name",
            "label", "labels.name"));

    @Autowired
    private ProductRepository repository;

    @Autowired
    private EntityManager entityManager;

    private final GenericSpecificationBuilder builder = new GenericSpecificationBuilder(true);

    private CategoryEntity laptops;
    private CategoryEntity clothes;

    @BeforeEach
    void setUp() {
        laptops = persist(new CategoryEntity("Laptops"));
        clothes = persist(new CategoryEntity("Clothing"));
        var offer = persist(new LabelEntity("offer"));
        var news = persist(new LabelEntity("new"));

        repository.saveAll(List.of(
                new ProductEntity("Laptop Pro 15", "High end", new BigDecimal("1499.00"),
                        ProductStatus.PUBLISHED, Instant.parse("2026-01-10T10:00:00Z"), laptops).withLabels(offer, news),
                new ProductEntity("Laptop Air 13", null, new BigDecimal("999.00"),
                        ProductStatus.PUBLISHED, Instant.parse("2026-03-05T10:00:00Z"), laptops).withLabels(offer),
                new ProductEntity("Café tee 100%", "Basic", new BigDecimal("19.90"),
                        ProductStatus.DRAFT, Instant.parse("2026-05-20T10:00:00Z"), clothes),
                new ProductEntity("Café_tux", null, new BigDecimal("29.90"),
                        ProductStatus.DISCONTINUED, Instant.parse("2026-07-01T10:00:00Z"), clothes),
                new ProductEntity("Old laptop", "Retired", new BigDecimal("299.00"),
                        ProductStatus.DISCONTINUED, Instant.parse("2025-06-01T10:00:00Z"), laptops).inactive()));
        entityManager.flush();
        entityManager.clear();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private List<String> names(FilterCriterion... criteria) {
        return names(false, criteria);
    }

    private List<String> names(boolean includeInactive, FilterCriterion... criteria) {
        return repository.findAll(builder.<ProductEntity>build(List.of(criteria), MAPPING, includeInactive),
                        Sort.by("name"))
                .stream().map(ProductEntity::getName).toList();
    }

    @Nested
    @DisplayName("comparison operators")
    class ComparisonOperators {

        @Test
        @DisplayName("eq and ne compare for equality, also with enums")
        void eqAndNe_shouldCompareEquality() {
            assertThat(names(FilterCriterion.of("status", EQ, ProductStatus.DRAFT))).containsExactly("Café tee 100%");
            assertThat(names(FilterCriterion.of("status", NE, ProductStatus.PUBLISHED)))
                    .containsExactly("Café tee 100%", "Café_tux");
        }

        @Test
        @DisplayName("gt, gte, lt and lte compare numbers")
        void ranges_shouldCompareNumbers() {
            assertThat(names(FilterCriterion.of("price", GT, new BigDecimal("999.00")))).containsExactly("Laptop Pro 15");
            assertThat(names(FilterCriterion.of("price", GTE, new BigDecimal("999.00"))))
                    .containsExactly("Laptop Air 13", "Laptop Pro 15");
            assertThat(names(FilterCriterion.of("price", LT, new BigDecimal("29.90")))).containsExactly("Café tee 100%");
            assertThat(names(FilterCriterion.of("price", LTE, new BigDecimal("29.90"))))
                    .containsExactly("Café tee 100%", "Café_tux");
        }

        @Test
        @DisplayName("between includes both ends and works with dates")
        void between_shouldIncludeBoundsWithDates() {
            assertThat(names(FilterCriterion.of("createdAt", BETWEEN,
                    Instant.parse("2026-03-05T10:00:00Z"), Instant.parse("2026-05-20T10:00:00Z"))))
                    .containsExactly("Café tee 100%", "Laptop Air 13");
        }

        @Test
        @DisplayName("in accepts several values and isNull / isNotNull check for empties")
        void inAndNullChecks_shouldWork() {
            assertThat(names(FilterCriterion.of("status", IN, ProductStatus.DRAFT, ProductStatus.DISCONTINUED)))
                    .containsExactly("Café tee 100%", "Café_tux");
            assertThat(names(FilterCriterion.of("description", IS_NULL)))
                    .containsExactly("Café_tux", "Laptop Air 13");
            assertThat(names(FilterCriterion.of("description", IS_NOT_NULL)))
                    .containsExactly("Café tee 100%", "Laptop Pro 15");
        }
    }

    @Nested
    @DisplayName("text searches")
    class TextOperators {

        @Test
        @DisplayName("like ignores case and accents")
        void like_shouldIgnoreCaseAndAccents() {
            assertThat(names(FilterCriterion.of("name", LIKE, "LAPTOP"))).containsExactly("Laptop Air 13", "Laptop Pro 15");
            assertThat(names(FilterCriterion.of("name", LIKE, "cafe tee"))).containsExactly("Café tee 100%");
        }

        @Test
        @DisplayName("like treats the user's % and _ as text, not wildcards")
        void like_shouldEscapeWildcards() {
            assertThat(names(FilterCriterion.of("name", LIKE, "100%"))).containsExactly("Café tee 100%");
            assertThat(names(FilterCriterion.of("name", LIKE, "e_t"))).containsExactly("Café_tux");
            assertThat(names(FilterCriterion.of("name", LIKE, "%"))).containsExactly("Café tee 100%");
        }

        @Test
        @DisplayName("startsWith searches from the beginning")
        void startsWith_shouldMatchPrefix() {
            assertThat(names(FilterCriterion.of("name", STARTS_WITH, "cafe")))
                    .containsExactly("Café tee 100%", "Café_tux");
            assertThat(names(FilterCriterion.of("name", STARTS_WITH, "13"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("joins, soft delete and pagination")
    class JoinsAndPagination {

        @Test
        @DisplayName("translates the public name to the JPA path with joins")
        void mapping_shouldResolveJoins() {
            assertThat(names(FilterCriterion.of("categoryId", IN, laptops.getId())))
                    .containsExactly("Laptop Air 13", "Laptop Pro 15");
            assertThat(names(FilterCriterion.of("categoryName", LIKE, "clothing")))
                    .containsExactly("Café tee 100%", "Café_tux");
        }

        @Test
        @DisplayName("does not duplicate rows or skew the total when filtering by a collection")
        void collectionJoin_shouldNotDuplicateRowsOrCount() {
            // given: "Laptop Pro 15" has both tags
            var spec = builder.<ProductEntity>build(
                    List.of(FilterCriterion.of("label", IN, "offer", "new")), MAPPING, false);

            // when
            var page = repository.findAll(spec, PageRequest.of(0, 10, Sort.by("name")));

            // then
            assertThat(page.getContent()).extracting(ProductEntity::getName)
                    .containsExactly("Laptop Air 13", "Laptop Pro 15");
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("excludes inactive records unless explicitly requested")
        void inactive_shouldBeExcludedByDefault() {
            assertThat(names(FilterCriterion.of("categoryId", EQ, laptops.getId()))).hasSize(2);
            assertThat(names(true, FilterCriterion.of("categoryId", EQ, laptops.getId())))
                    .containsExactly("Laptop Air 13", "Laptop Pro 15", "Old laptop");
        }

        @Test
        @DisplayName("combines several filters with AND and the page total reflects the filters")
        void severalFilters_shouldBeCombinedWithAnd() {
            var spec = builder.<ProductEntity>build(List.of(
                    FilterCriterion.of("name", LIKE, "laptop"),
                    FilterCriterion.of("price", BETWEEN, new BigDecimal("500"), new BigDecimal("1000"))), MAPPING, false);

            var page = repository.findAll(spec, PageRequest.of(0, 1));

            assertThat(page.getContent()).extracting(ProductEntity::getName).containsExactly("Laptop Air 13");
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getTotalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("without filters it returns all active ones")
        void noFilters_shouldReturnAllActive() {
            assertThat(names()).hasSize(4);
        }
    }
}
