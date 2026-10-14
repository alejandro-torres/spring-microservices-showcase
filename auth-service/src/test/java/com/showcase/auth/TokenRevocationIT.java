package com.showcase.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.showcase.auth.application.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Revocación real con Redis (Testcontainers): los tokens se firman con la misma clave que usa auth-service,
 * así que pasan por toda la validación del JwtDecoder, incluida la comprobación en Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenRevocationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    private NimbusJwtEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new NimbusJwtEncoder(jwkSource);
    }

    /** Token como el que emite auth-service para un usuario con rol USER. */
    private String tokenFor(String username) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("http://localhost:9000").subject(username).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("roles", List.of("USER")).claim("permissions", List.of("order:read"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    @DisplayName("tras el logout, el mismo token se rechaza con 401 INVALID_TOKEN")
    void logout_shouldRevokeCurrentToken() throws Exception {
        userService.create("revoca.logout", "logout@example.com", "Secreta-123!", Set.of());
        String token = tokenFor("revoca.logout");

        me(token).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("al cambiar la contraseña se revocan todos los tokens anteriores del usuario, no los de otros")
    void passwordChange_shouldRevokeAllPreviousTokens() throws Exception {
        userService.create("revoca.todos", "todos@example.com", "Secreta-123!", Set.of());
        userService.create("revoca.otro", "otro@example.com", "Secreta-123!", Set.of());
        String first = tokenFor("revoca.todos");
        String second = tokenFor("revoca.todos");
        String otherUser = tokenFor("revoca.otro");

        mockMvc.perform(put("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Secreta-123!","newPassword":"Nueva-456!"}
                                """))
                .andExpect(status().isNoContent());

        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
        me(otherUser).andExpect(status().isOk());

        // El claim iat tiene precisión de segundos: un token emitido un segundo después ya es válido.
        Thread.sleep(1_100);
        me(tokenFor("revoca.todos")).andExpect(status().isOk());
    }
}
