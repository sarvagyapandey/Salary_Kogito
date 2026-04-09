package org.acme.salary;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Explicit CORS filter to ensure Access-Control-Allow-* headers are always returned,
 * including for GraphQL OPTIONS preflight.
 */
@Provider
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String ALLOWED_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000";
    private static final String ALLOWED_HEADERS = "accept,authorization,content-type,x-requested-with,apollographql-client-name,apollographql-client-version";
    private static final String ALLOWED_METHODS = "GET,POST,OPTIONS";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Handle preflight requests early.
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            addCorsHeaders(requestContext.getHeaders().getFirst("Origin"), null);
            requestContext.abortWith(Response.ok().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String origin = requestContext.getHeaders().getFirst("Origin");
        addCorsHeaders(origin, responseContext);
    }

    private void addCorsHeaders(String origin, ContainerResponseContext responseContext) {
        if (origin == null) return;
        // Mirror allowed origins list (no wildcard when credentials might be used).
        List<String> allowed = List.of(ALLOWED_ORIGINS.split(","));
        if (!allowed.contains(origin)) {
            return;
        }
        if (responseContext != null) {
            responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().putSingle("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            responseContext.getHeaders().putSingle("Access-Control-Allow-Methods", ALLOWED_METHODS);
            responseContext.getHeaders().putSingle("Access-Control-Allow-Credentials", "true");
        }
    }
}
