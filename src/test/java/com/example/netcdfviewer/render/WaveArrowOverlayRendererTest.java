package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveArrowOverlayRendererTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 64.0, 0.0},
        new double[]{0.0, 0.0, 64.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 64.0);
    private final WaveArrowOverlayRenderer renderer = new WaveArrowOverlayRenderer();

    @Test
    void sampleArrowsBuildsBoundedGlyphsForValidWaveField() {
        RangeStats wavelengthRange = RenderMath.computeRange(new double[]{12.0, 18.0, 24.0}, null);

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleArrows(
            TRIANGLE_MESH,
            new double[]{45.0, 45.0, 45.0},
            new double[]{12.0, 18.0, 24.0},
            SNAPSHOT,
            64,
            64,
            false,
            null,
            null,
            0,
            wavelengthRange
        );

        assertFalse(arrows.isEmpty());
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() >= WaveArrowOverlayRenderer.MIN_ARROW_LENGTH));
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() <= WaveArrowOverlayRenderer.MAX_ARROW_LENGTH));
    }

    @Test
    void sampleArrowsSkipsInvalidWaveValues() {
        RangeStats wavelengthRange = RenderMath.computeRange(new double[]{12.0, 18.0, 24.0}, -9999.0);

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleArrows(
            TRIANGLE_MESH,
            new double[]{-9999.0, -9999.0, -9999.0},
            new double[]{12.0, 18.0, 24.0},
            SNAPSHOT,
            64,
            64,
            false,
            -9999.0,
            -9999.0,
            0,
            wavelengthRange
        );

        assertTrue(arrows.isEmpty());
    }

    @Test
    void mapArrowLengthClampsIntoConfiguredPixelRange() {
        RangeStats wavelengthRange = new RangeStats(10.0, 30.0, 3);

        assertTrue(renderer.mapArrowLength(0.0, wavelengthRange) >= WaveArrowOverlayRenderer.MIN_ARROW_LENGTH);
        assertTrue(renderer.mapArrowLength(1000.0, wavelengthRange) <= WaveArrowOverlayRenderer.MAX_ARROW_LENGTH);
    }
}
