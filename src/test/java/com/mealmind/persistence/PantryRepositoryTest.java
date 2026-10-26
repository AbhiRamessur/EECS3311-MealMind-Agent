package com.mealmind.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PantryRepositoryTest {

    @TempDir
    Path dir;

    @Test
    void missingFileGivesEmptyList() {
        assertEquals(List.of(), PantryRepository.load(dir.resolve("pantry.json")));
    }

    @Test
    void roundTripPreservesItemsWithAndWithoutExpiry() throws IOException {
        List<PantryItem> items = List.of(
                new PantryItem("Rice", 500, Unit.GRAM, LocalDate.of(2026, 11, 30)),
                new PantryItem("Olive Oil", 0.75, Unit.LITER));
        Path file = dir.resolve("pantry.json");

        PantryRepository.save(file, items);

        assertEquals(items, PantryRepository.load(file));
        String json = Files.readString(file);
        assertTrue(json.contains("\"expiry\" : \"2026-11-30\""), json);
        assertEquals(1, json.split("expiry", -1).length - 1, "expiry omitted when absent");
    }

    @Test
    void corruptFileThrowsAndIsLeftUntouched() throws IOException {
        Path file = dir.resolve("pantry.json");
        String corrupt = "{\"items\": [ {\"name\": \"Rice\", ";
        Files.writeString(file, corrupt);

        RepositoryException e = assertThrows(RepositoryException.class, () -> PantryRepository.load(file));

        assertTrue(e.getMessage().contains(file.toString()), e.getMessage());
        assertEquals(corrupt, Files.readString(file));
    }

    @Test
    void invalidSavedQuantityIsReported() throws IOException {
        Path file = dir.resolve("pantry.json");
        Files.writeString(file, "{\"items\": [{\"name\": \"Rice\", \"quantity\": 0, \"unit\": \"GRAM\"}]}");
        RepositoryException zero = assertThrows(RepositoryException.class, () -> PantryRepository.load(file));
        assertTrue(zero.getMessage().contains("Rice"), zero.getMessage());

        Files.writeString(file, "{\"items\": [{\"name\": \"Rice\", \"quantity\": -5, \"unit\": \"GRAM\"}]}");
        assertThrows(RepositoryException.class, () -> PantryRepository.load(file));
    }

    @Test
    void invalidDateAndDuplicateNamesAreReported() throws IOException {
        Path file = dir.resolve("pantry.json");
        Files.writeString(file, "{\"items\": [{\"name\": \"Milk\", \"quantity\": 1, \"unit\": \"l\", \"expiry\": \"soon\"}]}");
        assertThrows(RepositoryException.class, () -> PantryRepository.load(file));

        Files.writeString(file, "{\"items\": [{\"name\": \"Milk\", \"quantity\": 1, \"unit\": \"l\"},"
                + " {\"name\": \" milk \", \"quantity\": 2, \"unit\": \"l\"}]}");
        RepositoryException e = assertThrows(RepositoryException.class, () -> PantryRepository.load(file));
        assertTrue(e.getMessage().contains("duplicate"), e.getMessage());
    }

    @Test
    void saveLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("pantry.json");

        PantryRepository.save(file, List.of(new PantryItem("Rice", 500, Unit.GRAM)));
        PantryRepository.save(file, List.of(new PantryItem("Rice", 600, Unit.GRAM)));

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(file.getFileName()), files.map(Path::getFileName).toList());
        }
        assertFalse(Files.readString(file).contains("500"));
    }
}
