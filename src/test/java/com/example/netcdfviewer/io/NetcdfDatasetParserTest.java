package com.example.netcdfviewer.io;

import org.junit.jupiter.api.Test;
import ucar.ma2.DataType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetcdfDatasetParserTest {
    @Test
    void normalizeConnectivityConvertsOneBasedIndicesToZeroBased() {
        int[][] normalized = NetcdfDatasetParser.normalizeConnectivity(new int[][]{
            {1, 2, 3},
            {3, 4, 5}
        }, 5);

        assertArrayEquals(new int[]{0, 1, 2}, normalized[0]);
        assertArrayEquals(new int[]{2, 3, 4}, normalized[1]);
    }

    @Test
    void normalizeConnectivityRejectsUnexpectedIndexBase() {
        assertThrows(IllegalArgumentException.class, () ->
            NetcdfDatasetParser.normalizeConnectivity(new int[][]{
                {2, 3, 4}
            }, 8)
        );
    }

    @Test
    void describeVariableMarksNodeScalarAsPlottable() {
        var info = NetcdfDatasetParser.describeVariable(
            "bottomDepth",
            DataType.DOUBLE,
            List.of("node"),
            List.of(12),
            12,
            Set.of("depth")
        );

        assertTrue(info.plottable());
        assertFalse(info.layered());
        assertEquals(-1, info.layerAxis());
        assertEquals(0, info.nodeAxis());
    }

    @Test
    void describeVariableMarksDepthNodeScalarAsLayeredPlottable() {
        var info = NetcdfDatasetParser.describeVariable(
            "temperature",
            DataType.FLOAT,
            List.of("depth", "node"),
            List.of(6, 12),
            12,
            Set.of("depth")
        );

        assertTrue(info.plottable());
        assertTrue(info.layered());
        assertEquals(0, info.layerAxis());
        assertEquals(1, info.nodeAxis());
        assertEquals(6, info.layerCount());
    }

    @Test
    void describeVariableRejectsMismatchedDimensions() {
        var info = NetcdfDatasetParser.describeVariable(
            "profile",
            DataType.FLOAT,
            List.of("time", "station"),
            List.of(6, 12),
            20,
            Set.of("depth")
        );

        assertFalse(info.plottable());
    }

    @Test
    void describeVariableDoesNotTreatCoordinateAxisAsScalarAttribute() {
        var info = NetcdfDatasetParser.describeVariable(
            "lon",
            DataType.DOUBLE,
            List.of("node"),
            List.of(12),
            12,
            Set.of("depth")
        );

        assertFalse(info.plottable());
    }

    @Test
    void describeVariableDoesNotTreatLayerAxisVariableAsScalarAttribute() {
        var info = NetcdfDatasetParser.describeVariable(
            "siglay",
            DataType.DOUBLE,
            List.of("siglay", "node"),
            List.of(6, 12),
            12,
            Set.of("depth", "siglay")
        );

        assertFalse(info.plottable());
    }

    @Test
    void describeVariableRejectsNonLayerTwoDimensionalTopologyArray() {
        var info = NetcdfDatasetParser.describeVariable(
            "nbsn",
            DataType.INT,
            List.of("maxnode", "node"),
            List.of(8, 12),
            12,
            Set.of("depth", "siglay")
        );

        assertFalse(info.plottable());
    }
}
