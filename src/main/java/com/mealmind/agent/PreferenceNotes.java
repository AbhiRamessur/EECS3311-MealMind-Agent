package com.mealmind.agent;

import com.mealmind.model.Recipe;
import com.mealmind.model.RecipeBook;
import com.mealmind.preferences.RecipePreferences;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Turns the user's ratings and favourites into short, deterministic taste hints for the agent's
 * prompt (F12: "preference history … influences future AI planning choices"). Recipe names are
 * untrusted data, so they are sanitized: quotes, braces, backslashes and control characters become
 * spaces, whitespace is collapsed, and long names are cut to 60 characters.
 */
public final class PreferenceNotes {

    private static final int MAX_NAMES = 5;
    private static final int MAX_NAME_LENGTH = 60;

    private PreferenceNotes() {
    }

    /**
     * Builds up to three lines: highly rated recipes (4-5 stars), poorly rated ones (1-2 stars) and
     * favourites, each listing at most five names and present only when non-empty. Ids no longer in
     * the book are skipped.
     *
     * @param prefs the ratings and favourites
     * @param book  where names are looked up
     * @return the lines, possibly empty
     * @throws NullPointerException if an argument is null
     */
    public static List<String> build(RecipePreferences prefs, RecipeBook book) {
        Objects.requireNonNull(prefs, "prefs");
        Objects.requireNonNull(book, "book");
        Map<String, Integer> ratings = prefs.ratings();
        List<String> lines = new ArrayList<>();

        Stream<String> high = ratings.entrySet().stream().filter(e -> e.getValue() >= 4)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey);
        addLine(lines, "Recipes the user rated highly (4-5 stars): ", names(high, book));

        Stream<String> low = ratings.entrySet().stream().filter(e -> e.getValue() <= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey);
        addLine(lines, "Recipes the user rated poorly (1-2 stars; avoid similar dishes): ", names(low, book));

        addLine(lines, "Recipes the user marked as favorites: ", names(prefs.favorites().stream(), book));
        return List.copyOf(lines);
    }

    private static void addLine(List<String> lines, String prefix, List<String> names) {
        if (!names.isEmpty()) {
            lines.add(prefix + String.join("; ", names) + ".");
        }
    }

    /** Sanitized names of the first five ids that are still in the book. */
    private static List<String> names(Stream<String> ids, RecipeBook book) {
        return ids.map(book::get)
                .flatMap(Optional::stream)
                .map(Recipe::getName)
                .map(PreferenceNotes::sanitize)
                .filter(name -> !name.isEmpty())
                .limit(MAX_NAMES)
                .toList();
    }

    /** Removes characters that could look like markup or instructions and caps the length. */
    static String sanitize(String name) {
        String clean = name.replaceAll("[\\p{Cntrl}\"{}\\\\]", " ").replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_NAME_LENGTH ? clean : clean.substring(0, MAX_NAME_LENGTH - 1) + "…";
    }
}
