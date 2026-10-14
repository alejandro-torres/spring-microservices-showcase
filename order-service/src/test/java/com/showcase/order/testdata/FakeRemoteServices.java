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
 * Servidor HTTP mínimo que hace de product-service y customer-service en los tests de integración, para probar
 * Feign de verdad (JSON, códigos de error, cabeceras) sin arrancar los otros servicios.
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

    /** Respuesta 200 con este JSON para la ruta indicada (p. ej. {@code /api/v1/products/1}). */
    public void respond(String path, String json) {
        responses.put(path, json);
    }

    /** Simula una caída: todas las peticiones responden 500. */
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
