package com.example.netcdfviewer.io;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class LocalSampleDatasetSupport {
    private LocalSampleDatasetSupport() {
    }

    static List<Path> findLocalNcFiles(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".nc"))
                .sorted(Comparator.comparingLong(LocalSampleDatasetSupport::safeSize))
                .toList();
        }
    }

    static List<Path> requireLocalNcFilesOrSkip(Path directory) throws IOException {
        List<Path> files = findLocalNcFiles(directory);
        Assumptions.assumeFalse(
            files.isEmpty(),
            "No local .nc sample files found under " + directory.toAbsolutePath() + "; skipping local sample validation."
        );
        return files;
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }
}
