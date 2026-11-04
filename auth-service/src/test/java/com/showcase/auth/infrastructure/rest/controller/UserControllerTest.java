package com.showcase.auth.infrastructure.rest.controller;

import static com.showcase.auth.testdata.AuthMother.adminRole;
import static com.showcase.auth.testdata.AuthMother.user;
import static com.showcase.auth.testdata.AuthMother.userRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.domain.exception.UserNotFoundException;
import com.showcase.auth.infrastructure.rest.mapper.AuthRestMapperImpl;
import com.showcase.auth.infrastructure.security.config.ApiSecurityConfig;
import com.showcase.auth.testdata.TestJwt;
import com.showcase.common.domain.exception.CommonErrorCode;
import com.showcase.common.domain.exception.ConflictException;
import com.showcase.common.domain.filter.FilterCriterion;
import com.showcase.common.domain.filter.FilterOperator;
import com.showcase.common.domain.page.PageQuery;
import com.showcase.common.domain.page.PageResult;
import com.showcase.common.domain.page.SortDirection;
import com.showcase.common.infrastructure.config.CommonWebAutoConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(UserController.class)
@Import({CommonWebAutoConfiguration.class, ApiSecurityConfig.class, AuthRestMapperImpl.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** By default requests go with an ADMIN token; permission tests use their own token. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()));
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @DisplayName("without a token it returns 401 in the common format and does not call the service")
        void withoutToken_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/users/10"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."));
            verify(userService, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("permissions (@PreAuthorize)")
    class Permissions {

        @Test
        @DisplayName("with user:read it can view, but not create (403 in the common format)")
        void readOnlyUser_canReadButNotWrite() throws Exception {
            given(userService.findById(10L)).willReturn(user(10L, "ana"));
            var reader = TestJwt.withPermissions("reader", "user:read");

            mockMvc.perform(get("/api/v1/users/10").with(reader)).andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/users").with(reader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"ana","email":"ana@example.com","password":"Secret-123"}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            verify(userService, never()).create(any(), any(), any(), any());
        }

        @Test
        @DisplayName("GET /me returns the token's user even without user:read")
        void me_shouldReturnAuthenticatedUser() throws Exception {
            given(userService.findByUsername("ana")).willReturn(user(10L, "ana", userRole()));

            mockMvc.perform(get("/api/v1/users/me").with(TestJwt.withPermissions("ana", "order:read")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("ana"));
        }

        @Test
        @DisplayName("PUT /me/password changes the password of the token's user and returns 204")
        void changePassword_shouldUseTokenSubject() throws Exception {
            mockMvc.perform(put("/api/v1/users/me/password").with(TestJwt.withPermissions("ana"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"Current-123","newPassword":"New-456!"}
                                    """))
                    .andExpect(status().isNoContent());
            verify(userService).changePassword("ana", "Current-123", "New-456!");
        }

        @Test
        @DisplayName("PUT /me/password rejects a weak password with the translated message")
        void changePassword_weakPassword_shouldReturn400() throws Exception {
            mockMvc.perform(put("/api/v1/users/me/password").with(TestJwt.withPermissions("ana"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"currentPassword":"Current-123","newPassword":"weak"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                    .andExpect(jsonPath("$.errors[0].message").value(
                            "must be 8 to 72 characters long, without spaces, and include uppercase, lowercase, digits and symbols"));
            verify(userService, never()).changePassword(any(), any(), any());
        }

        @Test
        @DisplayName("without user:read it cannot view users")
        void withoutPermission_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/users/10").with(TestJwt.withPermissions("customer", "order:read")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("includeInactive is only allowed for the ADMIN role")
        void includeInactive_onlyForAdmin() throws Exception {
            given(userService.findAll(anyList(), any(PageQuery.class), anyBoolean()))
                    .willReturn(PageResult.of(List.of(), 0, 10, 0));
            var reader = TestJwt.withPermissions("reader", "user:read");

            mockMvc.perform(get("/api/v1/users").with(reader)).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/users").param("includeInactive", "true").with(reader))
                    .andExpect(status().isForbidden());
            perform(get("/api/v1/users").param("includeInactive", "true")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users")
    class Create {

        @Test
        @DisplayName("returns 201 with the Location header and the user without password data")
        void create_shouldReturn201WithLocation() throws Exception {
            given(userService.create("ana", "ana@example.com", "Secret-123", Set.of(2L)))
                    .willReturn(user(10L, "ana", userRole()));

            perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"ana","email":"ana@example.com","password":"Secret-123","roleIds":[2]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/api/v1/users/10")))
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.username").value("ana"))
                    .andExpect(jsonPath("$.roles[0]").value("USER"))
                    .andExpect(jsonPath("$.version").value(0))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 with each field's errors and does not call the service")
        void create_withInvalidBody_shouldReturn400() throws Exception {
            perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"a","email":"not-an-email","password":"short"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("username", "email", "password")));
            verify(userService, never()).create(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class FindById {

        @Test
        @DisplayName("returns 200 with the user and their sorted roles")
        void findById_shouldReturnUser() throws Exception {
            given(userService.findById(10L)).willReturn(user(10L, "ana", userRole(), adminRole()));

            perform(get("/api/v1/users/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                    .andExpect(jsonPath("$.roles[1]").value("USER"));
        }

        @Test
        @DisplayName("returns 404 with the service's message in English or Spanish")
        void findById_missing_shouldReturn404Translated() throws Exception {
            given(userService.findById(99L)).willThrow(new UserNotFoundException(99L));

            perform(get("/api/v1/users/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("No active user with id 99 was found."));
            perform(get("/api/v1/users/99").header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                    .andExpect(jsonPath("$.message").value("No existe ningún usuario activo con id 99."));
        }

        @Test
        @DisplayName("returns 400 if the id is not positive")
        void findById_withNegativeId_shouldReturn400() throws Exception {
            perform(get("/api/v1/users/-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users (paginated with filters)")
    class FindAll {

        @Test
        @DisplayName("converts the filters and pagination and returns the page with its totals")
        void findAll_shouldParseFiltersAndReturnPage() throws Exception {
            // given
            given(userService.findAll(anyList(), any(PageQuery.class), anyBoolean()))
                    .willReturn(PageResult.of(List.of(user(10L, "ana", adminRole())), 0, 10, 1));

            // when
            perform(get("/api/v1/users")
                            .param("filter", "role:in:ADMIN,USER")
                            .param("filter", "username:like:an")
                            .param("sort", "createdAt")
                            .param("direction", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].username").value("ana"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(true));

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<FilterCriterion>> filters = ArgumentCaptor.forClass(List.class);
            verify(userService).findAll(filters.capture(), eq(new PageQuery(0, 10, "createdAt", SortDirection.DESC)),
                    eq(false));
            assertThat(filters.getValue()).containsExactly(
                    FilterCriterion.of("role", FilterOperator.IN, "ADMIN", "USER"),
                    FilterCriterion.of("username", FilterOperator.LIKE, "an"));
        }

        @Test
        @DisplayName("rejects filtering by a field outside the whitelist, such as the password hash")
        void findAll_withForbiddenField_shouldReturn400() throws Exception {
            perform(get("/api/v1/users").param("filter", "passwordHash:eq:x"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("FILTER_FIELD_NOT_ALLOWED"));
            verify(userService, never()).findAll(anyList(), any(), anyBoolean());
        }

        @Test
        @DisplayName("rejects a sort field that is not allowed and an excessive page size")
        void findAll_withInvalidPagination_shouldReturn400() throws Exception {
            perform(get("/api/v1/users").param("sort", "passwordHash"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
            perform(get("/api/v1/users").param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
        }
    }

    @Nested
    @DisplayName("PUT, DELETE and role management")
    class Modify {

        @Test
        @DisplayName("PUT returns 200 with the updated user")
        void update_shouldReturn200() throws Exception {
            given(userService.update(10L, "new@example.com", false, 0L)).willReturn(user(10L, "ana"));

            perform(put("/api/v1/users/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com","enabled":false,"version":0}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("PUT returns 409 if another user modified the record")
        void update_withStaleVersion_shouldReturn409() throws Exception {
            given(userService.update(10L, "new@example.com", true, 0L))
                    .willThrow(new ConflictException(CommonErrorCode.CONCURRENT_MODIFICATION, "User", 10L));

            perform(put("/api/v1/users/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com","enabled":true,"version":0}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }

        @Test
        @DisplayName("PUT requires the version for optimistic locking")
        void update_withoutVersion_shouldReturn400() throws Exception {
            perform(put("/api/v1/users/10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com","enabled":true}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("version"));
        }

        @Test
        @DisplayName("DELETE returns 204 with no body, and 404 if the user does not exist")
        void delete_shouldReturn204Or404() throws Exception {
            perform(delete("/api/v1/users/10")).andExpect(status().isNoContent());
            verify(userService).delete(10L);

            willThrow(new UserNotFoundException(11L)).given(userService).delete(11L);
            perform(delete("/api/v1/users/11")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("assigns and removes roles, returning the updated user")
        void roles_shouldReturnUpdatedUser() throws Exception {
            given(userService.assignRole(10L, 1L)).willReturn(user(10L, "ana", userRole(), adminRole()));
            given(userService.removeRole(10L, 1L)).willReturn(user(10L, "ana", userRole()));

            perform(post("/api/v1/users/10/roles/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles.length()").value(2));
            perform(delete("/api/v1/users/10/roles/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles.length()").value(1));
        }
    }
}
