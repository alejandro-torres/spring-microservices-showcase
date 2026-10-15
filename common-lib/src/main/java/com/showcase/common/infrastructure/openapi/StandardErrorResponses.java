package com.showcase.common.infrastructure.openapi;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Respuestas de error que puede dar cada tipo de operación, según la tabla del CRUD de la especificación. Todas
 * usan el cuerpo común {@code ErrorResponseDTO}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StandardErrorResponses {

    private static final Map<Integer, String> DESCRIPTIONS = Map.of(
            400, "Petición no válida (datos, paginación o filtros)",
            401, "Falta el token o no es válido",
            403, "El token no tiene el permiso necesario",
            404, "El recurso no existe o está borrado",
            409, "Conflicto: duplicado, versión desactualizada o dependencias activas",
            422, "La operación incumple una regla de negocio",
            500, "Error inesperado (sin detalles internos)",
            503, "Un servicio del que depende no está disponible");

    /**
     * @param targetsOneResource si la ruta lleva una variable como {@code {id}} (operación sobre un recurso)
     * @return código HTTP → descripción, ordenado por código
     */
    public static SortedMap<Integer, String> forOperation(RequestMethod method, boolean targetsOneResource) {
        SortedMap<Integer, String> responses = new TreeMap<>();
        add(responses, 401, 403, 500, 503);
        switch (method) {
            case GET -> add(responses, targetsOneResource ? 404 : 400);
            case POST -> add(responses, 400, 409, 422);
            case PUT, PATCH -> add(responses, 400, 404, 409, 422);
            case DELETE -> add(responses, 404, 409);
            default -> {
                // HEAD, OPTIONS y TRACE no tienen errores propios que documentar.
            }
        }
        if (targetsOneResource && method == RequestMethod.POST) {
            // POST sobre un recurso existente (p. ej. /customers/{id}/addresses): puede no existir.
            add(responses, 404);
        }
        return responses;
    }

    private static void add(SortedMap<Integer, String> responses, int... statuses) {
        for (int status : statuses) {
            responses.put(status, DESCRIPTIONS.get(status));
        }
    }
}
