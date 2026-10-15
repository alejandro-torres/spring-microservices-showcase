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
        laptops = persist(new CategoryEntity("Portátiles"));
        clothes = persist(new CategoryEntity("Ropa"));
        var offer = persist(new LabelEntity("oferta"));
        var news = persist(new LabelEntity("novedad"));

        repository.saveAll(List.of(
                new ProductEntity("Portátil Pro 15", "Gama alta", new BigDecimal("1499.00"),
                        ProductStatus.PUBLISHED, Instant.parse("2026-01-10T10:00:00Z"), laptops).withLabels(offer, news),
                new ProductEntity("Portátil Air 13", null, new BigDecimal("999.00"),
                        ProductStatus.PUBLISHED, Instant.parse("2026-03-05T10:00:00Z"), laptops).withLabels(offer),
                new ProductEntity("Camiseta 100% algodón", "Básica", new BigDecimal("19.90"),
                        ProductStatus.DRAFT, Instant.parse("2026-05-20T10:00:00Z"), clothes),
                new ProductEntity("Camiseta_tecnica", null, new BigDecimal("29.90"),
                        ProductStatus.DISCONTINUED, Instant.parse("2026-07-01T10:00:00Z"), clothes),
                new ProductEntity("Portátil antiguo", "Retirado", new BigDecimal("299.00"),
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
    @DisplayName("operadores de comparación")
    class ComparisonOperators {

        @Test
        @DisplayName("eq y ne comparan por igualdad, también con enums")
        void eqAndNe_shouldCompareEquality() {
            assertThat(names(FilterCriterion.of("status", EQ, ProductStatus.DRAFT))).containsExactly("Camiseta 100% algodón");
            assertThat(names(FilterCriterion.of("status", NE, ProductStatus.PUBLISHED)))
                    .containsExactly("Camiseta 100% algodón", "Camiseta_tecnica");
        }

        @Test
        @DisplayName("gt, gte, lt y lte comparan números")
        void ranges_shouldCompareNumbers() {
            assertThat(names(FilterCriterion.of("price", GT, new BigDecimal("999.00")))).containsExactly("Portátil Pro 15");
            assertThat(names(FilterCriterion.of("price", GTE, new BigDecimal("999.00"))))
                    .containsExactly("Portátil Air 13", "Portátil Pro 15");
            assertThat(names(FilterCriterion.of("price", LT, new BigDecimal("29.90")))).containsExactly("Camiseta 100% algodón");
            assertThat(names(FilterCriterion.of("price", LTE, new BigDecimal("29.90"))))
                    .containsExactly("Camiseta 100% algodón", "Camiseta_tecnica");
        }

        @Test
        @DisplayName("between incluye los dos extremos y funciona con fechas")
        void between_shouldIncludeBoundsWithDates() {
            assertThat(names(FilterCriterion.of("createdAt", BETWEEN,
                    Instant.parse("2026-03-05T10:00:00Z"), Instant.parse("2026-05-20T10:00:00Z"))))
                    .containsExactly("Camiseta 100% algodón", "Portátil Air 13");
        }

        @Test
        @DisplayName("in admite varios valores e isNull / isNotNull comprueban vacíos")
        void inAndNullChecks_shouldWork() {
            assertThat(names(FilterCriterion.of("status", IN, ProductStatus.DRAFT, ProductStatus.DISCONTINUED)))
                    .containsExactly("Camiseta 100% algodón", "Camiseta_tecnica");
            assertThat(names(FilterCriterion.of("description", IS_NULL)))
                    .containsExactly("Camiseta_tecnica", "Portátil Air 13");
            assertThat(names(FilterCriterion.of("description", IS_NOT_NULL)))
                    .containsExactly("Camiseta 100% algodón", "Portátil Pro 15");
        }
    }

    @Nested
    @DisplayName("búsquedas de texto")
    class TextOperators {

        @Test
        @DisplayName("like no distingue mayúsculas ni tildes")
        void like_shouldIgnoreCaseAndAccents() {
            assertThat(names(FilterCriterion.of("name", LIKE, "PORTATIL"))).containsExactly("Portátil Air 13", "Portátil Pro 15");
            assertThat(names(FilterCriterion.of("name", LIKE, "algodon"))).containsExactly("Camiseta 100% algodón");
        }

        @Test
        @DisplayName("like trata % y _ del usuario como texto, no como comodines")
        void like_shouldEscapeWildcards() {
            assertThat(names(FilterCriterion.of("name", LIKE, "100%"))).containsExactly("Camiseta 100% algodón");
            assertThat(names(FilterCriterion.of("name", LIKE, "a_t"))).containsExactly("Camiseta_tecnica");
            assertThat(names(FilterCriterion.of("name", LIKE, "%"))).containsExactly("Camiseta 100% algodón");
        }

        @Test
        @DisplayName("startsWith busca por el principio")
        void startsWith_shouldMatchPrefix() {
            assertThat(names(FilterCriterion.of("name", STARTS_WITH, "cami")))
                    .containsExactly("Camiseta 100% algodón", "Camiseta_tecnica");
            assertThat(names(FilterCriterion.of("name", STARTS_WITH, "13"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("joins, borrado lógico y paginación")
    class JoinsAndPagination {

        @Test
        @DisplayName("traduce el nombre público al path JPA con joins")
        void mapping_shouldResolveJoins() {
            assertThat(names(FilterCriterion.of("categoryId", IN, laptops.getId())))
                    .containsExactly("Portátil Air 13", "Portátil Pro 15");
            assertThat(names(FilterCriterion.of("categoryName", LIKE, "ropa")))
                    .containsExactly("Camiseta 100% algodón", "Camiseta_tecnica");
        }

        @Test
        @DisplayName("no duplica filas ni falsea el total al filtrar por una colección")
        void collectionJoin_shouldNotDuplicateRowsOrCount() {
            // given: "Portátil Pro 15" tiene las dos etiquetas
            var spec = builder.<ProductEntity>build(
                    List.of(FilterCriterion.of("label", IN, "oferta", "novedad")), MAPPING, false);

            // when
            var page = repository.findAll(spec, PageRequest.of(0, 10, Sort.by("name")));

            // then
            assertThat(page.getContent()).extracting(ProductEntity::getName)
                    .containsExactly("Portátil Air 13", "Portátil Pro 15");
            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("excluye los registros inactivos salvo que se pidan explícitamente")
        void inactive_shouldBeExcludedByDefault() {
            assertThat(names(FilterCriterion.of("categoryId", EQ, laptops.getId()))).hasSize(2);
            assertThat(names(true, FilterCriterion.of("categoryId", EQ, laptops.getId())))
                    .containsExactly("Portátil Air 13", "Portátil Pro 15", "Portátil antiguo");
        }

        @Test
        @DisplayName("combina varios filtros con AND y el total de la página refleja los filtros")
        void severalFilters_shouldBeCombinedWithAnd() {
            var spec = builder.<ProductEntity>build(List.of(
                    FilterCriterion.of("name", LIKE, "portatil"),
                    FilterCriterion.of("price", BETWEEN, new BigDecimal("500"), new BigDecimal("1000"))), MAPPING, false);

            var page = repository.findAll(spec, PageRequest.of(0, 1));

            assertThat(page.getContent()).extracting(ProductEntity::getName).containsExactly("Portátil Air 13");
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getTotalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("sin filtros devuelve todos los activos")
        void noFilters_shouldReturnAllActive() {
            assertThat(names()).hasSize(4);
        }
    }
}
