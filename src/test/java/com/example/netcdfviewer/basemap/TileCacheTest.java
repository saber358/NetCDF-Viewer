package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void putAndGetRoundTripsImageThroughMemoryAndDisk() throws Exception {
        TileCache cache = new TileCache(tempDir, 8);
        BufferedImage image = image(Color.RED);

        cache.put("https://tiles.example.com/1/2/3.png", image);

        Optional<BufferedImage> fromMemory = cache.get("https://tiles.example.com/1/2/3.png");
        TileCache reopened = new TileCache(tempDir, 8);
        Optional<BufferedImage> fromDisk = reopened.get("https://tiles.example.com/1/2/3.png");

        assertTrue(fromMemory.isPresent());
        assertTrue(fromDisk.isPresent());
        assertEquals(Color.RED.getRGB(), fromDisk.get().getRGB(0, 0));
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 2, 2);
        graphics.dispose();
        return image;
    }
}
