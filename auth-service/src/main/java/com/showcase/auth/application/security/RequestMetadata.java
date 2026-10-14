package com.showcase.auth.application.security;

/**
 * Datos de la petición en curso que se guardan en la auditoría.
 *
 * @param ip        IP del cliente (la primera de X-Forwarded-For si viene del gateway)
 * @param userAgent navegador o cliente HTTP
 * @param traceId   identificador de la traza (el mismo que en Zipkin y en los logs)
 * @param actor     usuario autenticado que hace la operación, o null si es anónima
 */
public record RequestMetadata(String ip, String userAgent, String traceId, String actor) {

    public static RequestMetadata empty() {
        return new RequestMetadata(null, null, null, null);
    }
}
