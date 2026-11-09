package com.mealmind.leftovers;

import com.mealmind.agent.AgentEvent;
import com.mealmind.agent.AgentException;
import com.mealmind.agent.AgentFacade;
import com.mealmind.agent.RecommendationRanker;
import com.mealmind.llm.LlmException;
import com.mealmind.model.MealPlan;
import com.mealmind.recommend.Recommendation;
import com.mealmind.recommend.RecommendationResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The F10 flow shared by the CLI ({@code plan leftovers}) and the GUI (the Pantry tab's "Use up expiring items"
 * section), so the two front ends cannot drift: the deterministic proposal ({@link #propose}), the optional AI
 * re-ranking ({@link #rerank}) and the wording both print. The AI may only reorder the safe candidates the
 * {@link LeftoverPlanner} chose; if it fails, the deterministic proposal stands and the problem is reported.
 */
public final class LeftoverFlow {

    /** Shown above the proposed meals. */
    public static final String PROPOSAL_TITLE = "Proposal (nothing applied yet):";
    /** Added when the AI reordered the candidates. */
    public static final String AI_NOTE = RecommendationRanker.NOTE + " (it can only reorder these safe recipes).";
    /** Added when AI re-ranking was asked for but there is only one candidate. */
    public static final String SINGLE_CANDIDATE_NOTE = "Only one candidate recipe, so AI ranking was not needed.";
    /** Prefix of the line reporting an AI failure. */
    public static final String AI_PROBLEM_PREFIX = "AI explanation unavailable: ";
    /** Shown after a successful apply. */
    public static final String UNDO_HINT = "Use 'undo' to revert.";
    /** Shown after a discard. */
    public static final String DISCARDED = "Discarded the leftover proposal.";
    /** Shown when there is nothing to apply or discard. */
    public static final String NO_PENDING = "No pending leftover proposal.";

    private LeftoverFlow() {
    }

    /**
     * The result of one step of the flow.
     *
     * @param proposal   the proposal, or empty when blocked
     * @param candidates the candidates (also present when blocked)
     * @param blocked    why there is nothing to do, or empty
     * @param aiProblem  the AI failure message (without the prefix), or empty
     * @param notes      notes to show after the proposal's own notes (unmodifiable)
     */
    public record Outcome(Optional<LeftoverProposal> proposal, Optional<LeftoverCandidates> candidates,
                          Optional<String> blocked, Optional<String> aiProblem, List<String> notes) {

        /**
         * Validates and copies the fields.
         *
         * @throws NullPointerException if a field is null
         */
        public Outcome {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(candidates, "candidates");
            Objects.requireNonNull(blocked, "blocked");
            Objects.requireNonNull(aiProblem, "aiProblem");
            notes = List.copyOf(notes);
        }
    }

    /**
     * Makes the deterministic proposal for the plan, or reports why there is nothing to do.
     *
     * @param planner the planner
     * @param plan    the current plan
     * @param request the request
     * @param today   the current date
     * @return the outcome: blocked, or a proposal with its candidates
     * @throws NullPointerException if an argument is null
     */
    public static Outcome propose(LeftoverPlanner planner, MealPlan plan, LeftoverRequest request, LocalDate today) {
        Objects.requireNonNull(planner, "planner");
        LeftoverCandidates candidates = planner.findCandidates(plan, request, today);
        if (candidates.isBlocked()) {
            return new Outcome(Optional.empty(), Optional.of(candidates), candidates.blocked(), Optional.empty(), List.of());
        }
        LeftoverProposal proposal = planner.assign(candidates, plan, List.of(), Map.of(), request);
        return new Outcome(Optional.of(proposal), Optional.of(candidates), Optional.empty(), Optional.empty(), List.of());
    }

    /**
     * Returns whether AI re-ranking can change anything (at least 2 candidates).
     *
     * @param candidates the candidates
     * @return {@code true} when the AI would be asked
     */
    public static boolean canRerank(LeftoverCandidates candidates) {
        return candidates.candidates().size() >= 2;
    }

    /**
     * Asks the AI to reorder the candidates and assigns them in that order. With fewer than 2 candidates the
     * AI is not asked. If the AI fails, the deterministic proposal is returned with the problem.
     *
     * @param planner    the planner
     * @param candidates the (not blocked) candidates from {@link #propose}
     * @param plan       the plan the candidates were found for
     * @param request    the request
     * @param agent      the AI facade (its busy guard applies)
     * @param activity   receives agent activity events, or null
     * @return the AI-ordered proposal with {@link #AI_NOTE}, or the deterministic one with a note or a problem
     * @throws NullPointerException if a required argument is null
     */
    public static Outcome rerank(LeftoverPlanner planner, LeftoverCandidates candidates, MealPlan plan,
                                 LeftoverRequest request, AgentFacade agent, Consumer<AgentEvent> activity) {
        Objects.requireNonNull(planner, "planner");
        Objects.requireNonNull(agent, "agent");
        LeftoverProposal deterministic = planner.assign(candidates, plan, List.of(), Map.of(), request);
        if (!canRerank(candidates)) {
            return new Outcome(Optional.of(deterministic), Optional.of(candidates), Optional.empty(), Optional.empty(),
                    List.of(SINGLE_CANDIDATE_NOTE));
        }
        try {
            RecommendationResult ranked = agent.rerankRecommendations(planner.toRecommendations(candidates), activity);
            List<String> order = ranked.items().stream().map(item -> item.recipe().getId()).toList();
            Map<String, String> reasons = new LinkedHashMap<>();
            for (Recommendation item : ranked.items()) {
                reasons.put(item.recipe().getId(), item.reason());
            }
            LeftoverProposal ordered = planner.assign(candidates, plan, order, reasons, request);
            return new Outcome(Optional.of(ordered), Optional.of(candidates), Optional.empty(), Optional.empty(), List.of(AI_NOTE));
        } catch (LlmException | AgentException | IllegalStateException e) {
            return new Outcome(Optional.of(deterministic), Optional.of(candidates), Optional.empty(),
                    Optional.of(String.valueOf(e.getMessage())), List.of());
        }
    }

    /**
     * The lines shown above a proposal: "Expiring within N days: ..." and one "Note: ..." line per note.
     *
     * @param proposal   the proposal
     * @param request    the request (for the day window)
     * @param extraNotes notes from the {@link Outcome}, shown after the proposal's own notes
     * @return the lines
     */
    public static List<String> headerLines(LeftoverProposal proposal, LeftoverRequest request, List<String> extraNotes) {
        List<String> lines = new ArrayList<>();
        lines.add("Expiring within " + request.withinDays() + " days: "
                + proposal.expiring().stream().map(ExpiringItem::label).collect(Collectors.joining("; ")));
        proposal.notes().forEach(note -> lines.add("Note: " + note));
        extraNotes.forEach(note -> lines.add("Note: " + note));
        return lines;
    }

    /**
     * The message after a successful apply.
     *
     * @param commandDescription the description of the command now on the history
     * @return "Applied: &lt;description&gt;."
     */
    public static String appliedMessage(String commandDescription) {
        return "Applied: " + commandDescription + ".";
    }
}
