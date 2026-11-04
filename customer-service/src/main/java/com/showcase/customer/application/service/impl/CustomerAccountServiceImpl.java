package com.showcase.customer.application.service.impl;

import com.showcase.common.domain.exception.DuplicateResourceException;
import com.showcase.common.domain.messaging.ProcessedEventRegistry;
import com.showcase.customer.application.service.CustomerAccountService;
import com.showcase.customer.domain.exception.CustomerErrorCode;
import com.showcase.customer.domain.model.Customer;
import com.showcase.customer.domain.repository.CustomerRepository;
import com.showcase.events.EventTypes;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registering the processed event and changing the customer happen in the same transaction: if saving the customer
 * fails, the rollback also undoes the registration and the retry processes it again.
 */
@Service
@RequiredArgsConstructor
public class CustomerAccountServiceImpl implements CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountServiceImpl.class);

    private final CustomerRepository customerRepository;
    private final ProcessedEventRegistry processedEvents;

    /**
     * If the user already has a customer (e.g. a manager created it before the event arrived), nothing is done.
     * If another customer already uses that email, the business exception sends the message to the DLQ for manual
     * review.
     */
    @Override
    @Transactional
    @Observed(name = "customer.create-for-user", contextualName = "create the user's customer")
    public void createForUser(UUID eventId, Long userId, String email, String firstName, String lastName) {
        if (!isNew(eventId, EventTypes.USER_REGISTERED)) {
            return;
        }
        if (customerRepository.existsByUserId(userId)) {
            log.info("User {} already has a customer; no other is created", userId);
            return;
        }
        Customer customer = Customer.create(userId, firstName, lastName, email, null);
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new DuplicateResourceException(CustomerErrorCode.EMAIL_ALREADY_EXISTS, customer.getEmail());
        }
        Customer saved = customerRepository.save(customer);
        log.info("Customer {} created for user {}", saved.getId(), userId);
    }

    /** A user without a customer (e.g. an administrator) is not an error: there is nothing to deactivate. */
    @Override
    @Transactional
    public void deactivateForUser(UUID eventId, Long userId) {
        if (!isNew(eventId, EventTypes.USER_DISABLED)) {
            return;
        }
        customerRepository.findByUserId(userId).ifPresentOrElse(customer -> {
            customer.deactivate();
            customerRepository.save(customer);
            log.info("Customer {} deactivated because user {} was deactivated", customer.getId(), userId);
        }, () -> log.info("Deactivated user {} has no active customer", userId));
    }

    @Override
    @Transactional
    public void recordAccountLocked(UUID eventId, Long userId, Instant lockedUntil) {
        if (isNew(eventId, EventTypes.ACCOUNT_LOCKED)) {
            log.warn("The account of user {} is locked until {}", userId, lockedUntil);
        }
    }

    private boolean isNew(UUID eventId, String eventType) {
        boolean isNew = processedEvents.registerIfAbsent(eventId, eventType);
        if (!isNew) {
            log.info("Event {} ({}) already processed; ignoring the duplicate", eventId, eventType);
        }
        return isNew;
    }
}
