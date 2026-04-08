package com.example.netcdfviewer.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PNG 导出支持类。
 * 该类负责把 JavaFX 图像稳定地写出为 PNG，并在写出后做一次有效性校验。
 */
public final class PngExportSupport {
    private PngExportSupport() {
        // 工具类不允许被实例化。
    }

    public static void writePng(WritableImage image, Path path) throws IOException {
        // 图像对象不能为空。
        if (image == null) {
            throw new IllegalArgumentException("Image must not be null.");
        }
        // 输出路径不能为空。
        if (path == null) {
            throw new IllegalArgumentException("Output path must not be null.");
        }

        // 将路径转换为绝对路径，便于统一处理。
        Path absolutePath = path.toAbsolutePath();
        // 取得父目录路径。
        Path parent = absolutePath.getParent();
        // 如果父目录存在，则确保目录已创建。
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // 先把 JavaFX 图像转换成 AWT BufferedImage。
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        // 如果转换失败，直接中断导出。
        if (bufferedImage == null) {
            throw new IOException("Could not convert the JavaFX image into PNG data.");
        }

        // 通过输出流写入 PNG 文件。
        try (OutputStream outputStream = Files.newOutputStream(absolutePath)) {
            boolean written = ImageIO.write(bufferedImage, "png", outputStream);
            // 如果当前运行时没有 PNG 写入器，则明确报错。
            if (!written) {
                throw new IOException("No PNG writer is available in the current runtime.");
            }
        }

        // 写出后检查文件是否真实存在且长度大于 0。
        if (!Files.isRegularFile(absolutePath) || Files.size(absolutePath) <= 0L) {
            throw new IOException("The exported PNG file is empty.");
        }
        // 再做一次读回验证，确保导出的文件可被标准图片读取器打开。
        if (ImageIO.read(absolutePath.toFile()) == null) {
            throw new IOException("The exported PNG file could not be validated after writing.");
        }
    }
}
