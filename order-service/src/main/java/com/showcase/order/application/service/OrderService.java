package com.showcase.order.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.ShippingAddress;
import java.util.List;

public interface OrderService {

    /** Creates the PENDING order and starts the stock reservation saga. */
    Order create(Long customerId, List<OrderLine> lines);

    Order findById(Long id);

    PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Changes the shipping address of an order that is not closed; the lines are not modified. */
    Order update(Long id, ShippingAddress shippingAddress, Long version);

    /** Cancels a confirmed order and triggers the return of its stock. */
    Order cancel(Long id);

    void delete(Long id);
}
