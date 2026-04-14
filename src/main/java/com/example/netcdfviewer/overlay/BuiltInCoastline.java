package com.example.netcdfviewer.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 内置海岸线资源加载器。
 * 从应用 classpath 中读取默认 GeoJSON 海岸线资源。
 */
public final class BuiltInCoastline {
    public static final String RESOURCE_PATH = "/coastline/default-coastline.geojson";
    private static final Path SYNTHETIC_PATH = Path.of("builtin", "default-coastline.geojson");

    private BuiltInCoastline() {
        // 工具类不允许被实例化。
    }

    public static CoastlineOverlay load() throws IOException {
        try (InputStream stream = BuiltInCoastline.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IOException("Built-in coastline resource is missing: " + RESOURCE_PATH);
            }
            return GeoJsonOverlayLoader.load(stream, SYNTHETIC_PATH, "Built-in coastline");
        }
    }
}
