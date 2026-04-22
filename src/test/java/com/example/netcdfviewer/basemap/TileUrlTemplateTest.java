package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TileUrlTemplateTest {
    @Test
    void expandReplacesXyzSubdomainAndToken() {
        BaseMapLayer layer = new BaseMapLayer(
            "测试",
            "https://t{s}.example.com/{z}/{x}/{y}.png?tk={tk}",
            "abc123",
            List.of("0", "1", "2"),
            0.75
        );

        String url = TileUrlTemplate.expand(layer, new TileAddress(5, 10, 12));

        assertEquals("https://t1.example.com/5/10/12.png?tk=abc123", url);
    }

    @Test
    void expandUsesBlankSubdomainWhenLayerHasNone() {
        BaseMapLayer layer = new BaseMapLayer(
            "测试",
            "https://tiles.example.com/{z}/{x}/{y}.png",
            "",
            List.of(),
            1.0
        );

        String url = TileUrlTemplate.expand(layer, new TileAddress(3, 4, 5));

        assertEquals("https://tiles.example.com/3/4/5.png", url);
    }

    @Test
    void blankTemplateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BaseMapLayer("坏底图", " ", "", List.of(), 1.0));
    }
}
