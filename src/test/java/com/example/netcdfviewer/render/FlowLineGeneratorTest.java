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

class FlowLineGeneratorTest {
    private static final MeshData SQUARE_MESH = new MeshData(
        new double[]{0.0, 96.0, 0.0, 96.0},
        new double[]{0.0, 0.0, 96.0, 96.0},
        new int[][]{{0, 1, 2}, {1, 3, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 96.0);
    private final FlowLineGenerator generator = new FlowLineGenerator();

    @Test
    void generateBuildsVisibleFlowLinesForUniformVelocityField() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            SQUARE_MESH,
            new double[]{1.0, 1.0, 1.0, 1.0},
            new double[]{0.0, 0.0, 0.0, 0.0},
            SNAPSHOT,
            96,
            96,
            false,
            null,
            null,
            0
        );

        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().allMatch(line -> line.points().size() >= 2));
        assertTrue(lines.stream().allMatch(line -> line.totalLength() >= FlowLineGenerator.MIN_LINE_LENGTH));
    }

    @Test
    void generateSkipsInvalidVelocityValues() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            SQUARE_MESH,
            new double[]{-9999.0, -9999.0, -9999.0, -9999.0},
            new double[]{0.0, 0.0, 0.0, 0.0},
            SNAPSHOT,
            96,
            96,
            false,
            -9999.0,
            null,
            0
        );

        assertTrue(lines.isEmpty());
    }

    @Test
    void generateSuppressesDuplicateSeedsInUniformField() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            SQUARE_MESH,
            new double[]{1.0, 1.0, 1.0, 1.0},
            new double[]{0.0, 0.0, 0.0, 0.0},
            SNAPSHOT,
            96,
            96,
            false,
            null,
            null,
            0
        );

        assertTrue(lines.size() <= 4);
    }

    @Test
    void smoothPointsBuildsDenserCurveAndKeepsEndpoints() {
        List<FlowLineGenerator.FlowPoint> source = List.of(
            new FlowLineGenerator.FlowPoint(0.0, 0.0),
            new FlowLineGenerator.FlowPoint(30.0, 18.0),
            new FlowLineGenerator.FlowPoint(60.0, -12.0),
            new FlowLineGenerator.FlowPoint(90.0, 0.0)
        );

        List<FlowLineGenerator.FlowPoint> smoothed = generator.smoothPoints(source);

        assertTrue(smoothed.size() > source.size());
        assertTrue(smoothed.get(0).x() == 0.0 && smoothed.get(0).y() == 0.0);
        assertTrue(smoothed.get(smoothed.size() - 1).x() == 90.0
            && smoothed.get(smoothed.size() - 1).y() == 0.0);
    }

    @Test
    void generateStructuredBuildsFlowLinesForUniformVectorField() {
        StructuredGridDomain uDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon_u:lat_u", "lon_u", "lat_u", List.of("x_u", "y_u"), false),
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 2.0, 3.0},
                null,
                null,
                4,
                4
            ),
            new CoordinateBinding("binding:lon_u:lat_u", "lon_u", "lat_u", List.of("x_u", "y_u"), false)
        );
        StructuredGridDomain vDomain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon_v:lat_v", "lon_v", "lat_v", List.of("x_v", "y_v"), false),
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 2.0, 3.0},
                null,
                null,
                4,
                4
            ),
            new CoordinateBinding("binding:lon_v:lat_v", "lon_v", "lat_v", List.of("x_v", "y_v"), false)
        );

        List<FlowLineGenerator.FlowLine> lines = generator.generateStructured(
            uDomain,
            vDomain,
            new double[16],
            filled(16, 1.0),
            new ViewportState.Snapshot(40.0, 0.0, 120.0),
            120,
            120,
            false,
            false,
            null,
            null,
            0
        );

        assertFalse(lines.isEmpty());
    }

    private static double[] filled(int count, double value) {
        double[] values = new double[count];
        for (int index = 0; index < count; index++) {
            values[index] = value;
        }
        return values;
    }
}
