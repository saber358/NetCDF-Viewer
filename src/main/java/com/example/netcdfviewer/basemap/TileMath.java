package com.example.netcdfviewer.basemap;

public final class TileMath {
    public static final int TILE_SIZE = 256;
    public static final double WEB_MERCATOR_MAX_LATITUDE = 85.05112878;

    private TileMath() {
    }

    public static boolean isGeographicBounds(double minX, double maxX, double minY, double maxY) {
        return Double.isFinite(minX)
            && Double.isFinite(maxX)
            && Double.isFinite(minY)
            && Double.isFinite(maxY)
            && minX >= -180.0
            && maxX <= 180.0
            && minY >= -WEB_MERCATOR_MAX_LATITUDE
            && maxY <= WEB_MERCATOR_MAX_LATITUDE
            && maxX > minX
            && maxY > minY;
    }

    public static int chooseZoom(double viewportScale, int minZoom, int maxZoom) {
        if (!Double.isFinite(viewportScale) || viewportScale <= 0.0) {
            return minZoom;
        }
        int zoom = (int) Math.round(Math.log(viewportScale) / Math.log(2.0)) + 1;
        return Math.max(minZoom, Math.min(maxZoom, zoom));
    }

    public static GlobalPixel lonLatToGlobalPixel(double lon, double lat, int zoom) {
        double clippedLat = Math.max(-WEB_MERCATOR_MAX_LATITUDE, Math.min(WEB_MERCATOR_MAX_LATITUDE, lat));
        double sinLat = Math.sin(Math.toRadians(clippedLat));
        double mapSize = mapSize(zoom);
        double x = (lon + 180.0) / 360.0 * mapSize;
        double y = (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * mapSize;
        return new GlobalPixel(x, y);
    }

    public static TileAddress globalPixelToAddress(GlobalPixel pixel, int zoom) {
        int maxTile = (1 << zoom) - 1;
        int x = Math.max(0, Math.min(maxTile, (int) Math.floor(pixel.x() / TILE_SIZE)));
        int y = Math.max(0, Math.min(maxTile, (int) Math.floor(pixel.y() / TILE_SIZE)));
        return new TileAddress(zoom, x, y);
    }

    public static double mapSize(int zoom) {
        return (double) TILE_SIZE * (1 << zoom);
    }

    public record GlobalPixel(double x, double y) {
    }
}
