package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.HorizontalBasis;
import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.model.TriangleDomain;
import com.example.netcdfviewer.model.VariableInfo;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * NetCDF 数据集解析器。
 * 负责识别维度、变量、坐标轴、三角形连接关系以及可视化变量信息。
 */
public final class NetcdfDatasetParser {
    // 用于识别层轴相关名称的正则表达式。
    private static final Pattern DEPTH_PATTERN = Pattern.compile(".*(depth|dep|layer|lev|level|sig|sigma|z).*");
    // 坐标变量名称的优先候选组合。
    private static final List<String[]> PREFERRED_COORDINATE_PAIRS = List.of(
        new String[]{"x", "y"},
        new String[]{"lon", "lat"},
        new String[]{"longitude", "latitude"}
    );

    public ParsedDataset open(Path path) throws IOException {
        // 打开 NetCDF 文件，并在方法结束后自动关闭资源。
        try (NetcdfFile netcdfFile = NetcdfFiles.open(path.toString())) {
            // 收集文件中的维度名称及长度。
            Map<String, Integer> dimensions = new LinkedHashMap<>();
            for (Dimension dimension : netcdfFile.getDimensions()) {
                dimensions.put(dimension.getShortName(), dimension.getLength());
            }

            // 收集全局属性。
            Map<String, String> globalAttributes = new LinkedHashMap<>();
            for (Attribute attribute : netcdfFile.getGlobalAttributes()) {
                globalAttributes.put(attribute.getShortName(), attribute.getStringValue());
            }

            // 读取所有变量对象，用于后续启发式判断。
            List<Variable> variables = new ArrayList<>(netcdfFile.getVariables());
            // 记录解析过程中出现的非致命警告。
            List<String> warnings = new ArrayList<>();
            // 缓存所有一维轴的数值。
            Map<String, double[]> axisCoordinates = readAxisCoordinates(variables);

            // 网格对象初始化为空，只有成功识别坐标和连接关系后才会创建。
            MeshData mesh = null;
            String xVariableName = null;
            String yVariableName = null;
            String connectivityVariableName = null;
            int nodeCount = -1;
            int elementCount = -1;
            SpatialDomain spatialDomain = null;
            List<CoordinateBinding> coordinateBindings = List.of();
            CoordinateBinding selectedCoordinateBinding = null;

            // 找出所有可能的坐标变量组合。
            List<CoordinatePair> coordinatePairs = findCoordinatePairs(variables);
            if (!coordinatePairs.isEmpty()) {
                CoordinatePair selectedPair = pickTriangleCoordinatePair(coordinatePairs, warnings);

                // 坐标对选择成功后，再继续寻找三角形连接变量。
                if (selectedPair != null) {
                    double[] selectedX = readDoubleArray(selectedPair.xVariable());
                    double[] selectedY = readDoubleArray(selectedPair.yVariable());
                    nodeCount = selectedX.length;
                    xVariableName = selectedPair.xVariable().getShortName();
                    yVariableName = selectedPair.yVariable().getShortName();

                    Optional<Variable> connectivityVariable = findConnectivityVariable(variables, nodeCount);
                    if (connectivityVariable.isPresent()) {
                        Variable variable = connectivityVariable.get();
                        // 构造网格对象时会把连接索引统一转换为 0 基。
                        mesh = new MeshData(selectedX, selectedY, normalizeConnectivity(readTriangleConnectivity(variable), nodeCount));
                        connectivityVariableName = variable.getShortName();
                        elementCount = mesh.triangleCount();
                        spatialDomain = new TriangleDomain(mesh);
                    } else {
                        warnings.add("No valid triangle connectivity variable was found.");
                    }
                } else {
                    warnings.add("No compatible coordinate pair was found.");
                }
            }

            // 三角网未命中时，继续尝试标准格网坐标绑定。
            if (spatialDomain == null) {
                coordinateBindings = findStructuredCoordinateBindings(variables);
                if (!coordinateBindings.isEmpty()) {
                    selectedCoordinateBinding = coordinateBindings.get(0);
                    spatialDomain = buildStructuredDomain(variables, selectedCoordinateBinding);
                    xVariableName = selectedCoordinateBinding.xName();
                    yVariableName = selectedCoordinateBinding.yName();
                } else {
                    warnings.add("No compatible horizontal coordinate binding was found.");
                }
            }

            // 根据维度名称和轴名称识别潜在层轴。
            Set<String> layerDimensionNames = detectLayerDimensionNames(dimensions, axisCoordinates);
            final int resolvedNodeCount = nodeCount;
            final int resolvedElementCount = elementCount;
            final SpatialDomain resolvedSpatialDomain = spatialDomain;
            final List<CoordinateBinding> resolvedCoordinateBindings = coordinateBindings;
            // 为每个变量构建元信息描述对象。
            List<VariableInfo> variableInfos = variables.stream()
                .map(variable -> describeVariable(
                    variable.getShortName(),
                    variable.getDataType(),
                    variable.getDimensions().stream().map(Dimension::getShortName).collect(Collectors.toList()),
                    Arrays.stream(variable.getShape()).boxed().collect(Collectors.toList()),
                    resolvedNodeCount,
                    resolvedElementCount,
                    layerDimensionNames,
                    readFillValue(variable),
                    resolvedSpatialDomain,
                    resolvedCoordinateBindings
                ))
                .collect(Collectors.toList());

            // 返回最终解析结果对象。
            return new ParsedDataset(
                path,
                mesh,
                spatialDomain,
                coordinateBindings,
                selectedCoordinateBinding,
                variableInfos,
                dimensions,
                globalAttributes,
                axisCoordinates,
                warnings,
                xVariableName,
                yVariableName,
                connectivityVariableName
            );
        }
    }

