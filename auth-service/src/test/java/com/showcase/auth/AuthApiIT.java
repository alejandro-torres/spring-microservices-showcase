package com.showcase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.showcase.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.showcase.auth.testdata.TestJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Prueba de extremo a extremo: la aplicación completa (controllers, servicios con transacciones reales,
 * JPA y Flyway) contra PostgreSQL en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(TestJwt.admin()));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    @DisplayName("recorre el CRUD completo: permiso → rol → usuario, filtros, versión, borrados y errores")
    void fullCrudFlow() throws Exception {
        // Permiso y rol nuevos
        long permissionId = idOf(postJson("/api/v1/permissions", """
                {"name":"report:read","description":"Ver informes"}
                """).andExpect(status().isCreated()));
        long roleId = idOf(postJson("/api/v1/roles", """
                {"name":"analyst","description":"Analistas","permissionIds":[%d]}
                """.formatted(permissionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("ANALYST"))
                .andExpect(jsonPath("$.permissions[0]").value("report:read")));

        // Usuario con ese rol
        long userId = idOf(postJson("/api/v1/users", """
                {"username":"Lucia.Analista","email":"lucia@example.com","password":"Secreta-123","roleIds":[%d]}
                """.formatted(roleId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("lucia.analista"))
                .andExpect(jsonPath("$.version").value(0)));

        // Listado filtrado por rol (join con la colección)
        perform(get("/api/v1/users").param("filter", "role:eq:ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(userId));

        // Actualización con la versión correcta y después con una versión antigua
        perform(put("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"lucia.nueva@example.com","enabled":true,"version":0}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        perform(put("/api/v1/users/{id}", userId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"otra@example.com","enabled":true,"version":0}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // El rol y el permiso no se pueden borrar mientras se usan
        perform(delete("/api/v1/roles/{id}", roleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
        perform(delete("/api/v1/permissions/{id}", permissionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERMISSION_IN_USE"));

        // Borrado lógico del usuario: deja de verse, salvo con includeInactive
        perform(delete("/api/v1/users/{id}", userId)).andExpect(status().isNoContent());
        perform(get("/api/v1/users/{id}", userId)).andExpect(status().isNotFound());
        perform(get("/api/v1/users").param("filter", "username:eq:lucia.analista").param("includeInactive", "true"))
                .andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.content[0].enabled").value(false));

        // Ya sin usuarios activos, el rol sí se puede borrar
        perform(delete("/api/v1/roles/{id}", roleId)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("rechaza duplicados con 409 y no guarda nada si un rol no existe (rollback)")
    void duplicatesAndRollback() throws Exception {
        postJson("/api/v1/users", """
                {"username":"mario","email":"mario@example.com","password":"Secreta-123"}
                """).andExpect(status().isCreated());

        postJson("/api/v1/users", """
                {"username":"MARIO","email":"otro@example.com","password":"Secreta-123"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));

        long before = userJpaRepository.count();
        postJson("/api/v1/users", """
                {"username":"sin.rol","email":"sinrol@example.com","password":"Secreta-123","roleIds":[999]}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
        assertThat(userJpaRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("los roles y permisos base de la migración están disponibles por la API")
    void baseData_shouldBeAvailable() throws Exception {
        perform(get("/api/v1/roles").param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem("ADMIN")))
                .andExpect(jsonPath("$.content[*].name", hasItem("USER")));
        perform(get("/api/v1/permissions").param("filter", "name:startsWith:product:").param("size", "50"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
