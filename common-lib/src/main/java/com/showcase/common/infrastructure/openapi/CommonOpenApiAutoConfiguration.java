package com.showcase.common.infrastructure.openapi;

import com.showcase.common.infrastructure.rest.dto.response.ErrorResponseDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

/**
 * Common OpenAPI documentation of the web services: title, security schemes (paste auth-service's JWT or
 * log in with OAuth2 from the Swagger UI) and, on each operation, its possible error responses with the
 * {@code ErrorResponseDTO} body. This way no controller repeats the same {@code @ApiResponse}; if an operation
 * declares its own, it is respected.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springdoc.core.customizers.GlobalOperationCustomizer")
public class CommonOpenApiAutoConfiguration {

    static final String SECURITY_SCHEME = "bearer-jwt";
    static final String OAUTH2_SCHEME = "oauth2";
    static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponseDTO";

    /**
     * @param authServerUrl where {@code /oauth2/authorize} and {@code /oauth2/token} live. Empty (default): relative
     *                      paths, which the gateway's Swagger UI resolves against itself, the token issuer.
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApi(@Value("${spring.application.name}") String applicationName,
                           @Value("${showcase.openapi.auth-server-url:}") String authServerUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .version("v1")
                        .description("Errors in English or Spanish according to Accept-Language. To try the API, "
                                + "Authorize → oauth2 (client_id web-client, no secret) opens the login of "
                                + "auth-service; or paste a token you already have into bearer-jwt."))
                // Relative: the Swagger UI resolves it against where it is served (the gateway or the service itself).
                // Otherwise, springdoc computes it with the gateway's X-Forwarded-Prefix (/auth-service), which is not
                // an
                // API path.
                .servers(List.of(new Server().url("/").description("Where this documentation is served")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes(OAUTH2_SCHEME, oauth2Scheme(authServerUrl)))
                // Two alternative requirements: either scheme is valid.
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(OAUTH2_SCHEME));
    }

    /** Authorization code with PKCE: the same flow as a browser application, without a client secret. */
    static SecurityScheme oauth2Scheme(String authServerUrl) {
        String base = authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1)
                : authServerUrl;
        return new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("auth-service login with authorization code + PKCE")
                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl(base + "/oauth2/authorize")
                        .tokenUrl(base + "/oauth2/token")
                        .scopes(new Scopes().addString("openid", "User identity")
                                .addString("profile", "User profile"))));
    }

    /** Registers the schema of {@code ErrorResponseDTO} (and its nested types) in the components. */
    @Bean
    public GlobalOpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            ModelConverters.getInstance().readAll(ErrorResponseDTO.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
        };
    }

    @Bean
    public GlobalOperationCustomizer standardErrorResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            RequestMethod method = httpMethod(handlerMethod);
            if (method == null) {
                return operation;
            }
            ApiResponses responses = operation.getResponses() == null ? new ApiResponses() : operation.getResponses();
            StandardErrorResponses.forOperation(method, targetsOneResource(handlerMethod))
                    .forEach((status, description) -> responses.computeIfAbsent(String.valueOf(status),
                            key -> errorResponse(description)));
            operation.setResponses(responses);
            return operation;
        };
    }

    private static RequestMethod httpMethod(HandlerMethod handlerMethod) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequestMapping.class);
        return mapping == null || mapping.method().length == 0 ? null : mapping.method()[0];
    }

    private static boolean targetsOneResource(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(PathVariable.class));
    }

    private static ApiResponse errorResponse(String description) {
        MediaType json = new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
        return new ApiResponse().description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, json));
    }
}
