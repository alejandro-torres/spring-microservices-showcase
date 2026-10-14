package com.showcase.auth.domain.repository;

import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;

/**
 * Puerto de salida para la auditoría de seguridad. Solo se añaden registros: nunca se modifican ni se borran.
 */
public interface SecurityAuditRepository {

    SecurityAuditEvent save(SecurityAuditEvent event);

    PageResult<SecurityAuditEvent> findAll(List<FilterCriterion> filters, PageQuery pageQuery);
}
