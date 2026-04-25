package com.example.netcdfviewer.basemap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

/**
 * 底图瓦片提供器。
 * 渲染器只依赖这个接口，便于测试时替换为内存瓦片。
 */
public interface BasemapTileProvider {
    Optional<BufferedImage> tile(BasemapLayer layer, int zoom, int tileX, int tileY) throws IOException;
}
