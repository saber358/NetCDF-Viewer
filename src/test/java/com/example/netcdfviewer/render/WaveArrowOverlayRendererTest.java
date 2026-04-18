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

    @Test
    void sampleStructuredArrowsBuildsBoundedGlyphsForValidWaveField() {
        StructuredGridDomain directionDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                3,
                3
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        StructuredGridDomain wavelengthDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                3,
                3
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        RangeStats wavelengthRange = RenderMath.computeRange(new double[]{12.0, 18.0, 24.0, 16.0}, null);

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleStructuredArrows(
            directionDomain,
            wavelengthDomain,
            new double[]{45.0, 45.0, 45.0, 45.0, 45.0, 45.0, 45.0, 45.0, 45.0},
            new double[]{12.0, 18.0, 24.0, 16.0, 20.0, 22.0, 18.0, 19.0, 21.0},
            new ViewportState.Snapshot(24.0, 0.0, 48.0),
            64,
            64,
            false,
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
    void sampleStructuredVectorArrowsBuildsGlyphsWhenUWaveAndVWaveExist() {
        StructuredGridDomain uDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon_u:lat_u", "lon_u", "lat_u", List.of("x_u", "y_u"), false),
                new double[]{0.5, 1.5},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                2,
                3
            ),
            new CoordinateBinding("binding:lon_u:lat_u", "lon_u", "lat_u", List.of("x_u", "y_u"), false)
        );
        StructuredGridDomain vDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon_v:lat_v", "lon_v", "lat_v", List.of("x_v", "y_v"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.5, 1.5},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon_v:lat_v", "lon_v", "lat_v", List.of("x_v", "y_v"), false)
        );
        StructuredGridDomain hDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon_rho:lat_rho", "lon_rho", "lat_rho", List.of("x_rho", "y_rho"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0, 2.0},
                null,
                null,
                3,
                3
            ),
            new CoordinateBinding("binding:lon_rho:lat_rho", "lon_rho", "lat_rho", List.of("x_rho", "y_rho"), false)
        );

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleStructuredVectorArrows(
            uDomain,
            vDomain,
            hDomain,
            new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
            new double[]{0.5, 0.5, 0.5, 0.5, 0.5, 0.5},
            new double[]{2.0, 3.0, 4.0, 2.5, 3.5, 4.5, 3.0, 4.0, 5.0},
            new ViewportState.Snapshot(24.0, 0.0, 48.0),
            64,
            64,
            false,
            false,
            false,
            null,
            null,
            null,
            0,
            RenderMath.computeRange(new double[]{2.0, 3.0, 4.0, 5.0}, null)
        );

        assertFalse(arrows.isEmpty());
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() >= WaveArrowOverlayRenderer.MIN_ARROW_LENGTH));
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() <= WaveArrowOverlayRenderer.MAX_ARROW_LENGTH));
    }
}
