# Structured Grid Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade NetCDF Viewer to support standard-grid NetCDF datasets for the `1.1.0` release while preserving the current unstructured-triangle workflow and feature set.

**Architecture:** Introduce a geometry-agnostic spatial-domain abstraction, then plug both triangle meshes and structured grids into the same controller flow. Parsing becomes metadata-first and basis-aware, scalar rendering and point query split by geometry implementation, and wave/flow overlays move to shared vector-sampling contracts instead of raw variable-name checks.

**Tech Stack:** Java 17, JavaFX 21, NetCDF-Java 5.9.x, Maven, JUnit 5, existing packaging scripts

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/model/SpatialDomain.java`
  Purpose: Common abstraction for viewport bounds, geometry kind, and coordinate-selection semantics.
- Create: `src/main/java/com/example/netcdfviewer/model/TriangleDomain.java`
  Purpose: Adapter that wraps the existing `MeshData` triangle representation behind `SpatialDomain`.
- Create: `src/main/java/com/example/netcdfviewer/model/StructuredGridData.java`
  Purpose: Immutable storage for structured-grid axes, optional 2D coordinates, and horizontal basis metadata.
- Create: `src/main/java/com/example/netcdfviewer/model/StructuredGridDomain.java`
  Purpose: Structured-grid implementation of `SpatialDomain`.
- Create: `src/main/java/com/example/netcdfviewer/model/CoordinateBinding.java`
  Purpose: Describe one valid horizontal coordinate choice for a dataset.
- Create: `src/main/java/com/example/netcdfviewer/model/HorizontalBasis.java`
  Purpose: Describe how a variable maps onto a spatial domain, including staggered bases.
- Modify: `src/main/java/com/example/netcdfviewer/model/VariableInfo.java`
  Purpose: Replace triangle-only spatial metadata with geometry-aware basis metadata.
- Modify: `src/main/java/com/example/netcdfviewer/io/ParsedDataset.java`
  Purpose: Store `SpatialDomain`, coordinate bindings, selected binding, and backward-compatible dataset metadata.
- Modify: `src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java`
  Purpose: Discover triangle or structured geometry, classify coordinate bindings, and attach basis metadata to variables.
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredGridImageRenderer.java`
  Purpose: Render scalar structured-grid data into the same buffered-image path used by the UI.
- Modify: `src/main/java/com/example/netcdfviewer/ui/ViewportState.java`
  Purpose: Fit and navigate any `SpatialDomain`, not only `MeshData`.
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredPointQuery.java`
  Purpose: Resolve point query on rectilinear and curvilinear structured grids.
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredVectorQuery.java`
  Purpose: Sample vectors from standard grids, including same-basis and staggered-basis cases.
- Modify: `src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java`
  Purpose: Detect vector pairs through metadata and basis compatibility before raw-name fallback.
- Modify: `src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java`
  Purpose: Detect direction/magnitude pairs through metadata and basis compatibility before raw-name fallback.
- Modify: `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java`
  Purpose: Render wave arrows through a geometry-aware vector query contract.
- Modify: `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`
  Purpose: Build flow lines through a geometry-aware vector sampling contract.
- Modify: `src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java`
  Purpose: Move triangle-only vector sampling into a dedicated triangle implementation or adapter.
- Modify: `src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java`
  Purpose: Keep triangle-only point query as one implementation under the shared query path.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add structured-grid coordinate-mode controls shown only for structured datasets.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Orchestrate geometry-aware render, query, coordinate-mode state, and overlay behavior without geometry-heavy branching.
- Create: `src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java`
  Purpose: TDD for structured-grid detection, coordinate bindings, and plottable-variable classification.
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredGridImageRendererTest.java`
  Purpose: TDD for structured-grid scalar rendering.
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredPointQueryTest.java`
  Purpose: TDD for structured-grid point query.
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredVectorQueryTest.java`
  Purpose: TDD for structured-grid vector sampling and staggered U/V support.
- Modify: `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`
  Purpose: Cover metadata-first and staggered-basis vector-pair detection.
- Modify: `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`
  Purpose: Cover metadata-first direction/magnitude pair detection.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Verify structured-grid load path, coordinate-mode UI, query behavior, and overlay control state.
- Modify: `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`
  Purpose: Keep packaged runtime verification aligned with the new geometry path.
- Modify: `src/test/java/com/example/netcdfviewer/io/LocalSampleDatasetTest.java`
  Purpose: Verify local structured-grid samples when present.

### Task 1: Introduce Geometry-Agnostic Spatial Domain Types

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/model/SpatialDomain.java`
- Create: `src/main/java/com/example/netcdfviewer/model/TriangleDomain.java`
- Create: `src/main/java/com/example/netcdfviewer/model/StructuredGridData.java`
- Create: `src/main/java/com/example/netcdfviewer/model/StructuredGridDomain.java`
- Create: `src/main/java/com/example/netcdfviewer/model/CoordinateBinding.java`
- Create: `src/main/java/com/example/netcdfviewer/model/HorizontalBasis.java`
- Modify: `src/main/java/com/example/netcdfviewer/io/ParsedDataset.java`
- Modify: `src/main/java/com/example/netcdfviewer/model/VariableInfo.java`
- Test: `src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java`

- [ ] **Step 1: Write the failing model-shape test**

