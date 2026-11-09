package com.mealmind.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mealmind.model.IncompatibleUnitException;
import com.mealmind.model.Ingredient;
import com.mealmind.model.Names;
import com.mealmind.model.Recipe;
import com.mealmind.pantry.Pantry;
import com.mealmind.substitution.SubstitutionOption;
import com.mealmind.substitution.SubstitutionProposal;
import com.mealmind.validation.ConstraintValidator;
import com.mealmind.validation.Violation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the model's substitution answer (F07): structure first (1 to 3 objects with a name, ratio, optional
 * note and non-negative nutrition and cost), then plausibility of the calories (only when every entry is
 * structurally valid), then safety. Unsafe options are dropped; the answer is accepted while at least one
 * safe option remains, and is {@link Check.Unsafe} when none does. Names and notes are sanitized and reduced
 * to ASCII. The pantry is only read.
 */
public final class SubstitutionChecker implements AnswerChecker<SubstitutionBatch> {

    private static final String[] AMOUNTS = {"calories", "proteinG", "carbsG", "fatG", "cost"};
    private static final double EPSILON = 1e-9;

    private final Ingredient target;
    private final Pantry pantry;
    private final ConstraintValidator validator;

    /** One structurally valid entry. */
    private record Entry(String name, double ratio, String note, double[] amounts) {
    }

    /**
     * Creates the checker.
     *
     * @param recipe          the recipe
     * @param ingredientIndex the position of the ingredient to replace
     * @param pantry          the pantry (for the in-pantry flag; read only)
     * @param validator       the safety check
     * @throws NullPointerException      if an argument is null
     * @throws IndexOutOfBoundsException if the index is not in the recipe
     */
    public SubstitutionChecker(Recipe recipe, int ingredientIndex, Pantry pantry, ConstraintValidator validator) {
        this.target = Objects.requireNonNull(recipe, "recipe").getIngredients().get(ingredientIndex);
        this.pantry = Objects.requireNonNull(pantry, "pantry");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public Check<SubstitutionBatch> check(JsonNode answer) {
        if (answer == null || !answer.isArray()) {
            return new Check.Repair<>("\"substitutions\" must be an array of 1 to 3 option objects.");
        }
        if (answer.isEmpty() || answer.size() > SubstitutionProposal.MAX_OPTIONS) {
            return new Check.Repair<>("\"substitutions\" must contain 1 to 3 options; it had " + answer.size() + ".");
        }
        List<String> problems = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < answer.size(); i++) {
            Entry entry = parse(answer.get(i), i + 1, seen, problems);
            if (entry != null) {
                entries.add(entry);
            }
        }
        if (!problems.isEmpty()) {
            return new Check.Repair<>(String.join(" ", problems));
        }
        for (int i = 0; i < entries.size(); i++) {
            double[] a = entries.get(i).amounts();
            double energy = 4 * a[1] + 4 * a[2] + 9 * a[3];
            if ((a[0] > 0 || energy > 0) && Math.abs(a[0] - energy) > Math.max(30, 0.35 * Math.max(a[0], energy))) {
                problems.add(String.format(Locale.ROOT, "Option %d: %.0f kcal does not match its macros (about %.0f kcal).",
                        i + 1, a[0], energy));
            }
        }
        if (!problems.isEmpty()) {
            return new Check.Repair<>(String.join(" ", problems));
        }
        return safetyFiltered(entries);
    }

    /** Parses one entry; adds problems naming the 1-based index and returns null when it is invalid. */
    private Entry parse(JsonNode node, int index, Set<String> seen, List<String> problems) {
        String prefix = "Option " + index + ": ";
        if (node == null || !node.isObject()) {
            problems.add(prefix + "must be an object.");
            return null;
        }
        int before = problems.size();
        JsonNode nameNode = node.get("name");
        String name = nameNode != null && nameNode.isTextual() ? ReplyText.clean(PreferenceNotes.sanitize(nameNode.asText())) : "";
        if (name.isEmpty() || name.length() > SubstitutionOption.MAX_NAME) {
            problems.add(prefix + "\"name\" must be a string of 1 to " + SubstitutionOption.MAX_NAME + " characters.");
        } else if (Names.normalize(name).equals(Names.normalize(target.getName()))) {
            problems.add(prefix + name + " is the ingredient being replaced; suggest a different one.");
        } else if (!seen.add(Names.normalize(name))) {
            problems.add(prefix + name + " is listed more than once.");
        }
        JsonNode ratioNode = node.get("ratio");
        double ratio = ratioNode != null && ratioNode.isNumber() ? ratioNode.asDouble() : Double.NaN;
        if (!(ratio >= SubstitutionOption.MIN_RATIO && ratio <= SubstitutionOption.MAX_RATIO)) {
            problems.add(prefix + "\"ratio\" must be a number from 0.25 to 4.");
        }
        JsonNode noteNode = node.get("note");
        String note = "";
        if (noteNode != null && !noteNode.isNull()) {
            if (!noteNode.isTextual()) {
                problems.add(prefix + "\"note\" must be a string.");
            } else {
                note = ReplyText.clean(noteNode.asText());
                note = note.length() <= SubstitutionOption.MAX_NOTE ? note : note.substring(0, SubstitutionOption.MAX_NOTE).trim();
            }
        }
        double[] amounts = new double[AMOUNTS.length];
        for (int k = 0; k < AMOUNTS.length; k++) {
            JsonNode value = node.get(AMOUNTS[k]);
            amounts[k] = value != null && value.isNumber() ? value.asDouble() : Double.NaN;
            if (!Double.isFinite(amounts[k]) || amounts[k] < 0) {
                problems.add(prefix + "\"" + AMOUNTS[k] + "\" must be a number >= 0.");
            }
        }
        return problems.size() == before ? new Entry(name, ratio, note, amounts) : null;
    }

    /** Builds the options, drops unsafe ones and decides the verdict. */
    private Check<SubstitutionBatch> safetyFiltered(List<Entry> entries) {
        List<SubstitutionOption> safe = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        int dropped = 0;
        for (Entry entry : entries) {
            double[] a = entry.amounts();
            double quantity = target.getQuantity() * entry.ratio();
            Ingredient replacement = new Ingredient(entry.name(), quantity, target.getUnit(), a[0], a[1], a[2], a[3], a[4]);
            List<Violation> unsafe = validator.validateAgainstBlacklists(replacement).getSafetyViolations();
            if (!unsafe.isEmpty()) {
                dropped++;
                unsafe.forEach(v -> violations.add(v.message()));
                continue;
            }
            safe.add(new SubstitutionOption(entry.name(), entry.ratio(), entry.note(), a[0], a[1], a[2], a[3], a[4],
                    inPantry(entry.name(), quantity)));
        }
        if (safe.isEmpty()) {
            return new Check.Unsafe<>("None of your suggestions are safe for the user's profile: "
                    + String.join("; ", violations) + ". Suggest different substitutes.", new SubstitutionBatch(List.of(), dropped));
        }
        return new Check.Accepted<>(new SubstitutionBatch(safe, dropped));
    }

    private boolean inPantry(String name, double quantity) {
        try {
            return pantry.availableQuantity(name, target.getUnit()) >= quantity - EPSILON;
        } catch (IncompatibleUnitException e) {
            return false;
        }
    }
}
