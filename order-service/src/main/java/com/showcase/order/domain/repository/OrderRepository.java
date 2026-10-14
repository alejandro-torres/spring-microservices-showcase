package com.showcase.order.domain.repository;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.domain.model.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    /** Solo pedidos activos. */
    Optional<Order> findById(Long id);

    PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);
}
