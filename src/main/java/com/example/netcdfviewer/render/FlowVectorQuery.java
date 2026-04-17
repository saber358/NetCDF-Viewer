package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

import java.util.logging.Logger;

public final class FlowVectorQuery {
    private static final double TOLERANCE = 1e-9;
    private static final Logger logger = Logger.getLogger(FlowVectorQuery.class.getName());

    private FlowVectorQuery() {
        // 工具类不允许被实例化。
    }

    /*
     * ========================================================================
     * 步骤1：查询指定屏幕点的流场向量
     * ========================================================================
     * 目标：
     *   1) 把屏幕点映射回世界坐标
     *   2) 返回该点对应的 u/v/speed 信息
     * 操作要点：
     *   1) 逐个三角形做命中判断
     *   2) 单元场直接取三角形值，节点场按重心坐标插值
     *   3) 命中但值无效时返回 INVALID_VALUE
     */
    public static Result query(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        logger.info(() -> "开始查询流场向量, screenX="
            + screenX
            + ", screenY="
            + screenY
            + ", layerIndex="
            + layerIndex);

        // 1.1 将屏幕坐标反算到世界坐标。
        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);

        // 1.2 顺序扫描三角网，找到命中的三角形并返回对应向量。
        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            Barycentric barycentric = barycentric(mesh, triangle, worldX, worldY);
            if (!barycentric.inside()) {
                continue;
            }
            final int resolvedTriangleIndex = triangleIndex;

            // 1.3 单元中心变量直接读取当前三角形的 u/v。
            if (elementCentered) {
                double u = triangleIndex < uValues.length ? uValues[triangleIndex] : Double.NaN;
                double v = triangleIndex < vValues.length ? vValues[triangleIndex] : Double.NaN;
                if (!RenderMath.isRenderableValue(u, uFillValue) || !RenderMath.isRenderableValue(v, vFillValue)) {
                    logger.info(() -> "流场向量查询结束, triangleIndex=" + resolvedTriangleIndex + ", reason=INVALID_VALUE");
                    return new Result(true, worldX, worldY, triangleIndex, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.INVALID_VALUE);
                }
                double speed = Math.hypot(u, v);
                logger.info(() -> "流场向量查询结束, triangleIndex=" + resolvedTriangleIndex + ", reason=HIT, speed=" + speed);
                return new Result(true, worldX, worldY, triangleIndex, u, v, speed, layerIndex, Reason.HIT);
            }

            // 1.4 节点中心变量先读取三个顶点，再按重心坐标插值。
            double u0 = uValues[triangle[0]];
            double u1 = uValues[triangle[1]];
            double u2 = uValues[triangle[2]];
            double v0 = vValues[triangle[0]];
            double v1 = vValues[triangle[1]];
            double v2 = vValues[triangle[2]];
            if (!RenderMath.isRenderableValue(u0, uFillValue)
                || !RenderMath.isRenderableValue(u1, uFillValue)
                || !RenderMath.isRenderableValue(u2, uFillValue)
                || !RenderMath.isRenderableValue(v0, vFillValue)
                || !RenderMath.isRenderableValue(v1, vFillValue)
                || !RenderMath.isRenderableValue(v2, vFillValue)) {
                logger.info(() -> "流场向量查询结束, triangleIndex=" + resolvedTriangleIndex + ", reason=INVALID_VALUE");
                return new Result(true, worldX, worldY, triangleIndex, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.INVALID_VALUE);
            }

            double u = barycentric.w1() * u0 + barycentric.w2() * u1 + barycentric.w3() * u2;
            double v = barycentric.w1() * v0 + barycentric.w2() * v1 + barycentric.w3() * v2;
            double speed = Math.hypot(u, v);
            logger.info(() -> "流场向量查询结束, triangleIndex=" + resolvedTriangleIndex + ", reason=HIT, speed=" + speed);
            return new Result(true, worldX, worldY, triangleIndex, u, v, speed, layerIndex, Reason.HIT);
        }

        logger.info("流场向量查询结束, reason=NO_HIT");
        return new Result(false, worldX, worldY, -1, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.NO_HIT);
    }

    /*
     * ========================================================================
     * 步骤2：计算三角形重心坐标
     * ========================================================================
     * 目标：
     *   1) 判断目标世界坐标是否位于当前三角形内部
     * 操作要点：
     *   1) 先计算分母避免退化三角形
     *   2) 再用容差判断点是否在三角形内
     */
    private static Barycentric barycentric(MeshData mesh, int[] triangle, double px, double py) {
        // 2.1 读取当前三角形三个顶点坐标。
        double x1 = mesh.x()[triangle[0]];
        double y1 = mesh.y()[triangle[0]];
        double x2 = mesh.x()[triangle[1]];
        double y2 = mesh.y()[triangle[1]];
        double x3 = mesh.x()[triangle[2]];
        double y3 = mesh.y()[triangle[2]];

        // 2.2 先计算分母，退化三角形直接判定为未命中。
        double denominator = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (Math.abs(denominator) <= TOLERANCE) {
            return new Barycentric(Double.NaN, Double.NaN, Double.NaN, false);
        }

        // 2.3 根据标准公式计算三个重心权重。
        double w1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / denominator;
        double w2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / denominator;
        double w3 = 1.0 - w1 - w2;

        // 2.4 允许少量浮点容差，判断该点是否落在三角形内部。
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
        double u,
        double v,
        double speed,
        int layerIndex,
        Reason reason
    ) {
        public boolean hasVelocity() {
            return hit && reason == Reason.HIT && Double.isFinite(u) && Double.isFinite(v);
        }
    }

    private record Barycentric(double w1, double w2, double w3, boolean inside) {
    }
}
