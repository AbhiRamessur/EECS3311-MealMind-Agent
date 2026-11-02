package com.mealmind.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.preferences.PreferenceData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreferencesRepositoryTest {

    @TempDir
    Path dir;

    private void loadFails(String json) throws IOException {
        Path file = dir.resolve("ratings.json");
        Files.writeString(file, json);
        RepositoryException e = assertThrows(RepositoryException.class, () -> PreferencesRepository.load(file));
        assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
        assertEquals(json, Files.readString(file));
    }

    @Test
    void missingFileGivesEmptyData() {
        assertEquals(PreferenceData.EMPTY, PreferencesRepository.load(dir.resolve("ratings.json")));
    }

    @Test
    void roundTrip() throws IOException {
        Path file = dir.resolve("ratings.json");
        PreferenceData data = new PreferenceData(Map.of("R101", 5, "R103", 2), Set.of("R101", "AI001"));

        PreferencesRepository.save(file, data);

        assertEquals(data, PreferencesRepository.load(file));
        assertTrue(Files.readString(file).contains("\"favorites\" : [ \"AI001\", \"R101\" ]"));
    }

    @Test
    void invalidFilesAreRejectedAndLeftUntouched() throws IOException {
        loadFails("{\"ratings\": {\"R101\": 5},");
        loadFails("{\"ratings\": {}, \"favorites\": [], \"notes\": []}");
        loadFails("{\"ratings\": {\"R101\": 0}}");
        loadFails("{\"ratings\": {\"R101\": 6}}");
        loadFails("{\"ratings\": {\"R101\": 4.5}}");
        loadFails("{\"ratings\": {\" \": 4}}");
        loadFails("{\"favorites\": [\" \"]}");
    }

    @Test
    void saveLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("ratings.json");

        PreferencesRepository.save(file, PreferenceData.EMPTY);
        PreferencesRepository.save(file, new PreferenceData(Map.of("R1", 3), Set.of()));

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(file.getFileName()), files.map(Path::getFileName).toList());
        }
    }
}
