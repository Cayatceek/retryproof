# RetryProof

**Concurrent, same-key idempotency regression testing for Java backend services.**

A retry after a lost response must not cause a second payment, debit or order. RetryProof runs a bounded number of simultaneous/repeated operations with the **same idempotency key**, compares operation identities, and validates a caller-defined business-state invariant.

**Status:** 0.1.0-SNAPSHOT experimental SDK. No Maven Central release. Java 21, zero runtime dependencies.

## Usage

```java
RetryPlan plan = new RetryPlan("payment-test-001", 10, 5, Duration.ofSeconds(10));

RetryReport report = RetryVerifier.verify(
    plan,
    key -> {
        // Send a POST to a disposable test API, including Idempotency-Key: key.
        // Map the response to the same underlying transaction/payment ID.
        return new OperationResponse(200, callTestPaymentApi(key).transactionId());
    },
    () -> readTestAccountBalance(),
    balance -> balance == expectedBalanceAfterOneDebit
);
report.assertValid();
```

Callers must supply a **known initial state**, calculate the expected state after **one** business operation, and provide an independent read of the resulting state.

## Semantics

- All requests use one stable idempotency key. Concurrent tasks share a bounded executor; results are collected within a single deadline.
- Every response must be HTTP 2xx and expose the **same non-empty operation ID**. Any thrown exception or timeout fails validation.
- The state invariant must pass. Successful HTTP responses alone cannot prove a single business effect.
- Timeouts cancel outstanding tasks best-effort; background server requests may finish later. The SDK **does not read final state after a timeout**, because the results could be premature. It reports the run as invalid; reset disposable state and rerun after pending server operations settle. **Do not test against real financial accounts.**
- RetryProof is an integration-test SDK, **not** a payment-processing or production retry library. It does not guarantee exactly-once delivery, inject network faults, or audit a distributed system.

## Development

```bash
mvn verify
mvn install
```

See [architecture](docs/ARCHITECTURE.md) and [roadmap](docs/ROADMAP.md). GPL-3.0 (see LICENSE).
