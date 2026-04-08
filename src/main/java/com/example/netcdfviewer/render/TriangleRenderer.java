package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public final class TriangleRenderer {
    private static final Color BACKGROUND = Color.web("#F8FBFD");
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
        graphics.setFill(BACKGROUND);
        graphics.fillRect(0, 0, width, height);

        if (mesh == null || rangeStats == null || rangeStats.empty()) {
            return;
        }

        viewportState.ensureFitted(mesh, width, height);
        double[] polygonX = new double[3];
        double[] polygonY = new double[3];
        boolean drawEdges = mesh.triangleCount() < 15000;

        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            Color fill = triangleColor(triangleIndex, triangle, values, colorMap, rangeStats, elementCentered, fillValue);
            if (fill == null) {
                continue;
            }

            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                int nodeIndex = triangle[vertexIndex];
                polygonX[vertexIndex] = viewportState.screenX(mesh.x()[nodeIndex]);
                polygonY[vertexIndex] = viewportState.screenY(mesh.y()[nodeIndex]);
            }

            graphics.setFill(fill);
            graphics.fillPolygon(polygonX, polygonY, 3);
            if (drawEdges) {
                graphics.setStroke(EDGE);
                graphics.strokePolygon(polygonX, polygonY, 3);
            }
        }
    }

    private Color triangleColor(int triangleIndex, int[] triangle, double[] values, ColorMap colorMap, RangeStats rangeStats, boolean elementCentered, Double fillValue) {
        if (elementCentered) {
            if (triangleIndex >= values.length) {
                return null;
            }
            double value = values[triangleIndex];
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                return null;
            }
            return colorMap.colorAt(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()));
        }

        double sum = 0.0;
        int count = 0;
        for (int nodeIndex : triangle) {
            double value = values[nodeIndex];
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                continue;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        double average = sum / count;
        return colorMap.colorAt(RenderMath.normalize(average, rangeStats.min(), rangeStats.max()));
    }
}
