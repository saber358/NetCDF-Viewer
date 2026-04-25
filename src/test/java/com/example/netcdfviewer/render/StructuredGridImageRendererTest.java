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

    @Test
    void parallelRenderMatchesSequentialRenderForRectilinearGrid() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                4,
                3
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(80.0, 0.0, 160.0);
        double[] values = new double[]{
            1.0, 2.0, 3.0, 4.0,
            5.0, 6.0, 7.0, 8.0,
            9.0, 10.0, 11.0, 12.0
        };

        BufferedImage sequentialImage = renderer.renderSequential(
            240,
            160,
            domain,
            values,
            ColorMaps.viridis(),
            new RangeStats(1.0, 12.0, 12),
            snapshot,
            false,
            null
        );
        BufferedImage parallelImage = renderer.render(
            240,
            160,
            domain,
            values,
            ColorMaps.viridis(),
            new RangeStats(1.0, 12.0, 12),
            snapshot,
            false,
            null
        );

        assertEquals(sequentialImage.getWidth(), parallelImage.getWidth());
        assertEquals(sequentialImage.getHeight(), parallelImage.getHeight());
        for (int y = 0; y < sequentialImage.getHeight(); y++) {
            for (int x = 0; x < sequentialImage.getWidth(); x++) {
                assertEquals(sequentialImage.getRGB(x, y), parallelImage.getRGB(x, y));
            }
        }
    }

    @Test
    void renderCanUseTransparentBackgroundForBasemapComposition() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                null,
                null,
                2,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(50.0, 20.0, 80.0);

        BufferedImage image = renderer.render(
            160,
            120,
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0},
            ColorMaps.viridis(),
            new RangeStats(1.0, 4.0, 4),
            snapshot,
            false,
            null,
            null,
            0x00000000
        );

        assertEquals(0, (image.getRGB(150, 110) >>> 24) & 0xFF);
    }
}