    public double[] readLayer(ParsedDataset dataset, VariableInfo variableInfo, int layerIndex) throws IOException {
        // 只允许对可视化变量执行按层读取。
        if (!variableInfo.plottable()) {
            throw new IllegalArgumentException("Variable is not plottable: " + variableInfo.name());
        }

        // 重新打开源文件读取目标变量，避免长期持有整个数据文件句柄。
        try (NetcdfFile netcdfFile = NetcdfFiles.open(dataset.sourcePath().toString())) {
            Variable variable = netcdfFile.findVariable(variableInfo.name());
            if (variable == null) {
                throw new IllegalArgumentException("Variable not found: " + variableInfo.name());
            }
            // 拷贝形状数组，后续会在层轴位置改成单层切片。
            int[] shape = variable.getShape().clone();
            // origin 数组用于指定每个维度的切片起始位置。
            int[] origin = new int[shape.length];
            try {
                // 如果变量存在层轴，则只读取目标层。
                for (int axis = 0; axis < shape.length; axis++) {
                    if (axis == variableInfo.layerAxis()) {
                        shape[axis] = 1;
                        origin[axis] = layerIndex;
                    }
                }
                // 读取切片后压缩长度为 1 的维度，并统一转成 double 数组。
                double[] values = readDoubleArray(variable.read(origin, shape).reduce());
                // 如果数据集已经有网格，则进一步校验值数量是否匹配。
                if (dataset.hasMesh() && values.length != variableInfo.expectedValueCount(dataset.mesh())) {
                    throw new IllegalArgumentException("Unexpected value count for " + variableInfo.name()
                        + ": expected " + variableInfo.expectedValueCount(dataset.mesh())
                        + " but read " + values.length);
                }
                return values;
            } catch (InvalidRangeException exception) {
                // 对非法层索引做更清晰的错误包装。
                throw new IllegalArgumentException("Layer index out of bounds: " + layerIndex, exception);
            }
        }
    }

    static VariableInfo describeVariable(
        String name,
        DataType dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        int nodeCount,
        Set<String> layerDimensionNames
    ) {
        return describeVariable(name, dataType, dimensionNames, dimensionSizes, nodeCount, -1, layerDimensionNames, null);
    }

    static VariableInfo describeVariable(
        String name,
        DataType dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        int nodeCount,
        Set<String> layerDimensionNames,
        Double fillValue
    ) {
        return describeVariable(name, dataType, dimensionNames, dimensionSizes, nodeCount, -1, layerDimensionNames, fillValue);
    }

    static VariableInfo describeVariable(
        String name,
        DataType dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        int nodeCount,
        int elementCount,
        Set<String> layerDimensionNames
    ) {
        return describeVariable(name, dataType, dimensionNames, dimensionSizes, nodeCount, elementCount, layerDimensionNames, null);
    }

    static VariableInfo describeVariable(
        String name,
        DataType dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        int nodeCount,
        int elementCount,
        Set<String> layerDimensionNames,
        Double fillValue
    ) {
        return describeVariable(name, dataType, dimensionNames, dimensionSizes, nodeCount, elementCount, layerDimensionNames, fillValue, null, List.of());
    }

