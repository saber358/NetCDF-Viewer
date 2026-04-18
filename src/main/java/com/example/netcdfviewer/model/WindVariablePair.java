package com.example.netcdfviewer.model;

import java.util.Objects;
import java.util.logging.Logger;

public record WindVariablePair(
    VariableInfo eastwardVariable,
    VariableInfo northwardVariable
) {
    private static final Logger logger = Logger.getLogger(WindVariablePair.class.getName());

    public WindVariablePair {
        /*
         * ========================================================================
         * 步骤1：校验风场变量配对
         * ========================================================================
         * 目标：
         *   1) 保证风场 eastward / northward 两个分量可作为同一组风场使用
         *   2) 在对象构造阶段尽早拦住不兼容数据
         * 操作要点：
         *   1) 校验可绘制性、几何类型、网格基准和层结构
         *   2) 对三角网要求水平基准完全一致
         */
        logger.info(() -> "开始校验风场变量配对, eastwardVariable="
            + (eastwardVariable == null ? null : eastwardVariable.name())
            + ", northwardVariable="
            + (northwardVariable == null ? null : northwardVariable.name()));

        // 1.1 变量对象不能为空。
        Objects.requireNonNull(eastwardVariable, "eastwardVariable");
        Objects.requireNonNull(northwardVariable, "northwardVariable");

        // 1.2 两个变量都必须可绘制。
        if (!eastwardVariable.plottable() || !northwardVariable.plottable()) {
            throw new IllegalArgumentException("Wind variables must both be plottable.");
        }

        // 1.3 风场两个分量必须来自同一种几何类型。
        if (eastwardVariable.geometryKind() != northwardVariable.geometryKind()) {
            throw new IllegalArgumentException("Wind variables must share the same geometry kind.");
        }

        // 1.4 风场两个分量必须共享同一种节点/单元中心定义。
        if (eastwardVariable.elementCentered() != northwardVariable.elementCentered()) {
            throw new IllegalArgumentException("Wind variables must share the same mesh basis.");
        }

        // 1.5 三角网风场要求主变量共享同一个水平基准。
        if (eastwardVariable.geometryKind() == SpatialDomain.Kind.TRIANGLE_MESH
            && !Objects.equals(eastwardVariable.basisId(), northwardVariable.basisId())) {
            throw new IllegalArgumentException("Triangle wind variables must share the same basis.");
        }

        // 1.6 层结构必须一致。
        if (eastwardVariable.layered() != northwardVariable.layered()) {
            throw new IllegalArgumentException("Wind variables must share the same layered shape.");
        }
        if (eastwardVariable.layered()
            && (!eastwardVariable.layerDimensionName().equals(northwardVariable.layerDimensionName())
            || eastwardVariable.layerCount() != northwardVariable.layerCount())) {
            throw new IllegalArgumentException("Wind variables must share the same layer dimension.");
        }

        logger.info(() -> "风场变量配对校验完成, eastwardVariable="
            + eastwardVariable.name()
            + ", northwardVariable="
            + northwardVariable.name());
    }

    public boolean layered() {
        return eastwardVariable.layered();
    }

    public boolean elementCentered() {
        return eastwardVariable.elementCentered();
    }

    public int resolveLayerIndex(int requestedLayerIndex) {
        if (!layered()) {
            return 0;
        }
        return Math.max(0, Math.min(requestedLayerIndex, eastwardVariable.layerCount() - 1));
    }
}