Create `src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java` with the first geometry assertions:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.model.TriangleDomain;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGridDatasetParserTest {
    private final NetcdfDatasetParser parser = new NetcdfDatasetParser();

    @Test
    void openKeepsTriangleDatasetsOnTriangleDomain() throws Exception {
        ParsedDataset dataset = parser.open(Path.of("HBHQY.nc"));

        assertInstanceOf(TriangleDomain.class, dataset.spatialDomain());
        assertTrue(dataset.coordinateBindings().isEmpty());
        assertTrue(dataset.selectedCoordinateBinding().isEmpty());
    }

    @Test
    void openBuildsStructuredDomainWhenHorizontalCoordinatesExist() throws Exception {
        ParsedDataset dataset = parser.open(Path.of("XTPY-wrf.nc"));

        assertInstanceOf(StructuredGridDomain.class, dataset.spatialDomain());
        assertTrue(dataset.coordinateBindings().size() >= 1);
        assertTrue(dataset.selectedCoordinateBinding().isPresent());
        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, dataset.spatialDomain().kind());
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=StructuredGridDatasetParserTest test`

Expected: FAIL because `SpatialDomain`, `StructuredGridDomain`, coordinate bindings, and the new `ParsedDataset` API do not exist yet.

- [ ] **Step 3: Add the spatial-domain contracts and immutable basis models**

Create these files with the exact minimal contracts:

`src/main/java/com/example/netcdfviewer/model/SpatialDomain.java`

```java
package com.example.netcdfviewer.model;

import java.util.Optional;

public interface SpatialDomain {
    enum Kind {
        TRIANGLE_MESH,
        STRUCTURED_GRID
    }

    Kind kind();

    double minX();

    double maxX();

    double minY();

    double maxY();

    default boolean supportsManualCoordinateSelection() {
        return false;
    }

    default Optional<CoordinateBinding> selectedBinding() {
        return Optional.empty();
    }
}
```

`src/main/java/com/example/netcdfviewer/model/TriangleDomain.java`

```java
package com.example.netcdfviewer.model;

import java.util.Arrays;

public record TriangleDomain(MeshData mesh) implements SpatialDomain {
    public TriangleDomain {
        if (mesh == null) {
            throw new IllegalArgumentException("mesh must not be null");
        }
    }

    @Override
    public Kind kind() {
        return Kind.TRIANGLE_MESH;
    }

    @Override
    public double minX() {
        return Arrays.stream(mesh.x()).min().orElse(0.0);
    }

    @Override
    public double maxX() {
        return Arrays.stream(mesh.x()).max().orElse(0.0);
    }

    @Override
    public double minY() {
        return Arrays.stream(mesh.y()).min().orElse(0.0);
    }

    @Override
    public double maxY() {
        return Arrays.stream(mesh.y()).max().orElse(0.0);
    }
}
```

`src/main/java/com/example/netcdfviewer/model/CoordinateBinding.java`

```java
package com.example.netcdfviewer.model;

import java.util.List;

public record CoordinateBinding(
    String id,
    String xName,
    String yName,
    List<String> horizontalDimensions,
    boolean curvilinear
) {
    public CoordinateBinding {
        horizontalDimensions = List.copyOf(horizontalDimensions);
    }
}
```

`src/main/java/com/example/netcdfviewer/model/HorizontalBasis.java`

```java
package com.example.netcdfviewer.model;

import java.util.List;

public record HorizontalBasis(
    String id,
    String bindingId,
    List<String> horizontalDimensions,
    boolean cellCentered,
    boolean staggered
) {
    public HorizontalBasis {
        horizontalDimensions = List.copyOf(horizontalDimensions);
    }
}
```

`src/main/java/com/example/netcdfviewer/model/StructuredGridData.java`

```java
package com.example.netcdfviewer.model;

public record StructuredGridData(
    CoordinateBinding binding,
    double[] xAxis,
    double[] yAxis,
    double[][] xCoordinates,
    double[][] yCoordinates,
    int width,
    int height
) {
    public boolean rectilinear() {
        return xAxis != null && yAxis != null;
    }

    public boolean curvilinear() {
        return xCoordinates != null && yCoordinates != null;
    }
}
```

`src/main/java/com/example/netcdfviewer/model/StructuredGridDomain.java`

```java
package com.example.netcdfviewer.model;

import java.util.Arrays;
import java.util.Optional;

public record StructuredGridDomain(
    StructuredGridData grid,
    CoordinateBinding selectedBinding
) implements SpatialDomain {
    public StructuredGridDomain {
        if (grid == null || selectedBinding == null) {
            throw new IllegalArgumentException("grid and selectedBinding must not be null");
        }
    }

    @Override
    public Kind kind() {
        return Kind.STRUCTURED_GRID;
    }

    @Override
    public double minX() {
        return grid.rectilinear()
            ? Arrays.stream(grid.xAxis()).min().orElse(0.0)
            : Arrays.stream(grid.xCoordinates()).flatMapToDouble(Arrays::stream).min().orElse(0.0);
    }

    @Override
    public double maxX() {
        return grid.rectilinear()
            ? Arrays.stream(grid.xAxis()).max().orElse(0.0)
            : Arrays.stream(grid.xCoordinates()).flatMapToDouble(Arrays::stream).max().orElse(0.0);
    }

    @Override
    public double minY() {
        return grid.rectilinear()
            ? Arrays.stream(grid.yAxis()).min().orElse(0.0)
            : Arrays.stream(grid.yCoordinates()).flatMapToDouble(Arrays::stream).min().orElse(0.0);
    }

    @Override
    public double maxY() {
        return grid.rectilinear()
            ? Arrays.stream(grid.yAxis()).max().orElse(0.0)
            : Arrays.stream(grid.yCoordinates()).flatMapToDouble(Arrays::stream).max().orElse(0.0);
    }

    @Override
    public boolean supportsManualCoordinateSelection() {
        return true;
    }

    @Override
    public Optional<CoordinateBinding> selectedBinding() {
        return Optional.of(selectedBinding);
    }
}
```

- [ ] **Step 4: Upgrade `ParsedDataset` and `VariableInfo` to carry geometry-aware metadata**

Modify `src/main/java/com/example/netcdfviewer/io/ParsedDataset.java`:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.TriangleDomain;
import com.example.netcdfviewer.model.VariableInfo;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record ParsedDataset(
    Path sourcePath,
    MeshData mesh,
    SpatialDomain spatialDomain,
    List<CoordinateBinding> coordinateBindings,
    CoordinateBinding selectedCoordinateBinding,
    List<VariableInfo> variables,
    Map<String, Integer> dimensions,
    Map<String, String> globalAttributes,
    Map<String, double[]> axisCoordinates,
    List<String> warnings,
    String xVariableName,
    String yVariableName,
    String connectivityVariableName
) {
    public ParsedDataset {
        variables = List.copyOf(variables);
        coordinateBindings = List.copyOf(coordinateBindings);
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
        globalAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(globalAttributes));
        axisCoordinates = Collections.unmodifiableMap(new LinkedHashMap<>(axisCoordinates));
        warnings = List.copyOf(warnings);
        if (spatialDomain == null && mesh != null) {
            spatialDomain = new TriangleDomain(mesh);
        }
    }

    public boolean hasMesh() {
        return mesh != null;
    }

    public boolean hasSpatialDomain() {
        return spatialDomain != null;
    }

    public List<VariableInfo> plottableVariables() {
        return variables.stream().filter(VariableInfo::plottable).collect(Collectors.toList());
    }

    public Optional<double[]> axisValues(String name) {
        return Optional.ofNullable(axisCoordinates.get(name));
    }

    public Optional<CoordinateBinding> selectedCoordinateBinding() {
        return Optional.ofNullable(selectedCoordinateBinding);
    }
}
```

Modify `src/main/java/com/example/netcdfviewer/model/VariableInfo.java`:

```java
package com.example.netcdfviewer.model;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record VariableInfo(
    String name,
    String dataType,
    List<String> dimensionNames,
    List<Integer> dimensionSizes,
    boolean plottable,
    String basisId,
    SpatialDomain.Kind geometryKind,
    boolean cellCentered,
    int layerAxis,
    Double fillValue
) {
    public VariableInfo {
        dimensionNames = List.copyOf(dimensionNames);
        dimensionSizes = List.copyOf(dimensionSizes);
    }

    public boolean layered() {
        return layerAxis >= 0;
    }

    public int layerCount() {
        return layered() ? dimensionSizes.get(layerAxis) : 1;
    }

    public String layerDimensionName() {
        return layered() ? dimensionNames.get(layerAxis) : "";
    }

    public String dimensionSummary() {
        return dimensionNames.stream().map(String::trim).collect(Collectors.joining(", ", "[", "]"));
    }

    public String presentableType() {
        return dataType.toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=StructuredGridDatasetParserTest test`

Expected: the test still fails, but now only because parser logic is not yet producing structured domains. Keep that failure and move to Task 2.

- [ ] **Step 6: Commit the model refactor**

```bash
git add src/main/java/com/example/netcdfviewer/model/SpatialDomain.java src/main/java/com/example/netcdfviewer/model/TriangleDomain.java src/main/java/com/example/netcdfviewer/model/StructuredGridData.java src/main/java/com/example/netcdfviewer/model/StructuredGridDomain.java src/main/java/com/example/netcdfviewer/model/CoordinateBinding.java src/main/java/com/example/netcdfviewer/model/HorizontalBasis.java src/main/java/com/example/netcdfviewer/model/VariableInfo.java src/main/java/com/example/netcdfviewer/io/ParsedDataset.java src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java
git commit -m "refactor: introduce spatial domain abstractions"
```

### Task 2: Detect Structured Grids and Coordinate Bindings in the Parser

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java`
- Modify: `src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/io/LocalSampleDatasetTest.java`

- [ ] **Step 1: Expand the failing parser tests for rectilinear and manual-coordinate candidates**

Append these tests to `src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java`:

```java
    @Test
    void openMarksRectilinearScalarsAsPlottable() throws Exception {
        ParsedDataset dataset = parser.open(Path.of("XTPY-wrf.nc"));

        VariableInfo t2 = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("T2"))
            .findFirst()
            .orElseThrow();

        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, t2.geometryKind());
        assertTrue(t2.basisId().startsWith("binding:"));
        assertTrue(!t2.layered());
    }

    @Test
    void openMarksLayeredStructuredScalarsAsPlottable() throws Exception {
        ParsedDataset dataset = parser.open(Path.of("XTPY-roms.nc"));

        VariableInfo temp = dataset.plottableVariables().stream()
            .filter(variable -> variable.name().equals("temp"))
            .findFirst()
            .orElseThrow();

        assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, temp.geometryKind());
        assertTrue(temp.layered());
        assertEquals(23, temp.layerCount());
    }

    @Test
    void openDoesNotExposeManualCoordinateBindingsForTriangleDatasets() throws Exception {
        ParsedDataset dataset = parser.open(Path.of("HBHQY.nc"));

        assertTrue(dataset.coordinateBindings().isEmpty());
        assertTrue(dataset.selectedCoordinateBinding().isEmpty());
        assertTrue(!dataset.spatialDomain().supportsManualCoordinateSelection());
    }
