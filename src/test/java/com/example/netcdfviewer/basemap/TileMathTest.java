package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileMathTest {
    @Test
    void geographicBoundsAcceptsLongitudeLatitudeRanges() {
        assertTrue(TileMath.isGeographicBounds(110.0, 125.0, 20.0, 40.0));
        assertTrue(TileMath.isGeographicBounds(-180.0, 180.0, -85.0, 85.0));
    }

    @Test
    void geographicBoundsRejectsProjectedOrInvalidRanges() {
        assertFalse(TileMath.isGeographicBounds(400000.0, 410000.0, 3400000.0, 3410000.0));
        assertFalse(TileMath.isGeographicBounds(125.0, 110.0, 20.0, 40.0));
        assertFalse(TileMath.isGeographicBounds(110.0, 125.0, -90.0, 90.0));
    }

    @Test
    void lonLatToGlobalPixelMatchesWebMercatorOriginAtZoomOne() {
        TileMath.GlobalPixel pixel = TileMath.lonLatToGlobalPixel(0.0, 0.0, 1);

        assertEquals(256.0, pixel.x(), 0.000001);
        assertEquals(256.0, pixel.y(), 0.000001);
    }

    @Test
    void globalPixelToTileAddressFloorsPixelCoordinates() {
        TileAddress address = TileMath.globalPixelToAddress(new TileMath.GlobalPixel(513.0, 255.0), 2);

        assertEquals(2, address.z());
        assertEquals(2, address.x());
        assertEquals(0, address.y());
    }

    @Test
    void chooseZoomUsesViewportScaleAndClampsToSupportedRange() {
        assertEquals(3, TileMath.chooseZoom(4.0, 0, 18));
        assertEquals(0, TileMath.chooseZoom(0.000001, 0, 18));
        assertEquals(18, TileMath.chooseZoom(999999.0, 0, 18));
    }
}
