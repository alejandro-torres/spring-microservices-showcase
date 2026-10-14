package com.showcase.order.infrastructure.persistence.repository.impl;

import com.showcase.common.domain.exception.ResourceNotFoundException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.persistence.page.SpringDataPageAdapter;
import com.showcase.common.infrastructure.persistence.specification.FilterMapping;
import com.showcase.common.infrastructure.persistence.specification.GenericSpecificationBuilder;
import com.showcase.order.domain.exception.OrderErrorCode;
import com.showcase.order.domain.model.Order;
import com.showcase.order.domain.model.OrderItem;
import com.showcase.order.domain.repository.OrderRepository;
import com.showcase.order.infrastructure.persistence.entity.OrderEntity;
import com.showcase.order.infrastructure.persistence.entity.OrderItemEntity;
import com.showcase.order.infrastructure.persistence.mapper.OrderEntityMapper;
import com.showcase.order.infrastructure.persistence.repository.OrderJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    /** {@code productId} filtra los pedidos que contienen ese producto (join con las líneas). */
    static final FilterMapping MAPPING = FilterMapping.of(Map.of("productId", "items.productId"));

    private final OrderJpaRepository jpaRepository;
    private final OrderEntityMapper mapper;
    private final GenericSpecificationBuilder specificationBuilder;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository, OrderEntityMapper mapper,
                               GenericSpecificationBuilder specificationBuilder) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = order.getId() == null ? new OrderEntity() : jpaRepository.findById(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException(OrderErrorCode.ORDER_NOT_FOUND, order.getId()));
        mapper.copyToEntity(order, entity);
        syncItems(order.getItems(), entity);
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    /**
     * Sincroniza por producto: actualiza las líneas que siguen, borra las que ya no están y añade las nuevas.
     * Borrar todas y volver a insertarlas rompería la restricción única (order_id, product_id), porque
     * Hibernate ejecuta los INSERT antes que los DELETE de los huérfanos.
     */
    private static void syncItems(List<OrderItem> items, OrderEntity entity) {
        Set<Long> productIds = items.stream().map(OrderItem::productId).collect(Collectors.toSet());
        entity.getItems().removeIf(existing -> !productIds.contains(existing.getProductId()));
        Map<Long, OrderItemEntity> byProduct = entity.getItems().stream()
                .collect(Collectors.toMap(OrderItemEntity::getProductId, Function.identity()));
        for (OrderItem item : items) {
            OrderItemEntity existing = byProduct.get(item.productId());
            if (existing == null) {
                entity.getItems().add(new OrderItemEntity(entity, item.productId(), item.productName(),
                        item.unitPrice(), item.quantity()));
            } else {
                existing.update(item.productName(), item.unitPrice(), item.quantity());
            }
        }
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findByIdAndActiveTrue(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Order> findAll(List<FilterCriterion> filters, PageQuery pageQuery, boolean includeInactive) {
        Page<OrderEntity> page = jpaRepository.findAll(specificationBuilder.build(filters, MAPPING, includeInactive),
                SpringDataPageAdapter.toPageable(pageQuery, MAPPING));
        return SpringDataPageAdapter.toPageResult(page, mapper::toDomain);
    }
}