```

- [ ] **Step 2: Run the focused parser test to verify it fails**

Run: `mvn -q -Dtest=StructuredGridDatasetParserTest test`

Expected: FAIL because `NetcdfDatasetParser` still only recognizes coordinate pairs plus triangle connectivity.

- [ ] **Step 3: Add structured-grid discovery to `NetcdfDatasetParser`**

Modify `src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java` with the exact new flow:

```java
// inside open(...)
SpatialDomain spatialDomain = null;
List<CoordinateBinding> coordinateBindings = List.of();
CoordinateBinding selectedCoordinateBinding = null;

List<CoordinatePair> coordinatePairs = findCoordinatePairs(variables);
if (!coordinatePairs.isEmpty()) {
    CoordinatePair selectedPair = pickTriangleCoordinatePair(coordinatePairs, warnings);
    if (selectedPair != null) {
        double[] selectedX = readDoubleArray(selectedPair.xVariable());
        double[] selectedY = readDoubleArray(selectedPair.yVariable());
        nodeCount = selectedX.length;
        xVariableName = selectedPair.xVariable().getShortName();
        yVariableName = selectedPair.yVariable().getShortName();

        Optional<Variable> connectivityVariable = findConnectivityVariable(variables, nodeCount);
        if (connectivityVariable.isPresent()) {
            Variable variable = connectivityVariable.get();
            mesh = new MeshData(selectedX, selectedY, normalizeConnectivity(readTriangleConnectivity(variable), nodeCount));
            connectivityVariableName = variable.getShortName();
            elementCount = mesh.triangleCount();
            spatialDomain = new TriangleDomain(mesh);
        }
    }
}

