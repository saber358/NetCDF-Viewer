package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.testsupport.SampleDatasetPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class LocalSampleDatasetTest {
    @Test
    void atLeastOneLocalNcFileSupportsVisualization() throws IOException {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        List<Path> files = LocalSampleDatasetSupport.requireLocalNcFilesOrSkip(
            SampleDatasetPaths.findNearestDirectoryContainingNcFiles(Path.of("."))
        );

        List<String> diagnostics = new ArrayList<>();

        for (Path file : files) {
            try {
                ParsedDataset dataset = parser.open(file);
                if (!dataset.hasMesh() || dataset.plottableVariables().isEmpty()) {
                    diagnostics.add(file.getFileName() + " => mesh=" + dataset.hasMesh()
                        + ", plottable=" + dataset.plottableVariables().size()
                        + ", warnings=" + dataset.warnings());
                    continue;
                }

                VariableInfo variable = dataset.plottableVariables().get(0);
                double[] values = parser.readLayer(dataset, variable, 0);
                assertEquals(variable.expectedValueCount(dataset.mesh()), values.length);
                System.out.println("Validated sample file: " + file.getFileName() + ", variable=" + variable.name());
                return;
            } catch (Exception exception) {
                diagnostics.add(file.getFileName() + " => " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            }
        }

        fail("No local .nc file could be loaded for planar visualization:\n" + String.join("\n", diagnostics));
    }

    @Test
    void reportWhetherLocalNcFileExposesLayeredScalarVariable() throws IOException {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        List<Path> files = LocalSampleDatasetSupport.requireLocalNcFilesOrSkip(
            SampleDatasetPaths.findNearestDirectoryContainingNcFiles(Path.of("."))
        );

        List<String> diagnostics = new ArrayList<>();

        for (Path file : files) {
            try {
                ParsedDataset dataset = parser.open(file);
                for (VariableInfo variable : dataset.plottableVariables()) {
                    if (!variable.layered()) {
                        continue;
                    }
                    double[] values = parser.readLayer(dataset, variable, 0);
                    assertEquals(variable.expectedValueCount(dataset.mesh()), values.length);
                    System.out.println("Validated layered sample file: " + file.getFileName() + ", variable=" + variable.name());
                    return;
                }
                diagnostics.add(file.getFileName() + " => no layered scalar variable");
            } catch (Exception exception) {
                diagnostics.add(file.getFileName() + " => " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            }
        }

        System.out.println("No local layered scalar sample found. Diagnostics:\n" + String.join("\n", diagnostics));
    }
}
