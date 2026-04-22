package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TileRendererTest {
    @Test
    void renderReturnsNullWhenSelectionIsDisabled() {
        TileRenderer renderer = new TileRenderer(url -> null);

        BaseMapRenderResult result = renderer.render(
            BaseMapSelection.none(),
            domain(110.0, 112.0, 20.0, 22.0),
            new ViewportState.Snapshot(100.0, 0.0, 2200.0),
            320,
            240
        );

        assertNull(result.image());
        assertNull(result.message());
    }

    @Test
    void renderSkipsNonGeographicDomainWithMessage() {
        TileRenderer renderer = new TileRenderer(url -> null);

        BaseMapRenderResult result = renderer.render(
            new BaseMapSelection(BaseMapPreset.OSM.create("")),
            domain(400000.0, 410000.0, 3400000.0, 3410000.0),
            new ViewportState.Snapshot(1.0, 0.0, 0.0),
            320,
            240
        );

        assertNull(result.image());
        assertEquals("当前坐标域不是经纬度，已跳过底图。", result.message());
    }

    @Test
    void renderDrawsTilesFromFakeClient() {
        TileRenderer renderer = new TileRenderer(url -> solid(Color.BLUE));

        BaseMapDefinition definition = new BaseMapDefinition(
            "custom",
            "测试底图",
            List.of(new BaseMapLayer("测试图层", "https://tiles.example.com/{z}/{x}/{y}.png", "", List.of(), 1.0)),
            false
        );
        BaseMapRenderResult result = renderer.render(
            new BaseMapSelection(definition),
            domain(110.0, 112.0, 20.0, 22.0),
            new ViewportState.Snapshot(100.0, -11000.0, 2200.0),
            160,
            120
        );

        assertNotNull(result.image());
        assertEquals(Color.BLUE.getRGB(), result.image().getRGB(80, 60));
    }

    @Test
    void renderReturnsQuicklyWhenTileClientIsSlow() {
        TileRenderer renderer = new TileRenderer(url -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return solid(Color.BLUE);
        }, 100);

        long startedAt = System.nanoTime();
        BaseMapRenderResult result = renderer.render(
            new BaseMapSelection(BaseMapPreset.OSM.create("")),
            domain(110.0, 112.0, 20.0, 22.0),
            new ViewportState.Snapshot(100.0, -11000.0, 2200.0),
            160,
            120
        );
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(elapsedMillis < 800, "slow tiles should not block render for too long");
        assertTrue(result.message() == null || result.message().contains("超时"));
    }

    private static SpatialDomain domain(double minX, double maxX, double minY, double maxY) {
        return new SpatialDomain() {
            @Override
            public Kind kind() {
                return Kind.STRUCTURED_GRID;
            }

            @Override
            public double minX() {
                return minX;
            }

            @Override
            public double maxX() {
                return maxX;
            }

            @Override
            public double minY() {
                return minY;
            }

            @Override
            public double maxY() {
                return maxY;
            }
        };
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 256, 256);
        graphics.dispose();
        return image;
    }
}