if (spatialDomain == null) {
    coordinateBindings = findStructuredCoordinateBindings(variables);
    if (!coordinateBindings.isEmpty()) {
        selectedCoordinateBinding = coordinateBindings.get(0);
        spatialDomain = buildStructuredDomain(netcdfFile, selectedCoordinateBinding);
        xVariableName = selectedCoordinateBinding.xName();
        yVariableName = selectedCoordinateBinding.yName();
    } else {
        warnings.add("No compatible horizontal coordinate binding was found.");
    }
}
```

Add these helper methods to the same file:

```java
private static CoordinatePair pickTriangleCoordinatePair(List<CoordinatePair> coordinatePairs, List<String> warnings) throws IOException {
    for (CoordinatePair pair : coordinatePairs) {
        double[] candidateX = readDoubleArray(pair.xVariable());
        double[] candidateY = readDoubleArray(pair.yVariable());
        if (!hasMeaningfulSpan(candidateX) || !hasMeaningfulSpan(candidateY)) {
            warnings.add("Skipping degenerate coordinate pair " + pair.xVariable().getShortName() + " / " + pair.yVariable().getShortName() + ".");
            continue;
        }
        return pair;
    }
    return null;
}

private static List<CoordinateBinding> findStructuredCoordinateBindings(List<Variable> variables) {
    return variables.stream()
        .filter(variable -> variable.getDataType().isNumeric())
        .filter(variable -> variable.getRank() == 1)
        .flatMap(xVariable -> variables.stream()
            .filter(yVariable -> yVariable != xVariable)
            .filter(yVariable -> yVariable.getDataType().isNumeric())
            .filter(yVariable -> yVariable.getRank() == 1)
            .filter(yVariable -> coordinateKindsDiffer(xVariable, yVariable))
            .map(yVariable -> new CoordinateBinding(
                "binding:" + xVariable.getShortName() + ":" + yVariable.getShortName(),
                looksLikeX(xVariable) ? xVariable.getShortName() : yVariable.getShortName(),
                looksLikeX(xVariable) ? yVariable.getShortName() : xVariable.getShortName(),
                List.of(
                    looksLikeX(xVariable) ? xVariable.getDimensions().get(0).getShortName() : yVariable.getDimensions().get(0).getShortName(),
                    looksLikeX(xVariable) ? yVariable.getDimensions().get(0).getShortName() : xVariable.getDimensions().get(0).getShortName()
                ),
                false
            )))
        .distinct()
        .toList();
}

private StructuredGridDomain buildStructuredDomain(NetcdfFile netcdfFile, CoordinateBinding binding) throws IOException {
    Variable xVariable = netcdfFile.findVariable(binding.xName());
    Variable yVariable = netcdfFile.findVariable(binding.yName());
    double[] xAxis = readDoubleArray(xVariable);
    double[] yAxis = readDoubleArray(yVariable);
    StructuredGridData grid = new StructuredGridData(binding, xAxis, yAxis, null, null, xAxis.length, yAxis.length);
    return new StructuredGridDomain(grid, binding);
}
```

Update `describeVariable(...)` to match structured bases:

```java
if (spatialDomain != null && spatialDomain.kind() == SpatialDomain.Kind.STRUCTURED_GRID) {
    HorizontalBasis basis = findStructuredBasis(dimensionNames, dimensionSizes, coordinateBindings);
    if (numeric && basis != null && !coordinateAxis) {
        List<Integer> remainingAxes = new ArrayList<>();
        for (int axis = 0; axis < dimensionSizes.size(); axis++) {
            if (!basis.horizontalDimensions().contains(dimensionNames.get(axis)) && dimensionSizes.get(axis) > 1) {
                remainingAxes.add(axis);
            }
        }
        if (remainingAxes.isEmpty()) {
            plottable = true;
        } else if (remainingAxes.size() == 1 && layerDimensionNames.contains(dimensionNames.get(remainingAxes.get(0)).toLowerCase(Locale.ROOT))) {
            layerAxis = remainingAxes.get(0);
            plottable = true;
        }
        basisId = basis.id();
        geometryKind = SpatialDomain.Kind.STRUCTURED_GRID;
        cellCentered = basis.cellCentered();
    }
}
```

- [ ] **Step 4: Add local-sample coverage for structured datasets when files exist**

Modify `src/test/java/com/example/netcdfviewer/io/LocalSampleDatasetTest.java`:

```java
    @Test
    void localStructuredSamplesExposeStructuredDomainWhenPresent() throws Exception {
        List<String> candidates = List.of("XTPY-wrf.nc", "XTPY-roms.nc", "ydy-wrf.nc", "ydy-roms.nc");
        NetcdfDatasetParser parser = new NetcdfDatasetParser();

        for (String fileName : candidates) {
            try {
                Path path = SampleDatasetPaths.resolve(fileName);
                ParsedDataset dataset = parser.open(path);
                assertEquals(SpatialDomain.Kind.STRUCTURED_GRID, dataset.spatialDomain().kind(), fileName);
                assertTrue(dataset.selectedCoordinateBinding().isPresent(), fileName);
            } catch (IllegalStateException ignored) {
                // sample missing locally, skip
            }
        }
    }
```

- [ ] **Step 5: Run the focused parser suite to verify it passes**

Run: `mvn -q -Dtest=StructuredGridDatasetParserTest,LocalSampleDatasetTest test`

Expected: PASS.

- [ ] **Step 6: Commit the parser upgrade**

```bash
git add src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java src/test/java/com/example/netcdfviewer/io/StructuredGridDatasetParserTest.java src/test/java/com/example/netcdfviewer/io/LocalSampleDatasetTest.java
git commit -m "feat: detect structured grid spatial domains"
```

### Task 3: Render Structured-Grid Scalar Fields and Fit the Viewport

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredGridImageRenderer.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/ViewportState.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredGridImageRendererTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/ViewportStateTest.java`

