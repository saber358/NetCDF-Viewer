package com.example.netcdfviewer.render;

public final class RenderMath {
    private RenderMath() {
    }

    public static RangeStats computeRange(double[] values, Double fillValue) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int validCount = 0;

        for (double value : values) {
            if (!isRenderableValue(value, fillValue)) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
            validCount++;
        }

        if (validCount == 0) {
            return new RangeStats(0.0, 0.0, 0);
        }
        return new RangeStats(min, max, validCount);
    }

    public static double normalize(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (max <= min) {
            return 0.5;
        }
        double normalized = (value - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    public static boolean isRenderableValue(double value, Double fillValue) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }
        return fillValue == null || Double.compare(value, fillValue) != 0;
    }
}
