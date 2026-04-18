package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WaveVariablePair;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public final class WaveVariablePairFinder {
    private static final Logger logger = Logger.getLogger(WaveVariablePairFinder.class.getName());

    /*
     * ========================================================================
     * 步骤1：从数据集对象中查找波场变量配对
     * ========================================================================
     * 目标：
     *   1) 给控制器提供基于 ParsedDataset 的统一入口
     * 操作要点：
     *   1) 直接复用变量列表查找逻辑
     */
    public Optional<WaveVariablePair> find(ParsedDataset dataset) {
        logger.info(() -> "开始从数据集查找波场变量配对, sourcePath=" + (dataset == null ? null : dataset.sourcePath()));

        // 1.1 将数据集变量列表交给统一查找逻辑处理。
        Optional<WaveVariablePair> pair = dataset == null ? Optional.empty() : find(dataset.variables());

        logger.info(() -> "数据集波场变量配对查找完成, sourcePath="
            + (dataset == null ? null : dataset.sourcePath())
            + ", found="
            + pair.isPresent());
        return pair;
    }

    /*
     * ========================================================================
     * 步骤2：从变量列表中查找波场变量配对
     * ========================================================================
     * 目标：
     *   1) 在变量列表中识别精确命名的 wdir / wlen
     *   2) 只返回结构兼容的变量配对
     * 操作要点：
     *   1) 先按精确名查找
     *   2) 再校验空间轴、网格基准和层结构
     */
    Optional<WaveVariablePair> find(List<VariableInfo> variables) {
        logger.info(() -> "开始从变量列表查找波场变量配对, variableCount=" + (variables == null ? null : variables.size()));

        // 2.1 输入为空时直接返回空结果。
        if (variables == null || variables.isEmpty()) {
            logger.info("变量列表为空，波场变量配对查找结束");
            return Optional.empty();
        }

        // 2.2 先尝试传统 `wdir/wlen` 极坐标波场。
        Optional<WaveVariablePair> polarPair = findPolarPair(variables);
        if (polarPair.isPresent()) {
            logger.info("变量列表波场变量配对查找完成, matchedPair=wdir/wlen");
            return polarPair;
        }

        // 2.3 极坐标模式未命中时，回退尝试 `uWave/vWave` 向量波场。
        Optional<WaveVariablePair> vectorPair = findVectorPair(variables);
        logger.info(() -> "变量列表波场变量配对查找完成, matchedPair=" + (vectorPair.isPresent() ? "uWave/vWave" : "none"));
        return vectorPair;
    }

    /*
     * ========================================================================
     * 步骤3：识别极坐标波场配对
     * ========================================================================
     * 目标：
     *   1) 兼容现有 `wdir/wlen` 数据
     * 操作要点：
     *   1) 先按精确名称查找
     *   2) 再校验空间轴、网格基准和层结构
     */
    private Optional<WaveVariablePair> findPolarPair(List<VariableInfo> variables) {
        // 3.1 按精确名称分别查找波向和波长变量。
        VariableInfo direction = findExactName(variables, "wdir");
        VariableInfo wavelength = findExactName(variables, "wlen");
        if (direction == null || wavelength == null) {
            logger.info(() -> "未找到完整的波场变量配对, directionFound="
                + (direction != null)
                + ", wavelengthFound="
                + (wavelength != null));
            return Optional.empty();
        }

        // 3.2 校验两个变量都能参与平面渲染。
        if (!direction.plottable() || !wavelength.plottable()) {
            logger.info(() -> "波场变量不可绘制，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name());
            return Optional.empty();
        }

        // 3.3 校验空间轴一致。
        if (direction.nodeAxis() != wavelength.nodeAxis()) {
            logger.info(() -> "波场变量空间轴不一致，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name());
            return Optional.empty();
        }

        // 3.4 校验节点/单元中心基准一致。
        if (direction.elementCentered() != wavelength.elementCentered()) {
            logger.info(() -> "波场变量网格基准不一致，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name());
            return Optional.empty();
        }

        // 3.5 校验是否都分层或都不分层。
        if (direction.layered() != wavelength.layered()) {
            logger.info(() -> "波场变量层化结构不一致，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name());
            return Optional.empty();
        }

        // 3.6 若是分层变量，则校验层维名称和层数一致。
        if (direction.layered()
            && (!direction.layerDimensionName().equals(wavelength.layerDimensionName())
            || direction.layerCount() != wavelength.layerCount())) {
            logger.info(() -> "波场变量层维不一致，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name());
            return Optional.empty();
        }

        // 3.7 所有条件满足后返回校验后的配对对象。
        Optional<WaveVariablePair> pair;
        try {
            pair = Optional.of(new WaveVariablePair(direction, wavelength));
        } catch (IllegalArgumentException exception) {
            logger.info(() -> "波场变量配对校验失败，放弃配对, directionVariable="
                + direction.name()
                + ", wavelengthVariable="
                + wavelength.name()
                + ", reason="
                + exception.getMessage());
            return Optional.empty();
        }

        logger.info(() -> "极坐标波场变量配对查找完成, directionVariable="
            + direction.name()
            + ", wavelengthVariable="
            + wavelength.name());
        return pair;
    }

    /*
     * ========================================================================
     * 步骤4：识别向量波场配对
     * ========================================================================
     * 目标：
     *   1) 支持规则格网上的 `uWave/vWave` 浪场
     * 操作要点：
     *   1) 只要求 `uWave` 与 `vWave` 同时存在即可
     *   2) 若有 `Hwave/hWave` 且兼容，则作为箭头长度控制字段
     */
    private Optional<WaveVariablePair> findVectorPair(List<VariableInfo> variables) {
        VariableInfo uWave = findExactName(variables, "uwave");
        VariableInfo vWave = findExactName(variables, "vwave");
        if (uWave == null || vWave == null) {
            logger.info(() -> "未找到完整的向量波场变量配对, uWaveFound="
                + (uWave != null)
                + ", vWaveFound="
                + (vWave != null));
            return Optional.empty();
        }

        // 4.1 主变量不可绘制时直接放弃。
        if (!uWave.plottable() || !vWave.plottable()) {
            logger.info(() -> "向量波场变量不可绘制，放弃配对, uWave="
                + uWave.name()
                + ", vWave="
                + vWave.name());
            return Optional.empty();
        }

        VariableInfo waveHeight = findCompatibleWaveHeight(variables, uWave);

        // 4.2 用向量模式构造波场配对；交错基准兼容性交给模型层处理。
        try {
            WaveVariablePair pair = new WaveVariablePair(uWave, vWave, true, waveHeight);
            logger.info(() -> "向量波场变量配对查找完成, uWave="
                + uWave.name()
                + ", vWave="
                + vWave.name()
                + ", waveHeight="
                + (waveHeight == null ? null : waveHeight.name()));
            return Optional.of(pair);
        } catch (IllegalArgumentException exception) {
            logger.info(() -> "向量波场变量配对校验失败，放弃配对, uWave="
                + uWave.name()
                + ", vWave="
                + vWave.name()
                + ", reason="
                + exception.getMessage());
            return Optional.empty();
        }
    }

    private VariableInfo findCompatibleWaveHeight(List<VariableInfo> variables, VariableInfo referenceVariable) {
        VariableInfo waveHeight = findExactName(variables, "hwave");
        if (waveHeight == null) {
            return null;
        }
        if (!waveHeight.plottable()) {
            return null;
        }
        if (waveHeight.geometryKind() != referenceVariable.geometryKind()) {
            return null;
        }
        if (waveHeight.layered()
            && (!referenceVariable.layered()
            || !waveHeight.layerDimensionName().equals(referenceVariable.layerDimensionName())
            || waveHeight.layerCount() != referenceVariable.layerCount())) {
            return null;
        }
        return waveHeight;
    }

    /*
     * ========================================================================
     * 步骤5：按精确名称查找变量
     * ========================================================================
     * 目标：
     *   1) 用精确变量名锁定候选变量
     * 操作要点：
     *   1) 名称比较统一转小写
     */
    private VariableInfo findExactName(List<VariableInfo> variables, String expectedName) {
        logger.info(() -> "开始按精确名称查找变量, expectedName=" + expectedName);

        // 3.1 遍历变量列表并做大小写无关的精确匹配。
        VariableInfo variable = variables.stream()
            .filter(item -> item.name().toLowerCase(Locale.ROOT).equals(expectedName))
            .findFirst()
            .orElse(null);

        logger.info(() -> "精确名称变量查找完成, expectedName=" + expectedName + ", found=" + (variable != null));
        return variable;
    }
}
