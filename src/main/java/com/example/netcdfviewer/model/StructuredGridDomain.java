package com.example.netcdfviewer.model;

import java.util.Arrays;
import java.util.Optional;

public record StructuredGridDomain(
    StructuredGridData grid,
    CoordinateBinding selectedCoordinateBinding
) implements SpatialDomain {
    public StructuredGridDomain {
        if (grid == null || selectedCoordinateBinding == null) {
            throw new IllegalArgumentException("grid and selectedBinding must not be null");
        }
    }

    @Override
    public Kind kind() {
        return Kind.STRUCTURED_GRID;
    }

    @Override
    public double minX() {
        return grid.rectilinear()
            ? Arrays.stream(grid.xAxis()).min().orElse(0.0)
            : Arrays.stream(grid.xCoordinates()).flatMapToDouble(Arrays::stream).min().orElse(0.0);
    }

    @Override
    public double maxX() {
        return grid.rectilinear()
            ? Arrays.stream(grid.xAxis()).max().orElse(0.0)
            : Arrays.stream(grid.xCoordinates()).flatMapToDouble(Arrays::stream).max().orElse(0.0);
    }

    @Override
    public double minY() {
        return grid.rectilinear()
            ? Arrays.stream(grid.yAxis()).min().orElse(0.0)
            : Arrays.stream(grid.yCoordinates()).flatMapToDouble(Arrays::stream).min().orElse(0.0);
    }

    @Override
    public double maxY() {
        return grid.rectilinear()
            ? Arrays.stream(grid.yAxis()).max().orElse(0.0)
            : Arrays.stream(grid.yCoordinates()).flatMapToDouble(Arrays::stream).max().orElse(0.0);
    }

    @Override
    public boolean supportsManualCoordinateSelection() {
        return true;
    }

    @Override
    public Optional<CoordinateBinding> selectedBinding() {
        return Optional.of(selectedCoordinateBinding);
    }
}
