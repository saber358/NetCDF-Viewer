package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

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
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(java.awt.Color.decode("#F8FBFD"));
            graphics.fillRect(0, 0, width, height);

            if (mesh == null || rangeStats == null || rangeStats.empty()) {
                return image;
            }

            java.awt.Color[] palette = buildPalette(colorMap);
            int[] xPoints = new int[3];
            int[] yPoints = new int[3];
            int[] screenX = new int[mesh.nodeCount()];
            int[] screenY = new int[mesh.nodeCount()];

            for (int nodeIndex = 0; nodeIndex < mesh.nodeCount(); nodeIndex++) {
                screenX[nodeIndex] = (int) Math.round(snapshot.screenX(mesh.x()[nodeIndex]));
                screenY[nodeIndex] = (int) Math.round(snapshot.screenY(mesh.y()[nodeIndex]));
            }

            for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
                int[] triangle = mesh.triangles()[triangleIndex];
                java.awt.Color fill = triangleColor(triangleIndex, triangle, values, rangeStats, elementCentered, fillValue, palette);
                if (fill == null) {
                    continue;
                }
                for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                    int nodeIndex = triangle[vertexIndex];
                    xPoints[vertexIndex] = screenX[nodeIndex];
                    yPoints[vertexIndex] = screenY[nodeIndex];
                }
                graphics.setColor(fill);
                graphics.fillPolygon(xPoints, yPoints, 3);
            }
            return image;
        } finally {
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
        if (elementCentered) {
            if (triangleIndex >= values.length) {
                return null;
            }
            double value = values[triangleIndex];
            if (!RenderMath.isRenderableValue(value, fillValue)) {
                return null;
            }
            return palette[(int) Math.round(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()) * 255.0)];
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
        double normalized = RenderMath.normalize(sum / count, rangeStats.min(), rangeStats.max());
        return palette[(int) Math.round(normalized * 255.0)];
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
