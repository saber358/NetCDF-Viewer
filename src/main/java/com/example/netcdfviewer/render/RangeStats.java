package com.example.netcdfviewer.render;

/**
 * 数值范围统计结果。
 * 用于保存最小值、最大值和有效值数量。
 */
public record RangeStats(double min, double max, int validCount) {
    public boolean empty() {
        // 有效值数量小于等于 0 时，说明当前层没有可用于渲染的数据。
        return validCount <= 0;
    }

    public double span() {
        // 返回范围跨度，供界面或渲染逻辑使用。
        return max - min;
    }
}
