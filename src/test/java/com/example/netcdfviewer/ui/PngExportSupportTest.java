package com.example.netcdfviewer.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PngExportSupportTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void writesReadablePngFile() throws Exception {
        WritableImage image = new WritableImage(24, 18);
        var writer = image.getPixelWriter();
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 24; x++) {
                writer.setColor(x, y, x < 12 ? Color.DARKCYAN : Color.GOLD);
            }
        }

        Path output = Files.createTempFile("netcdf-viewer-export-", ".png");
        try {
            PngExportSupport.writePng(image, output);
            BufferedImage readBack = ImageIO.read(output.toFile());

            assertNotNull(readBack);
            assertEquals(24, readBack.getWidth());
            assertEquals(18, readBack.getHeight());
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
