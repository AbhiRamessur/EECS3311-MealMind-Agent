package com.mealmind.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    private static final Violation SAFETY = new Violation(Severity.SAFETY, ViolationType.ALLERGEN, "Peanuts",
            "Peanuts contains peanut, a listed allergen");
    private static final Violation ADVISORY = new Violation(Severity.ADVISORY, ViolationType.CALORIES, "Day 1",
            "Day 1: 2500 kcal is above 110% of your 2000 kcal target");

    @Test
    void okIsSafeAndClean() {
        assertTrue(ValidationResult.ok().isSafe());
        assertTrue(ValidationResult.ok().isClean());
        assertEquals("no issues", ValidationResult.ok().summary());
    }

    @Test
    void advisoriesKeepResultSafeButNotClean() {
        ValidationResult result = new ValidationResult(List.of(ADVISORY));

        assertTrue(result.isSafe());
        assertFalse(result.isClean());
        assertEquals("1 advisory", result.summary());
    }

    @Test
    void safetyViolationMakesResultUnsafe() {
        ValidationResult result = new ValidationResult(List.of(SAFETY));

        assertFalse(result.isSafe());
        assertEquals("1 safety issue", result.summary());
    }

    @Test
    void mergeKeepsOrderAndSplitsBySeverity() {
        ValidationResult merged = new ValidationResult(List.of(SAFETY)).merge(new ValidationResult(List.of(ADVISORY, ADVISORY)))
                .merge(new ValidationResult(List.of(SAFETY)));

        assertEquals(List.of(SAFETY, ADVISORY, ADVISORY, SAFETY), merged.getViolations());
        assertEquals(List.of(SAFETY, SAFETY), merged.getSafetyViolations());
        assertEquals(List.of(ADVISORY, ADVISORY), merged.getAdvisories());
        assertEquals("2 safety issues, 2 advisories", merged.summary());
        assertEquals(new ValidationResult(List.of(SAFETY, ADVISORY, ADVISORY, SAFETY)), merged);
    }

    @Test
    void listsAreUnmodifiableAndViolationIsValidated() {
        ValidationResult result = new ValidationResult(List.of(SAFETY));

        assertThrows(UnsupportedOperationException.class, () -> result.getViolations().add(ADVISORY));
        assertThrows(UnsupportedOperationException.class, () -> result.getAdvisories().add(ADVISORY));
        assertThrows(IllegalArgumentException.class,
                () -> new Violation(Severity.SAFETY, ViolationType.DIET, "x", " "));
        assertThrows(NullPointerException.class, () -> new Violation(null, ViolationType.DIET, "x", "m"));
    }
}
