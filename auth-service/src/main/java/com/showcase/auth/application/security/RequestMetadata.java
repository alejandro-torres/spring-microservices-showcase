package com.showcase.auth.application.security;

/**
 * Data of the current request stored in the audit log.
 *
 * @param ip        client IP (the first one in X-Forwarded-For if it comes from the gateway)
 * @param userAgent browser or HTTP client
 * @param traceId   trace identifier (the same as in Zipkin and the logs)
 * @param actor     authenticated user performing the operation, or null if anonymous
 */
public record RequestMetadata(String ip, String userAgent, String traceId, String actor) {

    public static RequestMetadata empty() {
        return new RequestMetadata(null, null, null, null);
    }
}
