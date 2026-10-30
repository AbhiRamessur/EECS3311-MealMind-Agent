package com.mealmind.agent;

/**
 * Budgets for one agent run; they also act as cost guards.
 *
 * <p>Stage 1 specifies up to 3 safety retries (kept) and a single repair prompt for malformed JSON.
 * The default allows 2 repairs because plausibility problems in a draft (see
 * {@link RecipeDraftParser}) share the same repair budget.
 *
 * @param maxToolSteps     tool calls allowed per run, 0 to 10
 * @param maxSafetyRetries corrections allowed after a safety violation, 0 to 5
 * @param maxRepairs       corrections allowed for malformed or implausible replies, 0 to 5
 * @param maxLlmCalls      model calls allowed per run, 1 to 30
 * @param maxTokens        token limit per model reply, 200 to 8000
 * @param temperature      sampling temperature, 0 to 1
 */
public record AgentLimits(int maxToolSteps, int maxSafetyRetries, int maxRepairs, int maxLlmCalls, int maxTokens,
                          double temperature) {

    /**
     * Validates the limits.
     *
     * @throws IllegalArgumentException if any value is out of range
     */
    public AgentLimits {
        check(maxToolSteps, 0, 10, "maxToolSteps");
        check(maxSafetyRetries, 0, 5, "maxSafetyRetries");
        check(maxRepairs, 0, 5, "maxRepairs");
        check(maxLlmCalls, 1, 30, "maxLlmCalls");
        check(maxTokens, 200, 8000, "maxTokens");
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 1) {
            throw new IllegalArgumentException("temperature must be between 0 and 1, got " + temperature);
        }
    }

    private static void check(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ", got " + value);
        }
    }

    /**
     * Returns the defaults: 4 tool steps, 3 safety retries, 2 repairs, 12 model calls, 1500 tokens, temperature 0.3.
     *
     * @return the default limits
     */
    public static AgentLimits defaults() {
        return new AgentLimits(4, 3, 2, 12, 1500, 0.3);
    }
}
