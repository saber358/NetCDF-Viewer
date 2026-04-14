package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

/**
 * 三角网单点查询工具。
 * 负责把屏幕点击位置映射回世界坐标，并查询该点对应的网格值。
 */
public final class MeshPointQuery {
    private static final double TOLERANCE = 1e-9;

    private MeshPointQuery() {
        // 工具类不允许被实例化。
    }

    public static Result query(
        MeshData mesh,
        double[] values,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean elementCentered,
        Double fillValue,
        int layerIndex
    ) {
        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);
        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            Barycentric barycentric = barycentric(mesh, triangle, worldX, worldY);
            if (!barycentric.inside()) {
                continue;
            }

            if (elementCentered) {
                double value = triangleIndex < values.length ? values[triangleIndex] : Double.NaN;
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
                }
                return new Result(true, worldX, worldY, triangleIndex, value, layerIndex, Reason.HIT);
            }

            double value0 = values[triangle[0]];
            double value1 = values[triangle[1]];
            double value2 = values[triangle[2]];
            if (!RenderMath.isRenderableValue(value0, fillValue)
                || !RenderMath.isRenderableValue(value1, fillValue)
                || !RenderMath.isRenderableValue(value2, fillValue)) {
                return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
            }

            double interpolated = barycentric.w1() * value0
                + barycentric.w2() * value1
                + barycentric.w3() * value2;
            return new Result(true, worldX, worldY, triangleIndex, interpolated, layerIndex, Reason.HIT);
        }
        return new Result(false, worldX, worldY, -1, Double.NaN, layerIndex, Reason.NO_HIT);
    }

    private static Barycentric barycentric(MeshData mesh, int[] triangle, double px, double py) {
        double x1 = mesh.x()[triangle[0]];
        double y1 = mesh.y()[triangle[0]];
        double x2 = mesh.x()[triangle[1]];
        double y2 = mesh.y()[triangle[1]];
        double x3 = mesh.x()[triangle[2]];
        double y3 = mesh.y()[triangle[2]];
        double denominator = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (Math.abs(denominator) <= TOLERANCE) {
            return new Barycentric(Double.NaN, Double.NaN, Double.NaN, false);
        }
        double w1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / denominator;
        double w2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / denominator;
        double w3 = 1.0 - w1 - w2;
        boolean inside = w1 >= -TOLERANCE
            && w2 >= -TOLERANCE
            && w3 >= -TOLERANCE
            && w1 <= 1.0 + TOLERANCE
            && w2 <= 1.0 + TOLERANCE
            && w3 <= 1.0 + TOLERANCE;
        return new Barycentric(w1, w2, w3, inside);
    }

    public enum Reason {
        HIT,
        NO_HIT,
        INVALID_VALUE
    }

    public record Result(
        boolean hit,
        double worldX,
        double worldY,
        int triangleIndex,
        double value,
        int layerIndex,
        Reason reason
    ) {
        public boolean hasValue() {
            return hit && reason == Reason.HIT && Double.isFinite(value);
        }
    }

    private record Barycentric(double w1, double w2, double w3, boolean inside) {
    }
}
