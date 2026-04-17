package com.example.netcdfviewer.model;

import java.util.Objects;
import java.util.logging.Logger;

public record WaveVariablePair(
    VariableInfo directionVariable,
    VariableInfo wavelengthVariable
) {
    private static final Logger logger = Logger.getLogger(WaveVariablePair.class.getName());

    public WaveVariablePair {
        /*
         * ========================================================================
         * 步骤1：校验波向和波长变量配对
         * ========================================================================
         * 目标：
         *   1) 保证 wdir 和 wlen 可作为同一组波场变量使用
         *   2) 在对象构造阶段尽早拦住不兼容数据
         * 操作要点：
         *   1) 校验可绘制性、空间轴、网格基准和层结构
         *   2) 不兼容时直接抛出 IllegalArgumentException
         */
        logger.info(() -> "开始校验波场变量配对, directionVariable="
            + (directionVariable == null ? null : directionVariable.name())
            + ", wavelengthVariable="
            + (wavelengthVariable == null ? null : wavelengthVariable.name()));

        // 1.1 校验变量对象不能为空，避免后续属性访问空指针。
        Objects.requireNonNull(directionVariable, "directionVariable");
        Objects.requireNonNull(wavelengthVariable, "wavelengthVariable");

        // 1.2 校验两个变量都能参与平面渲染。
        if (!directionVariable.plottable() || !wavelengthVariable.plottable()) {
            throw new IllegalArgumentException("Wave variables must both be plottable.");
        }

        // 1.3 校验两个变量来自同一种几何类型。
        if (directionVariable.geometryKind() != wavelengthVariable.geometryKind()) {
            throw new IllegalArgumentException("Wave variables must share the same geometry kind.");
        }

        // 1.4 校验两个变量共享同一个空间轴位置。
        if (directionVariable.nodeAxis() != wavelengthVariable.nodeAxis()) {
            throw new IllegalArgumentException("Wave variables must share the same spatial axis.");
        }

        // 1.5 校验两个变量都基于相同的节点/单元中心定义。
        if (directionVariable.elementCentered() != wavelengthVariable.elementCentered()) {
            throw new IllegalArgumentException("Wave variables must share the same mesh basis.");
        }

        // 1.6 波场变量必须共享同一个水平基准。
        if (!Objects.equals(directionVariable.basisId(), wavelengthVariable.basisId())) {
            throw new IllegalArgumentException("Wave variables must share the same basis.");
        }

        // 1.7 校验两个变量的层化结构一致。
        if (directionVariable.layered() != wavelengthVariable.layered()) {
            throw new IllegalArgumentException("Wave variables must share the same layered shape.");
        }

        // 1.8 若是分层变量，则进一步校验层维名称和层数一致。
        if (directionVariable.layered()
            && (!directionVariable.layerDimensionName().equals(wavelengthVariable.layerDimensionName())
            || directionVariable.layerCount() != wavelengthVariable.layerCount())) {
            throw new IllegalArgumentException("Wave variables must share the same layer dimension.");
        }

        logger.info(() -> "波场变量配对校验完成, directionVariable="
            + directionVariable.name()
            + ", wavelengthVariable="
            + wavelengthVariable.name());
    }

    /*
     * ========================================================================
     * 步骤2：判断波场变量是否分层
     * ========================================================================
     * 目标：
     *   1) 给控制器提供简单的层化判断入口
     * 操作要点：
     *   1) 直接复用波向变量的层定义
     */
    public boolean layered() {
        logger.info(() -> "开始判断波场变量是否分层, directionVariable=" + directionVariable.name());

        // 2.1 直接沿用波向变量的分层定义作为配对结果。
        boolean layered = directionVariable.layered();

        logger.info(() -> "波场变量分层判断完成, directionVariable=" + directionVariable.name() + ", layered=" + layered);
        return layered;
    }

    /*
     * ========================================================================
     * 步骤3：判断波场变量网格基准
     * ========================================================================
     * 目标：
     *   1) 给渲染层判断当前配对是节点场还是单元场
     * 操作要点：
     *   1) 直接复用波向变量的网格基准定义
     */
    public boolean elementCentered() {
        logger.info(() -> "开始判断波场变量网格基准, directionVariable=" + directionVariable.name());

        // 3.1 直接沿用波向变量的网格中心定义。
        boolean elementCentered = directionVariable.elementCentered();

        logger.info(() -> "波场变量网格基准判断完成, directionVariable="
            + directionVariable.name()
            + ", elementCentered="
            + elementCentered);
        return elementCentered;
    }

    /*
     * ========================================================================
     * 步骤4：解析可用层号
     * ========================================================================
     * 目标：
     *   1) 把请求层号收敛到当前波场变量支持的范围内
     * 操作要点：
     *   1) 单层变量固定返回 0
     *   2) 分层变量做上下界裁剪
     */
    public int resolveLayerIndex(int requestedLayerIndex) {
        logger.info(() -> "开始解析波场变量层号, directionVariable="
            + directionVariable.name()
            + ", requestedLayerIndex="
            + requestedLayerIndex);

        // 4.1 单层变量固定回到第 0 层。
        if (!layered()) {
            logger.info(() -> "波场变量层号解析完成, directionVariable=" + directionVariable.name() + ", resolvedLayerIndex=0");
            return 0;
        }

        // 4.2 分层变量将请求层号裁剪到合法区间。
        int resolvedLayerIndex = Math.max(0, Math.min(requestedLayerIndex, directionVariable.layerCount() - 1));

        logger.info(() -> "波场变量层号解析完成, directionVariable="
            + directionVariable.name()
            + ", resolvedLayerIndex="
            + resolvedLayerIndex);
        return resolvedLayerIndex;
    }
}
