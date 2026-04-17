package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.VelocityVariablePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityVariablePairFinderTest {
    private final VelocityVariablePairFinder finder = new VelocityVariablePairFinder();

    @Test
    void findPrefersUVWhenBothUvAndUavaExist() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("ua", true, 0, true, -1, List.of("nele"), List.of(12)),
            variable("va", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("u", pair.get().eastwardVariable().name());
        assertEquals("v", pair.get().northwardVariable().name());
    }

    @Test
    void findFallsBackToUavaWhenUvIsUnavailable() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("ua", true, 0, true, -1, List.of("nele"), List.of(12)),
            variable("va", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("ua", pair.get().eastwardVariable().name());
        assertEquals("va", pair.get().northwardVariable().name());
    }

    @Test
    void findReturnsEmptyWhenVelocityBasisDiffers() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 1, false, 0, List.of("siglay", "node"), List.of(5, 12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void resolveLayerIndexClampsLayeredVelocityPair() {
        VelocityVariablePair pair = new VelocityVariablePair(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12))
        );

        assertEquals(0, pair.resolveLayerIndex(-1));
        assertEquals(2, pair.resolveLayerIndex(2));
        assertEquals(4, pair.resolveLayerIndex(99));
    }

    private static VariableInfo variable(
        String name,
        boolean plottable,
        int nodeAxis,
        boolean elementCentered,
        int layerAxis,
        List<String> dimensionNames,
        List<Integer> dimensionSizes
    ) {
        return new VariableInfo(
            name,
            "FLOAT",
            dimensionNames,
            dimensionSizes,
            plottable,
            nodeAxis,
            elementCentered,
            layerAxis,
            null
        );
    }
}
