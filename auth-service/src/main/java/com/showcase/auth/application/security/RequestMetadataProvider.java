package com.showcase.auth.application.security;

/**
 * Port to get the current request's data without the application depending on the Servlet API
 * or Spring Security.
 */
public interface RequestMetadataProvider {

    /** The current request's data, or {@link RequestMetadata#empty()} if there is no request (background tasks). */
    RequestMetadata current();
}
