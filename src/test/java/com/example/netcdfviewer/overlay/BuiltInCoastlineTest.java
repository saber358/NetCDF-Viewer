package com.example.netcdfviewer.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInCoastlineTest {
    @Test
    void bundledCoastlineResourceExistsOnClasspath() {
        assertNotNull(
            BuiltInCoastline.class.getResourceAsStream(BuiltInCoastline.RESOURCE_PATH),
            "Expected bundled coastline GeoJSON resource on the classpath."
        );
    }

    @Test
    void bundledCoastlineParsesIntoOverlayPaths() throws Exception {
        CoastlineOverlay overlay = BuiltInCoastline.load();

        assertTrue(!overlay.paths().isEmpty(), "Built-in coastline should contain overlay paths.");
    }
}
