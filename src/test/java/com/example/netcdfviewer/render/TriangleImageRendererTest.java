package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TriangleImageRendererTest {
    @Test
    void renderCanUseTransparentBackgroundForBasemapComposition() {
        TriangleImageRenderer renderer = new TriangleImageRenderer();
        MeshData mesh = new MeshData(
            new double[]{0.0, 1.0, 0.0},
            new double[]{0.0, 0.0, 1.0},
            new int[][]{{0, 1, 2}}
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(50.0, 20.0, 80.0);

        BufferedImage image = renderer.render(
            160,
            120,
            mesh,
            new double[]{1.0, 2.0, 3.0},
            ColorMaps.viridis(),
            new RangeStats(1.0, 3.0, 3),
            snapshot,
            false,
            null,
            new Color(0, 0, 0, 0)
        );

        assertEquals(0, (image.getRGB(150, 110) >>> 24) & 0xFF);
    }
}
