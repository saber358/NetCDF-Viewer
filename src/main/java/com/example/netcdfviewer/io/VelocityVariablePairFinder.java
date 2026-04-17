package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.VelocityVariablePair;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public final class VelocityVariablePairFinder {
    private static final Logger logger = Logger.getLogger(VelocityVariablePairFinder.class.getName());

    /*
     * ========================================================================
     * 步骤1：从数据集对象中查找速度变量配对
     * ========================================================================
     * 目标：
     *   1) 给控制器提供基于 ParsedDataset 的统一入口
     * 操作要点：
     *   1) 直接复用变量列表查找逻辑
     */
    public Optional<VelocityVariablePair> find(ParsedDataset dataset) {
        logger.info(() -> "开始从数据集查找速度变量配对, sourcePath=" + (dataset == null ? null : dataset.sourcePath()));

        // 1.1 将数据集变量列表交给统一查找逻辑处理。
        Optional<VelocityVariablePair> pair = dataset == null ? Optional.empty() : find(dataset.variables());

        logger.info(() -> "数据集速度变量配对查找完成, sourcePath="
            + (dataset == null ? null : dataset.sourcePath())
            + ", found="
            + pair.isPresent());
        return pair;
    }

    /*
     * ========================================================================
     * 步骤2：从变量列表中查找速度变量配对
     * ========================================================================
     * 目标：
     *   1) 优先识别精确命名的 u / v
     *   2) 缺少 u / v 时回退识别 ua / va
     * 操作要点：
     *   1) 先按优先级查找候选变量
     *   2) 再校验空间轴、网格基准和层结构
     */
    Optional<VelocityVariablePair> find(List<VariableInfo> variables) {
        logger.info(() -> "开始从变量列表查找速度变量配对, variableCount=" + (variables == null ? null : variables.size()));

        // 2.1 输入为空时直接返回空结果。
        if (variables == null || variables.isEmpty()) {
            logger.info("变量列表为空，速度变量配对查找结束");
            return Optional.empty();
        }

        // 2.2 先查找优先级更高的 u / v。
        Optional<VelocityVariablePair> directPair = findExactPair(variables, "u", "v");
        if (directPair.isPresent()) {
            logger.info("变量列表速度变量配对查找完成, matchedPair=u/v");
            return directPair;
        }

        // 2.3 当 u / v 不可用时回退查找 ua / va。
        Optional<VelocityVariablePair> fallbackPair = findExactPair(variables, "ua", "va");
        logger.info(() -> "变量列表速度变量配对查找完成, matchedPair=" + (fallbackPair.isPresent() ? "ua/va" : "none"));
        return fallbackPair;
    }

    /*
     * ========================================================================
     * 步骤3：按名称尝试组装一个速度变量配对
     * ========================================================================
     * 目标：
     *   1) 将指定名称的东向和北向速度变量组装成候选配对
     * 操作要点：
     *   1) 先按精确名称查找
     *   2) 再校验两个变量是否兼容
     */
    private Optional<VelocityVariablePair> findExactPair(List<VariableInfo> variables, String eastwardName, String northwardName) {
        logger.info(() -> "开始按名称组装速度变量配对, eastwardName=" + eastwardName + ", northwardName=" + northwardName);

        // 3.1 按精确名称分别查找东向和北向速度变量。
        VariableInfo eastward = findExactName(variables, eastwardName);
        VariableInfo northward = findExactName(variables, northwardName);
        if (eastward == null || northward == null) {
            logger.info(() -> "速度变量名称不完整，放弃配对, eastwardFound="
                + (eastward != null)
                + ", northwardFound="
                + (northward != null));
            return Optional.empty();
        }

        // 3.2 校验两个变量都能参与平面渲染。
        if (!eastward.plottable() || !northward.plottable()) {
            logger.info(() -> "速度变量不可绘制，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name());
            return Optional.empty();
        }

        // 3.3 校验空间轴一致。
        if (eastward.nodeAxis() != northward.nodeAxis()) {
            logger.info(() -> "速度变量空间轴不一致，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name());
            return Optional.empty();
        }

        // 3.4 校验网格基准一致。
        if (eastward.elementCentered() != northward.elementCentered()) {
            logger.info(() -> "速度变量网格基准不一致，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name());
            return Optional.empty();
        }

        // 3.5 校验层化结构一致。
        if (eastward.layered() != northward.layered()) {
            logger.info(() -> "速度变量层化结构不一致，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name());
            return Optional.empty();
        }

        // 3.6 若是分层变量，则校验层维名称和层数一致。
        if (eastward.layered()
            && (!eastward.layerDimensionName().equals(northward.layerDimensionName())
            || eastward.layerCount() != northward.layerCount())) {
            logger.info(() -> "速度变量层维不一致，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name());
            return Optional.empty();
        }

        // 3.7 所有条件满足后返回校验后的配对对象。
        Optional<VelocityVariablePair> pair;
        try {
            pair = Optional.of(new VelocityVariablePair(eastward, northward));
        } catch (IllegalArgumentException exception) {
            logger.info(() -> "速度变量配对校验失败，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name()
                + ", reason="
                + exception.getMessage());
            return Optional.empty();
        }

        logger.info(() -> "速度变量配对组装完成, eastwardVariable="
            + eastward.name()
            + ", northwardVariable="
            + northward.name());
        return pair;
    }

    /*
     * ========================================================================
     * 步骤4：按精确名称查找变量
     * ========================================================================
     * 目标：
     *   1) 用精确变量名锁定候选变量
     * 操作要点：
     *   1) 名称比较统一转小写
     */
    private VariableInfo findExactName(List<VariableInfo> variables, String expectedName) {
        logger.info(() -> "开始按精确名称查找速度变量, expectedName=" + expectedName);

        // 4.1 遍历变量列表并做大小写无关的精确匹配。
        VariableInfo variable = variables.stream()
            .filter(item -> item.name().toLowerCase(Locale.ROOT).equals(expectedName))
            .findFirst()
            .orElse(null);

        logger.info(() -> "精确名称速度变量查找完成, expectedName=" + expectedName + ", found=" + (variable != null));
        return variable;
    }
}
