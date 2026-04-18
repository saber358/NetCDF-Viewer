package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WindVariablePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindVariablePairFinderTest {
    private final WindVariablePairFinder finder = new WindVariablePairFinder();

    @Test
    void findReturnsTriangleWindPairForUwindSpeedAndVwindspeed() {
        Optional<WindVariablePair> pair = finder.find(List.of(
            variable("uwind_speed", "triangle:cell:1", SpatialDomain.Kind.TRIANGLE_MESH, true, List.of("time", "nele"), List.of(1, 12)),
            variable("vwindspeed", "triangle:cell:1", SpatialDomain.Kind.TRIANGLE_MESH, true, List.of("time", "nele"), List.of(1, 12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("uwind_speed", pair.get().eastwardVariable().name());
        assertEquals("vwindspeed", pair.get().northwardVariable().name());
    }

    @Test
    void findReturnsStructuredWindPairForU10AndV10() {
        Optional<WindVariablePair> pair = finder.find(List.of(
            variable("U10", "binding:x:y", SpatialDomain.Kind.STRUCTURED_GRID, false, List.of("y_rho", "x_rho"), List.of(12, 14)),
            variable("V10", "binding:x:y", SpatialDomain.Kind.STRUCTURED_GRID, false, List.of("y_rho", "x_rho"), List.of(12, 14))
        ));

        assertTrue(pair.isPresent());
        assertEquals("U10", pair.get().eastwardVariable().name());
        assertEquals("V10", pair.get().northwardVariable().name());
    }

    @Test
    void findReturnsEmptyWhenWindVariablesAreIncomplete() {
        Optional<WindVariablePair> pair = finder.find(List.of(
            variable("uwind_speed", "triangle:cell:1", SpatialDomain.Kind.TRIANGLE_MESH, true, List.of("time", "nele"), List.of(1, 12))
        ));

        assertTrue(pair.isEmpty());
    }

    private static VariableInfo variable(
        String name,
        String basisId,
        SpatialDomain.Kind geometryKind,
        boolean cellCentered,
        List<String> dimensionNames,
        List<Integer> dimensionSizes
    ) {
        return new VariableInfo(
            name,
            "FLOAT",
            dimensionNames,
            dimensionSizes,
            true,
            1,
            cellCentered,
            0,
            null,
            basisId,
            geometryKind,
            cellCentered
        );
    }
}
