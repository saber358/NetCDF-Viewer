package com.example.netcdfviewer.model;

import java.util.List;

public record HorizontalBasis(
    String id,
    String bindingId,
    List<String> horizontalDimensions,
    boolean cellCentered,
    boolean staggered
) {
    public HorizontalBasis {
        horizontalDimensions = List.copyOf(horizontalDimensions);
    }
}
