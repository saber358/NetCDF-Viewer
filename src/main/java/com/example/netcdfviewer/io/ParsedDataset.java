package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.VariableInfo;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 解析后的数据集对象。
 * 该对象把网格、变量、维度、属性和警告信息统一封装起来，供界面层直接使用。
 */
public record ParsedDataset(
    // 原始数据文件路径。
    Path sourcePath,
    // 解析得到的网格对象；如果无法构建网格则可能为 null。
    MeshData mesh,
    // 文件内所有变量的描述信息。
    List<VariableInfo> variables,
    // 所有维度名称及长度。
    Map<String, Integer> dimensions,
    // 全局属性键值对。
    Map<String, String> globalAttributes,
    // 一维坐标轴或层轴的数值缓存。
    Map<String, double[]> axisCoordinates,
    // 解析过程中产生的非致命警告。
    List<String> warnings,
    // 被选中的 X 坐标变量名。
    String xVariableName,
    // 被选中的 Y 坐标变量名。
    String yVariableName,
    // 被选中的三角形连接变量名。
    String connectivityVariableName
) {
    public ParsedDataset {
        // 复制变量列表，避免外部在构造后继续修改。
        variables = List.copyOf(variables);
        // 包装维度映射为不可变对象，保护内部状态。
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
        // 包装全局属性映射为不可变对象，保护内部状态。
        globalAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(globalAttributes));
        // 包装轴值映射为不可变对象，保护内部状态。
        axisCoordinates = Collections.unmodifiableMap(new LinkedHashMap<>(axisCoordinates));
        // 复制警告列表，避免外部修改。
        warnings = List.copyOf(warnings);
    }

    public boolean hasMesh() {
        // 只要网格对象不为空，就说明当前数据集具备网格可视化基础。
        return mesh != null;
    }

    public List<VariableInfo> plottableVariables() {
        // 仅返回可用于平面渲染的变量，供界面列表优先展示。
        return variables.stream()
            .filter(VariableInfo::plottable)
            .collect(Collectors.toList());
    }

    public Optional<double[]> axisValues(String name) {
        // 通过可选值返回轴数组，避免调用方处理空指针。
        return Optional.ofNullable(axisCoordinates.get(name));
    }
}
