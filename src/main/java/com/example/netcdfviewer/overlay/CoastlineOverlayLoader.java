package com.example.netcdfviewer.overlay;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 海岸线叠加层加载入口。
 * 根据文件扩展名分派到对应解析器。
 */
public final class CoastlineOverlayLoader {
    private CoastlineOverlayLoader() {
        // 工具类不允许被实例化。
    }

    public static CoastlineOverlay load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Overlay path must not be null.");
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".geojson") || fileName.endsWith(".json")) {
            return GeoJsonOverlayLoader.load(path);
        }
        if (fileName.endsWith(".shp")) {
            return ShapefileOverlayLoader.load(path);
        }
        throw new IOException("Unsupported coastline overlay file: " + path.getFileName());
    }
}
