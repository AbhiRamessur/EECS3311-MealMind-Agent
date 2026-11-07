package com.mealmind.gui.logic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Writes exported text (the shopping list) to a file: UTF-8, ending with a newline, replacing an existing
 * file atomically through a temporary file next to it, and never leaving that temporary file behind.
 */
public final class TextExport {

    private TextExport() {
    }

    /**
     * Writes {@code text} to {@code file}.
     *
     * @param file the target; missing parent directories are created
     * @param text the content; a newline is added when it does not end with one
     * @throws UncheckedIOException "could not write &lt;file name&gt;" if writing fails
     * @throws NullPointerException if an argument is null
     */
    public static void write(Path file, String text) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        String content = text.endsWith("\n") ? text : text + "\n";
        Path target = file.toAbsolutePath();
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + target.getFileName(), e);
        } finally {
            deleteQuietly(temp);
        }
    }

    /**
     * Returns the suggested export file name.
     *
     * @return "shopping-list.txt"
     */
    public static String defaultFileName() {
        return "shopping-list.txt";
    }

    private static void deleteQuietly(Path temp) {
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Nothing else can be done; the export itself already succeeded or failed.
            }
        }
    }
}
