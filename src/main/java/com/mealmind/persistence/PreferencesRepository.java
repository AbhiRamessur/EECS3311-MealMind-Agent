package com.mealmind.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mealmind.preferences.PreferenceData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads and writes recipe ratings and favourites as JSON.
 *
 * <p>Schema: {@code {"ratings": {"R101": 5}, "favorites": ["R101"]}}. A missing file means no
 * preferences. A corrupt or invalid file (unknown field, rating outside 1 to 5, non-integer rating,
 * blank id) is reported and never modified.
 */
public final class PreferencesRepository {

    private record PreferencesDto(Map<String, Integer> ratings, List<String> favorites) {
    }

    private PreferencesRepository() {
    }

    /**
     * Loads preferences from a file.
     *
     * @param file the JSON file
     * @return the saved preferences, or {@link PreferenceData#EMPTY} if the file does not exist
     * @throws RepositoryException if the file exists but is unreadable, malformed or invalid
     */
    public static PreferenceData load(Path file) {
        if (!Files.exists(file)) {
            return PreferenceData.EMPTY;
        }
        PreferencesDto dto;
        try {
            dto = JsonFiles.MAPPER.readValue(file.toFile(), PreferencesDto.class);
        } catch (JsonProcessingException e) {
            throw new RepositoryException(file + ": invalid ratings JSON: " + JsonFiles.describe(e), e);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + file + ": " + e.getMessage(), e);
        }
        Map<String, Integer> ratings = dto == null || dto.ratings() == null ? Map.of() : dto.ratings();
        List<String> favorites = dto == null || dto.favorites() == null ? List.of() : dto.favorites();
        for (Map.Entry<String, Integer> entry : ratings.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new RepositoryException(file + ": a rating has a blank recipe id");
            }
            if (entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > 5) {
                throw new RepositoryException(file + ": rating for " + entry.getKey() + " must be between 1 and 5");
            }
        }
        for (String id : favorites) {
            if (id == null || id.isBlank()) {
                throw new RepositoryException(file + ": a favorite has a blank recipe id");
            }
        }
        return new PreferenceData(ratings, Set.copyOf(favorites));
    }

    /**
     * Writes preferences to a file, atomically, creating parent directories.
     *
     * @param file the destination
     * @param data the preferences to write
     * @throws RepositoryException if the file cannot be written
     */
    public static void save(Path file, PreferenceData data) {
        JsonFiles.writeAtomically(file, new PreferencesDto(new TreeMap<>(data.ratings()),
                data.favorites().stream().sorted().toList()));
    }
}
