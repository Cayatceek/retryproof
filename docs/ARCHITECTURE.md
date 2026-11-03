# Design decisions

The test fixture provides an operation (with test credentials), a stable key, a final-state reader, and a final-state predicate.

RetryVerifier schedules all calls through an executor; a start latch promotes overlapping requests. It collects either HTTP result or exception type, without storing sensitive bodies or credentials. All attempts count toward a single deadline. A timeout is always an inconclusive failure, not a pass.

The report verifies:
1. State after retries matches **one** intended business effect.
2. All completed calls return 2xx.
3. All successful calls reference one operation identity.

Do not infer actual request concurrency from worker count; server-side serialization may change execution. Resource cleanup and timeout behavior should be verified against an external service in future releases.
