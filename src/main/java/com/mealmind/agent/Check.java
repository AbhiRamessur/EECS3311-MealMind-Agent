package com.mealmind.agent;

/**
 * The verdict of an {@link AnswerChecker} on a model's final answer.
 *
 * @param <T> the answer type
 */
public sealed interface Check<T> permits Check.Accepted, Check.Repair, Check.Unsafe {

    /**
     * The answer is valid and safe.
     *
     * @param value the accepted answer
     * @param <T>   the answer type
     */
    record Accepted<T>(T value) implements Check<T> {
    }

    /**
     * The answer is malformed or implausible; the feedback is sent back to the model.
     *
     * @param feedback what to fix
     * @param <T>      the answer type
     */
    record Repair<T>(String feedback) implements Check<T> {
    }

    /**
     * The answer is well formed but breaks the safety rules; the feedback is sent back to the model.
     *
     * @param feedback  the violations to fix
     * @param candidate the unsafe answer, kept in case no safe one follows
     * @param <T>       the answer type
     */
    record Unsafe<T>(String feedback, T candidate) implements Check<T> {
    }
}
