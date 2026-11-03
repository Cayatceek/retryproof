package dev.cayatceek.retryproof;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs same-key requests with bounded concurrency against disposable test state.
 * A timeout always fails verification; cancellation is best-effort.
 */
public final class RetryVerifier {
    private RetryVerifier() { }

    public static <T> RetryReport verify(
        RetryPlan plan,
        RetryOperation operation,
        Supplier<T> readFinalState,
        Predicate<T> expectedFinalState
    ) throws InterruptedException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(readFinalState, "readFinalState");
        Objects.requireNonNull(expectedFinalState, "expectedFinalState");

        ExecutorService pool = Executors.newFixedThreadPool(plan.concurrency());
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<AttemptResult>> pending = new ArrayList<>();
        List<AttemptResult> outcomes = new ArrayList<>();
        boolean timedOut = false;
        try {
            for (int i = 0; i < plan.attempts(); i++) {
                final int ordinal = i + 1;
                pending.add(pool.submit(() -> {
                    gate.await();
                    try {
                        OperationResponse response = Objects.requireNonNull(
                            operation.call(plan.idempotencyKey()), "operation response");
                        return new AttemptResult(ordinal, response, null);
                    } catch (Exception ex) {
                        return new AttemptResult(ordinal, null, ex.getClass().getSimpleName());
                    }
                }));
            }
            long startNanos = System.nanoTime();
            gate.countDown();
            for (int i = 0; i < pending.size(); i++) {
                Future<AttemptResult> future = pending.get(i);
                long remaining = plan.timeout().toNanos() - (System.nanoTime() - startNanos);
                if (remaining <= 0) {
                    timedOut = true;
                    outcomes.add(new AttemptResult(i + 1, null, "Timeout"));
                    continue;
                }
                try {
                    outcomes.add(future.get(remaining, TimeUnit.NANOSECONDS));
                } catch (TimeoutException ex) {
                    timedOut = true;
                    outcomes.add(new AttemptResult(i + 1, null, "Timeout"));
                } catch (ExecutionException ex) {
                    outcomes.add(new AttemptResult(i + 1, null,
                        ex.getCause() == null ? "ExecutionException" : ex.getCause().getClass().getSimpleName()));
                }
            }
        } finally {
            gate.countDown();
            for (Future<?> future : pending) future.cancel(true);
            pool.shutdownNow();
        }
        return new RetryReport(outcomes, expectedFinalState.test(readFinalState.get()), timedOut);
    }
}
