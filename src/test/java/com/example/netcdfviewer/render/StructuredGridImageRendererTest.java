package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGridImageRendererTest {
    private final StructuredGridImageRenderer renderer = new StructuredGridImageRenderer();

    @Test
    void renderProducesColoredImageForRectilinearGrid() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        BufferedImage image = renderer.render(
            200,
            120,
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
            ColorMaps.viridis(),
            new RangeStats(1.0, 6.0, 6),
            snapshot,
            false,
            null
        );

        assertEquals(200, image.getWidth());
        assertEquals(120, image.getHeight());
        assertTrue(image.getRGB(100, 60) != 0);
    }
}
