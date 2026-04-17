package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void autoFitSupportsStructuredGridDomain() {
        ViewportState viewportState = new ViewportState();
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 100.0},
                new double[]{0.0, 100.0},
                null,
                null,
                2,
                2
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );

        viewportState.ensureFitted(domain, 200, 200);
        double firstScale = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        viewportState.ensureFitted(domain, 400, 400);
        double secondScale = viewportState.screenX(10.0) - viewportState.screenX(0.0);

        assertNotEquals(firstScale, secondScale);
    }
}