    static VariableInfo describeVariable(
        String name,
        DataType dataType,
        List<String> dimensionNames,
        List<Integer> dimensionSizes,
        int nodeCount,
        int elementCount,
        Set<String> layerDimensionNames,
        Double fillValue,
        SpatialDomain spatialDomain,
        List<CoordinateBinding> coordinateBindings
    ) {
        // 先尝试识别变量对应的空间轴位置。
        SpatialMatch spatialMatch = findSpatialMatch(dimensionSizes, nodeCount, elementCount, dimensionNames);
        int nodeAxis = spatialMatch == null ? -1 : spatialMatch.axis();
        boolean elementCentered = spatialMatch != null && spatialMatch.elementCentered();
        // 只有数值型变量才有资格参与渲染。
        boolean numeric = dataType != null && dataType.isNumeric();
        // 坐标轴变量本身不应作为属性变量参与平面绘图。
        boolean coordinateAxis = looksLikeAxisVariable(name, dimensionNames)
            || coordinateBindings.stream().anyMatch(binding ->
            binding.xName().equalsIgnoreCase(name) || binding.yName().equalsIgnoreCase(name));
        boolean plottable = false;
        int layerAxis = -1;
        String basisId = null;
        SpatialDomain.Kind geometryKind = null;
        boolean cellCentered = elementCentered;

        // 满足条件后再继续判断它是单层变量还是分层变量。
        if (numeric && nodeAxis >= 0 && !coordinateAxis) {
            List<Integer> remainingAxes = new ArrayList<>();
            for (int axis = 0; axis < dimensionSizes.size(); axis++) {
                if (axis == nodeAxis) {
                    continue;
                }
                if (dimensionSizes.get(axis) > 1) {
                    remainingAxes.add(axis);
                }
            }

            // 没有额外有效维度时，说明这是单层平面变量。
            if (remainingAxes.isEmpty()) {
                plottable = true;
            } else if (remainingAxes.size() == 1) {
                // 只剩一个附加维度时，尝试把它识别为层轴。
                int candidateLayerAxis = remainingAxes.get(0);
                String dimensionName = dimensionNames.get(candidateLayerAxis).toLowerCase(Locale.ROOT);
                if (layerDimensionNames.contains(dimensionName)) {
                    layerAxis = candidateLayerAxis;
                    plottable = true;
                }
            }
        }

        // 标准格网按坐标绑定识别变量水平基准。
        if (spatialDomain != null
            && spatialDomain.kind() == SpatialDomain.Kind.STRUCTURED_GRID
            && numeric
            && !coordinateAxis) {
            HorizontalBasis basis = findStructuredBasis(dimensionNames, coordinateBindings);
            if (basis != null) {
                List<Integer> remainingAxes = new ArrayList<>();
                for (int axis = 0; axis < dimensionSizes.size(); axis++) {
                    if (basis.horizontalDimensions().contains(dimensionNames.get(axis))) {
                        continue;
                    }
                    if (dimensionSizes.get(axis) > 1) {
                        remainingAxes.add(axis);
                    }
                }
                if (remainingAxes.isEmpty()) {
                    plottable = true;
                } else if (remainingAxes.size() == 1) {
                    int candidateLayerAxis = remainingAxes.get(0);
                    String dimensionName = dimensionNames.get(candidateLayerAxis).toLowerCase(Locale.ROOT);
                    if (layerDimensionNames.contains(dimensionName)) {
                        layerAxis = candidateLayerAxis;
                        plottable = true;
                    }
                }
                basisId = basis.id();
                geometryKind = SpatialDomain.Kind.STRUCTURED_GRID;
                cellCentered = basis.cellCentered();
                nodeAxis = dimensionNames.indexOf(basis.horizontalDimensions().get(Math.min(1, basis.horizontalDimensions().size() - 1)));
                elementCentered = basis.cellCentered();
            }
        }

        return new VariableInfo(
            name,
            dataType == null ? "UNKNOWN" : dataType.name(),
            dimensionNames,
            dimensionSizes,
            plottable,
            nodeAxis,
            elementCentered,
            layerAxis,
            fillValue,
            basisId,
            geometryKind,
            cellCentered
        );
    }

