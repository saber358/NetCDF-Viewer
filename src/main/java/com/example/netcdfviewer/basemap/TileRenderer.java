package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class TileRenderer {
    private static final Logger logger = Logger.getLogger(TileRenderer.class.getName());
    private final TileClient tileClient;
    private final long renderTimeoutMillis;

    public TileRenderer(TileClient tileClient) {
        this(tileClient, 1200L);
    }

    public TileRenderer(TileClient tileClient, long renderTimeoutMillis) {
        this.tileClient = tileClient;
        this.renderTimeoutMillis = Math.max(0L, renderTimeoutMillis);
    }

    /*
     * ========================================================================
     * 步骤1：渲染在线底图
     * ========================================================================
     * 目标：
     *   1) 按当前视口计算可见瓦片
     *   2) 将多个瓦片层合成到透明离屏图像
     */
    public BaseMapRenderResult render(
        BaseMapSelection selection,
        SpatialDomain spatialDomain,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        logger.info(() -> "开始渲染在线底图, selection=" + (selection == null ? "null" : selection.displayName()));

        // 1.1 未启用底图时直接返回空结果。
        if (selection == null || !selection.enabled()) {
            logger.info("在线底图渲染结束, rendered=false, reason=disabled");
            return BaseMapRenderResult.empty();
        }

        // 1.2 非经纬度坐标域不渲染在线底图。
        if (spatialDomain == null
            || !TileMath.isGeographicBounds(spatialDomain.minX(), spatialDomain.maxX(), spatialDomain.minY(), spatialDomain.maxY())) {
            logger.info("在线底图渲染结束, rendered=false, reason=non-geographic");
            return new BaseMapRenderResult(null, "当前坐标域不是经纬度，已跳过底图。");
        }

        // 1.3 天地图缺少 token 时不发起请求，避免无效访问。
        if (selection.definition().tokenRequired()
            && selection.definition().layers().stream().anyMatch(layer -> layer.token().isBlank())) {
            logger.info("在线底图渲染结束, rendered=false, reason=missing-token");
            return new BaseMapRenderResult(null, "天地图需要 token，已跳过底图。");
        }

        // 1.4 逐层绘制当前视口覆盖的瓦片。
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int zoom = TileMath.chooseZoom(snapshot.scale(), 0, 18);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(renderTimeoutMillis);
        boolean timedOut = false;
        for (BaseMapLayer layer : selection.definition().layers()) {
            if (drawLayer(graphics, layer, snapshot, width, height, zoom, deadlineNanos)) {
                timedOut = true;
                break;
            }
        }
        graphics.dispose();

        logger.info(() -> "在线底图渲染结束, rendered=true, zoom=" + zoom);
        return new BaseMapRenderResult(output, timedOut ? "底图加载超时，已跳过部分瓦片。" : null);
    }

    /*
     * ========================================================================
     * 步骤2：绘制单个瓦片层
     * ========================================================================
     * 目标：
     *   1) 将屏幕视口转换为瓦片编号范围
     *   2) 逐张读取瓦片并贴到离屏图像
     */
    private boolean drawLayer(
        Graphics2D graphics,
        BaseMapLayer layer,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        int zoom,
        long deadlineNanos
    ) {
        // 2.1 将当前屏幕边界换算为 Web Mercator 全局像素。
        TileMath.GlobalPixel topLeft = TileMath.lonLatToGlobalPixel(snapshot.worldX(0.0), snapshot.worldY(0.0), zoom);
        TileMath.GlobalPixel bottomRight = TileMath.lonLatToGlobalPixel(snapshot.worldX(width), snapshot.worldY(height), zoom);
        TileAddress first = TileMath.globalPixelToAddress(topLeft, zoom);
        TileAddress last = TileMath.globalPixelToAddress(bottomRight, zoom);
        int minX = Math.min(first.x(), last.x());
        int maxX = Math.max(first.x(), last.x());
        int minY = Math.min(first.y(), last.y());
        int maxY = Math.max(first.y(), last.y());

        // 2.2 按图层透明度叠加绘制瓦片。
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) layer.opacity()));
        for (int tileX = minX; tileX <= maxX; tileX++) {
            for (int tileY = minY; tileY <= maxY; tileY++) {
                if (System.nanoTime() >= deadlineNanos) {
                    graphics.setComposite(AlphaComposite.SrcOver);
                    return true;
                }
                TileAddress address = new TileAddress(zoom, tileX, tileY);
                BufferedImage tile = tileClient.fetch(TileUrlTemplate.expand(layer, address));
                if (tile == null) {
                    continue;
                }
                double screenX = tileX * TileMath.TILE_SIZE - topLeft.x();
                double screenY = tileY * TileMath.TILE_SIZE - topLeft.y();
                graphics.drawImage(tile, (int) Math.round(screenX), (int) Math.round(screenY), null);
            }
        }

        // 2.3 恢复默认合成模式，避免影响后续图层。
        graphics.setComposite(AlphaComposite.SrcOver);
        return false;
    }
}
