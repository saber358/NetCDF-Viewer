package com.example.netcdfviewer.model;

import java.util.Objects;
import java.util.logging.Logger;

public record VelocityVariablePair(
    VariableInfo eastwardVariable,
    VariableInfo northwardVariable
) {
    private static final Logger logger = Logger.getLogger(VelocityVariablePair.class.getName());

    public VelocityVariablePair {
        /*
         * ========================================================================
         * 步骤1：校验流场速度变量配对
         * ========================================================================
         * 目标：
         *   1) 保证 u/v 或 ua/va 能作为同一组速度场使用
         *   2) 在对象构造时提前拦截不兼容变量
         * 操作要点：
         *   1) 校验可绘制性、空间轴、网格基准和层结构
         *   2) 不兼容时直接抛出 IllegalArgumentException
         */
        logger.info(() -> "开始校验流场速度变量配对, eastwardVariable="
            + (eastwardVariable == null ? null : eastwardVariable.name())
            + ", northwardVariable="
            + (northwardVariable == null ? null : northwardVariable.name()));

        // 1.1 校验变量对象不能为空，避免后续访问空指针。
        Objects.requireNonNull(eastwardVariable, "eastwardVariable");
        Objects.requireNonNull(northwardVariable, "northwardVariable");

        // 1.2 校验两个变量都能参与平面渲染。
        if (!eastwardVariable.plottable() || !northwardVariable.plottable()) {
            throw new IllegalArgumentException("Velocity variables must both be plottable.");
        }

        // 1.3 校验两个变量共享同一个空间轴位置。
        if (eastwardVariable.nodeAxis() != northwardVariable.nodeAxis()) {
            throw new IllegalArgumentException("Velocity variables must share the same spatial axis.");
        }

        // 1.4 校验两个变量基于同一种节点/单元中心定义。
        if (eastwardVariable.elementCentered() != northwardVariable.elementCentered()) {
            throw new IllegalArgumentException("Velocity variables must share the same mesh basis.");
        }

        // 1.5 校验两个变量的层化结构一致。
        if (eastwardVariable.layered() != northwardVariable.layered()) {
            throw new IllegalArgumentException("Velocity variables must share the same layered shape.");
        }

        // 1.6 若是分层变量，则进一步校验层维名称和层数一致。
        if (eastwardVariable.layered()
            && (!eastwardVariable.layerDimensionName().equals(northwardVariable.layerDimensionName())
            || eastwardVariable.layerCount() != northwardVariable.layerCount())) {
            throw new IllegalArgumentException("Velocity variables must share the same layer dimension.");
        }

        logger.info(() -> "流场速度变量配对校验完成, eastwardVariable="
            + eastwardVariable.name()
            + ", northwardVariable="
            + northwardVariable.name());
    }

    /*
     * ========================================================================
     * 步骤2：判断速度变量是否分层
     * ========================================================================
     * 目标：
     *   1) 给控制器提供简单的层化判断入口
     * 操作要点：
     *   1) 直接复用东向速度变量的层定义
     */
    public boolean layered() {
        logger.info(() -> "开始判断速度变量是否分层, eastwardVariable=" + eastwardVariable.name());

        // 2.1 直接沿用东向速度变量的分层定义。
        boolean layered = eastwardVariable.layered();

        logger.info(() -> "速度变量分层判断完成, eastwardVariable=" + eastwardVariable.name() + ", layered=" + layered);
        return layered;
    }

    /*
     * ========================================================================
     * 步骤3：判断速度变量网格基准
     * ========================================================================
     * 目标：
     *   1) 给渲染层判断当前配对是节点场还是单元场
     * 操作要点：
     *   1) 直接复用东向速度变量的网格基准定义
     */
    public boolean elementCentered() {
        logger.info(() -> "开始判断速度变量网格基准, eastwardVariable=" + eastwardVariable.name());

        // 3.1 直接沿用东向速度变量的网格中心定义。
        boolean elementCentered = eastwardVariable.elementCentered();

        logger.info(() -> "速度变量网格基准判断完成, eastwardVariable="
            + eastwardVariable.name()
            + ", elementCentered="
            + elementCentered);
        return elementCentered;
    }

    /*
     * ========================================================================
     * 步骤4：解析可用层号
     * ========================================================================
     * 目标：
     *   1) 把请求层号收敛到当前速度变量支持的范围内
     * 操作要点：
     *   1) 单层变量固定返回 0
     *   2) 分层变量做上下界裁剪
     */
    public int resolveLayerIndex(int requestedLayerIndex) {
        logger.info(() -> "开始解析速度变量层号, eastwardVariable="
            + eastwardVariable.name()
            + ", requestedLayerIndex="
            + requestedLayerIndex);

        // 4.1 单层变量固定回到第 0 层。
        if (!layered()) {
            logger.info(() -> "速度变量层号解析完成, eastwardVariable=" + eastwardVariable.name() + ", resolvedLayerIndex=0");
            return 0;
        }

        // 4.2 分层变量将请求层号裁剪到合法区间。
        int resolvedLayerIndex = Math.max(0, Math.min(requestedLayerIndex, eastwardVariable.layerCount() - 1));

        logger.info(() -> "速度变量层号解析完成, eastwardVariable="
            + eastwardVariable.name()
            + ", resolvedLayerIndex="
            + resolvedLayerIndex);
        return resolvedLayerIndex;
    }
}
