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
 * Real revocation with Redis (Testcontainers): tokens are signed with the same key auth-service uses,
 * so they go through the JwtDecoder's whole validation, including the check in Redis.
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

    /** A token like the one auth-service issues for a user with the USER role. */
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
    @DisplayName("after logout, the same token is rejected with 401 INVALID_TOKEN")
    void logout_shouldRevokeCurrentToken() throws Exception {
        userService.create("revokes.logout", "logout@example.com", "Secret-123!", Set.of());
        String token = tokenFor("revokes.logout");

        me(token).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("changing the password revokes all the user's previous tokens, not those of others")
    void passwordChange_shouldRevokeAllPreviousTokens() throws Exception {
        userService.create("revokes.all", "all@example.com", "Secret-123!", Set.of());
        userService.create("revokes.other", "other@example.com", "Secret-123!", Set.of());
        String first = tokenFor("revokes.all");
        String second = tokenFor("revokes.all");
        String otherUser = tokenFor("revokes.other");

        mockMvc.perform(put("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Secret-123!","newPassword":"New-456!"}
                                """))
                .andExpect(status().isNoContent());

        me(first).andExpect(status().isUnauthorized());
        me(second).andExpect(status().isUnauthorized());
        me(otherUser).andExpect(status().isOk());

        // The iat claim has second precision: a token issued one second later is valid.
        Thread.sleep(1_100);
        me(tokenFor("revokes.all")).andExpect(status().isOk());
    }
}
