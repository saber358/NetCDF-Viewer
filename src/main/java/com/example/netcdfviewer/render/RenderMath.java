package com.example.netcdfviewer.render;

/**
 * 渲染相关数学工具。
 * 这里集中处理数值范围计算、归一化和有效值判断。
 */
public final class RenderMath {
    private RenderMath() {
        // 工具类不允许被实例化。
    }

    public static RangeStats computeRange(double[] values, Double fillValue) {
        // 初始化范围上下界。
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        // 统计有效值数量。
        int validCount = 0;

        // 遍历所有值，跳过 NaN、无穷大和填充值。
        for (double value : values) {
            if (!isRenderableValue(value, fillValue)) {
                continue;
            }
            // 更新最小值。
            min = Math.min(min, value);
            // 更新最大值。
            max = Math.max(max, value);
            // 记录有效值数量。
            validCount++;
        }

        // 如果没有有效值，则返回空范围。
        if (validCount == 0) {
            return new RangeStats(0.0, 0.0, 0);
        }
        // 返回最终范围统计。
        return new RangeStats(min, max, validCount);
    }

    public static double normalize(double value, double min, double max) {
        // NaN 无法参与颜色映射，直接按 0 处理。
        if (Double.isNaN(value)) {
            return 0.0;
        }
        // 当范围退化时返回中间值，避免除零问题。
        if (max <= min) {
            return 0.5;
        }
        // 先计算线性归一化结果。
        double normalized = (value - min) / (max - min);
        // 再限制在 0 到 1 之间，避免颜色索引越界。
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    public static boolean isRenderableValue(double value, Double fillValue) {
        // NaN 和无穷大都不允许参与渲染。
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return false;
        }
        // 如果没有填充值定义，则只要不是异常数值就视为有效值。
        return fillValue == null || Double.compare(value, fillValue) != 0;
    }
}
