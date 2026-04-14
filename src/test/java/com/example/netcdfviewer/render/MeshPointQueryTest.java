package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshPointQueryTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 10.0, 0.0},
        new double[]{0.0, 0.0, 10.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 0.0);

    @Test
    void queryReturnsElementValueForPointInsideTriangle() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{7.5},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(0, result.triangleIndex());
        assertEquals(7.5, result.value());
    }

    @Test
    void queryInterpolatesNodeValueInsideTriangle() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{10.0, 20.0, 40.0},
            SNAPSHOT,
            2.0,
            -2.0,
            false,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(18.0, result.value(), 1e-9);
    }

    @Test
    void queryReturnsMissWhenPointIsOutsideMesh() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{7.5},
            SNAPSHOT,
            20.0,
            -20.0,
            true,
            null,
            0
        );

        assertFalse(result.hit());
        assertEquals(MeshPointQuery.Reason.NO_HIT, result.reason());
    }

    @Test
    void queryReturnsUnavailableWhenElementValueIsFillValue() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{-9999.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            -9999.0,
            0
        );

        assertTrue(result.hit());
        assertEquals(MeshPointQuery.Reason.INVALID_VALUE, result.reason());
    }

    @Test
    void screenPointIsConvertedBackToWorldCoordinates() {
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(2.0, 100.0, 200.0);

        assertEquals(5.0, snapshot.worldX(110.0), 1e-9);
        assertEquals(10.0, snapshot.worldY(180.0), 1e-9);
    }
}
