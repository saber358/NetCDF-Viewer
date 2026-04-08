package com.example.netcdfviewer.render;

import javafx.scene.paint.Color;

/**
 * 常用颜色表集合。
 * 当前项目提供 Jet、Viridis 和 Greys 三种默认颜色映射。
 */
public final class ColorMaps {
    private ColorMaps() {
        // 工具类不允许被实例化。
    }

    public static ColorMap jet() {
        // 返回经典的 Jet 颜色表。
        return interpolated(
            new double[]{0.0, 0.35, 0.5, 0.65, 1.0},
            new Color[]{
                Color.web("#00007F"),
                Color.web("#0066FF"),
                Color.web("#00FFFF"),
                Color.web("#FFFF00"),
                Color.web("#7F0000")
            }
        );
    }

    public static ColorMap viridis() {
        // 返回感知均匀性更好的 Viridis 颜色表。
        return interpolated(
            new double[]{0.0, 0.25, 0.5, 0.75, 1.0},
            new Color[]{
                Color.web("#440154"),
                Color.web("#3B528B"),
                Color.web("#21918C"),
                Color.web("#5DC863"),
                Color.web("#FDE725")
            }
        );
    }

    public static ColorMap greys() {
        // 返回灰度颜色表。
        return interpolated(
            new double[]{0.0, 1.0},
            new Color[]{
                Color.web("#111111"),
                Color.web("#F5F5F5")
            }
        );
    }

    private static ColorMap interpolated(double[] stops, Color[] colors) {
        // 构造基于分段线性插值的颜色映射。
        return normalizedValue -> {
            // 先把输入数值限制在合法区间内。
            double clamped = Math.max(0.0, Math.min(1.0, normalizedValue));
            // 小于最小停靠点时直接返回起始颜色。
            if (clamped <= stops[0]) {
                return colors[0];
            }
            // 大于最大停靠点时直接返回末尾颜色。
            if (clamped >= stops[stops.length - 1]) {
                return colors[colors.length - 1];
            }
            // 在每个相邻停靠点之间查找所在区间并计算插值颜色。
            for (int index = 0; index < stops.length - 1; index++) {
                double start = stops[index];
                double end = stops[index + 1];
                if (clamped >= start && clamped <= end) {
                    // 计算当前区间内的比例系数。
                    double factor = (clamped - start) / (end - start);
                    // 用 JavaFX 自带颜色插值功能生成过渡色。
                    return colors[index].interpolate(colors[index + 1], factor);
                }
            }
            // 理论上不会走到这里，作为兜底返回最后一种颜色。
            return colors[colors.length - 1];
        };
    }
}
