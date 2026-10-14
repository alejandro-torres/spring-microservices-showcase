package com.showcase.auth.infrastructure.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.SecurityAuditService;
import com.showcase.auth.domain.model.SecurityAuditEvent;
import com.showcase.auth.domain.model.SecurityEventType;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapperImpl;
import com.showcase.auth.infrastructure.security.config.ApiSecurityConfig;
import com.showcase.auth.testdata.TestJwt;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecurityAuditController.class)
@Import({CommonWebAutoConfiguration.class, ApiSecurityConfig.class, AuthRestMapperImpl.class})
class SecurityAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityAuditService auditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("un ADMIN consulta la auditoría filtrando por tipo de evento, ordenada por fecha descendente")
    void admin_shouldListAuditFiltered() throws Exception {
        var event = new SecurityAuditEvent(1L, SecurityEventType.ACCOUNT_LOCKED, "ana", "10.0.0.7", "curl",
                "trace-1", null, Instant.parse("2026-10-14T12:00:00Z"));
        given(auditService.findAll(anyList(), any(PageQuery.class))).willReturn(PageResult.of(List.of(event), 0, 20, 1));

        mockMvc.perform(get("/api/v1/security-audit").with(TestJwt.admin())
                        .param("filter", "eventType:in:LOGIN_FAILURE,ACCOUNT_LOCKED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.content[0].ip").value("10.0.0.7"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FilterCriterion>> filters = ArgumentCaptor.forClass(List.class);
        verify(auditService).findAll(filters.capture(), eq(new PageQuery(0, 20, "occurredAt", SortDirection.DESC)));
        assertThat(filters.getValue()).containsExactly(FilterCriterion.of("eventType", FilterOperator.IN,
                SecurityEventType.LOGIN_FAILURE, SecurityEventType.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("sin el rol ADMIN devuelve 403, aunque tenga user:read")
    void nonAdmin_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/security-audit").with(TestJwt.withPermissions("lector", "user:read")))
                .andExpect(status().isForbidden());
        verify(auditService, never()).findAll(anyList(), any());
    }

    @Test
    @DisplayName("rechaza un tipo de evento inexistente")
    void unknownEventType_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/security-audit").with(TestJwt.admin()).param("filter", "eventType:eq:HACKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER_VALUE"));
    }
}
