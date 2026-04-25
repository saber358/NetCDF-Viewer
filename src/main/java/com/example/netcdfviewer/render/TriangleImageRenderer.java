package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 基于 AWT BufferedImage 的三角网离屏渲染器。
 * 这个实现适合在后台线程中执行，以减少 JavaFX 主线程压力。
 */
public final class TriangleImageRenderer {
    public BufferedImage render(
        int width,
        int height,
        MeshData mesh,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean elementCentered,
        Double fillValue
    ) {
        return render(
            width,
            height,
            mesh,
            values,
            colorMap,
            rangeStats,
            snapshot,
            elementCentered,
            fillValue,
            java.awt.Color.decode("#F8FBFD")
        );
    }

    public BufferedImage render(
        int width,
        int height,
        MeshData mesh,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean elementCentered,
        Double fillValue,
        java.awt.Color backgroundColor
    ) {
        // 创建输出图像；宽高至少为 1，避免非法尺寸。
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        // 获取图像绘图上下文。
        Graphics2D graphics = image.createGraphics();
        try {
            // 关闭抗锯齿以优先保证大量三角形绘制性能。
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            // 先按调用方要求填充背景色；透明背景用于底图组合。
            if (backgroundColor != null && backgroundColor.getAlpha() > 0) {
                graphics.setColor(backgroundColor);
                graphics.fillRect(0, 0, width, height);
            }

            // 如果网格或范围无效，则直接返回背景图。
            if (mesh == null || rangeStats == null || rangeStats.empty()) {
                return image;
            }

            // 预构建 256 级调色板，减少循环内的颜色对象创建。
            java.awt.Color[] palette = buildPalette(colorMap);
            // 三角形 X 坐标缓存。
            int[] xPoints = new int[3];
            // 三角形 Y 坐标缓存。
            int[] yPoints = new int[3];
            // 所有节点屏幕 X 坐标缓存。
            int[] screenX = new int[mesh.nodeCount()];
            // 所有节点屏幕 Y 坐标缓存。
            int[] screenY = new int[mesh.nodeCount()];

            // 先把所有节点世界坐标转换成屏幕坐标，减少后续重复计算。
            for (int nodeIndex = 0; nodeIndex < mesh.nodeCount(); nodeIndex++) {
                screenX[nodeIndex] = (int) Math.round(snapshot.screenX(mesh.x()[nodeIndex]));
                screenY[nodeIndex] = (int) Math.round(snapshot.screenY(mesh.y()[nodeIndex]));
            }

            // 逐个三角形进行着色和填充。
            for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
                int[] triangle = mesh.triangles()[triangleIndex];
                // 先计算当前三角形的填充颜色。
                java.awt.Color fill = triangleColor(triangleIndex, triangle, values, rangeStats, elementCentered, fillValue, palette);
                // 如果当前三角形没有有效颜色，则跳过。
                if (fill == null) {
                    continue;
                }
                // 组装三角形三个顶点的屏幕坐标。
                for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                    int nodeIndex = triangle[vertexIndex];
                    xPoints[vertexIndex] = screenX[nodeIndex];
                    yPoints[vertexIndex] = screenY[nodeIndex];
                }
                // 填充当前三角形。
                graphics.setColor(fill);
                graphics.fillPolygon(xPoints, yPoints, 3);
            }
            // 返回最终图像。
            return image;
        } finally {
            // 释放图形上下文资源。
            graphics.dispose();
        }
    }

    private java.awt.Color triangleColor(
        int triangleIndex,
        int[] triangle,
        double[] values,
        RangeStats rangeStats,
        boolean elementCentered,
        Double fillValue,
        java.awt.Color[] palette
    ) {
        // 单元中心变量直接使用三角形索引对应的值。
        if (elementCentered) {
            // 防御式检查，避免访问越界。
            if (triangleIndex >= values.length) {
                return null;
            }
            double value = values[triangleIndex];
            // 当前值不可渲染时直接跳过。
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                return null;
            }
            // 将归一化后的值映射到 256 级调色板。
            return palette[(int) Math.round(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()) * 255.0)];
        }

        // 节点中心变量则对三个顶点值做平均近似。
        double sum = 0.0;
        int count = 0;
        for (int nodeIndex : triangle) {
            double value = values[nodeIndex];
            // 对无效节点值直接跳过，不参与平均。
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                continue;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        // 平均值归一化后映射到调色板。
        double normalized = RenderMath.normalize(sum / count, rangeStats.min(), rangeStats.max());
        return palette[(int) Math.round(normalized * 255.0)];
    }

    private java.awt.Color[] buildPalette(ColorMap colorMap) {
        // 固定生成 256 级颜色，便于快速索引。
        java.awt.Color[] palette = new java.awt.Color[256];
        for (int index = 0; index < palette.length; index++) {
            // 先从 JavaFX 颜色映射中取得颜色。
            javafx.scene.paint.Color fxColor = colorMap.colorAt(index / 255.0);
            // 再转换为 AWT 颜色，供 BufferedImage 绘制使用。
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
