package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.model.TriangleDomain;
import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.testsupport.SampleDatasetPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGridDatasetParserTest {
    private final NetcdfDatasetParser parser = new NetcdfDatasetParser();

    @Test
    void openKeepsTriangleDatasetsOnTriangleDomain() throws Exception {
        ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("HBHQY.nc"));

        assertInstanceOf(TriangleDomain.class, dataset.spatialDomain());
        assertTrue(dataset.coordinateBindings().isEmpty());
        assertTrue(dataset.selectedCoordinateBinding().isEmpty());
    }

    @Test
    void openBuildsStructuredDomainWhenHorizontalCoordinatesExist() throws Exception {
        ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("XTPY-wrf.nc"));

        assertInstanceOf(StructuredGridDomain.class, dataset.spatialDomain());
        assertTrue(dataset.coordinateBindings().size() >= 1);
        assertTrue(dataset.selectedCoordinateBinding().isPresent());
        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, dataset.spatialDomain().kind());
    }

    @Test
    void openMarksRectilinearScalarsAsPlottable() throws Exception {
        ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("XTPY-wrf.nc"));

        VariableInfo t2 = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("T2"))
            .findFirst()
            .orElseThrow();

        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, t2.geometryKind());
        assertTrue(t2.basisId().startsWith("binding:"));
        assertTrue(!t2.layered());
    }

    @Test
    void openMarksLayeredStructuredScalarsAsPlottable() throws Exception {
        ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("XTPY-roms.nc"));

        VariableInfo temp = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("temp"))
            .findFirst()
            .orElseThrow();

        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, temp.geometryKind());
        assertTrue(temp.layered());
        assertEquals(23, temp.layerCount());
    }

    @Test
    void openDoesNotExposeManualCoordinateBindingsForTriangleDatasets() throws Exception {
        ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("HBHQY.nc"));

        assertTrue(dataset.coordinateBindings().isEmpty());
        assertTrue(dataset.selectedCoordinateBinding().isEmpty());
        assertTrue(!dataset.spatialDomain().supportsManualCoordinateSelection());
    }
}
