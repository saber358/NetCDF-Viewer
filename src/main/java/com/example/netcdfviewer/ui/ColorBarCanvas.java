package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.render.ColorMap;
import com.example.netcdfviewer.render.RangeStats;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.Locale;

/**
 * 色条画布组件。
 * 该组件用于显示当前颜色表以及最小值、最大值和中间值刻度。
 */
public final class ColorBarCanvas extends Canvas {
    public ColorBarCanvas() {
        // 设置默认色条尺寸。
        super(110, 720);
    }

    public void clear() {
        // 清空整块画布。
        getGraphicsContext2D().clearRect(0, 0, getWidth(), getHeight());
    }

    public void render(ColorMap colorMap, RangeStats rangeStats) {
        // 取得绘图上下文。
        GraphicsContext graphics = getGraphicsContext2D();
        // 每次重绘前先清空旧内容。
        graphics.clearRect(0, 0, getWidth(), getHeight());
        // 如果范围为空，则不绘制色条。
        if (rangeStats == null || rangeStats.empty()) {
            return;
        }

        // 色条左边距。
        double left = 18;
        // 色条顶部边距。
        double top = 20;
        // 色条宽度。
        double barWidth = 24;
        // 色条高度会根据画布高度动态计算。
        double barHeight = Math.min(getHeight() - 40, Math.max(120, getHeight() - 40));

        // 从上到下逐行绘制颜色渐变。
        for (int row = 0; row < (int) barHeight; row++) {
            // 顶部对应最大值，因此这里做一次反向归一化。
            double normalized = 1.0 - (row / Math.max(1.0, barHeight - 1.0));
            graphics.setStroke(colorMap.colorAt(normalized));
            graphics.strokeLine(left, top + row, left + barWidth, top + row);
        }

        // 绘制色条边框。
        graphics.setStroke(Color.web("#374151"));
        graphics.strokeRect(left, top, barWidth, barHeight);
        // 设置文字颜色。
        graphics.setFill(Color.web("#1F2937"));
        // 设置刻度字体。
        graphics.setFont(Font.font(12));
        // 绘制最大值标签。
        graphics.fillText(format(rangeStats.max()), left + barWidth + 10, top + 10);
        // 绘制中间值标签。
        graphics.fillText(format((rangeStats.min() + rangeStats.max()) * 0.5), left + barWidth + 10, top + barHeight * 0.5);
        // 绘制最小值标签。
        graphics.fillText(format(rangeStats.min()), left + barWidth + 10, top + barHeight);
    }

    private String format(double value) {
        // 统一保留 4 位小数显示数值。
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
