package com.showcase.auth.application.service.impl;

import com.showcase.auth.application.security.RequestMetadata;
import com.showcase.auth.application.security.RequestMetadataProvider;
import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.domain.repository.SecurityAuditRepository;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditRepository auditRepository;
    private final RequestMetadataProvider requestMetadataProvider;
    private final Clock clock;

    /**
     * REQUIRES_NEW suspende la transacción del llamador y abre una nueva que se confirma al salir de
     * este método. Así, si la operación principal falla después (p. ej. un login fallido que lanza una
     * excepción), el registro de auditoría no se pierde con su rollback.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityEventType type, String username, String details) {
        RequestMetadata metadata = requestMetadataProvider.current();
        String fullDetails = metadata.actor() == null || metadata.actor().equals(username)
                ? details
                : join(details, "by=" + metadata.actor());
        auditRepository.save(SecurityAuditEvent.of(type, username, metadata.ip(), metadata.userAgent(),
                metadata.traceId(), fullDetails, Instant.now(clock)));
    }

    @Override
    public PageResult<SecurityAuditEvent> findAll(List<FilterCriterion> filters, PageQuery pageQuery) {
        return auditRepository.findAll(filters, pageQuery);
    }

    private static String join(String details, String extra) {
        return details == null || details.isBlank() ? extra : details + "; " + extra;
    }
}
