package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BasemapRendererTest {
    // 底图渲染测试日志对象。
    private static final Logger logger = Logger.getLogger(BasemapRendererTest.class.getName());

    @Test
    void rendererDrawsVisibleTileBelowDataViewport() {
        BasemapRenderer renderer = new BasemapRenderer(new SolidTileProvider(Color.RED));
        BasemapLayer layer = BasemapLayer.custom("Local", "https://tiles.example.com/{z}/{x}/{y}.png");
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(1.0, 180.0, 90.0);

        BufferedImage image = renderer.render(360, 180, snapshot, layer, 1.0);

        int argb = image.getRGB(180, 90);
        assertTrue(((argb >>> 24) & 0xFF) > 0);
        assertTrue(((argb >>> 16) & 0xFF) > 180);
    }

    @Test
    void rendererReprojectsWebMercatorTileRowsIntoGeographicViewport() {
        /*
         * ========================================================================
         * 步骤1：准备全局经纬度视口和纬线瓦片
         * ========================================================================
         * 目标：
         *   1) 用全局视口复现缩小时的错位
         *   2) 在 Web Mercator 瓦片中标记纬度 60 的像素行
         */
        logger.info("开始准备全局经纬度视口和纬线瓦片...");

        // 1.1 创建全局经纬度视口，x=经度，y=纬度。
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(1.0, 180.0, 90.0);

        // 1.2 创建只在纬度 60 对应 Mercator 行着色的瓦片。
        BasemapRenderer renderer = new BasemapRenderer(new LatitudeLineTileProvider(60.0, Color.RED));
        BasemapLayer layer = BasemapLayer.custom("Local", "https://tiles.example.com/{z}/{x}/{y}.png");

        logger.info("全局经纬度视口和纬线瓦片准备完成。");

        /*
         * ========================================================================
         * 步骤2：渲染底图
         * ========================================================================
         * 目标：
         *   1) 将 Web Mercator 瓦片重投影到经纬度画布
         *   2) 保证瓦片内部纬向位置与 NetCDF / 海岸线一致
         */
        logger.info("开始渲染纬线底图...");

        // 2.1 执行底图渲染。
        BufferedImage image = renderer.render(360, 180, snapshot, layer, 1.0);

        logger.info("纬线底图渲染完成。");

        /*
         * ========================================================================
         * 步骤3：校验纬线位置
         * ========================================================================
         * 目标：
         *   1) 纬度 60 应落在经纬度画布的 y=30 附近
         *   2) 旧的整瓦片线性拉伸会把该行画到 y=54 附近
         */
        logger.info("开始校验纬线位置...");

        // 3.1 读取经纬度投影下纬度 60 的屏幕行。
        int expectedY = (int) Math.round(snapshot.screenY(60.0));

        // 3.2 校验目标行附近已经绘制红色纬线。
        assertTrue(redChannel(image.getRGB(180, expectedY)) > 180
            || redChannel(image.getRGB(180, expectedY - 1)) > 180
            || redChannel(image.getRGB(180, expectedY + 1)) > 180);

        logger.info("纬线位置校验完成。");
    }

    private static int redChannel(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static final class SolidTileProvider implements BasemapTileProvider {
        private final Color color;

        private SolidTileProvider(Color color) {
            this.color = color;
        }

        @Override
        public Optional<BufferedImage> tile(BasemapLayer layer, int zoom, int tileX, int tileY) throws IOException {
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(color);
                graphics.fillRect(0, 0, 256, 256);
            } finally {
                graphics.dispose();
            }
            return Optional.of(image);
        }
    }

    private static final class LatitudeLineTileProvider implements BasemapTileProvider {
        private final double latitude;
        private final Color color;

        private LatitudeLineTileProvider(double latitude, Color color) {
            this.latitude = latitude;
            this.color = color;
        }

        @Override
        public Optional<BufferedImage> tile(BasemapLayer layer, int zoom, int tileX, int tileY) {
            BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(color);
                int lineY = (int) Math.round(BasemapTileMath.latToGlobalPixelY(latitude, zoom)
                    - tileY * BasemapTileMath.TILE_SIZE);
                graphics.drawLine(0, lineY, 255, lineY);
            } finally {
                graphics.dispose();
            }
            return Optional.of(image);
        }
    }
}
