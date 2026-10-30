package com.mealmind.persistence;

import static com.mealmind.persistence.JsonFiles.required;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import com.mealmind.profile.UserProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

/**
 * Reads and writes the user profile as JSON.
 *
 * <p>Schema: {@code {"diet":"VEGAN","allergens":["peanut"],"calorieTarget":2200,"proteinTargetG":120.0,
 * "carbsTargetG":250.0,"fatTargetG":70.0,"weeklyBudget":150.0,"householdServings":2}}; targets and
 * the budget are omitted when not set. Loaded values are validated by {@link UserProfile#applyData},
 * so invalid saved values are reported, never trusted.
 */
public final class ProfileRepository {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ProfileDto(String diet, List<String> allergens, Integer calorieTarget, Double proteinTargetG,
                              Double carbsTargetG, Double fatTargetG, Double weeklyBudget, Integer householdServings) {
    }

    private ProfileRepository() {
    }

    /**
     * Loads a profile from a file.
     *
     * @param file the JSON file
     * @return the saved profile, or {@link ProfileData#defaults()} if the file does not exist
     * @throws RepositoryException if the file exists but is unreadable, malformed or invalid; the file
     *                             itself is never modified
     */
    public static ProfileData load(Path file) {
        if (!Files.exists(file)) {
            return ProfileData.defaults();
        }
        ProfileDto dto;
        try {
            dto = JsonFiles.MAPPER.readValue(file.toFile(), ProfileDto.class);
        } catch (JsonProcessingException e) {
            throw new RepositoryException(file + ": invalid profile JSON: " + JsonFiles.describe(e), e);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + file + ": " + e.getMessage(), e);
        }
        try {
            required(dto, "profile");
            ProfileData data = new ProfileData(DietType.parse(required(dto.diet(), "diet")),
                    dto.allergens() == null ? null : new HashSet<>(dto.allergens()),
                    dto.calorieTarget(), dto.proteinTargetG(), dto.carbsTargetG(), dto.fatTargetG(),
                    dto.weeklyBudget(), required(dto.householdServings(), "householdServings"));
            new UserProfile().applyData(data); // validates every value
            return data;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RepositoryException(file + ": invalid profile: " + e.getMessage(), e);
        }
    }

    /**
     * Writes a profile to a file, atomically, creating parent directories.
     *
     * @param file the destination
     * @param data the profile to write
     * @throws RepositoryException if the file cannot be written
     */
    public static void save(Path file, ProfileData data) {
        JsonFiles.writeAtomically(file, new ProfileDto(data.diet().name(), List.copyOf(data.allergens()),
                data.calorieTarget(), data.proteinTargetG(), data.carbsTargetG(), data.fatTargetG(),
                data.weeklyBudget(), data.householdServings()));
    }
}
