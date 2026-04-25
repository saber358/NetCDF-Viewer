package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasemapTileMathTest {
    @Test
    void webMercatorTileCoordinatesMatchKnownOrigin() {
        int zoom = 1;

        int tileX = BasemapTileMath.lonToTileX(0.0, zoom);
        int tileY = BasemapTileMath.latToTileY(0.0, zoom);

        assertEquals(1, tileX);
        assertEquals(1, tileY);
        assertEquals(0.0, BasemapTileMath.tileWestLon(tileX, zoom), 1e-9);
        assertEquals(0.0, BasemapTileMath.tileNorthLat(tileY, zoom), 1e-9);
    }

    @Test
    void webMercatorGlobalPixelYMatchesKnownLatitudes() {
        assertEquals(128.0, BasemapTileMath.latToGlobalPixelY(0.0, 0), 1e-9);
        assertEquals(74.0, Math.round(BasemapTileMath.latToGlobalPixelY(60.0, 0)));
    }

    @Test
    void customLayerRequiresXyzPlaceholders() {
        assertThrows(
            IllegalArgumentException.class,
            () -> BasemapLayer.custom("bad", "https://example.com/{z}/{x}.png")
        );
    }

    @Test
    void tileUrlTemplateReplacesAllPlaceholders() {
        BasemapLayer layer = BasemapLayer.custom(
            "Custom",
            "https://tiles.example.com/{z}/{x}/{y}.png"
        );

        assertEquals("https://tiles.example.com/3/4/5.png", layer.tileUrl(3, 4, 5));
    }
}
