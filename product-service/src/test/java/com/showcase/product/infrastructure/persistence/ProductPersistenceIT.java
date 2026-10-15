package com.showcase.product.infrastructure.persistence;

import static com.showcase.common.domain.filter.FilterOperator.BETWEEN;
import static com.showcase.common.domain.filter.FilterOperator.EQ;
import static com.showcase.common.domain.filter.FilterOperator.GT;
import static com.showcase.common.domain.filter.FilterOperator.IN;
import static com.showcase.common.domain.filter.FilterOperator.LIKE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.product.TestcontainersConfiguration;
import com.showcase.product.domain.model.Category;
import com.showcase.product.domain.model.Product;
import com.showcase.product.domain.repository.CategoryRepository;
import com.showcase.product.domain.repository.ProductRepository;
import com.showcase.product.infrastructure.config.PersistenceConfig;
import com.showcase.product.infrastructure.persistence.mapper.CategoryEntityMapperImpl;
import com.showcase.product.infrastructure.persistence.mapper.ProductEntityMapperImpl;
import com.showcase.product.infrastructure.persistence.repository.impl.CategoryRepositoryImpl;
import com.showcase.product.infrastructure.persistence.repository.impl.ProductRepositoryImpl;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, ProductPersistenceIT.PersistenceTestConfiguration.class})
class ProductPersistenceIT {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    @Import({PersistenceConfig.class, ProductRepositoryImpl.class, CategoryRepositoryImpl.class,
            ProductEntityMapperImpl.class, CategoryEntityMapperImpl.class})
    static class PersistenceTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        GenericSpecificationBuilder genericSpecificationBuilder() {
            return new GenericSpecificationBuilder(true);
        }
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category laptops;
    private Category audio;

    @BeforeEach
    void createCatalog() {
        laptops = categoryRepository.save(Category.create("Portátiles", null));
        audio = categoryRepository.save(Category.create("Audio", null));
        productRepository.save(Product.create("Portátil Pro 15", null, new BigDecimal("1499.00"), 5, laptops));
        productRepository.save(Product.create("Portátil Air 13", null, new BigDecimal("999.00"), 0, laptops));
        productRepository.save(Product.create("Auriculares inalámbricos", null, new BigDecimal("129.90"), 40, audio));
        var discontinued = productRepository.save(Product.create("Altavoz antiguo", null, new BigDecimal("59.00"), 2, audio));
        discontinued.deactivate();
        productRepository.save(discontinued);
        entityManager.flush();
        entityManager.clear();
    }

    private List<String> names(FilterCriterion... filters) {
        return productRepository.findAll(List.of(filters), new PageQuery(0, 20, "name", SortDirection.ASC), false)
                .content().stream().map(Product::getName).toList();
    }

    @Nested
    @DisplayName("guardar y leer")
    class SaveAndFind {

        @Test
        @DisplayName("guarda con auditoría y versión, y carga la categoría junto al producto")
        void save_shouldAuditAndLoadCategory() {
            var saved = productRepository.save(Product.create("Ratón óptico", null, new BigDecimal("19.90"), 10, laptops));
            entityManager.clear();

            var found = productRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getVersion()).isZero();
            assertThat(found.getCreatedAt()).isEqualTo(NOW);
            assertThat(found.getPrice()).isEqualByComparingTo("19.90");
            assertThat(found.getCategory().getName()).isEqualTo("Portátiles");
        }

        @Test
        @DisplayName("el stock y la categoría se actualizan e incrementan la versión")
        void update_shouldIncrementVersion() {
            var product = productRepository.findAll(List.of(FilterCriterion.of("name", EQ, "Portátil Pro 15")),
                    PageQuery.firstPage(), false).content().getFirst();

            product.reserveStock(2);
            product.update(product.getName(), "Edición 2026", product.getPrice(), audio);
            var updated = productRepository.save(product);

            assertThat(updated.getVersion()).isEqualTo(1L);
            assertThat(updated.getStock()).isEqualTo(3);
            assertThat(updated.getCategory().getName()).isEqualTo("Audio");
        }

        @Test
        @DisplayName("la base de datos rechaza un stock negativo aunque fallara el dominio (CHECK)")
        void negativeStock_shouldBeRejectedByDatabase() {
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE products SET stock = -1 WHERE name = 'Portátil Pro 15'"))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_products_stock_not_negative");
        }
    }

    @Nested
    @DisplayName("filtros dinámicos y consultas")
    class Queries {

        @Test
        @DisplayName("filtra por nombre sin tildes, rango de precio, stock y categoría (join)")
        void findAll_withFilters() {
            assertThat(names(FilterCriterion.of("name", LIKE, "PORTATIL")))
                    .containsExactly("Portátil Air 13", "Portátil Pro 15");
            assertThat(names(FilterCriterion.of("price", BETWEEN, new BigDecimal("100"), new BigDecimal("1000"))))
                    .containsExactly("Auriculares inalámbricos", "Portátil Air 13");
            assertThat(names(FilterCriterion.of("stock", GT, 0), FilterCriterion.of("categoryName", LIKE, "portatil")))
                    .containsExactly("Portátil Pro 15");
            assertThat(names(FilterCriterion.of("categoryId", IN, audio.getId())))
                    .containsExactly("Auriculares inalámbricos");
        }

        @Test
        @DisplayName("el listado no provoca una consulta por producto para cargar su categoría (sin N+1)")
        void findAll_shouldNotTriggerNPlusOne() {
            var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();

            productRepository.findAll(List.of(), new PageQuery(0, 20, "name", SortDirection.ASC), true)
                    .content().forEach(product -> product.getCategory().getName());

            // Una sola consulta para 4 productos con su categoría. Spring Data se ahorra además el count
            // cuando la primera página no se llena (el total es el número de filas devueltas).
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("la consulta JPQL por categoría pagina con su propio countQuery")
        void findActiveByCategory_shouldPaginateWithCountQuery() {
            var page = productRepository.findActiveByCategory(laptops.getId(), new PageQuery(0, 1, "price", SortDirection.DESC));

            assertThat(page.content()).extracting(Product::getName).containsExactly("Portátil Pro 15");
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("comprueba nombres sin distinguir mayúsculas y si una categoría tiene productos activos")
        void existsQueries() {
            assertThat(productRepository.existsByName("portátil pro 15")).isTrue();
            assertThat(productRepository.existsByName("altavoz antiguo")).isTrue();
            assertThat(categoryRepository.existsByName("AUDIO")).isTrue();
            assertThat(productRepository.existsActiveByCategoryId(audio.getId())).isTrue();

            var empty = categoryRepository.save(Category.create("Vacía", null));
            assertThat(productRepository.existsActiveByCategoryId(empty.getId())).isFalse();
        }
    }
}
