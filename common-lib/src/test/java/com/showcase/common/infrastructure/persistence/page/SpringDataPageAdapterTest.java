package com.showcase.common.infrastructure.persistence.page;

import static org.assertj.core.api.Assertions.assertThat;

import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class SpringDataPageAdapterTest {

    @Nested
    @DisplayName("toPageable")
    class ToPageable {

        @Test
        @DisplayName("converts page, size and sort, translating the field and breaking ties by id")
        void toPageable_shouldMapSortAndAddIdTieBreaker() {
            // given
            var mapping = FilterMapping.of(Map.of("categoryName", "category.name"));

            // when
            var pageable = SpringDataPageAdapter.toPageable(new PageQuery(2, 25, "categoryName", SortDirection.DESC), mapping);

            // then
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(25);
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("category.name"), Sort.Order.asc("id")));
        }

        @Test
        @DisplayName("does not duplicate the id when already sorting by id")
        void toPageable_sortedById_shouldNotDuplicateId() {
            var pageable = SpringDataPageAdapter.toPageable(PageQuery.firstPage(), FilterMapping.identity());
            assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("id")));
        }
    }

    @Nested
    @DisplayName("toPageResult")
    class ToPageResult {

        @Test
        @DisplayName("converts the content and copies the totals of the Page")
        void toPageResult_shouldMapContentAndTotals() {
            // given
            var page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 7);

            // when
            var result = SpringDataPageAdapter.toPageResult(page, n -> "n" + n);

            // then
            assertThat(result.content()).containsExactly("n1", "n2");
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(7);
            assertThat(result.totalPages()).isEqualTo(4);
            assertThat(result.first()).isFalse();
            assertThat(result.last()).isFalse();
        }
    }
}
