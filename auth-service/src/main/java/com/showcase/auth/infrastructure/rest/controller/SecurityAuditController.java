package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.AUDIT_FILTERS;
import static com.showcase.auth.infrastructure.rest.filter.AuthQueryFields.AUDIT_SORT;

import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.infrastructure.rest.dto.response.SecurityAuditEventResponseDTO;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapper;
import com.showcase.common.infrastructure.rest.dto.response.PageResponseDTO;
import com.showcase.common.infrastructure.rest.filter.FilterCriteriaParser;
import com.showcase.common.infrastructure.rest.filter.FilterParameters;
import com.showcase.common.infrastructure.rest.page.PageQueryParser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la auditoría de seguridad. Solo lectura y solo para ADMIN.
 */
@RestController
@RequestMapping("/api/v1/security-audit")
@RequiredArgsConstructor
public class SecurityAuditController {

    private final SecurityAuditService auditService;
    private final AuthRestMapper mapper;
    private final FilterCriteriaParser filterParser;

    /**
     * P. ej. {@code ?filter=eventType:in:LOGIN_FAILURE,ACCOUNT_LOCKED&filter=username:eq:ana&sort=occurredAt&direction=DESC}.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponseDTO<SecurityAuditEventResponseDTO> findAll(
            FilterParameters filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        var result = auditService.findAll(
                filterParser.parse(filter.values(), AUDIT_FILTERS),
                PageQueryParser.parse(page, size, sort, direction, AUDIT_SORT));
        return PageResponseDTO.from(result, mapper::toResponse);
    }
}
