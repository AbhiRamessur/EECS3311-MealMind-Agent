package com.mealmind.leftovers;

import com.mealmind.agent.PlanMeals;
import com.mealmind.command.Command;
import com.mealmind.command.CommandHistory;
import com.mealmind.command.MacroCommand;
import com.mealmind.command.ReplaceMealCommand;
import com.mealmind.model.DayPlan;
import com.mealmind.model.Meal;
import com.mealmind.model.MealPlan;
import com.mealmind.model.MealPlan.Slot;
import com.mealmind.model.MealType;
import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.model.RecipeScaler;
import com.mealmind.pantry.Pantry;
import com.mealmind.planning.LeftoverStrategy;
import com.mealmind.planning.PantryCoverage;
import com.mealmind.planning.ScoringContext;
import com.mealmind.preferences.RecipePreferences;
import com.mealmind.profile.UserProfile;
import com.mealmind.recommend.Recommendation;
import com.mealmind.recommend.RecommendationResult;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plans meals that use up expiring pantry items (F10, UC10). Covers the Stage 1 responsibilities of
 * {@code ExpirationAnalyzer} (finding and ranking expiring items) and {@code generateZeroWastePlan}
 * (choosing recipes and slots).
 *
 * <p>The flow is: {@link #findCandidates} (expiring items, eligible slots, safe candidate recipes scored by
 * {@link LeftoverStrategy}), {@link #assign} (candidates to slots, in a given order), then {@link #apply}
 * (re-check and execute as one undoable {@link MacroCommand}). {@link #propose} does the first two in the
 * deterministic order. Every recipe is checked by {@link ConstraintValidator#validateAgainstBlacklists}
 * when candidates are built and again just before applying. Nothing here changes the recipe book or pantry.
 *
 * <p><strong>Limitations:</strong> matching is by ingredient <em>name</em> only (quantities are not
 * compared); the Stage 1 option of AI-drafted recipes is not implemented (AI may only reorder these
 * existing recipes through {@link #toRecommendations}); expired items are included, flagged as expired.
 */
public final class LeftoverPlanner {

    private static final int MAX_REASON = 140;
    private static final int MAX_NAMES_SUGGESTED = 3;
    private static final int LOW_RATING = 2;

    private final RecipeBook recipes;
    private final Pantry pantry;
    private final ConstraintValidator validator;
    private final UserProfile profile;
    private final RecipePreferences preferences;

    /** A candidate and its score, used only while sorting. */
    private record Scored(LeftoverCandidate candidate, double score) {
    }

    /**
     * Creates the planner.
     *
     * @param recipes     the recipe book to choose from (read only)
     * @param pantry      the pantry (read only)
     * @param validator   the safety check
     * @param profile     the profile whose household servings size each meal
     * @param preferences ratings and favourites
     * @throws NullPointerException if an argument is null
     */
    public LeftoverPlanner(RecipeBook recipes, Pantry pantry, ConstraintValidator validator, UserProfile profile,
                           RecipePreferences preferences) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    /**
     * Finds the expiring items, the eligible slots and the safe candidate recipes. When there is nothing to
     * do, {@link LeftoverCandidates#blocked()} says why (checked in the order: pantry, slots, recipes).
     *
     * @param plan    the current plan
     * @param request the request
     * @param today   the current date
     * @return the candidates, or a blocked result
     * @throws NullPointerException if an argument is null
     */
    public LeftoverCandidates findCandidates(MealPlan plan, LeftoverRequest request, LocalDate today) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(today, "today");
        if (pantry.isEmpty()) {
            return blocked(List.of(), List.of(), "The pantry is empty; nothing to do.");
        }
        List<ExpiringItem> expiring = pantry.getExpiringItems(today, request.withinDays()).stream()
                .map(item -> ExpiringItem.of(item, today))
                .sorted(Comparator.comparingLong(ExpiringItem::daysUntilExpiry).thenComparing(ExpiringItem::name))
                .toList();
        if (expiring.isEmpty()) {
            return blocked(expiring, List.of(),
                    "No pantry items expire within " + request.withinDays() + " days; nothing to do.");
        }
        List<Slot> slots = slots(plan, request.overwrite());
        if (slots.isEmpty()) {
            return blocked(expiring, slots, "The plan has no empty slots. Use --overwrite to let leftover meals "
                    + "replace existing ones (undoable with 'undo').");
        }

        List<String> notes = new ArrayList<>();
        List<LeftoverCandidate> candidates = rankedCandidates(expiring);
        List<LeftoverCandidate> liked = candidates.stream().filter(c -> !isLowRated(c)).toList();
        if (!liked.isEmpty() && liked.size() < candidates.size()) {
            notes.add("Excluded " + (candidates.size() - liked.size()) + " recipe(s) you rated 1-2 stars.");
            candidates = liked;
        }
        if (candidates.isEmpty()) {
            List<String> names = expiring.stream().map(ExpiringItem::name).limit(MAX_NAMES_SUGGESTED).toList();
            return blocked(expiring, slots, "No safe recipe in your recipe book uses: " + String.join(", ", names)
                    + ". Try: recipe generate \"a dish using " + String.join(" and ", names) + "\"");
        }
        return new LeftoverCandidates(expiring, candidates, slots, notes, Optional.empty());
    }

    private static LeftoverCandidates blocked(List<ExpiringItem> expiring, List<Slot> slots, String reason) {
        return new LeftoverCandidates(expiring, List.of(), slots, List.of(), Optional.of(reason));
    }

    /** Empty slots in day and slot order, then (when overwriting) the filled ones in the same order. */
    private static List<Slot> slots(MealPlan plan, boolean overwrite) {
        List<Slot> slots = new ArrayList<>(plan.getEmptySlots());
        if (overwrite) {
            for (DayPlan day : plan.getDays()) {
                for (MealType type : MealType.values()) {
                    if (day.getMeal(type).isPresent()) {
                        slots.add(new Slot(day.getDayNumber(), type));
                    }
                }
            }
        }
        return slots;
    }

    /** Safe recipes that use an expiring item, best score first, then fewer missing, then id. */
    private List<LeftoverCandidate> rankedCandidates(List<ExpiringItem> expiring) {
        LeftoverStrategy strategy = new LeftoverStrategy(expiring);
        ScoringContext context = new ScoringContext(pantry);
        List<Scored> scored = new ArrayList<>();
        for (Recipe recipe : recipes.getAll()) {
            List<ExpiringItem> uses = strategy.used(recipe);
            if (uses.isEmpty() || !safetyProblems(recipe).isEmpty()) {
                continue;
            }
            PantryCoverage.Result coverage = PantryCoverage.of(recipe, pantry);
            LeftoverCandidate candidate = new LeftoverCandidate(recipe, uses, coverage.percent(), coverage.missing(),
                    preferences.isFavorite(recipe.getId()), preferences.getRating(recipe.getId()).orElse(null));
            scored.add(new Scored(candidate, strategy.score(recipe, context)));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingInt(s -> s.candidate().missing().size())
                        .thenComparing(s -> s.candidate().recipe().getId()))
                .map(Scored::candidate)
                .toList();
    }

    private static boolean isLowRated(LeftoverCandidate candidate) {
        return candidate.rating() != null && candidate.rating() <= LOW_RATING;
    }

    private List<String> safetyProblems(Recipe recipe) {
        return validator.validateAgainstBlacklists(recipe).getSafetyViolations().stream()
                .map(Violation::message).toList();
    }

    /**
     * Turns the candidates into recommendations, so the existing AI re-ranking
     * ({@code AgentFacade.rerankRecommendations}) can reorder them.
     *
     * @param candidates the candidates
     * @return one recommendation per candidate, in the same order, with the notes copied
     * @throws NullPointerException if {@code candidates} is null
     */
    public RecommendationResult toRecommendations(LeftoverCandidates candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<Recommendation> items = candidates.candidates().stream()
                .map(c -> new Recommendation(c.recipe(), c.coveragePercent(), c.missing(), c.favorite(), c.rating(),
                        usesText(c.uses())))
                .toList();
        return new RecommendationResult(items, candidates.notes());
    }

    /**
     * Assigns candidates to slots. Recipes are taken in {@code orderedRecipeIds} order; ids that are not
     * candidates are ignored and candidates missing from the order are appended in their deterministic order.
     * Each recipe is used at most once, and there are at most {@code request.meals()} assignments and no
     * more than there are slots.
     *
     * @param candidates      the candidates (not blocked)
     * @param plan            the current plan, used to mark slots that are filled now
     * @param orderedRecipeIds the preferred order, e.g. from AI re-ranking
     * @param reasonOverrides reasons by recipe id, e.g. from AI re-ranking; blank ones are ignored
     * @param request         the request
     * @return the proposal
     * @throws IllegalStateException if {@code candidates} is blocked
     * @throws NullPointerException  if an argument is null
     */
    public LeftoverProposal assign(LeftoverCandidates candidates, MealPlan plan, List<String> orderedRecipeIds,
                                   Map<String, String> reasonOverrides, LeftoverRequest request) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(orderedRecipeIds, "orderedRecipeIds");
        Objects.requireNonNull(reasonOverrides, "reasonOverrides");
        Objects.requireNonNull(request, "request");
        candidates.blocked().ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });

        Map<String, LeftoverCandidate> byId = new LinkedHashMap<>();
        candidates.candidates().forEach(c -> byId.put(c.recipe().getId(), c));
        Set<String> order = new LinkedHashSet<>();
        orderedRecipeIds.stream().filter(byId::containsKey).forEach(order::add);
        order.addAll(byId.keySet());

        int count = Math.min(Math.min(request.meals(), candidates.slots().size()), order.size());
        List<LeftoverAssignment> assignments = new ArrayList<>();
        List<String> ids = List.copyOf(order);
        for (int i = 0; i < count; i++) {
            LeftoverCandidate candidate = byId.get(ids.get(i));
            Slot slot = candidates.slots().get(i);
            String override = reasonOverrides.get(candidate.recipe().getId());
            String reason = override != null && !override.isBlank() ? override.strip() : usesText(candidate.uses());
            assignments.add(new LeftoverAssignment(slot.day(), slot.type(), candidate.recipe(), candidate.uses(),
                    cap(reason), plan.getMeal(slot.day(), slot.type()).isPresent()));
        }
        return new LeftoverProposal(plan, assignments, candidates.expiring(),
                notes(candidates, assignments));
    }

    private static List<String> notes(LeftoverCandidates candidates, List<LeftoverAssignment> assignments) {
        List<String> notes = new ArrayList<>(candidates.notes());
        notes.add(assignments.size() + " meal(s) proposed for " + candidates.expiring().size() + " expiring item(s).");
        long replacing = assignments.stream().filter(LeftoverAssignment::replacesExisting).count();
        if (replacing > 0) {
            notes.add("Replaces " + replacing + " existing meal(s).");
        }
        Set<ExpiringItem> used = assignments.stream().flatMap(a -> a.uses().stream()).collect(Collectors.toSet());
        List<String> uncovered = candidates.expiring().stream().filter(item -> !used.contains(item))
                .map(ExpiringItem::name).toList();
        if (!uncovered.isEmpty()) {
            notes.add("Not covered: " + String.join(", ", uncovered));
        }
        return notes;
    }

    private static String usesText(List<ExpiringItem> uses) {
        return cap("Uses " + uses.stream().map(ExpiringItem::label).collect(Collectors.joining(", ")));
    }

    private static String cap(String text) {
        return text.length() <= MAX_REASON ? text : text.substring(0, MAX_REASON);
    }

    /**
     * Makes the deterministic proposal: {@link #findCandidates} then {@link #assign} in candidate order.
     *
     * @param plan    the current plan
     * @param request the request
     * @param today   the current date
     * @return the proposal
     * @throws IllegalStateException with the blocked message when there is nothing to do
     * @throws NullPointerException  if an argument is null
     */
    public LeftoverProposal propose(MealPlan plan, LeftoverRequest request, LocalDate today) {
        LeftoverCandidates candidates = findCandidates(plan, request, today);
        return assign(candidates, plan, List.of(), Map.of(), request);
    }

    /**
     * Applies a proposal as one undoable {@link MacroCommand} of {@link ReplaceMealCommand}s. Every recipe is
     * checked for safety again first (the profile may have changed), each meal is scaled to the household
     * servings like an AI plan edit, and nothing is executed if any check fails. The caller must not apply
     * the same proposal again.
     *
     * @param proposal    the proposal
     * @param currentPlan the plan in use now; must be the proposal's plan (identity)
     * @param history     where the command is run, so one {@code undo} reverts every meal
     * @return one line per meal, e.g. "Day 1 BREAKFAST: Red Lentil Soup (2 servings)"
     * @throws IllegalStateException if the plan was replaced, a recipe is now unsafe, a slot that was
     *                               empty is now filled, or the proposal is empty
     * @throws NullPointerException  if an argument is null
     */
    public List<String> apply(LeftoverProposal proposal, MealPlan currentPlan, CommandHistory history) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(currentPlan, "currentPlan");
        Objects.requireNonNull(history, "history");
        if (proposal.plan() != currentPlan) {
            throw new IllegalStateException("The plan changed since this proposal was made. Run 'plan leftovers' again.");
        }
        if (proposal.assignments().isEmpty()) {
            throw new IllegalStateException("The proposal has no meals.");
        }
        List<String> unsafe = new ArrayList<>();
        for (LeftoverAssignment assignment : proposal.assignments()) {
            unsafe.addAll(safetyProblems(assignment.recipe()));
        }
        if (!unsafe.isEmpty()) {
            throw new IllegalStateException("Refused: " + String.join("; ", unsafe));
        }
        for (LeftoverAssignment assignment : proposal.assignments()) {
            if (!assignment.replacesExisting() && currentPlan.getMeal(assignment.day(), assignment.slot()).isPresent()) {
                throw new IllegalStateException("Day " + assignment.day() + " " + assignment.slot()
                        + " is no longer empty; run 'plan leftovers' again.");
            }
        }

        List<Command> commands = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (LeftoverAssignment assignment : proposal.assignments()) {
            Recipe scaled = RecipeScaler.scale(assignment.recipe(), profile.getHouseholdServings());
            Meal meal = PlanMeals.forRecipe(assignment.slot(), scaled);
            commands.add(new ReplaceMealCommand(currentPlan, assignment.day(), assignment.slot(), meal));
            lines.add("Day " + assignment.day() + " " + assignment.slot() + ": " + meal.getName() + " ("
                    + scaled.getServings() + (scaled.getServings() == 1 ? " serving)" : " servings)"));
        }
        history.pushAndExecute(new MacroCommand("Add " + commands.size() + " leftover meal(s) to the plan", commands));
        return lines;
    }
}
