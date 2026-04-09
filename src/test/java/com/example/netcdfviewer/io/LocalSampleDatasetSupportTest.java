package com.example.netcdfviewer.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSampleDatasetSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void findsOnlyNcFilesSortedBySize() throws IOException {
        writeFile("large.nc", 12);
        writeFile("notes.txt", 4);
        writeFile("small.NC", 3);
        writeFile("medium.nc", 7);

        List<String> names = LocalSampleDatasetSupport.findLocalNcFiles(tempDir).stream()
            .map(path -> path.getFileName().toString())
            .toList();

        assertEquals(List.of("small.NC", "medium.nc", "large.nc"), names);
    }

    @Test
    void skipsWhenNoLocalNcFilesAreAvailable() {
        TestAbortedException exception = assertThrows(
            TestAbortedException.class,
            () -> LocalSampleDatasetSupport.requireLocalNcFilesOrSkip(tempDir)
        );

        assertTrue(exception.getMessage().contains("No local .nc sample files found"));
    }

    private void writeFile(String name, int size) throws IOException {
        Files.write(tempDir.resolve(name), new byte[size]);
    }
}