- [ ] **Step 1: Write the failing structured-renderer tests**

Create `src/test/java/com/example/netcdfviewer/render/StructuredGridImageRendererTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredGridImageRendererTest {
    private final StructuredGridImageRenderer renderer = new StructuredGridImageRenderer();

    @Test
    void renderProducesColoredImageForRectilinearGrid() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0},
                new double[]{0.0, 1.0},
                null,
                null,
                3,
                2
            ),
            new CoordinateBinding("binding:lon:lat", "lon", "lat", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        BufferedImage image = renderer.render(
            200,
            120,
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0},
            ColorMaps.viridis(),
            new RangeStats(1.0, 6.0, 6),
            snapshot,
            false,
            null
        );

        assertEquals(200, image.getWidth());
        assertEquals(120, image.getHeight());
        assertTrue(image.getRGB(100, 60) != 0);
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=StructuredGridImageRendererTest,ViewportStateTest test`

Expected: FAIL because no structured-grid renderer exists and viewport fitting still depends on `MeshData`.

- [ ] **Step 3: Add the structured-grid renderer and generalize viewport fit**

Create `src/main/java/com/example/netcdfviewer/render/StructuredGridImageRenderer.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class StructuredGridImageRenderer {
    public BufferedImage render(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue
    ) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setColor(java.awt.Color.decode("#F8FBFD"));
            graphics.fillRect(0, 0, width, height);
            if (domain == null || rangeStats == null || rangeStats.empty()) {
                return image;
            }

            java.awt.Color[] palette = buildPalette(colorMap);
            double[] xAxis = domain.grid().xAxis();
            double[] yAxis = domain.grid().yAxis();
            int valueIndex = 0;
            for (int y = 0; y < yAxis.length; y++) {
                for (int x = 0; x < xAxis.length; x++) {
                    double value = values[valueIndex++];
                    if (!RenderMath.isRenderableValue(value, fillValue)) {
                        continue;
                    }
                    java.awt.Color fill = palette[(int) Math.round(RenderMath.normalize(value, rangeStats.min(), rangeStats.max()) * 255.0)];
                    int screenX = (int) Math.round(snapshot.screenX(xAxis[x]));
                    int screenY = (int) Math.round(snapshot.screenY(yAxis[y]));
                    graphics.setColor(fill);
                    graphics.fillRect(screenX, screenY, 4, 4);
                }
            }
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private java.awt.Color[] buildPalette(ColorMap colorMap) {
        java.awt.Color[] palette = new java.awt.Color[256];
        for (int index = 0; index < palette.length; index++) {
            javafx.scene.paint.Color fxColor = colorMap.colorAt(index / 255.0);
            palette[index] = new java.awt.Color((float) fxColor.getRed(), (float) fxColor.getGreen(), (float) fxColor.getBlue(), (float) fxColor.getOpacity());
        }
        return palette;
    }
}
```

Modify `src/main/java/com/example/netcdfviewer/ui/ViewportState.java` so fit methods accept `SpatialDomain`:

```java
    public void ensureFitted(SpatialDomain domain, double width, double height) {
        if (!initialized) {
            fit(domain, width, height);
        }
    }

    public void fit(SpatialDomain domain, double width, double height) {
        if (domain == null || width <= 0 || height <= 0) {
            return;
        }
        double minX = domain.minX();
        double maxX = domain.maxX();
        double minY = domain.minY();
        double maxY = domain.maxY();
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minY) || !Double.isFinite(maxY)) {
            return;
        }
        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);
        double padding = 24.0;
        scale = Math.min((width - padding * 2.0) / spanX, (height - padding * 2.0) / spanY);
        offsetX = padding - minX * scale;
        offsetY = height - padding + minY * scale;
        initialized = true;
    }
```

Modify `src/main/java/com/example/netcdfviewer/ui/MainController.java` so scalar rendering dispatches by domain kind:

```java
    private final StructuredGridImageRenderer structuredImageRenderer = new StructuredGridImageRenderer();
```

```java
            viewportState.ensureFitted(currentDataset.spatialDomain(), canvas.getWidth(), canvas.getHeight());
```

```java
                var bufferedImage = dataset.spatialDomain().kind() == SpatialDomain.Kind.TRIANGLE_MESH
                    ? imageRenderer.render(
                        width,
                        height,
                        dataset.mesh(),
                        values,
                        colorMap,
                        displayRange,
                        snapshot,
                        variable.cellCentered(),
                        variable.fillValue()
                    )
                    : structuredImageRenderer.render(
                        width,
                        height,
                        (StructuredGridDomain) dataset.spatialDomain(),
                        values,
                        colorMap,
                        displayRange,
                        snapshot,
                        variable.cellCentered(),
                        variable.fillValue()
                    );
```

- [ ] **Step 4: Run the focused renderer tests to verify they pass**

Run: `mvn -q -Dtest=StructuredGridImageRendererTest,ViewportStateTest test`

Expected: PASS.

- [ ] **Step 5: Commit the structured scalar render path**

```bash
git add src/main/java/com/example/netcdfviewer/render/StructuredGridImageRenderer.java src/main/java/com/example/netcdfviewer/ui/ViewportState.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/render/StructuredGridImageRendererTest.java src/test/java/com/example/netcdfviewer/ui/ViewportStateTest.java
git commit -m "feat: render structured grid scalar fields"
```

### Task 4: Add Structured-Grid Point Query and Coordinate Selection UI

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredPointQuery.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredPointQueryTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write the failing structured-query and UI tests**

Create `src/test/java/com/example/netcdfviewer/render/StructuredPointQueryTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredPointQueryTest {
    @Test
    void queryReturnsDirectValueForStructuredNodeHit() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                null,
                null,
                2,
                2
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredPointQuery.Result result = StructuredPointQuery.query(
            domain,
            new double[]{1.0, 2.0, 3.0, 4.0},
            snapshot,
            100.0,
            0.0,
            false,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(4.0, result.value());
    }
}
```

