package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;

/**
 * Registro y consulta de la auditoría de seguridad.
 */
public interface SecurityAuditService {

    /**
     * Registra el evento en su propia transacción: queda guardado aunque la operación que lo provoca
     * falle y haga rollback.
     */
    void record(SecurityEventType type, String username, String details);

    PageResult<SecurityAuditEvent> findAll(List<FilterCriterion> filters, PageQuery pageQuery);
}
