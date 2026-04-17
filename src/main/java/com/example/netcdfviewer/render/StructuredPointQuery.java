package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.util.logging.Logger;

/**
 * 标准格网单点查询工具。
 * 负责把屏幕点击位置映射回规则格网，并返回对应节点或单元的数值。
 */
public final class StructuredPointQuery {
    private static final Logger logger = Logger.getLogger(StructuredPointQuery.class.getName());
    private static final double TOLERANCE = 1e-9;

    private StructuredPointQuery() {
        // 工具类不允许实例化。
    }

    /*
     * ========================================================================
     * 步骤1：执行标准格网单点查询
     * ========================================================================
     * 目标：
     *   1) 把屏幕坐标换算成世界坐标
     *   2) 在当前结构化网格中找到命中的节点或单元并返回数值
     * 操作要点：
     *   1) 先校验当前网格是否是 1D 规则轴
     *   2) 节点场按节点控制区域查询，单元场按单元范围查询
     */
    public static Result query(
        StructuredGridDomain domain,
        double[] values,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean cellCentered,
        Double fillValue,
        int layerIndex
    ) {
        logger.info(() -> "开始执行标准格网单点查询, screenX="
            + screenX
            + ", screenY="
            + screenY
            + ", cellCentered="
            + cellCentered
            + ", layerIndex="
            + layerIndex);

        // 1.1 先将点击位置转换成世界坐标，便于后续命中判断。
        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);

        // 1.2 仅支持当前阶段已打通的 1D 规则轴结构化网格。
        if (domain == null || domain.grid() == null || !domain.grid().rectilinear()) {
            logger.info(() -> "标准格网单点查询结束, reason=UNSUPPORTED");
            return new Result(false, worldX, worldY, -1, -1, -1, Double.NaN, layerIndex, Reason.UNSUPPORTED);
        }

        double[] xAxis = domain.grid().xAxis();
        double[] yAxis = domain.grid().yAxis();
        if (xAxis == null || yAxis == null || xAxis.length == 0 || yAxis.length == 0 || values == null || values.length == 0) {
            logger.info(() -> "标准格网单点查询结束, reason=NO_HIT");
            return new Result(false, worldX, worldY, -1, -1, -1, Double.NaN, layerIndex, Reason.NO_HIT);
        }

        // 1.3 单元中心场直接按网格单元范围命中；节点场按节点控制区域命中。
        Result result = cellCentered
            ? queryCellCentered(xAxis, yAxis, values, worldX, worldY, fillValue, layerIndex)
            : queryNodeCentered(xAxis, yAxis, values, worldX, worldY, fillValue, layerIndex);

