package com.example.netcdfviewer.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SampleDatasetPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void resolveFindsSampleFileInAncestorDirectory() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path worktreeDirectory = workspaceRoot.resolve(".worktrees").resolve("wave-arrow-overlay");
        Files.createDirectories(worktreeDirectory);
        Path sampleFile = workspaceRoot.resolve("HBHQY.nc");
        Files.write(sampleFile, new byte[]{1, 2, 3});

        Path resolved = SampleDatasetPaths.resolve(worktreeDirectory, "HBHQY.nc");

        assertEquals(sampleFile, resolved);
    }

    @Test
    void resolveThrowsWhenSampleFileCannotBeFound() throws IOException {
        Path workspaceRoot = tempDir.resolve("workspace");
        Path worktreeDirectory = workspaceRoot.resolve(".worktrees").resolve("wave-arrow-overlay");
        Files.createDirectories(worktreeDirectory);

        assertThrows(IllegalArgumentException.class, () -> SampleDatasetPaths.resolve(worktreeDirectory, "missing.nc"));
    }
}