Append UI tests to `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`:

```java
    @Test
    void structuredDatasetShowsCoordinateModeControls() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));

                assertTrue(view.getCoordinateModeCombo().isVisible());
                assertTrue(view.getCoordinateXCombo().isVisible());
                assertTrue(view.getCoordinateYCombo().isVisible());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `mvn -q -Dtest=StructuredPointQueryTest,MainControllerLoadFileTest test`

Expected: FAIL because no structured point query exists and no coordinate-mode controls are exposed.

- [ ] **Step 3: Implement the structured point query and UI controls**

Create `src/main/java/com/example/netcdfviewer/render/StructuredPointQuery.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

public final class StructuredPointQuery {
    private StructuredPointQuery() {
    }

    public static Result query(
        StructuredGridDomain domain,
        double[] values,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean cellCentered,
        Double fillValue,
        int layerIndex
    ) {
        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);
        double[] xAxis = domain.grid().xAxis();
        double[] yAxis = domain.grid().yAxis();
        int xIndex = nearestIndex(xAxis, worldX);
        int yIndex = nearestIndex(yAxis, worldY);
        int valueIndex = yIndex * xAxis.length + xIndex;
        double value = valueIndex < values.length ? values[valueIndex] : Double.NaN;
        if (!RenderMath.isRenderableValue(value, fillValue)) {
            return new Result(true, worldX, worldY, valueIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
        }
        return new Result(true, worldX, worldY, valueIndex, value, layerIndex, Reason.HIT);
    }

    private static int nearestIndex(double[] axis, double value) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < axis.length; index++) {
            double distance = Math.abs(axis[index] - value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    public enum Reason {
        HIT,
        INVALID_VALUE
    }

    public record Result(boolean hit, double worldX, double worldY, int cellIndex, double value, int layerIndex, Reason reason) {
        public boolean hasValue() {
            return hit && reason == Reason.HIT && Double.isFinite(value);
        }
    }
}
```

Modify `src/main/java/com/example/netcdfviewer/ui/MainView.java` to add:

```java
    private final ComboBox<String> coordinateModeCombo = new ComboBox<>();
    private final ComboBox<String> coordinateXCombo = new ComboBox<>();
    private final ComboBox<String> coordinateYCombo = new ComboBox<>();
```

Initialize them:

```java
        coordinateModeCombo.setItems(FXCollections.observableArrayList("Auto", "Manual"));
        coordinateModeCombo.getSelectionModel().select("Auto");
        coordinateModeCombo.setVisible(false);
        coordinateXCombo.setVisible(false);
        coordinateYCombo.setVisible(false);
```

Add them to the right panel above overlay controls and expose getters:

```java
    public ComboBox<String> getCoordinateModeCombo() {
        return coordinateModeCombo;
    }

    public ComboBox<String> getCoordinateXCombo() {
        return coordinateXCombo;
    }

    public ComboBox<String> getCoordinateYCombo() {
        return coordinateYCombo;
    }
```

Modify `src/main/java/com/example/netcdfviewer/ui/MainController.java` with structured-only coordinate UI state:

```java
    private void updateCoordinateControls() {
        boolean structured = currentDataset != null
            && currentDataset.hasSpatialDomain()
            && currentDataset.spatialDomain().kind() == SpatialDomain.Kind.STRUCTURED_GRID;
        view.getCoordinateModeCombo().setVisible(structured);
        view.getCoordinateXCombo().setVisible(structured);
        view.getCoordinateYCombo().setVisible(structured);
        if (!structured) {
            return;
        }
        List<String> bindings = currentDataset.coordinateBindings().stream()
            .map(CoordinateBinding::id)
            .toList();
        view.getCoordinateXCombo().setItems(FXCollections.observableArrayList(
            currentDataset.coordinateBindings().stream().map(CoordinateBinding::xName).toList()
        ));
        view.getCoordinateYCombo().setItems(FXCollections.observableArrayList(
            currentDataset.coordinateBindings().stream().map(CoordinateBinding::yName).toList()
        ));
        CoordinateBinding selected = currentDataset.selectedCoordinateBinding().orElse(currentDataset.coordinateBindings().get(0));
        view.getCoordinateXCombo().getSelectionModel().select(selected.xName());
        view.getCoordinateYCombo().getSelectionModel().select(selected.yName());
        view.getCoordinateXCombo().setDisable(currentDataset.coordinateBindings().size() <= 1);
        view.getCoordinateYCombo().setDisable(currentDataset.coordinateBindings().size() <= 1);
    }
```

Call `updateCoordinateControls()` from dataset activation and clear state. Update `queryAtScreenPoint(...)`:

```java
        if (context.dataset().spatialDomain().kind() == SpatialDomain.Kind.STRUCTURED_GRID) {
            StructuredPointQuery.Result result = StructuredPointQuery.query(
                (StructuredGridDomain) context.dataset().spatialDomain(),
                context.values(),
                context.snapshot(),
                screenX,
                screenY,
                context.cellCentered(),
                context.fillValue(),
                context.layerIndex()
            );
            if (!result.hasValue()) {
                setStatus("No structured-grid value at clicked location.");
                return;
            }
            setStatus("Query " + context.variable().name() + " at (" + format(result.worldX()) + ", " + format(result.worldY()) + "): cell #" + result.cellIndex() + ", value=" + format(result.value()));
            return;
        }
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `mvn -q -Dtest=StructuredPointQueryTest,MainControllerLoadFileTest test`

Expected: PASS.

- [ ] **Step 5: Commit the structured query and coordinate UI**

```bash
git add src/main/java/com/example/netcdfviewer/render/StructuredPointQuery.java src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/render/StructuredPointQueryTest.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "feat: add structured grid query and coordinate controls"
```

### Task 5: Generalize Vector Pair Detection and Structured Wave Overlay

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java`
- Modify: `src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java`
- Modify: `src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java`
- Modify: `src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java`
- Create: `src/main/java/com/example/netcdfviewer/render/StructuredVectorQuery.java`
- Modify: `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java`
- Modify: `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`
- Create: `src/test/java/com/example/netcdfviewer/render/StructuredVectorQueryTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java`

- [ ] **Step 1: Write the failing vector-pair and structured-wave tests**

Append this test to `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`:

```java
    @Test
    void findAcceptsCompatibleStructuredStaggeredUvPair() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("u", true, "basis:u", SpatialDomain.Kind.STRUCTURED_GRID, false, 0, List.of("depth", "y_u", "x_u"), List.of(3, 10, 11)),
            variable("v", true, "basis:v", SpatialDomain.Kind.STRUCTURED_GRID, false, 0, List.of("depth", "y_v", "x_v"), List.of(3, 11, 10))
        ));

        assertTrue(pair.isPresent());
    }