        logger.info(() -> "标准格网单点查询结束, reason=" + result.reason() + ", value=" + result.value());
        return result;
    }

    /*
     * ========================================================================
     * 步骤2：查询节点中心结构化网格
     * ========================================================================
     * 目标：
     *   1) 找到点击位置所在的节点控制区域
     *   2) 返回该节点对应的原始数值
     * 操作要点：
     *   1) 用相邻节点中点构造控制边界
     *   2) 同时兼容升序和降序轴
     */
    private static Result queryNodeCentered(
        double[] xAxis,
        double[] yAxis,
        double[] values,
        double worldX,
        double worldY,
        Double fillValue,
        int layerIndex
    ) {
        // 2.1 用节点边界划分点击所在的节点控制区。
        double[] xEdges = nodeEdges(xAxis);
        double[] yEdges = nodeEdges(yAxis);
        int columnIndex = findInterval(xEdges, worldX);
        int rowIndex = findInterval(yEdges, worldY);
        if (columnIndex < 0 || rowIndex < 0) {
            return new Result(false, worldX, worldY, -1, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.NO_HIT);
        }

        // 2.2 按行优先顺序定位节点值。
        int sampleIndex = rowIndex * xAxis.length + columnIndex;
        if (sampleIndex < 0 || sampleIndex >= values.length) {
            return new Result(false, worldX, worldY, sampleIndex, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.NO_HIT);
        }
        double value = values[sampleIndex];
        if (!RenderMath.isRenderableValue(value, fillValue)) {
            return new Result(true, worldX, worldY, sampleIndex, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
        }
        return new Result(true, worldX, worldY, sampleIndex, rowIndex, columnIndex, value, layerIndex, Reason.HIT);
    }

    /*
     * ========================================================================
     * 步骤3：查询单元中心结构化网格
     * ========================================================================
     * 目标：
     *   1) 找到点击位置落入的网格单元
     *   2) 返回单元中心字段的原始值
     * 操作要点：
     *   1) 用原始轴段直接做命中判断
     *   2) 对单元外点击直接返回未命中
     */
    private static Result queryCellCentered(
        double[] xAxis,
        double[] yAxis,
        double[] values,
        double worldX,
        double worldY,
        Double fillValue,
        int layerIndex
    ) {
        // 3.1 单元中心值至少需要两个节点才能围出单元。
        if (xAxis.length < 2 || yAxis.length < 2) {
            return new Result(false, worldX, worldY, -1, -1, -1, Double.NaN, layerIndex, Reason.NO_HIT);
        }

        // 3.2 在原始轴段中定位命中的列和行。
        int columnIndex = findInterval(xAxis, worldX);
        int rowIndex = findInterval(yAxis, worldY);
        if (columnIndex < 0 || rowIndex < 0 || columnIndex >= xAxis.length - 1 || rowIndex >= yAxis.length - 1) {
            return new Result(false, worldX, worldY, -1, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.NO_HIT);
        }

        // 3.3 按单元顺序读取单元中心值。
        int sampleIndex = rowIndex * (xAxis.length - 1) + columnIndex;
        if (sampleIndex < 0 || sampleIndex >= values.length) {
            return new Result(false, worldX, worldY, sampleIndex, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.NO_HIT);
        }
        double value = values[sampleIndex];
        if (!RenderMath.isRenderableValue(value, fillValue)) {
            return new Result(true, worldX, worldY, sampleIndex, rowIndex, columnIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
        }
        return new Result(true, worldX, worldY, sampleIndex, rowIndex, columnIndex, value, layerIndex, Reason.HIT);
    }

    /*
     * ========================================================================
     * 步骤4：构造节点控制边界
     * ========================================================================
     * 目标：
     *   1) 把节点轴扩展成控制边界数组
     * 操作要点：
     *   1) 头尾边界按半个网格间距外扩
     *   2) 中间边界取相邻节点中点
     */
    private static double[] nodeEdges(double[] axis) {
        // 4.1 单点轴退化成一个固定宽度控制区。
        if (axis.length == 1) {
            return new double[]{axis[0] - 0.5, axis[0] + 0.5};
        }

        // 4.2 常规轴按相邻节点中点生成边界。
        double[] edges = new double[axis.length + 1];
        edges[0] = axis[0] - (axis[1] - axis[0]) * 0.5;
        for (int index = 1; index < axis.length; index++) {
            edges[index] = (axis[index - 1] + axis[index]) * 0.5;
        }
        edges[axis.length] = axis[axis.length - 1] + (axis[axis.length - 1] - axis[axis.length - 2]) * 0.5;
        return edges;
    }

    /*
     * ========================================================================
     * 步骤5：在升序或降序轴段中定位区间
     * ========================================================================
     * 目标：
     *   1) 找到目标值所在的段索引
     * 操作要点：
     *   1) 每段都按较小值和较大值比较
     *   2) 最后一段包含右端点，避免边界漏判
     */
    private static int findInterval(double[] axis, double value) {
        // 5.1 少于两个点时无法形成有效区间。
        if (axis == null || axis.length < 2 || !Double.isFinite(value)) {
            return -1;
        }

        // 5.2 逐段判断目标值是否落入当前区间。
        for (int index = 0; index < axis.length - 1; index++) {
            double lower = Math.min(axis[index], axis[index + 1]);
            double upper = Math.max(axis[index], axis[index + 1]);
            boolean lastInterval = index == axis.length - 2;
            boolean hit = value >= lower - TOLERANCE
                && (lastInterval ? value <= upper + TOLERANCE : value < upper - TOLERANCE || Math.abs(value - upper) <= TOLERANCE);
            if (hit) {
                return index;
            }
        }
        return -1;
    }

    public enum Reason {
        HIT,
        NO_HIT,
        INVALID_VALUE,
        UNSUPPORTED
    }

    public record Result(
        boolean hit,
        double worldX,
        double worldY,
        int sampleIndex,
        int rowIndex,
        int columnIndex,
        double value,
        int layerIndex,
        Reason reason
    ) {
        public boolean hasValue() {
            return hit && reason == Reason.HIT && Double.isFinite(value);
        }

        public String sampleType(boolean cellCentered) {
            return cellCentered ? "cell" : "node";
        }
    }
}
