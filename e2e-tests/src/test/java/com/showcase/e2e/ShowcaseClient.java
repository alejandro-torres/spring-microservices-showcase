package com.showcase.e2e;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP de las pruebas de extremo a extremo: todo pasa por el gateway, igual que un cliente real. Obtiene el
 * token con el flujo authorization code + PKCE (formulario de login de auth-service), como haría un navegador.
 */
final class ShowcaseClient {

    static final String CLIENT_ID = "web-client";
    static final String REDIRECT_URI = "http://127.0.0.1:3000/callback";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final String baseUrl;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private String accessToken;

    ShowcaseClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** authorization code + PKCE: /oauth2/authorize → /login → /oauth2/authorize → código → /oauth2/token. */
    void login(String username, String password) throws Exception {
        String verifier = randomUrlSafe(32);
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String authorizeUrl = baseUrl + "/oauth2/authorize?" + form(Map.of(
                "response_type", "code", "client_id", CLIENT_ID, "scope", "openid profile",
                "redirect_uri", REDIRECT_URI, "code_challenge", challenge, "code_challenge_method", "S256",
                "state", randomUrlSafe(8)));

        HttpResponse<String> authorize = send(browserGet(authorizeUrl));
        String loginPage = send(browserGet(absolute(location(authorize)))).body();
        Matcher csrf = CSRF.matcher(loginPage);
        if (!csrf.find()) {
            throw new IllegalStateException("La página de login no tiene token CSRF: " + loginPage);
        }
        HttpResponse<String> loggedIn = send(HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                        "username", username, "password", password, "_csrf", csrf.group(1)))))
                .build());
        HttpResponse<String> authorized = send(browserGet(absolute(location(loggedIn))));
        String callback = location(authorized);
        if (!callback.startsWith(REDIRECT_URI)) {
            throw new IllegalStateException("No se ha obtenido el código de autorización: " + callback);
        }
        String code = queryParameter(callback, "code");

        HttpResponse<String> token = send(HttpRequest.newBuilder(URI.create(baseUrl + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of(
                        "grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT_URI,
                        "client_id", CLIENT_ID, "code_verifier", verifier))))
                .build());
        if (token.statusCode() != 200) {
            throw new IllegalStateException("Error al pedir el token: " + token.statusCode() + " " + token.body());
        }
        accessToken = json.readTree(token.body()).get("access_token").asString();
    }

    Response get(String path) throws Exception {
        return call(authorized(path).GET());
    }

    Response post(String path, String body) throws Exception {
        return call(authorized(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    Response put(String path, String body) throws Exception {
        return call(authorized(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body)));
    }

    private HttpRequest.Builder authorized(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept-Language", "es");
        return accessToken == null ? builder : builder.header("Authorization", "Bearer " + accessToken);
    }

    private Response call(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = send(request.build());
        JsonNode body = response.body() == null || response.body().isBlank() ? null : json.readTree(response.body());
        return new Response(response.statusCode(), body, response.headers().firstValue("X-Trace-Id").orElse(null),
                response.headers().firstValue("X-Instance-Id").orElse(null));
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest browserGet(String url) {
        return HttpRequest.newBuilder(URI.create(url)).GET().build();
    }

    private String absolute(String location) {
        return location.startsWith("http") ? location : baseUrl + location;
    }

    private static String location(HttpResponse<?> response) {
        return response.headers().firstValue("Location").orElseThrow(() -> new IllegalStateException(
                "Se esperaba una redirección y llegó " + response.statusCode() + " " + response.body()));
    }

    private static String queryParameter(String url, String name) {
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Falta " + name + " en " + url);
    }

    private static String form(Map<String, String> values) {
        return new LinkedHashMap<>(values).entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String randomUrlSafe(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    record Response(int status, JsonNode body, String traceId, String instanceId) {
    }
}
