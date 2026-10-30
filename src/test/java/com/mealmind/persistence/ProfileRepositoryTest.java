package com.mealmind.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.profile.DietType;
import com.mealmind.profile.ProfileData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileRepositoryTest {

    @TempDir
    Path dir;

    private RepositoryException loadFails(String json) throws IOException {
        Path file = dir.resolve("profile.json");
        Files.writeString(file, json);
        RepositoryException e = assertThrows(RepositoryException.class, () -> ProfileRepository.load(file));
        assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
        assertEquals(json, Files.readString(file));
        return e;
    }

    @Test
    void missingFileGivesDefaults() {
        assertEquals(ProfileData.defaults(), ProfileRepository.load(dir.resolve("profile.json")));
    }

    @Test
    void roundTripPreservesEveryField() throws IOException {
        Path file = dir.resolve("profile.json");
        ProfileData full = new ProfileData(DietType.VEGAN, Set.of("peanut", "sesame"), 2200, 120.0, 250.0, 70.0,
                150.0, 2);
        ProfileData partial = new ProfileData(DietType.PESCATARIAN, Set.of(), null, 90.0, null, null, null, 4);

        ProfileRepository.save(file, full);
        assertEquals(full, ProfileRepository.load(file));

        ProfileRepository.save(file, partial);
        assertEquals(partial, ProfileRepository.load(file));
        String json = Files.readString(file);
        assertFalse(json.contains("calorieTarget"), "null targets are omitted");
        assertTrue(json.contains("\"diet\" : \"PESCATARIAN\""), json);
    }

    @Test
    void corruptJsonIsRejectedAndLeftUntouched() throws IOException {
        loadFails("{\"diet\": \"VEGAN\", ");
    }

    @Test
    void unknownFieldIsRejected() throws IOException {
        loadFails("{\"diet\": \"VEGAN\", \"householdServings\": 1, \"favouriteColour\": \"blue\"}");
    }

    @Test
    void unknownDietIsRejected() throws IOException {
        RepositoryException e = loadFails("{\"diet\": \"KETO\", \"householdServings\": 1}");
        assertTrue(e.getMessage().contains("keto") || e.getMessage().contains("KETO"), e.getMessage());
    }

    @Test
    void outOfRangeValueIsRejected() throws IOException {
        RepositoryException e = loadFails("{\"diet\": \"VEGAN\", \"calorieTarget\": 100, \"householdServings\": 1}");
        assertTrue(e.getMessage().contains("Calorie target"), e.getMessage());
    }

    @Test
    void saveLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("profile.json");

        ProfileRepository.save(file, ProfileData.defaults());
        ProfileRepository.save(file, ProfileData.defaults());

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(file.getFileName()), files.map(Path::getFileName).toList());
        }
    }
}
