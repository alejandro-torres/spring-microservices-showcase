package com.showcase.order.testdata;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP server that plays product-service and customer-service in the integration tests, to test
 * Feign for real (JSON, error codes, headers) without starting the other services.
 */
public final class FakeRemoteServices implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> responses = new ConcurrentHashMap<>();
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    public FakeRemoteServices() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 200 response with this JSON for the given path (e.g. {@code /api/v1/products/1}). */
    public void respond(String path, String json) {
        responses.put(path, json);
    }

    /** Simulates an outage: all requests respond 500. */
    public void setDown(boolean isDown) {
        down.set(isDown);
    }

    public String lastAuthorization() {
        return lastAuthorization.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String body = responses.get(exchange.getRequestURI().getPath());
        int status = down.get() ? 500 : body == null ? 404 : 200;
        byte[] bytes = (status == 200 ? body : "{\"code\":\"ERROR\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
