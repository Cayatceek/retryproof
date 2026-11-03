package dev.cayatceek.retryproof;

/**
 * A successful repeat must refer to the same underlying operation.
 * The caller should parse operationId from its API response or resource identity.
 */
public record OperationResponse(int statusCode, String operationId) {
    public OperationResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("invalid HTTP status");
        }
    }
    public boolean successful() { return statusCode >= 200 && statusCode < 300; }
}
