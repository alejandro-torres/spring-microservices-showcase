package com.showcase.common.infrastructure.openapi;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Paths of the OpenAPI documentation and Swagger UI. They are public in all services: they describe the API, they
 * do not give access to it (each endpoint still requires its token).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenApiPaths {

    public static final String[] PUBLIC = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};
}
