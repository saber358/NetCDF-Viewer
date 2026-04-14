package com.example.netcdfviewer.overlay;

import java.util.Arrays;

/**
 * 世界坐标中的一条叠加路径。
 * 使用并行坐标数组存储，便于高效绘制。
 */
public record OverlayPath(double[] x, double[] y) {
    public OverlayPath {
        if (x == null || y == null) {
            throw new IllegalArgumentException("Overlay path coordinates must not be null.");
        }
        if (x.length != y.length) {
            throw new IllegalArgumentException("Overlay path coordinate arrays must have the same length.");
        }
        x = Arrays.copyOf(x, x.length);
        y = Arrays.copyOf(y, y.length);
    }

    public int pointCount() {
        return x.length;
    }
}
