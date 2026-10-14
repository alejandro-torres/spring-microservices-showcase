package com.showcase.order.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.domain.model.Order;
import java.util.List;

public interface OrderService {

    Order create(Long customerId, List<OrderLine> lines);

    Order findById(Long id);

    PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Sustituye las líneas de un pedido pendiente. */
    Order update(Long id, List<OrderLine> lines, Long version);

    Order cancel(Long id);

    void delete(Long id);
}
