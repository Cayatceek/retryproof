package dev.cayatceek.retryproof;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Test report: checks state invariant and repeat-response consistency. */
public final class RetryReport {
    private final List<AttemptResult> attempts;
    private final boolean stateMatches;
    private final boolean timedOut;

    RetryReport(List<AttemptResult> attempts, boolean stateMatches, boolean timedOut) {
        this.attempts = List.copyOf(attempts);
        this.stateMatches = stateMatches;
        this.timedOut = timedOut;
    }

    public List<AttemptResult> attempts() { return attempts; }
    public boolean stateMatches() { return stateMatches; }
    public boolean timedOut() { return timedOut; }

    public List<String> violations() {
        List<String> issues = new ArrayList<>();
        if (timedOut) issues.add("one or more operations exceeded the deadline");
        if (!stateMatches) issues.add("final business state violates the expected invariant");
        String operationId = null;
        for (AttemptResult attempt : attempts) {
            if (!attempt.successful()) {
                issues.add("attempt " + attempt.ordinal() + " did not return HTTP 2xx"
                    + (attempt.errorType() == null ? "" : " (" + attempt.errorType() + ")"));
                continue;
            }
            String currentId = attempt.response().operationId();
            if (currentId == null || currentId.isBlank()) {
                issues.add("attempt " + attempt.ordinal() + " has no operation identifier");
            } else if (operationId == null) {
                operationId = currentId;
            } else if (!Objects.equals(operationId, currentId)) {
                issues.add("attempt " + attempt.ordinal() + " returned a different operation identifier");
            }
        }
        return List.copyOf(issues);
    }

    public void assertValid() {
        List<String> issues = violations();
        if (!issues.isEmpty()) {
            throw new AssertionError("RetryProof found " + issues.size()
                + " idempotency contract failure(s):\n- " + String.join("\n- ", issues));
        }
    }
}
