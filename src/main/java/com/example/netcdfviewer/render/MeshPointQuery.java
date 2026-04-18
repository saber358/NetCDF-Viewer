package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 三角网单点查询工具。
 * 负责把屏幕点击位置映射回世界坐标，并查询该点对应的网格值。
 */
public final class MeshPointQuery {
    private static final double TOLERANCE = 1e-9;
    private static final Logger logger = Logger.getLogger(MeshPointQuery.class.getName());

    private MeshPointQuery() {
        // 工具类不允许被实例化。
    }

    /*
     * ========================================================================
     * 步骤1：执行三角网单点查询
     * ========================================================================
     * 目标：
     *   1) 将屏幕点映射回世界坐标
     *   2) 借助空间索引缩小候选三角形范围
     * 操作要点：
     *   1) 先取候选桶
     *   2) 再对候选三角形做精确重心判断
     */
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
        return query(
            mesh,
            TriangleSpatialIndexCache.get(mesh),
            values,
            snapshot,
            screenX,
            screenY,
            elementCentered,
            fillValue,
            layerIndex
        );
    }

    /*
     * ========================================================================
     * 步骤2：使用已知空间索引执行三角网单点查询
     * ========================================================================
     * 目标：
     *   1) 在高频采样时复用同一个空间索引对象
     *   2) 避免热点循环里重复访问缓存层
     * 操作要点：
     *   1) 调用方可显式传入索引
     *   2) 查询逻辑与默认入口保持一致
     */
    static Result query(
        MeshData mesh,
        TriangleSpatialIndex spatialIndex,
        double[] values,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean elementCentered,
        Double fillValue,
        int layerIndex
    ) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("开始执行三角网单点查询, screenX="
                + screenX
                + ", screenY="
                + screenY
                + ", layerIndex="
                + layerIndex);
        }

        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);
        List<Integer> candidateTriangles = (spatialIndex == null ? TriangleSpatialIndexCache.get(mesh) : spatialIndex)
            .findCandidateTriangles(worldX, worldY);
        for (int triangleIndex : candidateTriangles) {
            int[] triangle = mesh.triangles()[triangleIndex];
            Barycentric barycentric = barycentric(mesh, triangle, worldX, worldY);
            if (!barycentric.inside()) {
                continue;
            }

            if (elementCentered) {
                double value = triangleIndex < values.length ? values[triangleIndex] : Double.NaN;
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("三角网单点查询结束, triangleIndex=" + triangleIndex + ", reason=INVALID_VALUE");
                    }
                    return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
                }
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("三角网单点查询结束, triangleIndex=" + triangleIndex + ", reason=HIT");
                }
                return new Result(true, worldX, worldY, triangleIndex, value, layerIndex, Reason.HIT);
            }

            double value0 = values[triangle[0]];
            double value1 = values[triangle[1]];
            double value2 = values[triangle[2]];
            if (!RenderMath.isRenderableValue(value0, fillValue)
                || !RenderMath.isRenderableValue(value1, fillValue)
                || !RenderMath.isRenderableValue(value2, fillValue)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("三角网单点查询结束, triangleIndex=" + triangleIndex + ", reason=INVALID_VALUE");
                }
                return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
            }

            double interpolated = barycentric.w1() * value0
                + barycentric.w2() * value1
                + barycentric.w3() * value2;
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("三角网单点查询结束, triangleIndex=" + triangleIndex + ", reason=HIT");
            }
            return new Result(true, worldX, worldY, triangleIndex, interpolated, layerIndex, Reason.HIT);
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("三角网单点查询结束, reason=NO_HIT");
        }
        return new Result(false, worldX, worldY, -1, Double.NaN, layerIndex, Reason.NO_HIT);
    }

    /*
     * ========================================================================
     * 步骤3：计算三角形重心坐标
     * ========================================================================
     * 目标：
     *   1) 用重心坐标判断点是否命中三角形
     * 操作要点：
     *   1) 先计算分母过滤退化三角形
     *   2) 再在容差范围内判断 inside
     */
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
