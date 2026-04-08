package com.example.netcdfviewer.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PngExportSupport {
    private PngExportSupport() {
    }

    public static void writePng(WritableImage image, Path path) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image must not be null.");
        }
        if (path == null) {
            throw new IllegalArgumentException("Output path must not be null.");
        }

        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        if (bufferedImage == null) {
            throw new IOException("Could not convert the JavaFX image into PNG data.");
        }

        try (OutputStream outputStream = Files.newOutputStream(absolutePath)) {
            boolean written = ImageIO.write(bufferedImage, "png", outputStream);
            if (!written) {
                throw new IOException("No PNG writer is available in the current runtime.");
            }
        }

        if (!Files.isRegularFile(absolutePath) || Files.size(absolutePath) <= 0L) {
            throw new IOException("The exported PNG file is empty.");
        }
        if (ImageIO.read(absolutePath.toFile()) == null) {
            throw new IOException("The exported PNG file could not be validated after writing.");
        }
    }
}
