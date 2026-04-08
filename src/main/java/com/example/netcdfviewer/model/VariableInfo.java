package com.example.netcdfviewer.model;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record VariableInfo(
    String name,
    String dataType,
    List<String> dimensionNames,
    List<Integer> dimensionSizes,
    boolean plottable,
    int nodeAxis,
    boolean elementCentered,
    int layerAxis,
    Double fillValue
) {
    public VariableInfo {
        dimensionNames = List.copyOf(dimensionNames);
        dimensionSizes = List.copyOf(dimensionSizes);
    }

    public boolean layered() {
        return layerAxis >= 0;
    }

    public int layerCount() {
        return layered() ? dimensionSizes.get(layerAxis) : 1;
    }

    public String dimensionSummary() {
        return dimensionNames.stream()
            .map(String::trim)
            .collect(Collectors.joining(", ", "[", "]"));
    }

    public String layerDimensionName() {
        return layered() ? dimensionNames.get(layerAxis) : "";
    }

    public int expectedValueCount(MeshData mesh) {
        return elementCentered ? mesh.triangleCount() : mesh.nodeCount();
    }

    public String presentableType() {
        return dataType.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        String location = elementCentered ? "element" : "node";
        String suffix = plottable ? (layered() ? " (layered " + location + ")" : " (planar " + location + ")") : " (info)";
        return name + " " + dimensionSummary() + suffix;
    }
}
