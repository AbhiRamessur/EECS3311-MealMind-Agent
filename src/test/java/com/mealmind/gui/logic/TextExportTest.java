package com.mealmind.gui.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextExportTest {

    @TempDir
    Path dir;

    private long filesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void writesWithATrailingNewlineAndCreatesParents() throws IOException {
        Path file = dir.resolve("a").resolve("b").resolve("list.txt");

        TextExport.write(file, "Milk\nEggs");

        assertEquals("Milk\nEggs\n", Files.readString(file));
        TextExport.write(file, "Done\n");
        assertEquals("Done\n", Files.readString(file));
    }

    @Test
    void replacesAnExistingFileAndLeavesNoTempFile() throws IOException {
        Path file = dir.resolve("shopping-list.txt");
        Files.writeString(file, "old content that is longer than the new one");

        TextExport.write(file, "new");

        assertEquals("new\n", Files.readString(file));
        assertEquals(1, filesIn(dir));
    }

    @Test
    void failureGivesTheSpecifiedMessage() throws IOException {
        Path folder = Files.createDirectory(dir.resolve("taken.txt"));
        Files.writeString(folder.resolve("inside"), "keeps the directory non-empty");

        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> TextExport.write(folder, "text"));

        assertEquals("could not write taken.txt", e.getMessage());
        assertEquals(1, filesIn(dir), "no temporary file is left next to the target");
        assertEquals(1, filesIn(folder));
    }

    @Test
    void defaultFileName() {
        assertEquals("shopping-list.txt", TextExport.defaultFileName());
    }
}
