package dev.cayatceek.retryproof;

/** Call a controlled test endpoint with the supplied stable idempotency key. */
@FunctionalInterface
public interface RetryOperation {
    OperationResponse call(String idempotencyKey) throws Exception;
}