```

Create `src/test/java/com/example/netcdfviewer/render/StructuredVectorQueryTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.StructuredGridData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredVectorQueryTest {
    @Test
    void queryReturnsVelocityForSameBasisStructuredVectorField() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0},
                new double[]{0.0, 1.0},
                null,
                null,
                2,
                2
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(100.0, 0.0, 100.0);

        StructuredVectorQuery.Result result = StructuredVectorQuery.query(
            domain,
            new double[]{1.0, 1.0, 1.0, 1.0},
            new double[]{2.0, 2.0, 2.0, 2.0},
            snapshot,
            50.0,
            50.0,
            null,
            null,
            0
        );

        assertTrue(result.hasVelocity());
    }
}
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `mvn -q -Dtest=VelocityVariablePairFinderTest,WaveVariablePairFinderTest,StructuredVectorQueryTest,WaveArrowOverlayRendererTest test`

Expected: FAIL because basis-aware metadata and structured-vector sampling do not exist yet.

- [ ] **Step 3: Generalize vector-pair metadata and add structured-vector sampling**

Modify `src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java` and `WaveVariablePair.java` so basis compatibility replaces triangle-only axis checks:

```java
        if (eastwardVariable.geometryKind() != northwardVariable.geometryKind()) {
            throw new IllegalArgumentException("Velocity variables must share the same geometry kind.");
        }
        if (eastwardVariable.geometryKind() == SpatialDomain.Kind.TRIANGLE_MESH
            && !eastwardVariable.basisId().equals(northwardVariable.basisId())) {
            throw new IllegalArgumentException("Triangle velocity variables must share the same basis.");
        }
```

Modify both pair finders with metadata-first detection:

```java
        Optional<VelocityVariablePair> metadataPair = findCompatiblePairBySemantics(variables);
        if (metadataPair.isPresent()) {
            return metadataPair;
        }
        Optional<VelocityVariablePair> uv = findExactPair(variables, "u", "v");
        if (uv.isPresent()) {
            return uv;
        }
        return findExactPair(variables, "ua", "va");
```

Create `src/main/java/com/example/netcdfviewer/render/StructuredVectorQuery.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

public final class StructuredVectorQuery {
    private StructuredVectorQuery() {
    }

    public static Result query(
        StructuredGridDomain domain,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        StructuredPointQuery.Result uResult = StructuredPointQuery.query(domain, uValues, snapshot, screenX, screenY, false, uFillValue, layerIndex);
        StructuredPointQuery.Result vResult = StructuredPointQuery.query(domain, vValues, snapshot, screenX, screenY, false, vFillValue, layerIndex);
        if (!uResult.hasValue() || !vResult.hasValue()) {
            return new Result(false, snapshot.worldX(screenX), snapshot.worldY(screenY), Double.NaN, Double.NaN, Double.NaN, layerIndex);
        }
        double u = uResult.value();
        double v = vResult.value();
        return new Result(true, snapshot.worldX(screenX), snapshot.worldY(screenY), u, v, Math.hypot(u, v), layerIndex);
    }

    public record Result(boolean hit, double worldX, double worldY, double u, double v, double speed, int layerIndex) {
        public boolean hasVelocity() {
            return hit && Double.isFinite(u) && Double.isFinite(v);
        }
    }
}
```

Modify `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java` to branch on geometry kind and query through `StructuredVectorQuery` for structured datasets.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `mvn -q -Dtest=VelocityVariablePairFinderTest,WaveVariablePairFinderTest,StructuredVectorQueryTest,WaveArrowOverlayRendererTest test`

Expected: PASS.

- [ ] **Step 5: Commit the generalized vector path**

```bash
git add src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java src/main/java/com/example/netcdfviewer/render/StructuredVectorQuery.java src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java src/test/java/com/example/netcdfviewer/render/StructuredVectorQueryTest.java src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java
git commit -m "feat: support structured vector overlays"
```

### Task 6: Support Structured-Grid Flow Lines

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java`
- Modify: `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write the failing structured-flow tests**

Append to `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`:

