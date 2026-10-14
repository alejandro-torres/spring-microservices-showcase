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
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
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
 * Documentación OpenAPI común de los servicios web: título, esquema de seguridad Bearer (el JWT de auth-service)
 * y, en cada operación, sus posibles respuestas de error con el cuerpo {@code ErrorResponseDTO}. Así ningún
 * controller repite las mismas {@code @ApiResponse}; si una operación declara la suya, se respeta.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springdoc.core.customizers.GlobalOperationCustomizer")
public class CommonOpenApiAutoConfiguration {

    static final String SECURITY_SCHEME = "bearer-jwt";
    static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponseDTO";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApi(@Value("${spring.application.name}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .version("v1")
                        .description("Errores en español o inglés según Accept-Language. El token se obtiene de "
                                + "auth-service (OAuth2) y se envía como Authorization: Bearer <token>."))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }

    /** Registra el esquema de {@code ErrorResponseDTO} (y sus tipos anidados) en los componentes. */
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
