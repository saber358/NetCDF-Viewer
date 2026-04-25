package com.example.netcdfviewer.basemap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Logger;

/**
 * 底图图层定义。
 * 目前使用 XYZ 瓦片 URL 模板描述在线底图来源。
 */
public record BasemapLayer(
    String name,
    String urlTemplate,
    String attribution,
    String cacheKey
) {
    // 底图图层日志对象。
    private static final Logger logger = Logger.getLogger(BasemapLayer.class.getName());
    // XYZ 模板必须包含的缩放占位符。
    private static final String Z_PLACEHOLDER = "{z}";
    // XYZ 模板必须包含的横向瓦片占位符。
    private static final String X_PLACEHOLDER = "{x}";
    // XYZ 模板必须包含的纵向瓦片占位符。
    private static final String Y_PLACEHOLDER = "{y}";

    public BasemapLayer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("底图名称不能为空。");
        }
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new IllegalArgumentException("底图 URL 模板不能为空。");
        }
        if (!urlTemplate.contains(Z_PLACEHOLDER)
            || !urlTemplate.contains(X_PLACEHOLDER)
            || !urlTemplate.contains(Y_PLACEHOLDER)) {
            throw new IllegalArgumentException("底图 URL 模板必须包含 {z}、{x}、{y}。");
        }
        name = name.trim();
        urlTemplate = urlTemplate.trim();
        attribution = attribution == null ? "" : attribution.trim();
        cacheKey = cacheKey == null || cacheKey.isBlank() ? stableCacheKey(urlTemplate) : cacheKey.trim();
    }

    /*
     * ========================================================================
     * 步骤1：创建 OpenStreetMap 标准底图
     * ========================================================================
     * 目标：
     *   1) 提供内置在线地图来源
     *   2) 使用稳定缓存键，避免 URL 变动导致缓存混乱
     */
    public static BasemapLayer openStreetMapStandard() {
        logger.info("开始创建 OpenStreetMap 标准底图...");

        // 1.1 使用 OSM 官方标准瓦片模板。
        BasemapLayer layer = new BasemapLayer(
            "OpenStreetMap 标准地图",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            "© OpenStreetMap contributors",
            "openstreetmap-standard"
        );

        logger.info("OpenStreetMap 标准底图创建完成。");
        return layer;
    }

    /*
     * ========================================================================
     * 步骤2：创建自定义 XYZ 底图
     * ========================================================================
     * 目标：
     *   1) 接收用户输入的瓦片 URL 模板
     *   2) 自动生成稳定缓存键
     */
    public static BasemapLayer custom(String name, String urlTemplate) {
        logger.info(() -> "开始创建自定义 XYZ 底图, name=" + name);

        // 2.1 通过规范构造器完成模板校验和缓存键生成。
        BasemapLayer layer = new BasemapLayer(name, urlTemplate, "", null);

        logger.info(() -> "自定义 XYZ 底图创建完成, cacheKey=" + layer.cacheKey());
        return layer;
    }

    /*
     * ========================================================================
     * 步骤3：生成瓦片 URL
     * ========================================================================
     * 目标：
     *   1) 将 z/x/y 坐标填入 URL 模板
     */
    public String tileUrl(int zoom, int tileX, int tileY) {
        logger.info(() -> "开始生成底图瓦片 URL, zoom=" + zoom + ", tileX=" + tileX + ", tileY=" + tileY);

        // 3.1 按 XYZ 标准替换模板占位符。
        String url = urlTemplate
            .replace(Z_PLACEHOLDER, Integer.toString(zoom))
            .replace(X_PLACEHOLDER, Integer.toString(tileX))
            .replace(Y_PLACEHOLDER, Integer.toString(tileY));

        logger.info("底图瓦片 URL 生成完成。");
        return url;
    }

    private static String stableCacheKey(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
