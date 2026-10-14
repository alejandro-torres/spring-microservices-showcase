package com.showcase.common.infrastructure.openapi;

/**
 * Rutas de la documentación OpenAPI y de Swagger UI. Son públicas en todos los servicios: describen la API, no
 * dan acceso a ella (cada endpoint sigue exigiendo su token).
 */
public final class OpenApiPaths {

    public static final String[] PUBLIC = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    private OpenApiPaths() {
    }
}
