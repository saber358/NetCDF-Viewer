package com.example.netcdfviewer.render;

import javafx.scene.paint.Color;

public final class ColorMaps {
    private ColorMaps() {
    }

    public static ColorMap jet() {
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
        return interpolated(
            new double[]{0.0, 1.0},
            new Color[]{
                Color.web("#111111"),
                Color.web("#F5F5F5")
            }
        );
    }

    private static ColorMap interpolated(double[] stops, Color[] colors) {
        return normalizedValue -> {
            double clamped = Math.max(0.0, Math.min(1.0, normalizedValue));
            if (clamped <= stops[0]) {
                return colors[0];
            }
            if (clamped >= stops[stops.length - 1]) {
                return colors[colors.length - 1];
            }
            for (int index = 0; index < stops.length - 1; index++) {
                double start = stops[index];
                double end = stops[index + 1];
                if (clamped >= start && clamped <= end) {
                    double factor = (clamped - start) / (end - start);
                    return colors[index].interpolate(colors[index + 1], factor);
                }
            }
            return colors[colors.length - 1];
        };
    }
}
