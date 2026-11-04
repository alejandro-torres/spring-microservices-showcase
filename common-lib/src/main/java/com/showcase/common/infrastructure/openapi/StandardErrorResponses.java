package com.showcase.common.infrastructure.openapi;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Error responses each type of operation can give, per the CRUD table in the specification. All
 * use the common {@code ErrorResponseDTO} body.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StandardErrorResponses {

    private static final Map<Integer, String> DESCRIPTIONS = Map.of(
            400, "Invalid request (data, pagination or filters)",
            401, "Missing or invalid token",
            403, "The token does not have the required permission",
            404, "The resource does not exist or has been deleted",
            409, "Conflict: duplicate, stale version or active dependencies",
            422, "The operation breaks a business rule",
            500, "Unexpected error (no internal details)",
            503, "A service it depends on is unavailable");

    /**
     * @param targetsOneResource whether the path has a variable such as {@code {id}} (operation on one resource)
     * @return HTTP code → description, sorted by code
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
                // HEAD, OPTIONS and TRACE have no errors of their own to document.
            }
        }
        if (targetsOneResource && method == RequestMethod.POST) {
            // POST on an existing resource (e.g. /customers/{id}/addresses): it may not exist.
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
