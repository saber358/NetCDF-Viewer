package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredVectorQueryTest {
    @Test
    void queryReturnsVelocityForSameBasisStructuredVectorField() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                null,
                null,
                2,
                2
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredVectorQuery.Result result = StructuredVectorQuery.query(
            domain,
            domain,
            new double[]{1.0, 1.0, 1.0, 1.0},
            new double[]{2.0, 2.0, 2.0, 2.0},
            snapshot,
            50.0,
            50.0,
            false,
            false,
            null,
            null,
            0
        );

        assertTrue(result.hasVelocity());
        assertEquals(1.0, result.u());
        assertEquals(2.0, result.v());
    }

    @Test
    void queryReturnsVelocityForStructuredStaggeredVectorField() {
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
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 200.0);

        StructuredVectorQuery.Result result = StructuredVectorQuery.query(
            uDomain,
            vDomain,
            new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0},
            new double[]{2.0, 2.0, 2.0, 2.0, 2.0, 2.0},
            snapshot,
            100.0,
            100.0,
            false,
            false,
            null,
            null,
            0
        );

        assertTrue(result.hasVelocity());
        assertEquals(1.0, result.u());
        assertEquals(2.0, result.v());
    }
}
