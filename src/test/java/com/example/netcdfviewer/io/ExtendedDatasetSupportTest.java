package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedDatasetSupportTest {
    @Test
    void hbhqyExposesNodeLayerNodeTimeAndElementTimeVariables() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset dataset = parser.open(Path.of("HBHQY.nc"));

        Set<String> plottableNames = dataset.plottableVariables().stream()
            .map(VariableInfo::name)
            .collect(Collectors.toSet());

        assertTrue(dataset.hasMesh(), "HBHQY.nc should expose a triangle mesh.");
        assertTrue(plottableNames.contains("temp"), "Expected temp to be plottable.");
        assertTrue(plottableNames.contains("salinity"), "Expected salinity to be plottable.");
        assertTrue(plottableNames.contains("uwind_speed"), "Expected uwind_speed to be plottable.");
        assertTrue(plottableNames.contains("hs"), "Expected hs to be plottable.");
        assertTrue(!plottableNames.contains("lonc"), "Center longitude should not appear as a plottable attribute.");
        assertTrue(!plottableNames.contains("latc"), "Center latitude should not appear as a plottable attribute.");
    }

    @Test
    void ydwTempReadsAsLayeredNodeField() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset dataset = parser.open(Path.of("ydw.nc"));
        VariableInfo temp = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("temp"))
            .findFirst()
            .orElseThrow();

        double[] values = parser.readLayer(dataset, temp, 0);

        assertTrue(temp.layered(), "temp should be layered.");
        assertEquals(dataset.mesh().nodeCount(), values.length);
    }

    @Test
    void hbhqyElementTimeFieldReadsAgainstTriangleCount() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset dataset = parser.open(Path.of("HBHQY.nc"));
        VariableInfo wind = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("uwind_speed"))
            .findFirst()
            .orElseThrow();

        double[] values = parser.readLayer(dataset, wind, 0);

        assertEquals(dataset.mesh().triangleCount(), values.length);
    }

    @Test
    void hydAndDsdSupportTriConnectivityStoredAsDouble() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset hyd = parser.open(Path.of("HYD.nc"));
        ParsedDataset dsd = parser.open(Path.of("DSD1211.nc"));

        assertTrue(hyd.hasMesh(), "HYD.nc should expose a mesh from Tri.");
        assertTrue(dsd.hasMesh(), "DSD1211.nc should expose a mesh from Tri.");
        assertEquals("Tri", hyd.connectivityVariableName());
        assertEquals("Tri", dsd.connectivityVariableName());
    }

    @Test
    void nanhaiDoesNotExposeCoordinateLikeCenterAxesAsAttributes() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset dataset = parser.open(Path.of("nanhai.nc"));

        Set<String> plottableNames = dataset.plottableVariables().stream()
            .map(VariableInfo::name)
            .collect(Collectors.toSet());

        assertTrue(!plottableNames.contains("xc"));
        assertTrue(!plottableNames.contains("yc"));
        assertTrue(!plottableNames.contains("lonc"));
        assertTrue(!plottableNames.contains("latc"));
        assertTrue(!plottableNames.contains("siglay_center"));
        assertTrue(!plottableNames.contains("siglev_center"));
        assertTrue(plottableNames.contains("ua"));
        assertTrue(plottableNames.contains("temp"));
    }

    @Test
    void nanhaiFallsBackToLonLatWhenXYAxesAreDegenerate() throws Exception {
        NetcdfDatasetParser parser = new NetcdfDatasetParser();
        ParsedDataset dataset = parser.open(Path.of("nanhai.nc"));

        assertEquals("lon", dataset.xVariableName());
        assertEquals("lat", dataset.yVariableName());
        assertTrue(span(dataset.mesh().x()) > 1.0, "Longitude span should remain visible.");
        assertTrue(span(dataset.mesh().y()) > 1.0, "Latitude span should remain visible.");
    }

    private double span(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }
}
