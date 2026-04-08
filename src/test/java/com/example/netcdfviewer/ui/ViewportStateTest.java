package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.model.MeshData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ViewportStateTest {
    @Test
    void autoFitRecomputesScaleWhenViewportChangesBeforeUserInteraction() {
        ViewportState viewportState = new ViewportState();
        MeshData mesh = new MeshData(
            new double[]{0.0, 100.0},
            new double[]{0.0, 100.0},
            new int[0][]
        );

        viewportState.ensureFitted(mesh, 200, 200);
        double firstScale = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        viewportState.ensureFitted(mesh, 400, 400);
        double secondScale = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        assertNotEquals(firstScale, secondScale);
    }

    @Test
    void manualPanStopsAutomaticRefitOnLaterViewportChanges() {
        ViewportState viewportState = new ViewportState();
        MeshData mesh = new MeshData(
            new double[]{0.0, 100.0},
            new double[]{0.0, 100.0},
            new int[0][]
        );

        viewportState.ensureFitted(mesh, 200, 200);
        viewportState.pan(15.0, 10.0);
        double beforeResize = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        viewportState.ensureFitted(mesh, 400, 400);
        double afterResize = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        assertEquals(beforeResize, afterResize);
    }
}
