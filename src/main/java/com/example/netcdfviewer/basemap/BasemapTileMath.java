package com.example.netcdfviewer.basemap;

/**
 * Web Mercator XYZ 瓦片数学工具。
 * 该类只处理经纬度与瓦片索引之间的确定性换算。
 */
public final class BasemapTileMath {
    // Web Mercator 可表达的最大纬度。
    public static final double MAX_MERCATOR_LAT = 85.0511287798066;
    // XYZ 瓦片默认像素尺寸。
    public static final int TILE_SIZE = 256;

    private BasemapTileMath() {
        // 工具类不允许被实例化。
    }

    public static int chooseZoom(double viewportScale, int minZoom, int maxZoom) {
        double safeScale = Math.max(1e-9, viewportScale);
        double rawZoom = Math.log(safeScale * 360.0 / TILE_SIZE) / Math.log(2.0);
        int zoom = (int) Math.round(rawZoom);
        return Math.max(minZoom, Math.min(maxZoom, zoom));
    }

    public static int lonToTileX(double lon, int zoom) {
        int tileCount = tileCount(zoom);
        double normalized = (clampLon(lon) + 180.0) / 360.0;
        int tileX = (int) Math.floor(normalized * tileCount);
        return Math.max(0, Math.min(tileCount - 1, tileX));
    }

    public static int latToTileY(double lat, int zoom) {
        int tileCount = tileCount(zoom);
        int tileY = (int) Math.floor(latToGlobalPixelY(lat, zoom) / TILE_SIZE);
        return Math.max(0, Math.min(tileCount - 1, tileY));
    }

    public static double latToGlobalPixelY(double lat, int zoom) {
        int tileCount = tileCount(zoom);
        double latRad = Math.toRadians(clampLat(lat));
        double mercator = Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad));
        return (1.0 - mercator / Math.PI) * 0.5 * tileCount * TILE_SIZE;
    }

    public static double tileWestLon(int tileX, int zoom) {
        return tileX / (double) tileCount(zoom) * 360.0 - 180.0;
    }

    public static double tileEastLon(int tileX, int zoom) {
        return (tileX + 1.0) / tileCount(zoom) * 360.0 - 180.0;
    }

    public static double tileNorthLat(int tileY, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * tileY / tileCount(zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    public static double tileSouthLat(int tileY, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * (tileY + 1.0) / tileCount(zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    public static int tileCount(int zoom) {
        if (zoom < 0 || zoom > 30) {
            throw new IllegalArgumentException("Unsupported tile zoom: " + zoom);
        }
        return 1 << zoom;
    }

    public static double clampLon(double lon) {
        if (!Double.isFinite(lon)) {
            return 0.0;
        }
        return Math.max(-180.0, Math.min(180.0, lon));
    }

    public static double clampLat(double lat) {
        if (!Double.isFinite(lat)) {
            return 0.0;
        }
        return Math.max(-MAX_MERCATOR_LAT, Math.min(MAX_MERCATOR_LAT, lat));
    }
}
