package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.MeshData;
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

public final class NetcdfDatasetParser {
    private static final Pattern DEPTH_PATTERN = Pattern.compile(".*(depth|dep|layer|lev|level|sig|sigma|z).*");
    private static final List<String[]> PREFERRED_COORDINATE_PAIRS = List.of(
        new String[]{"x", "y"},
        new String[]{"lon", "lat"},
        new String[]{"longitude", "latitude"}
    );

    public ParsedDataset open(Path path) throws IOException {
        try (NetcdfFile netcdfFile = NetcdfFiles.open(path.toString())) {
            Map<String, Integer> dimensions = new LinkedHashMap<>();
            for (Dimension dimension : netcdfFile.getDimensions()) {
                dimensions.put(dimension.getShortName(), dimension.getLength());
            }

            Map<String, String> globalAttributes = new LinkedHashMap<>();
            for (Attribute attribute : netcdfFile.getGlobalAttributes()) {
                globalAttributes.put(attribute.getShortName(), attribute.getStringValue());
            }

            List<Variable> variables = new ArrayList<>(netcdfFile.getVariables());
            List<String> warnings = new ArrayList<>();
            Map<String, double[]> axisCoordinates = readAxisCoordinates(variables);

            MeshData mesh = null;
            String xVariableName = null;
            String yVariableName = null;
            String connectivityVariableName = null;
            int nodeCount = -1;
            int elementCount = -1;

            List<CoordinatePair> coordinatePairs = findCoordinatePairs(variables);
            if (!coordinatePairs.isEmpty()) {
                double[] selectedX = null;
                double[] selectedY = null;
                CoordinatePair selectedPair = null;

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
                    selectedPair = pair;
                    selectedX = candidateX;
                    selectedY = candidateY;
                    break;
                }

                if (selectedPair != null) {
                    nodeCount = selectedX.length;
                    xVariableName = selectedPair.xVariable().getShortName();
                    yVariableName = selectedPair.yVariable().getShortName();

                    Optional<Variable> connectivityVariable = findConnectivityVariable(variables, nodeCount);
                    if (connectivityVariable.isPresent()) {
                        Variable variable = connectivityVariable.get();
                        mesh = new MeshData(selectedX, selectedY, normalizeConnectivity(readTriangleConnectivity(variable), nodeCount));
                        connectivityVariableName = variable.getShortName();
                        elementCount = mesh.triangleCount();
                    } else {
                        warnings.add("No valid triangle connectivity variable was found.");
                    }
                } else {
                    warnings.add("No compatible coordinate pair was found.");
                }
            } else {
                warnings.add("No compatible coordinate pair was found.");
            }

            Set<String> layerDimensionNames = detectLayerDimensionNames(dimensions, axisCoordinates);
            final int resolvedNodeCount = nodeCount;
            final int resolvedElementCount = elementCount;
            List<VariableInfo> variableInfos = variables.stream()
                .map(variable -> describeVariable(
                    variable.getShortName(),
                    variable.getDataType(),
                    variable.getDimensions().stream().map(Dimension::getShortName).collect(Collectors.toList()),
                    Arrays.stream(variable.getShape()).boxed().collect(Collectors.toList()),
                    resolvedNodeCount,
                    resolvedElementCount,
                    layerDimensionNames,
                    readFillValue(variable)
                ))
                .collect(Collectors.toList());

            return new ParsedDataset(
                path,
                mesh,
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
        if (!variableInfo.plottable()) {
            throw new IllegalArgumentException("Variable is not plottable: " + variableInfo.name());
        }

        try (NetcdfFile netcdfFile = NetcdfFiles.open(dataset.sourcePath().toString())) {
            Variable variable = netcdfFile.findVariable(variableInfo.name());
            if (variable == null) {
                throw new IllegalArgumentException("Variable not found: " + variableInfo.name());
            }
            int[] shape = variable.getShape().clone();
            int[] origin = new int[shape.length];
            try {
                for (int axis = 0; axis < shape.length; axis++) {
                    if (axis == variableInfo.layerAxis()) {
                        shape[axis] = 1;
                        origin[axis] = layerIndex;
                    }
                }
                double[] values = readDoubleArray(variable.read(origin, shape).reduce());
                if (dataset.hasMesh() && values.length != variableInfo.expectedValueCount(dataset.mesh())) {
                    throw new IllegalArgumentException("Unexpected value count for " + variableInfo.name()
                        + ": expected " + variableInfo.expectedValueCount(dataset.mesh())
                        + " but read " + values.length);
                }
                return values;
            } catch (InvalidRangeException exception) {
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
        SpatialMatch spatialMatch = findSpatialMatch(dimensionSizes, nodeCount, elementCount, dimensionNames);
        int nodeAxis = spatialMatch == null ? -1 : spatialMatch.axis();
        boolean elementCentered = spatialMatch != null && spatialMatch.elementCentered();
        boolean numeric = dataType != null && dataType.isNumeric();
        boolean coordinateAxis = looksLikeAxisVariable(name, dimensionNames);
        boolean plottable = false;
        int layerAxis = -1;

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
            fillValue
        );
    }

    static int[][] normalizeConnectivity(int[][] triangles, int nodeCount) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int[] triangle : triangles) {
            if (triangle.length != 3) {
                throw new IllegalArgumentException("Triangle connectivity must contain exactly 3 vertices.");
            }
            for (int vertex : triangle) {
                minimum = Math.min(minimum, vertex);
                maximum = Math.max(maximum, vertex);
            }
        }

        int base;
        if (minimum == 0) {
            base = 0;
        } else if (minimum == 1) {
            base = 1;
        } else {
            throw new IllegalArgumentException("Connectivity index base must start at 0 or 1.");
        }

        if (maximum - base >= nodeCount) {
            throw new IllegalArgumentException("Connectivity indices exceed available node count.");
        }

        int[][] normalized = new int[triangles.length][3];
        for (int triangleIndex = 0; triangleIndex < triangles.length; triangleIndex++) {
            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                normalized[triangleIndex][vertexIndex] = triangles[triangleIndex][vertexIndex] - base;
            }
        }
        return normalized;
    }

    private static List<CoordinatePair> findCoordinatePairs(List<Variable> variables) {
        List<Variable> numeric1d = variables.stream()
            .filter(variable -> variable.getRank() == 1 && variable.getDataType().isNumeric())
            .collect(Collectors.toList());

        Map<String, Variable> byName = numeric1d.stream()
            .collect(Collectors.toMap(
                variable -> variable.getShortName().toLowerCase(Locale.ROOT),
                variable -> variable,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<CoordinatePair> pairs = new ArrayList<>();
        for (String[] pair : PREFERRED_COORDINATE_PAIRS) {
            Variable xVariable = byName.get(pair[0]);
            Variable yVariable = byName.get(pair[1]);
            if (xVariable != null && yVariable != null && xVariable.getSize() == yVariable.getSize()) {
                addCoordinatePair(pairs, xVariable, yVariable);
            }
        }

        List<Variable> coordinateLike = numeric1d.stream()
            .filter(variable -> coordinateScore(variable) > 0)
            .sorted(Comparator.comparingInt(NetcdfDatasetParser::coordinateScore).reversed())
            .collect(Collectors.toList());

        for (Variable first : coordinateLike) {
            for (Variable second : coordinateLike) {
                if (first != second && first.getSize() == second.getSize() && coordinateKindsDiffer(first, second)) {
                    addCoordinatePair(pairs, looksLikeX(first) ? first : second, looksLikeX(first) ? second : first);
                }
            }
        }

        return pairs;
    }

    private static void addCoordinatePair(List<CoordinatePair> pairs, Variable xVariable, Variable yVariable) {
        boolean exists = pairs.stream()
            .anyMatch(pair -> pair.xVariable() == xVariable && pair.yVariable() == yVariable);
        if (!exists) {
            pairs.add(new CoordinatePair(xVariable, yVariable));
        }
    }

    private static boolean hasMeaningfulSpan(double[] values) {
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

    private static Optional<Variable> findConnectivityVariable(List<Variable> variables, int nodeCount) {
        List<Variable> candidates = variables.stream()
            .filter(variable -> variable.getRank() == 2)
            .filter(variable -> variable.getDataType().isNumeric())
            .filter(variable -> variable.getShape(0) == 3 || variable.getShape(1) == 3)
            .sorted(Comparator.comparingInt(NetcdfDatasetParser::connectivityScore).reversed())
            .collect(Collectors.toList());

        for (Variable candidate : candidates) {
            try {
                normalizeConnectivity(readTriangleConnectivity(candidate), nodeCount);
                return Optional.of(candidate);
            } catch (IOException | IllegalArgumentException ignored) {
                // Try the next candidate.
            }
        }
        return Optional.empty();
    }

    private static Set<String> detectLayerDimensionNames(Map<String, Integer> dimensions, Map<String, double[]> axisCoordinates) {
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

    private static Map<String, double[]> readAxisCoordinates(List<Variable> variables) throws IOException {
        Map<String, double[]> axisCoordinates = new LinkedHashMap<>();
        for (Variable variable : variables) {
            if (variable.getRank() != 1 || !variable.getDataType().isNumeric()) {
                continue;
            }
            List<Dimension> dimensions = variable.getDimensions();
            if (dimensions.size() != 1) {
                continue;
            }
            double[] values = readDoubleArray(variable);
            axisCoordinates.put(variable.getShortName(), values);
            axisCoordinates.putIfAbsent(dimensions.get(0).getShortName(), values);
        }
        return axisCoordinates;
    }

    private static SpatialMatch findSpatialMatch(List<Integer> dimensionSizes, int nodeCount, int elementCount, List<String> dimensionNames) {
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
        return readDoubleArray(variable.read());
    }

    private static double[] readDoubleArray(Array array) {
        double[] values = new double[(int) array.getSize()];
        int index = 0;
        var iterator = array.getIndexIterator();
        while (iterator.hasNext()) {
            values[index++] = iterator.getDoubleNext();
        }
        return values;
    }

    private static int[][] readTriangleConnectivity(Variable variable) throws IOException {
        Array array = variable.read();
        int[] shape = array.getShape();
        boolean transposed = shape[0] == 3 && shape[1] != 3;
        int triangleCount = transposed ? shape[1] : shape[0];
        int[][] triangles = new int[triangleCount][3];
        Index index = array.getIndex();
        for (int triangleIndex = 0; triangleIndex < triangleCount; triangleIndex++) {
            for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
                int row = transposed ? vertexIndex : triangleIndex;
                int column = transposed ? triangleIndex : vertexIndex;
                double rawValue = array.getDouble(index.set(row, column));
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
        Attribute fillValue = variable.findAttributeIgnoreCase("_FillValue");
        if (fillValue == null) {
            fillValue = variable.findAttributeIgnoreCase("missing_value");
        }
        if (fillValue != null && fillValue.getNumericValue() != null) {
            return fillValue.getNumericValue().doubleValue();
        }
        return null;
    }

    private static int coordinateScore(Variable variable) {
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        int score = 0;
        if (name.equals("x") || name.equals("y") || name.equals("lon") || name.equals("lat")) {
            score += 100;
        }
        if (name.contains("lon") || name.contains("lat") || name.contains("coord") || name.equals("x") || name.equals("y")) {
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
        return looksLikeX(first) != looksLikeX(second);
    }

    private static boolean looksLikeX(Variable variable) {
        String name = variable.getShortName().toLowerCase(Locale.ROOT);
        return name.equals("x") || name.contains("lon") || name.contains("east");
    }

    private static boolean looksLikeAxisVariable(String variableName, List<String> dimensionNames) {
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
        if (normalized.startsWith("siglay") || normalized.startsWith("siglev")) {
            return true;
        }
        return dimensionNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .anyMatch(normalized::equals);
    }

    private static int connectivityScore(Variable variable) {
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
    }

    private record SpatialMatch(int axis, boolean elementCentered) {
    }
}
