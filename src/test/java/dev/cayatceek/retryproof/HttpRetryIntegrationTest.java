package dev.cayatceek.retryproof;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real HTTP integration test using a disposable, concurrency-safe transfer endpoint. */
class HttpRetryIntegrationTest {
    @Test
    void repeatedConcurrentHttpRequestsCauseOnlyOneDebit() throws Exception {
        AtomicInteger balance = new AtomicInteger(100);
        AtomicBoolean alreadyDebited = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverWorkers = Executors.newFixedThreadPool(4);
        server.setExecutor(serverWorkers);
        server.createContext("/transfer", exchange -> {
            try {
                String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                if (key == null || key.isBlank()) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    if (alreadyDebited.compareAndSet(false, true)) {
                        balance.addAndGet(-10);
                    }
                    byte[] body = key.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/transfer");
            try (HttpClient client = HttpClient.newHttpClient()) {
                RetryReport report = RetryVerifier.verify(
                    new RetryPlan("transfer-http-1", 12, 4, Duration.ofSeconds(10)),
                    key -> {
                        HttpRequest request = HttpRequest.newBuilder(uri)
                            .header("Idempotency-Key", key)
                            .POST(HttpRequest.BodyPublishers.noBody()).build();
                        HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString());
                        return new OperationResponse(response.statusCode(), response.body());
                    },
                    balance::get,
                    value -> value == 90);
                report.assertValid();
                assertEquals(12, report.attempts().size());
                assertEquals(90, balance.get());
            }
        } finally {
            server.stop(0);
            serverWorkers.shutdownNow();
        }
    }
}
