package com.mealmind.persistence;

import static com.mealmind.persistence.JsonFiles.required;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.mealmind.model.Unit;
import com.mealmind.pantry.PantryItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes the pantry as JSON.
 *
 * <p>Schema: {@code {"items": [{"name": "Rice", "quantity": 500, "unit": "GRAM", "expiry": "2026-11-30"}]}},
 * with {@code expiry} (an ISO-8601 date) omitted for items that do not expire. Every item goes through
 * the {@link PantryItem} constructor, so invalid saved values are reported rather than trusted, and a
 * file containing two items with the same normalized name is rejected rather than silently merged.
 */
public final class PantryRepository {

    private record PantryFileDto(List<PantryItemDto> items) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PantryItemDto(String name, Double quantity, String unit, String expiry) {
    }

    private PantryRepository() {
    }

    /**
     * Loads pantry items from a file.
     *
     * @param file the JSON file
     * @return the items, or an empty list if the file does not exist
     * @throws RepositoryException if the file exists but is unreadable, malformed or invalid; the file
     *                             itself is never modified
     */
    public static List<PantryItem> load(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        PantryFileDto dto;
        try {
            dto = JsonFiles.MAPPER.readValue(file.toFile(), PantryFileDto.class);
        } catch (JsonProcessingException e) {
            throw new RepositoryException(file + ": invalid pantry JSON: " + JsonFiles.describe(e), e);
        } catch (IOException e) {
            throw new RepositoryException("Could not read " + file + ": " + e.getMessage(), e);
        }
        if (dto == null || dto.items() == null) {
            throw new RepositoryException(file + ": missing field 'items'");
        }

        List<PantryItem> items = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < dto.items().size(); i++) {
            PantryItemDto itemDto = dto.items().get(i);
            String label = (itemDto != null && itemDto.name() != null) ? "item '" + itemDto.name() + "'" : "item #" + (i + 1);
            PantryItem item;
            try {
                item = toItem(required(itemDto, "item"));
            } catch (IllegalArgumentException | DateTimeException e) {
                throw new RepositoryException(file + ", " + label + ": " + e.getMessage(), e);
            }
            if (!names.add(item.normalizedName())) {
                throw new RepositoryException(file + ", " + label + ": duplicate pantry item");
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    private static PantryItem toItem(PantryItemDto dto) {
        LocalDate expiry = dto.expiry() == null ? null : LocalDate.parse(dto.expiry());
        return new PantryItem(required(dto.name(), "name"), required(dto.quantity(), "quantity"),
                Unit.parse(required(dto.unit(), "unit")), expiry);
    }

    /**
     * Writes pantry items to a file, atomically, creating parent directories.
     *
     * @param file  the destination
     * @param items the items to write
     * @throws RepositoryException if the file cannot be written
     */
    public static void save(Path file, Collection<PantryItem> items) {
        List<PantryItemDto> dtos = items.stream()
                .map(item -> new PantryItemDto(item.getName(), item.getQuantity(), item.getUnit().name(),
                        item.getExpiry().map(LocalDate::toString).orElse(null)))
                .toList();
        JsonFiles.writeAtomically(file, new PantryFileDto(dtos));
    }
}
