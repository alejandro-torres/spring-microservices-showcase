package com.showcase.auth.application.security;

/**
 * Puerto para obtener los datos de la petición en curso sin que la aplicación dependa de la API de Servlet
 * ni de Spring Security.
 */
public interface RequestMetadataProvider {

    /** Los datos de la petición actual, o {@link RequestMetadata#empty()} si no hay petición (tareas en segundo plano). */
    RequestMetadata current();
}
