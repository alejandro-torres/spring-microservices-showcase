package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.showcase.auth.application.service.UserService;
import com.showcase.auth.testdata.TestJwt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordChangeIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private ResultActions changePassword(String username, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/password").with(TestJwt.withPermissions(username))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(current, next)));
    }

    private void login(String username, String password, String expectedRedirect) throws Exception {
        mockMvc.perform(post("/login").with(csrf()).param("username", username).param("password", password))
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).contains(expectedRedirect));
    }

    @Test
    @DisplayName("cambia la contraseña, invalida la anterior e impide volver a las últimas usadas")
    void changePassword_fullFlow() throws Exception {
        userService.create("cambio.clave", "cambio@example.com", "Primera-111!", Set.of());

        changePassword("cambio.clave", "Primera-111!", "Segunda-222!").andExpect(status().isNoContent());
        login("cambio.clave", "Primera-111!", "/login?error");
        login("cambio.clave", "Segunda-222!", "/");

        // La primera sigue en el historial: no se puede reutilizar.
        changePassword("cambio.clave", "Segunda-222!", "Primera-111!")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PASSWORD_RECENTLY_USED"))
                .andExpect(jsonPath("$.message").value("La nueva contraseña no puede ser ninguna de las últimas 3 que has usado."));

        changePassword("cambio.clave", "incorrecta", "Tercera-333!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }
}
