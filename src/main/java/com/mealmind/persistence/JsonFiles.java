package com.mealmind.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON settings and file writing shared by the repositories.
 */
final class JsonFiles {

    /** Strict mapper: unknown fields and fractional values for whole-number fields are errors. */
    static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonFiles() {
    }

    /**
     * Writes {@code value} as pretty-printed JSON, atomically: the data goes to a temporary file in
     * the same directory, which then replaces {@code file} in one step, so a crash mid-write can never
     * leave a half-written file. Parent directories are created.
     */
    static void writeAtomically(Path file, Object value) {
        Path target = file.toAbsolutePath();
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
            MAPPER.writeValue(temp.toFile(), value);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RepositoryException("Could not write " + file + ": " + e.getMessage(), e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // The temp file is only left behind if the write already failed and was reported.
        }
    }

    /** A short, user-facing description of a JSON parse error, including where it happened. */
    static String describe(JsonProcessingException e) {
        String where = e.getLocation() == null ? ""
                : " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")";
        return e.getOriginalMessage() + where;
    }

    /** Throws an {@link IllegalArgumentException} naming {@code field} if {@code value} is null. */
    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("missing field '" + field + "'");
        }
        return value;
    }
}
