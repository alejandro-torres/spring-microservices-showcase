package com.showcase.auth.application.service;

import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import java.util.List;

/**
 * Recording and lookup of the security audit log.
 */
public interface SecurityAuditService {

    /**
     * Records the event in its own transaction: it stays saved even if the operation causing it
     * fails and rolls back.
     */
    void record(SecurityEventType type, String username, String details);

    PageResult<SecurityAuditEvent> findAll(List<FilterCriterion> filters, PageQuery pageQuery);
}
