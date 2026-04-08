package com.example.netcdfviewer.render;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderMathTest {
    @Test
    void computeRangeIgnoresNaNAndFillValue() {
        RangeStats stats = RenderMath.computeRange(new double[]{Double.NaN, -9999.0, 2.5, 7.5}, -9999.0);

        assertEquals(2.5, stats.min());
        assertEquals(7.5, stats.max());
        assertEquals(2, stats.validCount());
    }

    @Test
    void normalizeClampsIntoUnitRange() {
        assertEquals(0.0, RenderMath.normalize(-2.0, 0.0, 10.0));
        assertEquals(0.5, RenderMath.normalize(5.0, 0.0, 10.0));
        assertEquals(1.0, RenderMath.normalize(12.0, 0.0, 10.0));
    }

    @Test
    void viridisColorMapReturnsExpectedEndpoints() {
        ColorMap colorMap = ColorMaps.viridis();

        assertEquals(Color.web("#440154"), colorMap.colorAt(0.0));
        assertEquals(Color.web("#FDE725"), colorMap.colorAt(1.0));
    }
}
