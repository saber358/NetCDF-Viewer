package com.example.netcdfviewer.model;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 变量描述对象。
 * 该对象不保存真实数据，只保存变量名、维度、可视化属性等元信息。
 */
public record VariableInfo(
    // 变量名称。
    String name,
    // 原始数据类型名称。
    String dataType,
    // 维度名称列表。
    List<String> dimensionNames,
    // 维度长度列表。
    List<Integer> dimensionSizes,
    // 是否支持作为平面属性进行绘制。
    boolean plottable,
    // 空间轴所在位置；如果不存在则为 -1。
    int nodeAxis,
    // 是否为单元中心变量；否则视为节点中心变量。
    boolean elementCentered,
    // 层轴所在位置；如果不存在则为 -1。
    int layerAxis,
    // 缺测值或填充值。
    Double fillValue,
    // 水平基准标识。
    String basisId,
    // 几何类型。
    SpatialDomain.Kind geometryKind,
    // 是否为网格单元中心变量。
    boolean cellCentered
) {
    public VariableInfo {
        // 复制维度名称列表，避免外部修改。
        dimensionNames = List.copyOf(dimensionNames);
        // 复制维度长度列表，避免外部修改。
        dimensionSizes = List.copyOf(dimensionSizes);
    }

    public VariableInfo(
        String name,
        String dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        boolean plottable,
        int nodeAxis,
        boolean elementCentered,
        int layerAxis,
        Double fillValue
    ) {
        this(
            name,
            dataType,
            dimensionNames,
            dimensionSizes,
            plottable,
            nodeAxis,
            elementCentered,
            layerAxis,
            fillValue,
            nodeAxis >= 0 ? (elementCentered ? "triangle:cell:" + nodeAxis : "triangle:node:" + nodeAxis) : null,
            nodeAxis >= 0 ? SpatialDomain.Kind.TRIANGLE_MESH : null,
            elementCentered
        );
    }

    public boolean layered() {
        // 只要层轴存在，就说明变量是多层变量。
        return layerAxis >= 0;
    }

    public int layerCount() {
        // 多层变量返回层数，单层变量统一返回 1。
        return layered() ? dimensionSizes.get(layerAxis) : 1;
    }

    public String dimensionSummary() {
        // 将维度名称格式化成类似 [time, siglay, node] 的展示字符串。
        return dimensionNames.stream()
            .map(String::trim)
            .collect(Collectors.joining(", ", "[", "]"));
    }

    public String layerDimensionName() {
        // 返回层轴名称；若不是多层变量则返回空字符串。
        return layered() ? dimensionNames.get(layerAxis) : "";
    }

    public int expectedValueCount(MeshData mesh) {
        // 单元中心变量对应三角形数量，节点中心变量对应节点数量。
        return elementCentered ? mesh.triangleCount() : mesh.nodeCount();
    }

    public String presentableType() {
        // 统一以大写形式展示数据类型，更符合界面显示习惯。
        return dataType.toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        // 根据变量空间位置生成人类可读说明。
        String location = elementCentered ? "element" : "node";
        // 根据变量是否可画和是否分层补充后缀信息。
        String suffix = plottable ? (layered() ? " (layered " + location + ")" : " (planar " + location + ")") : " (info)";
        // 返回最终展示字符串。
        return name + " " + dimensionSummary() + suffix;
    }
}
