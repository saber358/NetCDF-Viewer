package com.example.netcdfviewer.model;

import java.util.Optional;

public interface SpatialDomain {
    enum Kind {
        TRIANGLE_MESH,
        STRUCTURED_GRID
    }

    Kind kind();

    double minX();

    double maxX();

    double minY();

    double maxY();

    default boolean supportsManualCoordinateSelection() {
        return false;
    }

    default Optional<CoordinateBinding> selectedBinding() {
        return Optional.empty();
    }
}
