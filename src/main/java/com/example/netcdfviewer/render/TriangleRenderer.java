package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 直接绘制到 JavaFX Canvas 的三角网渲染器。
 * 当前主要用于较轻量的同步绘制场景。
 */
public final class TriangleRenderer {
    // 主绘图区域背景色。
    private static final Color BACKGROUND = Color.web("#F8FBFD");
    // 小网格场景下的边线颜色。
    private static final Color EDGE = Color.rgb(35, 48, 68, 0.15);

    public void render(
        GraphicsContext graphics,
        double width,
        double height,
        MeshData mesh,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState viewportState,
        boolean elementCentered,
        Double fillValue
    ) {
        // 先清空并填充背景。
        graphics.setFill(BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        // 如果网格或范围无效，则不继续绘制。
        if (mesh == null || rangeStats == null || rangeStats.empty()) {
            return;
        }

        // 确保视口已经适配当前网格和画布大小。
        viewportState.ensureFitted(mesh, width, height);
        // 三角形三个顶点的 X 坐标缓存。
        double[] polygonX = new double[3];
        // 三角形三个顶点的 Y 坐标缓存。
        double[] polygonY = new double[3];
        // 小网格时绘制边线，大网格时只绘制填充以提升性能。
        boolean drawEdges = mesh.triangleCount() < 15000;

        // 逐个三角形进行绘制。
        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            // 先计算当前三角形对应的颜色。
            Color fill = triangleColor(triangleIndex, triangle, values, colorMap, rangeStats, elementCentered, fillValue);
            // 如果颜色为空，说明当前三角形没有可用值。
            if (fill == null) {
                continue;
            }

            // 计算当前三角形三个顶点的屏幕坐标。
            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                int nodeIndex = triangle[vertexIndex];
                polygonX[vertexIndex] = viewportState.screenX(mesh.x()[nodeIndex]);
                polygonY[vertexIndex] = viewportState.screenY(mesh.y()[nodeIndex]);
            }

            // 填充三角形。
            graphics.setFill(fill);
            graphics.fillPolygon(polygonX, polygonY, 3);
            // 仅在小网格下绘制边线。
            if (drawEdges) {
                graphics.setStroke(EDGE);
                graphics.strokePolygon(polygonX, polygonY, 3);
            }
        }
    }

    private Color triangleColor(int triangleIndex, int[] triangle, double[] values, ColorMap colorMap, RangeStats rangeStats, boolean elementCentered, Double fillValue) {
        // 单元中心变量直接按当前三角形索引取值。
        if (elementCentered) {
            if (triangleIndex >= values.length) {
                return null;
            }
            double value = values[triangleIndex];
            // 无效值直接跳过。
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                return null;
            }
            // 将单元值归一化后映射为颜色。
            return colorMap.colorAt(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()));
        }

        // 节点中心变量按三个顶点求平均色值。
        double sum = 0.0;
        int count = 0;
        for (int nodeIndex : triangle) {
            double value = values[nodeIndex];
            // 对无效值直接跳过。
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                continue;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        // 用平均值决定当前三角形颜色。
        double average = sum / count;
        return colorMap.colorAt(RenderMath.normalize(average, rangeStats.min(), rangeStats.max()));
    }
}
