package com.example.netcdfviewer.model;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public record WaveVariablePair(
    VariableInfo directionVariable,
    VariableInfo wavelengthVariable,
    boolean vectorMode,
    VariableInfo waveHeightVariable
) {
    private static final Logger logger = Logger.getLogger(WaveVariablePair.class.getName());

    public WaveVariablePair(VariableInfo directionVariable, VariableInfo wavelengthVariable) {
        this(directionVariable, wavelengthVariable, false, null);
    }

    public WaveVariablePair {
        /*
         * ========================================================================
         * 步骤1：校验波场变量配对
         * ========================================================================
         * 目标：
         *   1) 同时支持极坐标模式 `wdir/wlen` 与向量模式 `uWave/vWave`
         *   2) 在对象构造阶段尽早拦住不兼容数据
         * 操作要点：
         *   1) 先校验主变量是否可绘制且几何类型一致
         *   2) 再按模式分别校验基准、层结构和可选波高变量
         */
        logger.info(() -> "开始校验波场变量配对, directionVariable="
            + (directionVariable == null ? null : directionVariable.name())
            + ", wavelengthVariable="
            + (wavelengthVariable == null ? null : wavelengthVariable.name())
            + ", vectorMode="
            + vectorMode
            + ", waveHeightVariable="
            + (waveHeightVariable == null ? null : waveHeightVariable.name()));

        // 1.1 主变量不能为空，避免后续访问空指针。
        Objects.requireNonNull(directionVariable, "directionVariable");
        Objects.requireNonNull(wavelengthVariable, "wavelengthVariable");

        // 1.2 两个主变量都必须可绘制。
        if (!directionVariable.plottable() || !wavelengthVariable.plottable()) {
            throw new IllegalArgumentException("Wave variables must both be plottable.");
        }

        // 1.3 两个主变量必须来自同一种几何类型。
        if (directionVariable.geometryKind() != wavelengthVariable.geometryKind()) {
            throw new IllegalArgumentException("Wave variables must share the same geometry kind.");
        }

        // 1.4 若提供波高变量，也必须可绘制且几何类型一致。
        if (waveHeightVariable != null) {
            if (!waveHeightVariable.plottable()) {
                throw new IllegalArgumentException("Wave height variable must be plottable.");
            }
            if (waveHeightVariable.geometryKind() != directionVariable.geometryKind()) {
                throw new IllegalArgumentException("Wave height variable must share the same geometry kind.");
            }
        }

        // 1.5 向量模式只允许当前已支持的结构化网格路径。
        if (vectorMode) {
            validateVectorWavePair(directionVariable, wavelengthVariable, waveHeightVariable);
        } else {
            validatePolarWavePair(directionVariable, wavelengthVariable, waveHeightVariable);
        }

        logger.info(() -> "波场变量配对校验完成, directionVariable="
            + directionVariable.name()
            + ", wavelengthVariable="
            + wavelengthVariable.name()
            + ", vectorMode="
            + vectorMode);
    }

    /*
     * ========================================================================
     * 步骤2：校验极坐标波场配对
     * ========================================================================
     * 目标：
     *   1) 保持 `wdir/wlen` 现有行为不变
     * 操作要点：
     *   1) 要求空间轴、网格基准和水平基准完全一致
     *   2) 可选波高变量只校验层结构，不参与主配对判断
     */
    private static void validatePolarWavePair(
        VariableInfo directionVariable,
        VariableInfo wavelengthVariable,
        VariableInfo waveHeightVariable
    ) {
        // 2.1 极坐标模式要求两个主变量共享同一个空间轴位置。
        if (directionVariable.nodeAxis() != wavelengthVariable.nodeAxis()) {
            throw new IllegalArgumentException("Wave variables must share the same spatial axis.");
        }

        // 2.2 两个主变量都必须基于相同的节点/单元中心定义。
        if (directionVariable.elementCentered() != wavelengthVariable.elementCentered()) {
            throw new IllegalArgumentException("Wave variables must share the same mesh basis.");
        }

        // 2.3 极坐标模式要求共享同一个水平基准。
        if (!Objects.equals(directionVariable.basisId(), wavelengthVariable.basisId())) {
            throw new IllegalArgumentException("Wave variables must share the same basis.");
        }

        // 2.4 主变量层结构必须一致。
        validateLayerCompatibility(directionVariable, wavelengthVariable);

        // 2.5 若附带波高变量，则允许单独存在，但层结构需要兼容。
        if (waveHeightVariable != null) {
            validateOptionalWaveHeight(directionVariable, waveHeightVariable);
        }
    }

    /*
     * ========================================================================
     * 步骤3：校验向量波场配对
     * ========================================================================
     * 目标：
     *   1) 支持 `uWave/vWave` 结构化浪场
     * 操作要点：
     *   1) 允许主变量使用不同交错基准
     *   2) 只要求几何类型、层结构和节点/单元中心定义兼容
     */
    private static void validateVectorWavePair(
        VariableInfo xComponentVariable,
        VariableInfo yComponentVariable,
        VariableInfo waveHeightVariable
    ) {
        // 3.1 当前向量波场只支持结构化网格。
        if (xComponentVariable.geometryKind() != SpatialDomain.Kind.STRUCTURED_GRID) {
            throw new IllegalArgumentException("Vector wave variables currently require structured-grid geometry.");
        }

        // 3.2 两个分量都必须共享同一种节点/单元中心定义。
        if (xComponentVariable.elementCentered() != yComponentVariable.elementCentered()) {
            throw new IllegalArgumentException("Vector wave variables must share the same mesh basis.");
        }

        // 3.3 主变量层结构必须一致。
        validateLayerCompatibility(xComponentVariable, yComponentVariable);

        // 3.4 若提供波高变量，则只要求层结构兼容。
        if (waveHeightVariable != null) {
            validateOptionalWaveHeight(xComponentVariable, waveHeightVariable);
        }
    }

    /*
     * ========================================================================
     * 步骤4：校验层结构兼容性
     * ========================================================================
     * 目标：
     *   1) 保证两个变量能用同一个层号读取
     * 操作要点：
     *   1) 都分层或都不分层
     *   2) 若分层则层维名称和层数一致
     */
    private static void validateLayerCompatibility(VariableInfo left, VariableInfo right) {
        // 4.1 分层状态必须一致。
        if (left.layered() != right.layered()) {
            throw new IllegalArgumentException("Wave variables must share the same layered shape.");
        }

        // 4.2 分层变量还要共享层维名称和层数。
        if (left.layered()
            && (!left.layerDimensionName().equals(right.layerDimensionName())
            || left.layerCount() != right.layerCount())) {
            throw new IllegalArgumentException("Wave variables must share the same layer dimension.");
        }
    }

    /*
     * ========================================================================
     * 步骤5：校验可选波高变量
     * ========================================================================
     * 目标：
     *   1) 让 `Hwave/hWave` 只作为长度控制辅助字段
     * 操作要点：
     *   1) 单层波高允许与单层主变量直接配合
     *   2) 若分层则必须和主变量共享层结构
     */
    private static void validateOptionalWaveHeight(VariableInfo mainVariable, VariableInfo waveHeightVariable) {
        // 5.1 主变量分层时，波高变量若也分层则必须兼容同一层结构。
        if (waveHeightVariable.layered()) {
            validateLayerCompatibility(mainVariable, waveHeightVariable);
            return;
        }

        // 5.2 主变量非分层时，单层波高可以直接复用。
        if (!mainVariable.layered()) {
            return;
        }

        throw new IllegalArgumentException("Wave height variable must share the same layer dimension when the main wave variables are layered.");
    }

    /*
     * ========================================================================
     * 步骤6：判断波场变量是否分层
     * ========================================================================
     * 目标：
     *   1) 给控制器提供简单的层化判断入口
     * 操作要点：
     *   1) 直接复用主变量的层定义
     */
    public boolean layered() {
        logger.info(() -> "开始判断波场变量是否分层, directionVariable=" + directionVariable.name());

        // 6.1 直接沿用主变量的分层定义作为配对结果。
        boolean layered = directionVariable.layered();

        logger.info(() -> "波场变量分层判断完成, directionVariable=" + directionVariable.name() + ", layered=" + layered);
        return layered;
    }

    /*
     * ========================================================================
     * 步骤7：判断波场变量网格基准
     * ========================================================================
     * 目标：
     *   1) 给渲染层判断当前配对是节点场还是单元场
     * 操作要点：
     *   1) 直接复用主变量的网格基准定义
     */
    public boolean elementCentered() {
        logger.info(() -> "开始判断波场变量网格基准, directionVariable=" + directionVariable.name());

        // 7.1 直接沿用主变量的网格中心定义。
        boolean elementCentered = directionVariable.elementCentered();

        logger.info(() -> "波场变量网格基准判断完成, directionVariable="
            + directionVariable.name()
            + ", elementCentered="
            + elementCentered);
        return elementCentered;
    }

    /*
     * ========================================================================
     * 步骤8：暴露可选波高变量
     * ========================================================================
     * 目标：
     *   1) 让控制器按需读取 `Hwave/hWave`
     * 操作要点：
     *   1) 统一通过 Optional 返回，避免空指针
     */
    public Optional<VariableInfo> optionalWaveHeightVariable() {
        logger.info(() -> "开始获取可选波高变量, directionVariable=" + directionVariable.name());

        // 8.1 用 Optional 包装可选波高变量。
        Optional<VariableInfo> waveHeight = Optional.ofNullable(waveHeightVariable);

        logger.info(() -> "获取可选波高变量完成, directionVariable="
            + directionVariable.name()
            + ", hasWaveHeight="
            + waveHeight.isPresent());
        return waveHeight;
    }

    /*
     * ========================================================================
     * 步骤9：解析可用层号
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

        // 9.1 单层变量固定回到第 0 层。
        if (!layered()) {
            logger.info(() -> "波场变量层号解析完成, directionVariable=" + directionVariable.name() + ", resolvedLayerIndex=0");
            return 0;
        }

        // 9.2 分层变量将请求层号裁剪到合法区间。
        int resolvedLayerIndex = Math.max(0, Math.min(requestedLayerIndex, directionVariable.layerCount() - 1));

        logger.info(() -> "波场变量层号解析完成, directionVariable="
            + directionVariable.name()
            + ", resolvedLayerIndex="
            + resolvedLayerIndex);
        return resolvedLayerIndex;
    }
}
