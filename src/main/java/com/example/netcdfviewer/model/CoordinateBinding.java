package com.example.netcdfviewer.model;

import java.util.List;

public record CoordinateBinding(
    String id,
    String xName,
    String yName,
    List<String> horizontalDimensions,
    boolean curvilinear
) {
    public CoordinateBinding {
        horizontalDimensions = List.copyOf(horizontalDimensions);
    }
}
