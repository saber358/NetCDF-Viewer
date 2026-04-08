package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.VariableInfo;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record ParsedDataset(
    Path sourcePath,
    MeshData mesh,
    List<VariableInfo> variables,
    Map<String, Integer> dimensions,
    Map<String, String> globalAttributes,
    Map<String, double[]> axisCoordinates,
    List<String> warnings,
    String xVariableName,
    String yVariableName,
    String connectivityVariableName
) {
    public ParsedDataset {
        variables = List.copyOf(variables);
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
        globalAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(globalAttributes));
        axisCoordinates = Collections.unmodifiableMap(new LinkedHashMap<>(axisCoordinates));
        warnings = List.copyOf(warnings);
    }

    public boolean hasMesh() {
        return mesh != null;
    }

    public List<VariableInfo> plottableVariables() {
        return variables.stream()
            .filter(VariableInfo::plottable)
            .collect(Collectors.toList());
    }

    public Optional<double[]> axisValues(String name) {
        return Optional.ofNullable(axisCoordinates.get(name));
    }
}
