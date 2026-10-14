package com.showcase.common.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.common.infrastructure.exception.ErrorCodeHttpStatusMapper;
import com.showcase.common.infrastructure.exception.ErrorResponseFactory;
import com.showcase.common.infrastructure.exception.GlobalExceptionHandler;
import com.showcase.common.infrastructure.i18n.ErrorMessageResolver;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class SecurityExceptionHandlerTest {

    @RestController
    static class SecuredController {

        @GetMapping("/denied")
        String denied() {
            // Lo mismo que lanza @PreAuthorize cuando no se cumple la regla.
            throw new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
        }

        @GetMapping("/bad-credentials")
        String badCredentials() {
            throw new BadCredentialsException("Bad credentials");
        }
    }

    private final MockMvc mockMvc = createMockMvc();

    private static MockMvc createMockMvc() {
        var responses = new ErrorResponseFactory(new ErrorMessageResolver(new StaticMessageSource()), Clock.systemUTC());
        return MockMvcBuilders.standaloneSetup(new SecuredController())
                .setControllerAdvice(new SecurityExceptionHandler(responses),
                        new GlobalExceptionHandler(responses, new ErrorCodeHttpStatusMapper()))
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("un @PreAuthorize denegado a un usuario autenticado devuelve 403, no 500")
    void accessDenied_authenticated_shouldReturn403() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("ana", null, List.of()));

        mockMvc.perform(get("/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("un acceso denegado sin autenticación devuelve 401")
    void accessDenied_anonymous_shouldReturn401() throws Exception {
        mockMvc.perform(get("/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("un error de autenticación devuelve 401")
    void authenticationException_shouldReturn401() throws Exception {
        mockMvc.perform(get("/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
