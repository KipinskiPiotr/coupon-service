package com.empik.couponservice.geolocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Runs against a local HTTP server standing in for ipapi.co, so the tests are fast, deterministic and
 * also cover timeouts and error responses.
 */
class IpapiCountryResolverTest {

    private static final String PUBLIC_IP = "203.0.113.10";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private HttpServer server;
    private final List<HttpExchange> requests = new CopyOnWriteArrayList<>();
    private volatile Responder responder;
    private IpapiCountryResolver resolver;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange);
            responder.respond(exchange);
        });
        server.start();
        resolver = resolverFor(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsCountryCodeFromProvider() {
        responder = exchange -> respond(exchange, 200, "PL");

        assertThat(resolver.resolveCountry(PUBLIC_IP)).isEqualTo("PL");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.getRequestURI()).hasPath("/" + PUBLIC_IP + "/country/");
            assertThat(request.getRequestHeaders().getFirst("User-Agent")).isEqualTo("coupon-service");
        });
    }

    @Test
    void failsWhenProviderDoesNotKnowTheCountry() {
        responder = exchange -> respond(exchange, 200, "Undefined");

        assertUnavailable(PUBLIC_IP);
    }

    @Test
    void failsWhenProviderReturnsError() {
        responder = exchange -> respond(exchange, 429, "Too many requests");

        assertUnavailable(PUBLIC_IP);
    }

    @Test
    void failsWhenProviderIsTooSlow() {
        responder = exchange -> {
            sleep(READ_TIMEOUT.multipliedBy(3));
            respond(exchange, 200, "PL");
        };

        assertUnavailable(PUBLIC_IP);
    }

    @Test
    void failsWhenProviderIsUnreachable() {
        server.stop(0);

        assertUnavailable(PUBLIC_IP);
    }

    @ParameterizedTest
    @ValueSource(strings = { "127.0.0.1", "10.0.0.1", "172.17.0.1", "192.168.1.1", "169.254.0.1", "::1",
            "not-an-ip" })
    void doesNotCallProviderForNonPublicAddresses(String ipAddress) {
        responder = exchange -> respond(exchange, 200, "PL");

        assertUnavailable(ipAddress);
        assertThat(requests).isEmpty();
    }

    private void assertUnavailable(String ipAddress) {
        assertThatThrownBy(() -> resolver.resolveCountry(ipAddress))
            .isInstanceOf(GeolocationUnavailableException.class);
    }

    private static IpapiCountryResolver resolverFor(URI baseUrl) {
        return new IpapiCountryResolver(new GeolocationProperties(baseUrl, Duration.ofSeconds(1), READ_TIMEOUT, null));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Responder {

        void respond(HttpExchange exchange) throws IOException;

    }

}
