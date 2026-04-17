package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowVectorQueryTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 10.0, 0.0},
        new double[]{0.0, 0.0, 10.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 0.0);

    @Test
    void queryReturnsElementCenteredVelocityInsideTriangle() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{3.0},
            new double[]{4.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            null,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(3.0, result.u());
        assertEquals(4.0, result.v());
        assertEquals(5.0, result.speed(), 1e-9);
    }

    @Test
    void queryReturnsMissOutsideMesh() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{3.0},
            new double[]{4.0},
            SNAPSHOT,
            20.0,
            -20.0,
            true,
            null,
            null,
            0
        );

        assertFalse(result.hit());
        assertEquals(FlowVectorQuery.Reason.NO_HIT, result.reason());
    }

    @Test
    void queryReturnsInvalidWhenOneComponentIsFillValue() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{-9999.0},
            new double[]{4.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            -9999.0,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(FlowVectorQuery.Reason.INVALID_VALUE, result.reason());
    }
}
