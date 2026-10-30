package com.mealmind.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * The immutable outcome of a validation: zero or more {@link Violation}s.
 * A result is <em>safe</em> when it has no {@link Severity#SAFETY} violations (advisories are
 * allowed) and <em>clean</em> when it has no violations at all.
 */
public final class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(List.of());

    private final List<Violation> violations;

    /**
     * Creates a result.
     *
     * @param violations the violations found, in report order (copied)
     * @throws NullPointerException if the list or an element is null
     */
    public ValidationResult(List<Violation> violations) {
        this.violations = List.copyOf(violations);
    }

    /**
     * Returns a result with no violations.
     *
     * @return the empty result
     */
    public static ValidationResult ok() {
        return OK;
    }

    /**
     * Returns all violations.
     *
     * @return an unmodifiable list
     */
    public List<Violation> getViolations() {
        return violations;
    }

    /**
     * Returns the violations that block acceptance.
     *
     * @return an unmodifiable list of {@link Severity#SAFETY} violations
     */
    public List<Violation> getSafetyViolations() {
        return violations.stream().filter(v -> v.severity() == Severity.SAFETY).toList();
    }

    /**
     * Returns the warnings that do not block.
     *
     * @return an unmodifiable list of {@link Severity#ADVISORY} violations
     */
    public List<Violation> getAdvisories() {
        return violations.stream().filter(v -> v.severity() == Severity.ADVISORY).toList();
    }

    /**
     * Returns whether there are no safety violations.
     *
     * @return {@code true} if safe (advisories may still exist)
     */
    public boolean isSafe() {
        return getSafetyViolations().isEmpty();
    }

    /**
     * Returns whether there are no violations at all.
     *
     * @return {@code true} if clean
     */
    public boolean isClean() {
        return violations.isEmpty();
    }

    /**
     * Combines this result with another, keeping this result's violations first.
     *
     * @param other the result to add
     * @return a new result with both lists of violations
     */
    public ValidationResult merge(ValidationResult other) {
        List<Violation> all = new ArrayList<>(violations);
        all.addAll(other.violations);
        return new ValidationResult(all);
    }

    /**
     * Summarizes the counts in one line, e.g. {@code "2 safety issues, 1 advisory"} or {@code "no issues"}.
     *
     * @return the summary
     */
    public String summary() {
        int safety = getSafetyViolations().size();
        int advisories = getAdvisories().size();
        if (safety == 0 && advisories == 0) {
            return "no issues";
        }
        List<String> parts = new ArrayList<>();
        if (safety > 0) {
            parts.add(safety + (safety == 1 ? " safety issue" : " safety issues"));
        }
        if (advisories > 0) {
            parts.add(advisories + (advisories == 1 ? " advisory" : " advisories"));
        }
        return String.join(", ", parts);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ValidationResult other && violations.equals(other.violations));
    }

    @Override
    public int hashCode() {
        return violations.hashCode();
    }

    @Override
    public String toString() {
        return "ValidationResult{" + summary() + ", violations=" + violations + "}";
    }
}
