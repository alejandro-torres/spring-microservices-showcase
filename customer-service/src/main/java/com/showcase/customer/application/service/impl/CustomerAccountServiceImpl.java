package com.showcase.customer.application.service.impl;

import com.showcase.common.domain.exception.DuplicateResourceException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.customer.application.service.CustomerAccountService;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import com.showcase.events.EventTypes;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El registro del evento procesado y el cambio del cliente van en la misma transacción: si guardar el cliente
 * falla, el rollback deshace también el registro y el reintento lo procesa de nuevo.
 */
@Service
public class CustomerAccountServiceImpl implements CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final ProcessedEventRegistry processedEvents;

    public CustomerAccountServiceImpl(CustomerRepository customerRepository, ProcessedEventRegistry processedEvents) {
        this.customerRepository = customerRepository;
        this.processedEvents = processedEvents;
    }

    /**
     * Si el usuario ya tiene cliente (p. ej. lo creó un gestor antes de que llegara el evento) no se hace nada.
     * Si otro cliente ya usa ese email, la excepción de negocio manda el mensaje a la DLQ para revisarlo a mano.
     */
    @Override
    @Transactional
    public void createForUser(UUID eventId, Long userId, String email, String firstName, String lastName) {
        if (!isNew(eventId, EventTypes.USER_REGISTERED)) {
            return;
        }
        if (customerRepository.existsByUserId(userId)) {
            log.info("El usuario {} ya tiene cliente; no se crea otro", userId);
            return;
        }
        Customer customer = Customer.create(userId, firstName, lastName, email, null);
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new DuplicateResourceException(CustomerErrorCode.EMAIL_ALREADY_EXISTS, customer.getEmail());
        }
        Customer saved = customerRepository.save(customer);
        log.info("Cliente {} creado para el usuario {}", saved.getId(), userId);
    }

    /** Un usuario sin cliente (p. ej. un administrador) no es un error: no hay nada que desactivar. */
    @Override
    @Transactional
    public void deactivateForUser(UUID eventId, Long userId) {
        if (!isNew(eventId, EventTypes.USER_DISABLED)) {
            return;
        }
        customerRepository.findByUserId(userId).ifPresentOrElse(customer -> {
            customer.deactivate();
            customerRepository.save(customer);
            log.info("Cliente {} desactivado porque se ha desactivado el usuario {}", customer.getId(), userId);
        }, () -> log.info("El usuario desactivado {} no tiene cliente activo", userId));
    }

    @Override
    @Transactional
    public void recordAccountLocked(UUID eventId, Long userId, Instant lockedUntil) {
        if (isNew(eventId, EventTypes.ACCOUNT_LOCKED)) {
            log.warn("La cuenta del usuario {} está bloqueada hasta {}", userId, lockedUntil);
        }
    }

    private boolean isNew(UUID eventId, String eventType) {
        boolean isNew = processedEvents.registerIfAbsent(eventId, eventType);
        if (!isNew) {
            log.info("Evento {} ({}) ya procesado; se ignora el duplicado", eventId, eventType);
        }
        return isNew;
    }
}
