package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindBarbOverlayRendererTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 64.0, 0.0},
        new double[]{0.0, 0.0, 64.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 64.0);
    private final WindBarbOverlayRenderer renderer = new WindBarbOverlayRenderer();

    @Test
    void sampleBarbsBuildsTriangleWindGlyphs() {
        List<WindBarbOverlayRenderer.WindBarbGlyph> glyphs = renderer.sampleBarbs(
            TRIANGLE_MESH,
            new double[]{5.0, 5.0, 5.0},
            new double[]{2.0, 2.0, 2.0},
            SNAPSHOT,
            64,
            64,
            false,
            null,
            null,
            0
        );

        assertFalse(glyphs.isEmpty());
        assertTrue(glyphs.stream().allMatch(glyph -> !glyph.featherLines().isEmpty() || !glyph.flagTriangles().isEmpty()));
    }

    @Test
    void sampleStructuredBarbsBuildsStructuredWindGlyphs() {
        StructuredGridDomain uDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                3,
                3
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );
        StructuredGridDomain vDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                3,
                3
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );

        List<WindBarbOverlayRenderer.WindBarbGlyph> glyphs = renderer.sampleStructuredBarbs(
            uDomain,
            vDomain,
            new double[]{5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0},
            new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
            new ViewportState.Snapshot(24.0, 0.0, 48.0),
            64,
            64,
            false,
            false,
            null,
            null,
            0
        );

        assertFalse(glyphs.isEmpty());
        assertTrue(glyphs.stream().allMatch(glyph -> !glyph.featherLines().isEmpty() || !glyph.flagTriangles().isEmpty()));
    }
}