```java
    @Test
    void generateBuildsStructuredFlowLinesForUniformVectorField() {
        StructuredGridDomain domain = new StructuredGridDomain(
            new StructuredGridData(
                new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false),
                new double[]{0.0, 1.0, 2.0, 3.0},
                new double[]{0.0, 1.0, 2.0, 3.0},
                null,
                null,
                4,
                4
            ),
            new CoordinateBinding("binding:x:y", "x", "y", List.of("x", "y"), false)
        );

        List<FlowLineGenerator.FlowLine> lines = generator.generateStructured(
            domain,
            new double[16],
            filled(16, 1.0),
            SNAPSHOT,
            120,
            120,
            null,
            null,
            0
        );

        assertTrue(!lines.isEmpty());
    }
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `mvn -q -Dtest=FlowVectorQueryTest,FlowLineGeneratorTest,MainControllerLoadFileTest test`

Expected: FAIL because no structured flowline path exists.

- [ ] **Step 3: Add the structured flowline generation branch**

Modify `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`:

```java
    public List<FlowLine> generateStructured(
        StructuredGridDomain domain,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        if (domain == null || uValues == null || vValues == null || snapshot == null || width <= 0 || height <= 0) {
            return List.of();
        }
        boolean[][] occupancy = new boolean[Math.max(1, (int) Math.ceil(height / (double) OCCUPANCY_CELL_SIZE))]
            [Math.max(1, (int) Math.ceil(width / (double) OCCUPANCY_CELL_SIZE))];
        List<FlowLine> lines = new ArrayList<>();
        for (int screenY = SEED_SPACING / 2; screenY < height; screenY += SEED_SPACING) {
            for (int screenX = SEED_SPACING / 2; screenX < width; screenX += SEED_SPACING) {
                if (isOccupied(occupancy, screenX, screenY)) {
                    continue;
                }
                StructuredVectorQuery.Result seed = StructuredVectorQuery.query(domain, uValues, vValues, snapshot, screenX, screenY, uFillValue, vFillValue, layerIndex);
                if (!seed.hasVelocity() || seed.speed() <= MIN_SPEED) {
                    continue;
                }
                FlowLine line = buildStructuredLineFromSeed(domain, uValues, vValues, snapshot, width, height, uFillValue, vFillValue, layerIndex, screenX, screenY);
                if (line == null || line.points().size() < 2 || line.totalLength() < MIN_LINE_LENGTH) {
                    continue;
                }
                lines.add(line);
                markOccupied(occupancy, line);
            }
        }
        return lines;
    }
```

Modify `src/main/java/com/example/netcdfviewer/ui/MainController.java` where flow overlays are prepared:

```java
                        List<FlowLineGenerator.FlowLine> lines = dataset.spatialDomain().kind() == SpatialDomain.Kind.TRIANGLE_MESH
                            ? flowLineGenerator.generate(
                                dataset.mesh(),
                                uValues,
                                vValues,
                                snapshot,
                                width,
                                height,
                                flowPair.cellCentered(),
                                flowPair.eastwardVariable().fillValue(),
                                flowPair.northwardVariable().fillValue(),
                                flowLayerIndex
                            )
                            : flowLineGenerator.generateStructured(
                                (StructuredGridDomain) dataset.spatialDomain(),
                                uValues,
                                vValues,
                                snapshot,
                                width,
                                height,
                                flowPair.eastwardVariable().fillValue(),
                                flowPair.northwardVariable().fillValue(),
                                flowLayerIndex
                            );
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `mvn -q -Dtest=FlowVectorQueryTest,FlowLineGeneratorTest,MainControllerLoadFileTest test`

Expected: PASS.

- [ ] **Step 5: Commit the structured flowline support**

```bash
git add src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "feat: support structured flowline overlays"
```

### Task 7: Run Integration, Export, and Packaging Regression

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/NanhaiRenderingTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/PngExportSupportTest.java`

- [ ] **Step 1: Add failing integration expectations for structured-grid export/runtime**

Append structured-grid expectations to `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`:

```java
    @Test
    void parserProbeAcceptsStructuredGridSamplesWhenPresent() throws Exception {
        String output = runParserProbe("XTPY-wrf.nc");
        assertTrue(output.contains("STRUCTURED_GRID"), output);
    }
```

Append export coverage to `src/test/java/com/example/netcdfviewer/ui/PngExportSupportTest.java`:

```java
    @Test
    void writePngSupportsStructuredGridSnapshots() throws Exception {
        WritableImage image = new WritableImage(80, 60);
        Path output = Files.createTempFile("structured-grid-export", ".png");
        PngExportSupport.writePng(image, output);
        assertTrue(Files.size(output) > 0L);
    }
```

- [ ] **Step 2: Run the regression suite to verify it fails or exposes remaining gaps**

Run: `mvn -q "-Dtest=StructuredGridDatasetParserTest,StructuredGridImageRendererTest,StructuredPointQueryTest,StructuredVectorQueryTest,VelocityVariablePairFinderTest,WaveVariablePairFinderTest,FlowLineGeneratorTest,MainControllerLoadFileTest,PngExportSupportTest,PackagedRuntimeCompatibilityTest" test`

Expected: FAIL only if any structured-grid integration path is still incomplete.

- [ ] **Step 3: Align runtime probes, export, and package verification**

Modify runtime/export tests and probes with exact structured-grid expectations:

```java
// in ParserProbeMain output
System.out.println("domain=" + (dataset.spatialDomain() == null ? "none" : dataset.spatialDomain().kind()));
```

```java
// in packaged compatibility test
assertTrue(script.contains("function Get-ProjectVersion"));
assertTrue(script.contains("Installer created under"));
```

Run packaging:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1
```

Expected: `target/installer/NetCDFViewer-1.1.0.exe` is created and the script ends with `Installer created under ...`.

- [ ] **Step 4: Run the full Maven suite**

Run: `mvn -q test`

Expected: PASS.

- [ ] **Step 5: Commit the regression and packaging updates**

```bash
git add src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java src/test/java/com/example/netcdfviewer/ui/NanhaiRenderingTest.java src/test/java/com/example/netcdfviewer/ui/PngExportSupportTest.java src/test/java/com/example/netcdfviewer/runtime/ParserProbeMain.java
git commit -m "test: verify structured grid runtime and export support"
```

## Self-Review

Spec coverage checked:

- structured-grid spatial abstraction: Task 1
- metadata-first parsing and coordinate bindings: Task 2
- scalar render and viewport fit: Task 3
- point query and manual coordinate controls: Task 4
- generalized vector detection and wave overlay: Task 5
- structured-grid flow overlays: Task 6
- export/runtime/package verification: Task 7

Placeholder scan checked:

- no `TODO`
- no `TBD`
- no “similar to previous task” shortcuts
- each task contains concrete files, commands, and code blocks

Type consistency checked:

- `SpatialDomain.Kind` is used consistently across parser, controller, and tests
- `basisId`, `geometryKind`, and `cellCentered` replace triangle-only metadata consistently in later tasks
- structured-grid query and vector sampling names are stable across later tasks
