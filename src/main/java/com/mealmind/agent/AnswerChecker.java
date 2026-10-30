package com.mealmind.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Deterministically checks the model's final answer and decides whether it is accepted, needs
 * repair, or is unsafe.
 *
 * @param <T> the answer type
 */
@FunctionalInterface
public interface AnswerChecker<T> {

    /**
     * Checks an answer.
     *
     * @param answer the value of the final-answer key in the model's JSON reply
     * @return the verdict
     */
    Check<T> check(JsonNode answer);
}
