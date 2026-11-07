package com.mealmind.cli;

import com.mealmind.agent.AgentEvent;
import com.mealmind.agent.AgentEventType;
import com.mealmind.agent.AgentException;
import com.mealmind.app.ApplicationContext;
import com.mealmind.llm.LlmException;
import com.mealmind.nutrition.NutritionFormatter;
import com.mealmind.nutrition.NutritionSummary;
import com.mealmind.recommend.RecommendationFormatter;
import com.mealmind.recommend.RecommendationRequest;
import com.mealmind.recommend.RecommendationResult;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The analysis commands: {@code plan nutrition} (F09) and {@code recipe recommend} (F03).
 *
 * <p>Both print the <strong>deterministic</strong> result first. {@code --explain} then asks the AI for
 * commentary or a re-ranking through the {@code AgentFacade}; if that fails or AI is disabled, one line
 * "AI explanation unavailable: ..." is printed and the deterministic result stays the answer.
 */
final class AnalysisCommands {

    static final String NUTRITION_USAGE = "plan nutrition [--explain]";
    static final String RECOMMEND_USAGE = "recipe recommend [--pantry-first] [--max-time <n>] [--limit <n>] [--explain]";
    private static final String EXPLAIN = "--explain";
    private static final String PANTRY_FIRST = "--pantry-first";

    private final ApplicationContext ctx;
    private final PrintStream out;

    AnalysisCommands(ApplicationContext ctx, PrintStream out) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.out = Objects.requireNonNull(out, "out");
    }

    /** {@code plan nutrition [--explain]}. */
    void nutrition(List<String> args) {
        List<String> rest = new ArrayList<>(args);
        boolean explain = takeSwitch(rest, EXPLAIN, NUTRITION_USAGE);
        if (!rest.isEmpty()) {
            throw new UsageException(NUTRITION_USAGE);
        }
        NutritionSummary summary = ctx.nutrition();
        out.print(NutritionFormatter.toText(summary));
        if (!explain || summary.mealsPlanned() == 0) {
            return;
        }
        try {
            out.println("AI commentary: " + ctx.agent().explainNutrition(summary, activity()));
        } catch (LlmException | AgentException e) {
            out.println("AI explanation unavailable: " + e.getMessage());
        }
    }

    /** {@code recipe recommend [--pantry-first] [--max-time n] [--limit n] [--explain]}. */
    void recommend(List<String> args) {
        // The two switches have no value, so they are taken out before CliArgs sees the other flags.
        List<String> rest = new ArrayList<>(args);
        boolean pantryFirst = takeSwitch(rest, PANTRY_FIRST, RECOMMEND_USAGE);
        boolean explain = takeSwitch(rest, EXPLAIN, RECOMMEND_USAGE);
        CliArgs parsed = CliArgs.parse(rest, Set.of("max-time", "limit"), RECOMMEND_USAGE)
                .expectPositional(0, 0, RECOMMEND_USAGE);
        RecommendationRequest defaults = RecommendationRequest.defaults();
        Integer maxTime = parsed.flags().containsKey("max-time")
                ? Integer.valueOf(bounded(parsed.flags().get("max-time"), Integer.MAX_VALUE)) : defaults.maxPrepMinutes();
        int limit = parsed.flags().containsKey("limit")
                ? bounded(parsed.flags().get("limit"), RecommendationRequest.MAX_LIMIT) : defaults.limit();

        RecommendationResult result = ctx.recommender().recommend(new RecommendationRequest(pantryFirst, maxTime, limit));
        out.print(RecommendationFormatter.toText(result));
        if (!explain || result.items().size() < 2) {
            return;
        }
        try {
            RecommendationResult ranked = ctx.agent().rerankRecommendations(result, activity());
            out.println("AI-ranked recommendations:");
            out.print(RecommendationFormatter.toText(ranked));
        } catch (LlmException | AgentException e) {
            out.println("AI explanation unavailable: " + e.getMessage());
        }
    }

    /** Removes {@code name} (any case) from {@code tokens}; given twice is a usage error. */
    private static boolean takeSwitch(List<String> tokens, String name, String usage) {
        int before = tokens.size();
        tokens.removeIf(token -> token.equalsIgnoreCase(name));
        if (before - tokens.size() > 1) {
            throw new UsageException(usage);
        }
        return tokens.size() < before;
    }

    /** A whole number from 1 to {@code max}, or a usage error. */
    private static int bounded(String text, int max) {
        int value = CliArgs.parseInt(text, RECOMMEND_USAGE);
        if (value < 1 || value > max) {
            throw new UsageException(RECOMMEND_USAGE);
        }
        return value;
    }

    /** Prints agent activity as "[agent] ..." lines, except errors (they are reported once, afterwards). */
    private Consumer<AgentEvent> activity() {
        return event -> {
            if (event.type() != AgentEventType.ERROR) {
                out.println("[agent] " + event.message());
            }
        };
    }
}
