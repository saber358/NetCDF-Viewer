package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.ui.ViewportState;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * 底图瓦片渲染器。
 * 根据当前视口快照计算可见 XYZ 瓦片，并将瓦片画到底层图像。
 */
public final class BasemapRenderer {
    // 底图渲染日志对象。
    private static final Logger logger = Logger.getLogger(BasemapRenderer.class.getName());
    // 第一版允许的最小缩放级别。
    private static final int MIN_ZOOM = 0;
    // 第一版允许的最大缩放级别。
    private static final int MAX_ZOOM = 19;

    // 瓦片提供器。
    private final BasemapTileProvider tileProvider;

    public BasemapRenderer(BasemapTileProvider tileProvider) {
        this.tileProvider = tileProvider;
    }

    /*
     * ========================================================================
     * 步骤1：渲染底图瓦片
     * ========================================================================
     * 目标：
     *   1) 按当前视口计算可见瓦片范围
     *   2) 将瓦片绘制到透明 BufferedImage
     */
    public BufferedImage render(
        int width,
        int height,
        ViewportState.Snapshot snapshot,
        BasemapLayer layer,
        double opacity
    ) {
        logger.info(() -> "开始渲染底图, layer=" + layer.name() + ", width=" + width + ", height=" + height);

        // 1.1 创建透明底图画布。
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        if (width <= 0 || height <= 0) {
            logger.info("底图渲染结束, rendered=false");
            return image;
        }

        // 1.2 计算当前屏幕覆盖的经纬度范围。
        double west = Math.min(snapshot.worldX(0.0), snapshot.worldX(width));
        double east = Math.max(snapshot.worldX(0.0), snapshot.worldX(width));
        double north = Math.max(snapshot.worldY(0.0), snapshot.worldY(height));
        double south = Math.min(snapshot.worldY(0.0), snapshot.worldY(height));
        west = BasemapTileMath.clampLon(west);
        east = BasemapTileMath.clampLon(east);
        north = BasemapTileMath.clampLat(north);
        south = BasemapTileMath.clampLat(south);
        if (east <= west || north <= south) {
            logger.info("底图渲染结束, rendered=false");
            return image;
        }

        // 1.3 根据视口比例选择合适瓦片级别。
        int zoom = BasemapTileMath.chooseZoom(snapshot.scale(), MIN_ZOOM, MAX_ZOOM);
        int minTileX = BasemapTileMath.lonToTileX(west, zoom);
        int maxTileX = BasemapTileMath.lonToTileX(east, zoom);
        int minTileY = BasemapTileMath.latToTileY(north, zoom);
        int maxTileY = BasemapTileMath.latToTileY(south, zoom);

        // 1.4 逐张读取并绘制可见瓦片。
        drawTiles(image, snapshot, layer, opacity, zoom, minTileX, maxTileX, minTileY, maxTileY);

        logger.info("底图渲染结束, rendered=true");
        return image;
    }

    /*
     * ========================================================================
     * 步骤2：绘制可见瓦片
     * ========================================================================
     * 目标：
     *   1) 从瓦片提供器读取图片
     *   2) 按经纬度边界投影到当前画布
     */
    private void drawTiles(
        BufferedImage target,
        ViewportState.Snapshot snapshot,
        BasemapLayer layer,
        double opacity,
        int zoom,
        int minTileX,
        int maxTileX,
        int minTileY,
        int maxTileY
    ) {
        logger.info(() -> "开始绘制底图瓦片, zoom=" + zoom);

        Graphics2D graphics = target.createGraphics();
        try {
            // 2.1 设置插值和透明度。
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                (float) Math.max(0.0, Math.min(1.0, opacity))
            ));

            // 2.2 遍历可见瓦片范围，单张失败时跳过。
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                    drawTile(graphics, target.getWidth(), target.getHeight(), snapshot, layer, zoom, tileX, tileY);
                }
            }
        } finally {
            // 2.3 释放绘图上下文。
            graphics.dispose();
        }

        logger.info("底图瓦片绘制完成。");
    }

    private void drawTile(
        Graphics2D graphics,
        int targetWidth,
        int targetHeight,
        ViewportState.Snapshot snapshot,
        BasemapLayer layer,
        int zoom,
        int tileX,
        int tileY
    ) {
        try {
            // 2.4 读取瓦片图片。
            BufferedImage tileImage = tileProvider.tile(layer, zoom, tileX, tileY).orElse(null);
            if (tileImage == null) {
                return;
            }

            // 2.5 将 Web Mercator 瓦片按经纬度视口逐行重投影。
            drawReprojectedTile(graphics, targetWidth, targetHeight, snapshot, zoom, tileX, tileY, tileImage);
        } catch (IOException exception) {
            logger.info(() -> "跳过底图瓦片, reason=" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤3：重投影单张 Web Mercator 瓦片
     * ========================================================================
     * 目标：
     *   1) 横向按经度线性映射
     *   2) 纵向按每个屏幕行反算纬度后读取 Mercator 源行
     */
    private void drawReprojectedTile(
        Graphics2D graphics,
        int targetWidth,
        int targetHeight,
        ViewportState.Snapshot snapshot,
        int zoom,
        int tileX,
        int tileY,
        BufferedImage tileImage
    ) {
        logger.info(() -> "开始重投影底图瓦片, zoom=" + zoom + ", tileX=" + tileX + ", tileY=" + tileY);

        // 3.1 计算瓦片经纬度边界对应的屏幕范围。
        double west = BasemapTileMath.tileWestLon(tileX, zoom);
        double east = BasemapTileMath.tileEastLon(tileX, zoom);
        double north = BasemapTileMath.tileNorthLat(tileY, zoom);
        double south = BasemapTileMath.tileSouthLat(tileY, zoom);
        int x1 = (int) Math.round(snapshot.screenX(west));
        int x2 = (int) Math.round(snapshot.screenX(east));
        int yTop = (int) Math.floor(Math.min(snapshot.screenY(north), snapshot.screenY(south)));
        int yBottom = (int) Math.ceil(Math.max(snapshot.screenY(north), snapshot.screenY(south)));

        // 3.2 屏幕范围无效时直接跳过。
        if (x1 == x2 || yBottom <= yTop || x2 < 0 || x1 > targetWidth || yBottom < 0 || yTop > targetHeight) {
            logger.info("底图瓦片重投影完成, skipped=true");
            return;
        }

        // 3.3 裁剪到当前画布可见行。
        int visibleTop = Math.max(0, yTop);
        int visibleBottom = Math.min(targetHeight, yBottom);
        int sourceTileTop = tileY * BasemapTileMath.TILE_SIZE;
        double sourceHeightScale = tileImage.getHeight() / (double) BasemapTileMath.TILE_SIZE;

        // 3.4 逐个屏幕行取对应 Mercator 源行，修正全局视图下的纬向错位。
        for (int screenY = visibleTop; screenY < visibleBottom; screenY++) {
            double latitude = snapshot.worldY(screenY);
            int sourceY = (int) Math.floor(
                (BasemapTileMath.latToGlobalPixelY(latitude, zoom) - sourceTileTop) * sourceHeightScale
            );
            if (sourceY < 0 || sourceY >= tileImage.getHeight()) {
                continue;
            }
            graphics.drawImage(
                tileImage,
                x1,
                screenY,
                x2,
                screenY + 1,
                0,
                sourceY,
                tileImage.getWidth(),
                sourceY + 1,
                null
            );
        }

        logger.info("底图瓦片重投影完成, skipped=false");
    }
}
