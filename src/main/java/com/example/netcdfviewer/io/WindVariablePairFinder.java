package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WindVariablePair;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

public final class WindVariablePairFinder {
    private static final Logger logger = Logger.getLogger(WindVariablePairFinder.class.getName());

    public Optional<WindVariablePair> find(ParsedDataset dataset) {
        logger.info(() -> "开始从数据集查找风场变量配对, sourcePath=" + (dataset == null ? null : dataset.sourcePath()));
        Optional<WindVariablePair> pair = dataset == null ? Optional.empty() : find(dataset.variables());
        logger.info(() -> "数据集风场变量配对查找完成, sourcePath="
            + (dataset == null ? null : dataset.sourcePath())
            + ", found="
            + pair.isPresent());
        return pair;
    }

    Optional<WindVariablePair> find(List<VariableInfo> variables) {
        logger.info(() -> "开始从变量列表查找风场变量配对, variableCount=" + (variables == null ? null : variables.size()));
        if (variables == null || variables.isEmpty()) {
            logger.info("变量列表为空，风场变量配对查找结束");
            return Optional.empty();
        }

        Optional<WindVariablePair> trianglePair = findExactPair(variables, "uwind_speed", "vwindspeed");
        if (trianglePair.isPresent()) {
            logger.info("变量列表风场变量配对查找完成, matchedPair=uwind_speed/vwindspeed");
            return trianglePair;
        }

        Optional<WindVariablePair> triangleUnderscorePair = findExactPair(variables, "uwind_speed", "vwind_speed");
        if (triangleUnderscorePair.isPresent()) {
            logger.info("变量列表风场变量配对查找完成, matchedPair=uwind_speed/vwind_speed");
            return triangleUnderscorePair;
        }

        Optional<WindVariablePair> structuredPair = findExactPair(variables, "u10", "v10");
        logger.info(() -> "变量列表风场变量配对查找完成, matchedPair=" + (structuredPair.isPresent() ? "U10/V10" : "none"));
        return structuredPair;
    }

    private Optional<WindVariablePair> findExactPair(List<VariableInfo> variables, String eastwardName, String northwardName) {
        VariableInfo eastward = findExactName(variables, eastwardName);
        VariableInfo northward = findExactName(variables, northwardName);
        if (eastward == null || northward == null) {
            return Optional.empty();
        }
        if (!eastward.plottable() || !northward.plottable()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new WindVariablePair(eastward, northward));
        } catch (IllegalArgumentException exception) {
            logger.info(() -> "风场变量配对校验失败，放弃配对, eastwardVariable="
                + eastward.name()
                + ", northwardVariable="
                + northward.name()
                + ", reason="
                + exception.getMessage());
            return Optional.empty();
        }
    }

    private VariableInfo findExactName(List<VariableInfo> variables, String expectedName) {
        return variables.stream()
            .filter(item -> item.name().toLowerCase(Locale.ROOT).equals(expectedName))
            .findFirst()
            .orElse(null);
    }
}