    static int[][] normalizeConnectivity(int[][] triangles, int nodeCount) {
        // 扫描所有连接索引，以便识别索引基准并做范围校验。
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int[] triangle : triangles) {
            // 三角形单元必须刚好包含三个顶点索引。
            if (triangle.length != 3) {
                throw new IllegalArgumentException("Triangle connectivity must contain exactly 3 vertices.");
            }
            for (int vertex : triangle) {
                minimum = Math.min(minimum, vertex);
                maximum = Math.max(maximum, vertex);
            }
        }

        // 仅支持 0 基或 1 基两种索引形式。
        int base;
        if (minimum == 0) {
            base = 0;
        } else if (minimum == 1) {
            base = 1;
        } else {
            throw new IllegalArgumentException("Connectivity index base must start at 0 or 1.");
        }

        // 检查最大索引是否超出节点范围。
        if (maximum - base >= nodeCount) {
            throw new IllegalArgumentException("Connectivity indices exceed available node count.");
        }

        // 将所有索引统一转换成 0 基形式。
        int[][] normalized = new int[triangles.length][3];
        for (int triangleIndex = 0; triangleIndex < triangles.length; triangleIndex++) {
            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                normalized[triangleIndex][vertexIndex] = triangles[triangleIndex][vertexIndex] - base;
            }
        }
        return normalized;
    }

    private static List<CoordinatePair> findCoordinatePairs(List<Variable> variables) {
        // 坐标变量通常是一维数值变量，因此先筛选这一类变量。
        List<Variable> numeric1d = variables.stream()
            .filter(variable -> variable.getRank() == 1 && variable.getDataType().isNumeric())
            .collect(Collectors.toList());

        // 建立按小写变量名索引的映射，方便优先按常见命名组合匹配。
        Map<String, Variable> byName = numeric1d.stream()
            .collect(Collectors.toMap(
                variable -> variable.getShortName().toLowerCase(Locale.ROOT),
                variable -> variable,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        // 先尝试使用预定义优先级最高的坐标名称组合。
        List<CoordinatePair> pairs = new ArrayList<>();
        for (String[] pair : PREFERRED_COORDINATE_PAIRS) {
            Variable xVariable = byName.get(pair[0]);
            Variable yVariable = byName.get(pair[1]);
            if (xVariable != null && yVariable != null && xVariable.getSize() == yVariable.getSize()) {
                addCoordinatePair(pairs, xVariable, yVariable);
            }
        }

        // 如果显式命名组合不足，再根据名称和单位做启发式评分。
        List<Variable> coordinateLike = numeric1d.stream()
            .filter(variable -> coordinateScore(variable) > 0)
            .sorted(Comparator.comparingInt(NetcdfDatasetParser::coordinateScore).reversed())
            .collect(Collectors.toList());

        // 组合出一个更像 X 轴、一个更像 Y 轴的变量对。
        for (Variable first : coordinateLike) {
            for (Variable second : coordinateLike) {
                if (first != second && first.getSize() == second.getSize() && coordinateKindsDiffer(first, second)) {
                    addCoordinatePair(pairs, looksLikeX(first) ? first : second, looksLikeX(first) ? second : first);
                }
            }
        }

        return pairs;
    }

    private static CoordinatePair pickTriangleCoordinatePair(List<CoordinatePair> coordinatePairs, List<String> warnings) throws IOException {
        for (CoordinatePair pair : coordinatePairs) {
            double[] candidateX = readDoubleArray(pair.xVariable());
            double[] candidateY = readDoubleArray(pair.yVariable());
            if (!hasMeaningfulSpan(candidateX) || !hasMeaningfulSpan(candidateY)) {
                warnings.add("Skipping degenerate coordinate pair "
                    + pair.xVariable().getShortName()
                    + " / "
                    + pair.yVariable().getShortName()
                    + ".");
                continue;
            }
            return pair;
        }
        return null;
    }

    private static void addCoordinatePair(List<CoordinatePair> pairs, Variable xVariable, Variable yVariable) {
        // 避免同一变量对被重复加入候选列表。
        boolean exists = pairs.stream()
            .anyMatch(pair -> pair.xVariable() == xVariable && pair.yVariable() == yVariable);
        if (!exists) {
            pairs.add(new CoordinatePair(xVariable, yVariable));
        }
    }

    private static boolean hasMeaningfulSpan(double[] values) {
        // 通过最小值和最大值判断坐标数组是否存在真实空间跨度。
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                continue;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return Double.isFinite(min) && Double.isFinite(max) && (max - min) > 1e-9;
    }

    private static List<CoordinateBinding> findStructuredCoordinateBindings(List<Variable> variables) {
        List<Variable> numeric1d = variables.stream()
            .filter(variable -> variable.getRank() == 1 && variable.getDataType().isNumeric())
            .collect(Collectors.toList());

        return numeric1d.stream()
            .flatMap(xVariable -> numeric1d.stream()
                .filter(yVariable -> yVariable != xVariable)
                .filter(yVariable -> {
                    try {
                        return hasMeaningfulSpan(readDoubleArray(xVariable)) && hasMeaningfulSpan(readDoubleArray(yVariable));
                    } catch (IOException exception) {
                        return false;
                    }
                })
                .filter(yVariable -> coordinateKindsDiffer(xVariable, yVariable))
                .map(yVariable -> {
                    Variable actualX = looksLikeX(xVariable) ? xVariable : yVariable;
                    Variable actualY = looksLikeX(xVariable) ? yVariable : xVariable;
                    return new CoordinateBinding(
                        "binding:" + actualX.getShortName() + ":" + actualY.getShortName(),
                        actualX.getShortName(),
                        actualY.getShortName(),
                        List.of(
                            actualX.getDimensions().get(0).getShortName(),
                            actualY.getDimensions().get(0).getShortName()
                        ),
                        false
                    );
                }))
            .filter(binding -> !binding.horizontalDimensions().get(0).equals(binding.horizontalDimensions().get(1)))
            .sorted(Comparator.comparingInt(NetcdfDatasetParser::bindingScore).reversed())
            .distinct()
            .collect(Collectors.toList());
    }

    private static Optional<Variable> findConnectivityVariable(List<Variable> variables, int nodeCount) {
        // 候选连接变量通常是二维数值数组，并且某一维长度等于 3。
        List<Variable> candidates = variables.stream()
            .filter(variable -> variable.getRank() == 2)
            .filter(variable -> variable.getDataType().isNumeric())
            .filter(variable -> variable.getShape(0) == 3 || variable.getShape(1) == 3)
            .sorted(Comparator.comparingInt(NetcdfDatasetParser::connectivityScore).reversed())
            .collect(Collectors.toList());

        // 按评分从高到低尝试，找到第一个能通过校验的连接变量。
        for (Variable candidate : candidates) {
            try {
                normalizeConnectivity(readTriangleConnectivity(candidate), nodeCount);
                return Optional.of(candidate);
            } catch (IOException | IllegalArgumentException ignored) {
                // 当前候选不合法时继续尝试下一个。
            }
        }
        return Optional.empty();
    }

    private static Set<String> detectLayerDimensionNames(Map<String, Integer> dimensions, Map<String, double[]> axisCoordinates) {
        // 将维度名称和轴名称中像“层”的字段统一提取出来。
        Set<String> names = new LinkedHashSet<>();
        for (String dimensionName : dimensions.keySet()) {
            String normalized = dimensionName.toLowerCase(Locale.ROOT);
            if (DEPTH_PATTERN.matcher(normalized).matches()) {
                names.add(normalized);
            }
        }
        for (String axisName : axisCoordinates.keySet()) {
            String normalized = axisName.toLowerCase(Locale.ROOT);
            if (DEPTH_PATTERN.matcher(normalized).matches()) {
                names.add(normalized);
            }
        }
        return names;
    }

    private static StructuredGridDomain buildStructuredDomain(List<Variable> variables, CoordinateBinding binding) throws IOException {
        Variable xVariable = variables.stream()
            .filter(variable -> variable.getShortName().equals(binding.xName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Coordinate variable not found: " + binding.xName()));
        Variable yVariable = variables.stream()
            .filter(variable -> variable.getShortName().equals(binding.yName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Coordinate variable not found: " + binding.yName()));
        double[] xAxis = readDoubleArray(xVariable);
        double[] yAxis = readDoubleArray(yVariable);
        StructuredGridData grid = new StructuredGridData(binding, xAxis, yAxis, null, null, xAxis.length, yAxis.length);
        return new StructuredGridDomain(grid, binding);
    }

    private static Map<String, double[]> readAxisCoordinates(List<Variable> variables) throws IOException {
        // 读取全部一维数值轴，为层值显示和坐标识别做准备。
        Map<String, double[]> axisCoordinates = new LinkedHashMap<>();
        for (Variable variable : variables) {
            if (variable.getRank() != 1 || !variable.getDataType().isNumeric()) {
                continue;
            }
            List<Dimension> dimensions = variable.getDimensions();
            if (dimensions.size() != 1) {
                continue;
            }
            // 同时以变量名和维度名两个键缓存轴值，便于后续查询。
            double[] values = readDoubleArray(variable);
            axisCoordinates.put(variable.getShortName(), values);
            axisCoordinates.putIfAbsent(dimensions.get(0).getShortName(), values);
        }
        return axisCoordinates;
    }

    private static HorizontalBasis findStructuredBasis(List<String> dimensionNames, List<CoordinateBinding> coordinateBindings) {
        for (CoordinateBinding binding : coordinateBindings) {
            if (dimensionNames.containsAll(binding.horizontalDimensions())) {
                return new HorizontalBasis(binding.id(), binding.id(), binding.horizontalDimensions(), false, false);
            }
        }
        return null;
    }

    private static SpatialMatch findSpatialMatch(List<Integer> dimensionSizes, int nodeCount, int elementCount, List<String> dimensionNames) {
        // 收集所有与节点数或单元数相等的维度候选。
        List<SpatialMatch> matches = new ArrayList<>();
        for (int axis = 0; axis < dimensionSizes.size(); axis++) {
            if (dimensionSizes.get(axis) == nodeCount) {
                matches.add(new SpatialMatch(axis, false));
            } else if (dimensionSizes.get(axis) == elementCount) {
                matches.add(new SpatialMatch(axis, true));
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        // 当候选不止一个时，根据维度名称再次评分选择最像空间轴的那个。
        SpatialMatch best = matches.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (SpatialMatch match : matches) {
            String normalized = dimensionNames.get(match.axis()).toLowerCase(Locale.ROOT);
            int score = 0;
            if (!match.elementCentered() && (normalized.contains("node") || normalized.contains("vertex"))) {
                score += 100;
            }
            if (match.elementCentered() && (normalized.contains("nele") || normalized.contains("ele") || normalized.contains("cell") || normalized.contains("face") || normalized.contains("point"))) {
                score += 100;
            }
            if (!match.elementCentered() && normalized.equals("node")) {
                score += 20;
            }
            if (match.elementCentered() && (normalized.equals("nele") || normalized.equals("cell") || normalized.equals("element") || normalized.equals("point"))) {
                score += 20;
            }
            if (score > bestScore) {
                best = match;
                bestScore = score;
            }
        }
        return best;
    }

    private static double[] readDoubleArray(Variable variable) throws IOException {
        // 统一把变量读取结果转成 double 数组。
        return readDoubleArray(variable.read());
    }

    private static double[] readDoubleArray(Array array) {
        // 创建目标数组。
        double[] values = new double[(int) array.getSize()];
        int index = 0;
        // 使用数组迭代器兼容各种底层 NetCDF 数据类型。
        var iterator = array.getIndexIterator();
        while (iterator.hasNext()) {
            values[index++] = iterator.getDoubleNext();
        }
        return values;
    }

    private static int[][] readTriangleConnectivity(Variable variable) throws IOException {
        // 读取连接数组原始数据。
        Array array = variable.read();
        int[] shape = array.getShape();
        // 某些文件使用 [3, nele] 存储，因此需要识别访问方式是否转置。
        boolean transposed = shape[0] == 3 && shape[1] != 3;
        int triangleCount = transposed ? shape[1] : shape[0];
        int[][] triangles = new int[triangleCount][3];
        Index index = array.getIndex();
        for (int triangleIndex = 0; triangleIndex < triangleCount; triangleIndex++) {
            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                int row = transposed ? vertexIndex : triangleIndex;
                int column = transposed ? triangleIndex : vertexIndex;
                double rawValue = array.getDouble(index.set(row, column));
                // 某些连接数组会以浮点形式存储，这里统一四舍五入后再校验。
                long rounded = Math.round(rawValue);
                if (Math.abs(rawValue - rounded) > 1e-6) {
                    throw new IllegalArgumentException("Connectivity variable contains non-integer values.");
                }
                triangles[triangleIndex][vertexIndex] = (int) rounded;
            }
        }
        return triangles;
    }

    private static Double readFillValue(Variable variable) {
        // 优先读取标准 _FillValue 属性。
        Attribute fillValue = variable.findAttributeIgnoreCase("_FillValue");
        if (fillValue == null) {
            // 某些文件会使用 missing_value 表示缺测值。
            fillValue = variable.findAttributeIgnoreCase("missing_value");
        }
        // 只有当属性存在且可以转成数值时才返回。
        if (fillValue != null && fillValue.getNumericValue() != null) {
            return fillValue.getNumericValue().doubleValue();
        }
        return null;
    }

    private static int coordinateScore(Variable variable) {
        // 根据变量名和单位信息，对坐标轴候选做启发式评分。
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.equals("x") || name.equals("y") || name.equals("lon") || name.equals("lat")) {
            score += 100;
        }
        if (name.contains("lon") || name.contains("lat") || name.contains("long") || name.contains("coord") || name.equals("x") || name.equals("y")) {
            score += 25;
        }
        Attribute units = variable.findAttributeIgnoreCase("units");
        if (units != null && units.getStringValue() != null) {
            String value = units.getStringValue().toLowerCase(Locale.ROOT);
            if (value.contains("degree") || value.contains("meter") || value.contains("metre")) {
                score += 10;
            }
        }
        return score;
    }

    private static boolean coordinateKindsDiffer(Variable first, Variable second) {
        // 只有一个像 X 轴、一个像 Y 轴时，才认为它们适合配对。
        return (looksLikeX(first) && looksLikeY(second)) || (looksLikeY(first) && looksLikeX(second));
    }

    private static boolean looksLikeX(Variable variable) {
        // 根据名称判断该变量是否更像东西向坐标。
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        if (name.contains("lat") || name.contains("north")) {
            return false;
        }
        return name.equals("x")
            || name.startsWith("x")
            || name.contains("lon")
            || name.contains("long")
            || name.contains("east");
    }

    private static boolean looksLikeY(Variable variable) {
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        if (name.contains("lon") || name.contains("long") || name.contains("east")) {
            return false;
        }
        return name.equals("y")
            || name.startsWith("y")
            || name.contains("lat")
            || name.contains("north");
    }

    private static int bindingScore(CoordinateBinding binding) {
        int score = 0;
        String xName = binding.xName().toLowerCase(Locale.ROOT);
        String yName = binding.yName().toLowerCase(Locale.ROOT);
        String xDim = binding.horizontalDimensions().get(0).toLowerCase(Locale.ROOT);
        String yDim = binding.horizontalDimensions().get(1).toLowerCase(Locale.ROOT);
        if (xName.contains("rho") || yName.contains("rho")) {
            score += 50;
        }
        if (xDim.contains("rho") || yDim.contains("rho")) {
            score += 50;
        }
        if (xName.startsWith("x") || yName.startsWith("y")) {
            score += 10;
        }
        if (xDim.startsWith("x") || yDim.startsWith("y")) {
            score += 10;
        }
        return score;
    }

    private static boolean looksLikeAxisVariable(String variableName, List<String> dimensionNames) {
        // 常见坐标轴名称直接认定为轴变量。
        String normalized = variableName.toLowerCase(Locale.ROOT);
        if (normalized.equals("x")
            || normalized.equals("y")
            || normalized.equals("xc")
            || normalized.equals("yc")
            || normalized.equals("lon")
            || normalized.equals("lat")
            || normalized.equals("lonc")
            || normalized.equals("latc")
            || normalized.equals("longitude")
            || normalized.equals("latitude")
            || normalized.equals("time")
            || normalized.equals("depth")
            || normalized.equals("lev")
            || normalized.equals("level")
            || normalized.equals("siglay")
            || normalized.equals("siglev")
            || normalized.equals("z")) {
            return true;
        }
        // sigma 层相关变量通常也属于轴变量。
        if (normalized.startsWith("siglay") || normalized.startsWith("siglev")) {
            return true;
        }
        // 若变量名与维度名一致，也优先认为它是轴变量。
        return dimensionNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    private static int connectivityScore(Variable variable) {
        // 根据名称判断变量像不像连接关系数组。
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.contains("element")) {
            score += 100;
        }
        if (name.contains("connect")) {
            score += 80;
        }
        if (name.equals("nv") || name.contains("tri")) {
            score += 60;
        }
        return score;
    }

    private record CoordinatePair(Variable xVariable, Variable yVariable) {
        // 坐标变量对记录类型。
    }

    private record SpatialMatch(int axis, boolean elementCentered) {
        // 空间轴匹配结果记录类型。
    }
}
