package com.showcase.order.application.service.impl;

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.order.application.service.OrderWriter;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.domain.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderWriterImpl implements OrderWriter {

    private final OrderRepository orderRepository;

    public OrderWriterImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** El pedido y sus líneas se guardan juntos (cascada); en la fase de mensajería, también su evento. */
    @Override
    @Transactional
    public Order create(Order order) {
        return orderRepository.save(order);
    }

    /** Se vuelve a leer dentro de la transacción: entre la lectura inicial y ahora el pedido pudo cambiar. */
    @Override
    @Transactional
    public Order replaceItems(Long id, Long version, List<OrderItem> items) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND, id));
        order.checkVersion(version);
        order.replaceItems(items);
        return orderRepository.save(order);
    }
}
