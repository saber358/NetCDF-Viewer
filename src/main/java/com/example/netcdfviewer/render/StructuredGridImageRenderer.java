package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 标准格网离屏渲染器。
 * 当前版本先支持 1D 规则轴标准格网，并按节点中心值绘制近似网格单元。
 */
public final class StructuredGridImageRenderer {
    public BufferedImage render(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue
    ) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(java.awt.Color.decode("#F8FBFD"));
            graphics.fillRect(0, 0, width, height);

            if (domain == null || rangeStats == null || rangeStats.empty()) {
                return image;
            }
            if (!domain.grid().rectilinear()) {
                return image;
            }

            double[] xAxis = domain.grid().xAxis();
            double[] yAxis = domain.grid().yAxis();
            if (xAxis == null || yAxis == null || xAxis.length == 0 || yAxis.length == 0 || values == null || values.length == 0) {
                return image;
            }

            java.awt.Color[] palette = buildPalette(colorMap);
            if (cellCentered && values.length == Math.max(0, xAxis.length - 1) * Math.max(0, yAxis.length - 1)) {
                renderCellCentered(graphics, xAxis, yAxis, values, rangeStats, fillValue, palette, snapshot);
            } else {
                renderNodeCentered(graphics, xAxis, yAxis, values, rangeStats, fillValue, palette, snapshot);
            }
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private void renderNodeCentered(
        Graphics2D graphics,
        double[] xAxis,
        double[] yAxis,
        double[] values,
        RangeStats rangeStats,
        Double fillValue,
        java.awt.Color[] palette,
        ViewportState.Snapshot snapshot
    ) {
        double[] xEdges = nodeEdges(xAxis);
        double[] yEdges = nodeEdges(yAxis);
        int width = xAxis.length;
        int height = yAxis.length;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int valueIndex = y * width + x;
                if (valueIndex >= values.length) {
                    return;
                }
                double value = values[valueIndex];
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    continue;
                }
                java.awt.Color fill = palette[(int) Math.round(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()) * 255.0)];
                fillRect(
                    graphics,
                    snapshot,
                    xEdges[x],
                    xEdges[x + 1],
                    yEdges[y],
                    yEdges[y + 1],
                    fill
                );
            }
        }
    }

    private void renderCellCentered(
        Graphics2D graphics,
        double[] xAxis,
        double[] yAxis,
        double[] values,
        RangeStats rangeStats,
        Double fillValue,
        java.awt.Color[] palette,
        ViewportState.Snapshot snapshot
    ) {
        int valueIndex = 0;
        for (int y = 0; y < yAxis.length - 1; y++) {
            for (int x = 0; x < xAxis.length - 1; x++) {
                if (valueIndex >= values.length) {
                    return;
                }
                double value = values[valueIndex++];
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    continue;
                }
                java.awt.Color fill = palette[(int) Math.round(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()) * 255.0)];
                fillRect(
                    graphics,
                    snapshot,
                    xAxis[x],
                    xAxis[x + 1],
                    yAxis[y],
                    yAxis[y + 1],
                    fill
                );
            }
        }
    }

    private void fillRect(
        Graphics2D graphics,
        ViewportState.Snapshot snapshot,
        double minX,
        double maxX,
        double minY,
        double maxY,
        java.awt.Color fill
    ) {
        double worldLeft = Math.min(minX, maxX);
        double worldRight = Math.max(minX, maxX);
        double worldBottom = Math.min(minY, maxY);
        double worldTop = Math.max(minY, maxY);
        int left = (int) Math.floor(snapshot.screenX(worldLeft));
        int right = (int) Math.ceil(snapshot.screenX(worldRight));
        int top = (int) Math.floor(snapshot.screenY(worldTop));
        int bottom = (int) Math.ceil(snapshot.screenY(worldBottom));
        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);
        graphics.setColor(fill);
        graphics.fillRect(left, top, width, height);
    }

    private double[] nodeEdges(double[] axis) {
        double[] edges = new double[axis.length + 1];
        if (axis.length == 1) {
            edges[0] = axis[0] - 0.5;
            edges[1] = axis[0] + 0.5;
            return edges;
        }

        edges[0] = axis[0] - (axis[1] - axis[0]) * 0.5;
        for (int index = 1; index < axis.length; index++) {
            edges[index] = (axis[index - 1] + axis[index]) * 0.5;
        }
        edges[axis.length] = axis[axis.length - 1] + (axis[axis.length - 1] - axis[axis.length - 2]) * 0.5;
        return edges;
    }

    private java.awt.Color[] buildPalette(ColorMap colorMap) {
        java.awt.Color[] palette = new java.awt.Color[256];
        for (int index = 0; index < palette.length; index++) {
            javafx.scene.paint.Color fxColor = colorMap.colorAt(index / 255.0);
            palette[index] = new java.awt.Color(
                (float) fxColor.getRed(),
                (float) fxColor.getGreen(),
                (float) fxColor.getBlue(),
                (float) fxColor.getOpacity()
            );
        }
        return palette;
    }
}
