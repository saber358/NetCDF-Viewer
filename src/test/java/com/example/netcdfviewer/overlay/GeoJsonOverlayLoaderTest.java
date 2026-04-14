package com.example.netcdfviewer.overlay;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeoJsonOverlayLoaderTest {
    @Test
    void lineStringBecomesSingleOverlayPath() throws Exception {
        Path file = Files.createTempFile("coastline-line-", ".geojson");
        try {
            Files.writeString(file, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "LineString",
                        "coordinates": [[120.0, 30.0], [121.0, 31.0]]
                      }
                    }
                  ]
                }
                """);

            CoastlineOverlay overlay = CoastlineOverlayLoader.load(file);

            assertEquals(1, overlay.paths().size());
            assertEquals(2, overlay.paths().get(0).x().length);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void polygonBecomesRingStrokePath() throws Exception {
        Path file = Files.createTempFile("coastline-polygon-", ".geojson");
        try {
            Files.writeString(file, """
                {
                  "type": "Polygon",
                  "coordinates": [
                    [[120.0, 30.0], [121.0, 30.0], [121.0, 31.0], [120.0, 30.0]]
                  ]
                }
                """);

            CoastlineOverlay overlay = CoastlineOverlayLoader.load(file);

            assertEquals(1, overlay.paths().size());
            assertEquals(4, overlay.paths().get(0).x().length);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void pointGeometryIsIgnored() throws Exception {
        Path file = Files.createTempFile("coastline-point-", ".geojson");
        try {
            Files.writeString(file, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "geometry": {
                        "type": "Point",
                        "coordinates": [120.0, 30.0]
                      }
                    }
                  ]
                }
                """);

            CoastlineOverlay overlay = CoastlineOverlayLoader.load(file);

            assertEquals(0, overlay.paths().size());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
