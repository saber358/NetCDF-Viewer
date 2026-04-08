package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.render.ColorMap;
import com.example.netcdfviewer.render.RangeStats;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.Locale;

public final class ColorBarCanvas extends Canvas {
    public ColorBarCanvas() {
        super(110, 720);
    }

    public void clear() {
        getGraphicsContext2D().clearRect(0, 0, getWidth(), getHeight());
    }

    public void render(ColorMap colorMap, RangeStats rangeStats) {
        GraphicsContext graphics = getGraphicsContext2D();
        graphics.clearRect(0, 0, getWidth(), getHeight());
        if (rangeStats == null || rangeStats.empty()) {
            return;
        }

        double left = 18;
        double top = 20;
        double barWidth = 24;
        double barHeight = Math.min(getHeight() - 40, Math.max(120, getHeight() - 40));

        for (int row = 0; row < (int) barHeight; row++) {
            double normalized = 1.0 - (row / Math.max(1.0, barHeight - 1.0));
            graphics.setStroke(colorMap.colorAt(normalized));
            graphics.strokeLine(left, top + row, left + barWidth, top + row);
        }

        graphics.setStroke(Color.web("#374151"));
        graphics.strokeRect(left, top, barWidth, barHeight);
        graphics.setFill(Color.web("#1F2937"));
        graphics.setFont(Font.font(12));
        graphics.fillText(format(rangeStats.max()), left + barWidth + 10, top + 10);
        graphics.fillText(format((rangeStats.min() + rangeStats.max()) * 0.5), left + barWidth + 10, top + barHeight * 0.5);
        graphics.fillText(format(rangeStats.min()), left + barWidth + 10, top + barHeight);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
