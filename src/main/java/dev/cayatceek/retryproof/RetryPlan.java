package dev.cayatceek.retryproof;

import java.time.Duration;
import java.util.Objects;

/** Bounded workload of repeated calls carrying the same idempotency key. */
public record RetryPlan(String idempotencyKey, int attempts, int concurrency, Duration timeout) {
    public RetryPlan {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(timeout, "timeout");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey cannot be blank");
        if (attempts < 1 || attempts > 1000) throw new IllegalArgumentException("attempts must be 1..1000");
        if (concurrency < 1 || concurrency > attempts) {
            throw new IllegalArgumentException("concurrency must be 1..attempts");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("timeout must be in (0, 5 minutes]");
        }
    }
}
