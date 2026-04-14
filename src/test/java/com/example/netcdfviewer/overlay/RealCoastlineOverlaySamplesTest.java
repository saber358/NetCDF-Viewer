package com.example.netcdfviewer.overlay;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RealCoastlineOverlaySamplesTest {
    @Test
    void bundledGeoJsonCoastlineSampleParsesSuccessfully() throws Exception {
        Path path = Path.of("samples", "coastline", "ne_110m_coastline.geojson");

        assertTrue(Files.isRegularFile(path), "Expected bundled GeoJSON coastline sample.");

        CoastlineOverlay overlay = CoastlineOverlayLoader.load(path);

        assertTrue(!overlay.paths().isEmpty(), "GeoJSON sample should contain coastline paths.");
    }

    @Test
    void bundledShapefileCoastlineSampleParsesSuccessfully() throws Exception {
        Path path = Path.of("samples", "coastline", "ne_110m_coastline.shp");

        assertTrue(Files.isRegularFile(path), "Expected bundled shapefile coastline sample.");

        CoastlineOverlay overlay = CoastlineOverlayLoader.load(path);

        assertTrue(!overlay.paths().isEmpty(), "Shapefile sample should contain coastline paths.");
    }
}
