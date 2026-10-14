package com.showcase.order.application.service;

import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.ShippingAddress;
import java.util.List;

public interface OrderService {

    /** Crea el pedido PENDING y lanza la saga de reserva de stock. */
    Order create(Long customerId, List<OrderLine> lines);

    Order findById(Long id);

    PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive);

    /** Cambia la dirección de envío de un pedido que no está cerrado; las líneas no se modifican. */
    Order update(Long id, ShippingAddress shippingAddress, Long version);

    /** Cancela un pedido confirmado y lanza la devolución de su stock. */
    Order cancel(Long id);

    void delete(Long id);
}
