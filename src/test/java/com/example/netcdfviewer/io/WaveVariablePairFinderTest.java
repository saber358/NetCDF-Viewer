package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WaveVariablePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveVariablePairFinderTest {
    private final WaveVariablePairFinder finder = new WaveVariablePairFinder();

    @Test
    void findExactWavePairReturnsCompatiblePair() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("hs", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wlen", true, 0, false, -1, List.of("node"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("wdir", pair.get().directionVariable().name());
        assertEquals("wlen", pair.get().wavelengthVariable().name());
        assertFalse(pair.get().layered());
    }

    @Test
    void findReturnsEmptyWhenOneVariableIsMissing() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void findReturnsEmptyWhenSpatialBasisDiffers() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wlen", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void findReturnsEmptyWhenLayerShapeDiffers() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12)),
            variable("wlen", true, 1, false, 0, List.of("siglev", "node"), List.of(4, 12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void findExactWavePairReturnsStructuredPairWhenBasisMatches() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            new VariableInfo(
                "wdir",
                "FLOAT",
                List.of("depth", "y_rho", "x_rho"),
                List.of(3, 10, 11),
                true,
                1,
                false,
                0,
                null,
                "binding:lon_rho:lat_rho",
                com.example.netcdfviewer.model.SpatialDomain.Kind.STRUCTURED_GRID,
                false
            ),
            new VariableInfo(
                "wlen",
                "FLOAT",
                List.of("depth", "y_rho", "x_rho"),
                List.of(3, 10, 11),
                true,
                1,
                false,
                0,
                null,
                "binding:lon_rho:lat_rho",
                com.example.netcdfviewer.model.SpatialDomain.Kind.STRUCTURED_GRID,
                false
            )
        ));

        assertTrue(pair.isPresent());
    }

    @Test
    void findReturnsStructuredVectorWavePairWhenUWaveAndVWaveExist() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            new VariableInfo(
                "uWave",
                "FLOAT",
                List.of("depth", "y_u", "x_u"),
                List.of(3, 10, 11),
                true,
                1,
                false,
                0,
                null,
                "binding:lon_u:lat_u",
                com.example.netcdfviewer.model.SpatialDomain.Kind.STRUCTURED_GRID,
                false
            ),
            new VariableInfo(
                "vWave",
                "FLOAT",
                List.of("depth", "y_v", "x_v"),
                List.of(3, 11, 10),
                true,
                1,
                false,
                0,
                null,
                "binding:lon_v:lat_v",
                com.example.netcdfviewer.model.SpatialDomain.Kind.STRUCTURED_GRID,
                false
            ),
            new VariableInfo(
                "Hwave",
                "FLOAT",
                List.of("depth", "y_rho", "x_rho"),
                List.of(3, 11, 11),
                true,
                1,
                false,
                0,
                null,
                "binding:lon_rho:lat_rho",
                com.example.netcdfviewer.model.SpatialDomain.Kind.STRUCTURED_GRID,
                false
            )
        ));

        assertTrue(pair.isPresent());
        assertTrue(pair.get().vectorMode());
        assertEquals("uWave", pair.get().directionVariable().name());
        assertEquals("vWave", pair.get().wavelengthVariable().name());
        assertTrue(pair.get().optionalWaveHeightVariable().isPresent());
    }

    @Test
    void resolveLayerIndexClampsToSupportedRange() {
        WaveVariablePair pair = new WaveVariablePair(
            variable("wdir", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12)),
            variable("wlen", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12))
        );

        assertEquals(0, pair.resolveLayerIndex(-5));
        assertEquals(2, pair.resolveLayerIndex(2));
        assertEquals(3, pair.resolveLayerIndex(20));
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
