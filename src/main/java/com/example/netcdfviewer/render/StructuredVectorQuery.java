package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.util.logging.Logger;

/**
 * 标准格网向量查询工具。
 * 支持同基准和交错基准的 u/v 在同一屏幕点联合采样。
 */
public final class StructuredVectorQuery {
    private static final Logger logger = Logger.getLogger(StructuredVectorQuery.class.getName());

    private StructuredVectorQuery() {
        // 工具类不允许实例化。
    }

    /*
     * ========================================================================
     * 步骤1：查询结构化网格向量
     * ========================================================================
     * 目标：
     *   1) 在同一屏幕点分别采样 u、v 分量
     *   2) 返回合成后的速度向量和流速
     * 操作要点：
     *   1) u、v 允许来自不同的结构化坐标基准
     *   2) 任一分量未命中或无效时直接返回空结果
     */
    public static Result query(
        StructuredGridDomain uDomain,
        StructuredGridDomain vDomain,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean uCellCentered,
        boolean vCellCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        logger.info(() -> "开始查询结构化网格向量, screenX="
            + screenX
            + ", screenY="
            + screenY
            + ", layerIndex="
            + layerIndex);

        // 1.1 分别查询 u、v 分量在当前屏幕点的数值。
        StructuredPointQuery.Result uResult = StructuredPointQuery.query(
            uDomain,
            uValues,
            snapshot,
            screenX,
            screenY,
            uCellCentered,
            uFillValue,
            layerIndex
        );
        StructuredPointQuery.Result vResult = StructuredPointQuery.query(
            vDomain,
            vValues,
            snapshot,
            screenX,
            screenY,
            vCellCentered,
            vFillValue,
            layerIndex
        );

        // 1.2 任一分量未命中时直接返回未命中结果。
        if (!uResult.hit() || !vResult.hit()) {
            logger.info("结构化网格向量查询结束, reason=NO_HIT");
            return new Result(
                false,
                snapshot.worldX(screenX),
                snapshot.worldY(screenY),
                Double.NaN,
                Double.NaN,
                Double.NaN,
                layerIndex,
                Reason.NO_HIT
            );
        }

        // 1.3 任一分量无有效值时返回无效结果。
        if (!uResult.hasValue() || !vResult.hasValue()) {
            logger.info("结构化网格向量查询结束, reason=INVALID_VALUE");
            return new Result(
                true,
                uResult.worldX(),
                uResult.worldY(),
                Double.NaN,
                Double.NaN,
                Double.NaN,
                layerIndex,
                Reason.INVALID_VALUE
            );
        }

        // 1.4 两个分量都有效时合成速度和流速。
        double u = uResult.value();
        double v = vResult.value();
        double speed = Math.hypot(u, v);
        logger.info(() -> "结构化网格向量查询结束, reason=HIT, speed=" + speed);
        return new Result(true, uResult.worldX(), uResult.worldY(), u, v, speed, layerIndex, Reason.HIT);
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
}
