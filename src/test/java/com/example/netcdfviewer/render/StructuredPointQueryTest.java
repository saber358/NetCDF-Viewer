package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredPointQueryTest {
    @Test
    void queryReturnsNodeValueForRectilinearGrid() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredPointQuery.Result result = StructuredPointQuery.query(
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
            snapshot,
            170.0,
            75.0,
            false,
            null,
            0
        );

        assertTrue(result.hit());
        assertTrue(result.hasValue());
        assertEquals(2, result.columnIndex());
        assertEquals(0, result.rowIndex());
        assertEquals(3.0, result.value());
    }

    @Test
    void queryReturnsCellValueForCellCenteredGrid() {
        StructuredGridDomain domain = new StructuredGridDomain(
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
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 200.0);

        StructuredPointQuery.Result result = StructuredPointQuery.query(
            domain,
            new double[]{10.0, 20.0, 30.0, 40.0},
            snapshot,
            125.0,
            175.0,
            true,
            null,
            0
        );

        assertTrue(result.hit());
        assertTrue(result.hasValue());
        assertEquals(1, result.columnIndex());
        assertEquals(0, result.rowIndex());
        assertEquals(20.0, result.value());
    }

    @Test
    void querySupportsDescendingAxes() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{2.0, 1.0, 0.0},
                new double[]{1.0, 0.0},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredPointQuery.Result result = StructuredPointQuery.query(
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
            snapshot,
            30.0,
            25.0,
            false,
            null,
            0
        );

        assertTrue(result.hit());
        assertTrue(result.hasValue());
        assertEquals(2, result.columnIndex());
        assertEquals(0, result.rowIndex());
        assertEquals(3.0, result.value());
    }

    @Test
    void queryOutsideRectilinearGridReturnsMiss() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredPointQuery.Result result = StructuredPointQuery.query(
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
            snapshot,
            -200.0,
            -200.0,
            false,
            null,
            0
        );

        assertFalse(result.hit());
        assertFalse(result.hasValue());
    }
}
