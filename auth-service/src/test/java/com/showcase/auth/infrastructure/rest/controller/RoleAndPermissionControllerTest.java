package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.permission;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.PermissionService;
import com.showcase.auth.application.service.RoleService;
import com.showcase.auth.domain.exception.PermissionInUseException;
import com.showcase.auth.domain.exception.RoleInUseException;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapperImpl;
import com.showcase.auth.infrastructure.security.config.ApiSecurityConfig;
import com.showcase.auth.testdata.TestJwt;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({RoleController.class, PermissionController.class})
@Import({CommonWebAutoConfiguration.class, ApiSecurityConfig.class, AuthRestMapperImpl.class})
class RoleAndPermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()));
    }

    @Nested
    @DisplayName("/api/v1/roles")
    class Roles {

        @Test
        @DisplayName("POST returns 201 with the role's permissions")
        void create_shouldReturn201() throws Exception {
            given(roleService.create("ADMIN", "Administration", Set.of(1L, 2L))).willReturn(adminRole());

            perform(post("/api/v1/roles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"ADMIN","description":"Administration","permissionIds":[1,2]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/roles/1")))
                    .andExpect(jsonPath("$.permissions[0]").value("user:read"))
                    .andExpect(jsonPath("$.permissions[1]").value("user:write"));
        }

        @Test
        @DisplayName("Paginated GET filters by permission and PUT updates")
        void findAllAndUpdate_shouldWork() throws Exception {
            given(roleService.findAll(anyList(), any(PageQuery.class), anyBoolean()))
                    .willReturn(PageResult.of(List.of(adminRole()), 0, 10, 1));
            given(roleService.update(1L, "Admin", Set.of(1L), 0L)).willReturn(adminRole());

            perform(get("/api/v1/roles").param("filter", "permission:eq:user:write"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("ADMIN"));
            perform(put("/api/v1/roles/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Admin","permissionIds":[1],"version":0}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE returns 409 if the role is assigned to users")
        void delete_roleInUse_shouldReturn409() throws Exception {
            willThrow(new RoleInUseException("ADMIN", 3L)).given(roleService).delete(1L);

            perform(delete("/api/v1/roles/1"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROLE_IN_USE"))
                    .andExpect(jsonPath("$.message")
                            .value("Role 'ADMIN' is assigned to 3 active users and cannot be deleted."));
        }
    }

    @Nested
    @DisplayName("/api/v1/permissions")
    class Permissions {

        @Test
        @DisplayName("POST returns 201 and GET by id returns the permission")
        void createAndFind_shouldWork() throws Exception {
            given(permissionService.create("report:read", "View reports")).willReturn(permission(20L, "report:read"));
            given(permissionService.findById(20L)).willReturn(permission(20L, "report:read"));

            perform(post("/api/v1/permissions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"report:read","description":"View reports"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/permissions/20")));
            perform(get("/api/v1/permissions/20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("report:read"));
        }

        @Test
        @DisplayName("rejects an operator not allowed for the field")
        void findAll_withOperatorNotAllowed_shouldReturn400() throws Exception {
            perform(get("/api/v1/permissions").param("filter", "name:gte:a"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FILTER_OPERATOR_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("PUT returns 200 and DELETE returns 409 if a role uses it")
        void updateAndDelete_shouldWork() throws Exception {
            given(permissionService.update(20L, "Read", 0L)).willReturn(permission(20L, "report:read"));
            willThrow(new PermissionInUseException("report:read", 1L)).given(permissionService).delete(20L);

            perform(put("/api/v1/permissions/20")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"description":"Read","version":0}
                                    """))
                    .andExpect(status().isOk());
            perform(delete("/api/v1/permissions/20"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PERMISSION_IN_USE"));
        }
    }
}
