package dev.cayatceek.retryproof;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryVerifierTest {
    @Test
    void recognizesSingleBusinessEffectForConcurrentRequests() throws Exception {
        var balance = new AtomicInteger(100);
        var processed = new AtomicBoolean(false);
        RetryPlan plan = new RetryPlan("transfer-123", 12, 6, Duration.ofSeconds(5));
        RetryReport report = RetryVerifier.verify(plan, key -> {
            if (processed.compareAndSet(false, true)) {
                balance.addAndGet(-10);
            }
            return new OperationResponse(200, key);
        }, balance::get, value -> value == 90);
        assertEquals(12, report.attempts().size());
        assertTrue(report.violations().isEmpty());
        report.assertValid();
    }

    @Test
    void detectsDoubleDebitEvenWhenResponsesAppearSuccessful() throws Exception {
        var balance = new AtomicInteger(100);
        RetryPlan plan = new RetryPlan("transfer-456", 4, 2, Duration.ofSeconds(5));
        RetryReport report = RetryVerifier.verify(plan, key -> {
            balance.addAndGet(-10);
            return new OperationResponse(200, key);
        }, balance::get, value -> value == 90);
        assertFalse(report.stateMatches());
        assertThrows(AssertionError.class, report::assertValid);
    }

    @Test
    void detectsInconsistentOperationIds() throws Exception {
        var counter = new AtomicInteger();
        RetryReport report = RetryVerifier.verify(
            new RetryPlan("order-1", 3, 1, Duration.ofSeconds(5)),
            key -> new OperationResponse(200, key + "-" + counter.incrementAndGet()),
            () -> 1, state -> state == 1);
        assertTrue(report.violations().stream().anyMatch(s -> s.contains("different operation identifier")));
    }

    @Test
    void rejectsInvalidPlansAndSurfacesFailures() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPlan("", 1, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPlan("k", 2, 3, Duration.ofSeconds(1)));
        RetryReport report = RetryVerifier.verify(
            new RetryPlan("failed", 1, 1, Duration.ofSeconds(5)),
            key -> { throw new java.io.IOException("network down"); },
            () -> 0, state -> state == 0);
        assertEquals("IOException", report.attempts().getFirst().errorType());
        assertThrows(AssertionError.class, report::assertValid);
    }
}
