package dev.cayatceek.retryproof;

/** One attempt's observable result. No secrets or response bodies are persisted. */
public record AttemptResult(int ordinal, OperationResponse response, String errorType) {
    public boolean successful() { return response != null && response.successful(); }
}
