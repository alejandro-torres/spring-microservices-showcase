package com.showcase.gateway.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor HTTP mínimo que hace de servicio detrás del gateway: sirve el JWKS de auth-service y, en cualquier otra
 * ruta, devuelve en JSON la ruta y las cabeceras que le han llegado (para comprobar qué reenvía el gateway).
 */
public final class FakeBackend implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> fixedResponses = new ConcurrentHashMap<>();

    public FakeBackend() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void respond(String path, String json) {
        fixedResponses.put(path, json);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = fixedResponses.getOrDefault(path, echo(exchange, path));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String echo(HttpExchange exchange, String path) {
        return "{\"path\":\"" + path + "\",\"authorization\":\"" + header(exchange, "Authorization")
                + "\",\"traceparent\":\"" + header(exchange, "traceparent")
                + "\",\"forwardedHost\":\"" + header(exchange, "X-Forwarded-Host") + "\"}";
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
