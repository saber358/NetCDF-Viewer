# U/V Flowline Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional animated flow-line overlay based on `u/v` velocity variables so compatible NetCDF datasets can show moving current bands above the existing scalar render.

**Architecture:** Extend the current scalar-plus-overlay pipeline with a velocity-pair finder, a flow vector sampling helper, a flowline generator that builds cached polylines from the current layer and viewport, and a renderer that animates a highlight band along those cached lines. `MainController` remains the orchestrator and only manages overlay availability, cache invalidation, and redraw timing.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing NetCDF parser/render pipeline, existing Maven packaging scripts

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java`
  Purpose: Immutable validated pair of `u/v` or `ua/va` variables with layer and mesh-basis helpers.
- Create: `src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java`
  Purpose: Detect `u/v` first and fall back to `ua/va` when `u/v` is unavailable.
- Create: `src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java`
  Purpose: Sample the velocity vector at an arbitrary screen or world position by resolving the containing triangle and returning `u/v` components and speed.
- Create: `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`
  Purpose: Seed, integrate, de-duplicate, and cache flowline polylines for the current layer and viewport.
- Create: `src/main/java/com/example/netcdfviewer/render/FlowLineOverlayRenderer.java`
  Purpose: Draw static flowline base strokes and animate a moving highlight band along cached flowline polylines.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add one `Flow lines` checkbox to the right-side control panel and expose it through a getter.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Manage velocity-pair availability, flowline cache invalidation, animation timing, and flowline overlay rendering.
- Create: `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`
  Purpose: TDD coverage for `u/v` priority, `ua/va` fallback, and compatibility validation.
- Create: `src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java`
  Purpose: TDD coverage for element-centered velocity sampling, invalid-value handling, and screen-to-world lookup.
- Create: `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`
  Purpose: TDD coverage for streamline generation, stop rules, and duplicate suppression.
- Create: `src/test/java/com/example/netcdfviewer/render/FlowLineOverlayRendererTest.java`
  Purpose: TDD coverage for moving highlight-band rendering state and phase progression.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Verify flowline toggle availability and render stability.
- Modify: `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`
  Purpose: Keep packaging/runtime assertions aligned with the updated script and overlay path.

### Task 1: Detect Compatible Velocity Pairs With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java`
- Create: `src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java`
- Test: `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`

- [ ] **Step 1: Write the failing pair-detection tests**

Create `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VelocityVariablePair;
import com.example.netcdfviewer.model.VariableInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityVariablePairFinderTest {
    private final VelocityVariablePairFinder finder = new VelocityVariablePairFinder();

    @Test
    void findPrefersUVWhenBothUvAndUavaExist() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("ua", true, 0, true, -1, List.of("nele"), List.of(12)),
            variable("va", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("u", pair.get().eastwardVariable().name());
        assertEquals("v", pair.get().northwardVariable().name());
    }

    @Test
    void findFallsBackToUavaWhenUvIsUnavailable() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("ua", true, 0, true, -1, List.of("nele"), List.of(12)),
            variable("va", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("ua", pair.get().eastwardVariable().name());
        assertEquals("va", pair.get().northwardVariable().name());
    }

    @Test
    void findReturnsEmptyWhenVelocityBasisDiffers() {
        Optional<VelocityVariablePair> pair = finder.find(List.of(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 1, false, 0, List.of("siglay", "node"), List.of(5, 12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void resolveLayerIndexClampsLayeredVelocityPair() {
        VelocityVariablePair pair = new VelocityVariablePair(
            variable("u", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12)),
            variable("v", true, 0, true, 0, List.of("siglay", "nele"), List.of(5, 12))
        );

        assertEquals(0, pair.resolveLayerIndex(-1));
        assertEquals(2, pair.resolveLayerIndex(2));
        assertEquals(4, pair.resolveLayerIndex(99));
    }

    private static VariableInfo variable(
        String name,
        boolean plottable,
        int nodeAxis,
        boolean elementCentered,
        int layerAxis,
        List<String> dimensionNames,
        List<Integer> dimensionSizes
    ) {
        return new VariableInfo(
            name,
            "FLOAT",
            dimensionNames,
            dimensionSizes,
            plottable,
            nodeAxis,
            elementCentered,
            layerAxis,
            null
        );
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=VelocityVariablePairFinderTest test`

Expected: FAIL because `VelocityVariablePair` and `VelocityVariablePairFinder` do not exist yet.

- [ ] **Step 3: Add the validated velocity pair record**

Create `src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java`:

```java
package com.example.netcdfviewer.model;

import java.util.Objects;

public record VelocityVariablePair(
    VariableInfo eastwardVariable,
    VariableInfo northwardVariable
) {
    public VelocityVariablePair {
        Objects.requireNonNull(eastwardVariable, "eastwardVariable");
        Objects.requireNonNull(northwardVariable, "northwardVariable");
        if (!eastwardVariable.plottable() || !northwardVariable.plottable()) {
            throw new IllegalArgumentException("Velocity variables must both be plottable.");
        }
        if (eastwardVariable.nodeAxis() != northwardVariable.nodeAxis()) {
            throw new IllegalArgumentException("Velocity variables must share the same spatial axis.");
        }
        if (eastwardVariable.elementCentered() != northwardVariable.elementCentered()) {
            throw new IllegalArgumentException("Velocity variables must share the same mesh basis.");
        }
        if (eastwardVariable.layered() != northwardVariable.layered()) {
            throw new IllegalArgumentException("Velocity variables must share the same layered shape.");
        }
        if (eastwardVariable.layered()
            && (!eastwardVariable.layerDimensionName().equals(northwardVariable.layerDimensionName())
            || eastwardVariable.layerCount() != northwardVariable.layerCount())) {
            throw new IllegalArgumentException("Velocity variables must share the same layer dimension.");
        }
    }

    public boolean layered() {
        return eastwardVariable.layered();
    }

    public boolean elementCentered() {
        return eastwardVariable.elementCentered();
    }

    public int resolveLayerIndex(int requestedLayerIndex) {
        if (!layered()) {
            return 0;
        }
        return Math.max(0, Math.min(requestedLayerIndex, eastwardVariable.layerCount() - 1));
    }
}
```

- [ ] **Step 4: Add the velocity-pair finder**

Create `src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java`:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.VelocityVariablePair;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VelocityVariablePairFinder {
    public Optional<VelocityVariablePair> find(ParsedDataset dataset) {
        return find(dataset.variables());
    }

    Optional<VelocityVariablePair> find(List<VariableInfo> variables) {
        Optional<VelocityVariablePair> uv = findExactPair(variables, "u", "v");
        if (uv.isPresent()) {
            return uv;
        }
        return findExactPair(variables, "ua", "va");
    }

    private Optional<VelocityVariablePair> findExactPair(List<VariableInfo> variables, String eastwardName, String northwardName) {
        VariableInfo eastward = findExactName(variables, eastwardName);
        VariableInfo northward = findExactName(variables, northwardName);
        if (eastward == null || northward == null) {
            return Optional.empty();
        }
        if (!eastward.plottable() || !northward.plottable()) {
            return Optional.empty();
        }
        if (eastward.nodeAxis() != northward.nodeAxis()) {
            return Optional.empty();
        }
        if (eastward.elementCentered() != northward.elementCentered()) {
            return Optional.empty();
        }
        if (eastward.layered() != northward.layered()) {
            return Optional.empty();
        }
        if (eastward.layered()
            && (!eastward.layerDimensionName().equals(northward.layerDimensionName())
            || eastward.layerCount() != northward.layerCount())) {
            return Optional.empty();
        }
        return Optional.of(new VelocityVariablePair(eastward, northward));
    }

    private VariableInfo findExactName(List<VariableInfo> variables, String expectedName) {
        return variables.stream()
            .filter(variable -> variable.name().toLowerCase(Locale.ROOT).equals(expectedName))
            .findFirst()
            .orElse(null);
    }
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=VelocityVariablePairFinderTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/model/VelocityVariablePair.java src/main/java/com/example/netcdfviewer/io/VelocityVariablePairFinder.java src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java
git commit -m "feat: detect compatible flow velocity pairs"
```

### Task 2: Add Flow Vector Sampling With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java`

- [ ] **Step 1: Write the failing vector-query tests**

Create `src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowVectorQueryTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 10.0, 0.0},
        new double[]{0.0, 0.0, 10.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 0.0);

    @Test
    void queryReturnsElementCenteredVelocityInsideTriangle() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{3.0},
            new double[]{4.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            null,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(3.0, result.u());
        assertEquals(4.0, result.v());
        assertEquals(5.0, result.speed(), 1e-9);
    }

    @Test
    void queryReturnsMissOutsideMesh() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{3.0},
            new double[]{4.0},
            SNAPSHOT,
            20.0,
            -20.0,
            true,
            null,
            null,
            0
        );

        assertFalse(result.hit());
        assertEquals(FlowVectorQuery.Reason.NO_HIT, result.reason());
    }

    @Test
    void queryReturnsInvalidWhenOneComponentIsFillValue() {
        FlowVectorQuery.Result result = FlowVectorQuery.query(
            TRIANGLE_MESH,
            new double[]{-9999.0},
            new double[]{4.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            -9999.0,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(FlowVectorQuery.Reason.INVALID_VALUE, result.reason());
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=FlowVectorQueryTest test`

Expected: FAIL because `FlowVectorQuery` does not exist yet.

- [ ] **Step 3: Add the pure flow-vector query helper**

Create `src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

public final class FlowVectorQuery {
    private static final double TOLERANCE = 1e-9;

    private FlowVectorQuery() {
    }

    public static Result query(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        double worldX = snapshot.worldX(screenX);
        double worldY = snapshot.worldY(screenY);
        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            Barycentric barycentric = barycentric(mesh, triangle, worldX, worldY);
            if (!barycentric.inside()) {
                continue;
            }

            if (elementCentered) {
                double u = triangleIndex < uValues.length ? uValues[triangleIndex] : Double.NaN;
                double v = triangleIndex < vValues.length ? vValues[triangleIndex] : Double.NaN;
                if (!RenderMath.isRenderableValue(u, uFillValue) || !RenderMath.isRenderableValue(v, vFillValue)) {
                    return new Result(true, worldX, worldY, triangleIndex, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.INVALID_VALUE);
                }
                return new Result(true, worldX, worldY, triangleIndex, u, v, Math.hypot(u, v), layerIndex, Reason.HIT);
            }

            double u0 = uValues[triangle[0]];
            double u1 = uValues[triangle[1]];
            double u2 = uValues[triangle[2]];
            double v0 = vValues[triangle[0]];
            double v1 = vValues[triangle[1]];
            double v2 = vValues[triangle[2]];
            if (!RenderMath.isRenderableValue(u0, uFillValue) || !RenderMath.isRenderableValue(u1, uFillValue)
                || !RenderMath.isRenderableValue(u2, uFillValue) || !RenderMath.isRenderableValue(v0, vFillValue)
                || !RenderMath.isRenderableValue(v1, vFillValue) || !RenderMath.isRenderableValue(v2, vFillValue)) {
                return new Result(true, worldX, worldY, triangleIndex, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.INVALID_VALUE);
            }
            double u = barycentric.w1() * u0 + barycentric.w2() * u1 + barycentric.w3() * u2;
            double v = barycentric.w1() * v0 + barycentric.w2() * v1 + barycentric.w3() * v2;
            return new Result(true, worldX, worldY, triangleIndex, u, v, Math.hypot(u, v), layerIndex, Reason.HIT);
        }
        return new Result(false, worldX, worldY, -1, Double.NaN, Double.NaN, Double.NaN, layerIndex, Reason.NO_HIT);
    }

    private static Barycentric barycentric(MeshData mesh, int[] triangle, double px, double py) {
        double x1 = mesh.x()[triangle[0]];
        double y1 = mesh.y()[triangle[0]];
        double x2 = mesh.x()[triangle[1]];
        double y2 = mesh.y()[triangle[1]];
        double x3 = mesh.x()[triangle[2]];
        double y3 = mesh.y()[triangle[2]];
        double denominator = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (Math.abs(denominator) <= TOLERANCE) {
            return new Barycentric(Double.NaN, Double.NaN, Double.NaN, false);
        }
        double w1 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / denominator;
        double w2 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / denominator;
        double w3 = 1.0 - w1 - w2;
        boolean inside = w1 >= -TOLERANCE && w2 >= -TOLERANCE && w3 >= -TOLERANCE
            && w1 <= 1.0 + TOLERANCE && w2 <= 1.0 + TOLERANCE && w3 <= 1.0 + TOLERANCE;
        return new Barycentric(w1, w2, w3, inside);
    }

    public enum Reason {
        HIT,
        NO_HIT,
        INVALID_VALUE
    }

    public record Result(
        boolean hit,
        double worldX,
        double worldY,
        int triangleIndex,
        double u,
        double v,
        double speed,
        int layerIndex,
        Reason reason
    ) {
        public boolean hasVelocity() {
            return hit && reason == Reason.HIT && Double.isFinite(u) && Double.isFinite(v);
        }
    }

    private record Barycentric(double w1, double w2, double w3, boolean inside) {
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=FlowVectorQueryTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/render/FlowVectorQuery.java src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java
git commit -m "feat: add flow vector query helper"
```

### Task 3: Generate Cached Flowlines With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`

- [ ] **Step 1: Write the failing flowline-generation tests**

Create `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowLineGeneratorTest {
    private static final MeshData MESH = new MeshData(
        new double[]{0.0, 100.0, 0.0},
        new double[]{0.0, 0.0, 100.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 100.0);
    private final FlowLineGenerator generator = new FlowLineGenerator();

    @Test
    void generateBuildsAtLeastOneFlowLineForValidVelocityField() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            MESH,
            new double[]{1.5},
            new double[]{0.5},
            SNAPSHOT,
            100,
            100,
            true,
            null,
            null,
            0
        );

        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().allMatch(line -> line.points().size() > 1));
    }

    @Test
    void generateReturnsNoLinesWhenVelocityIsInvalid() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            MESH,
            new double[]{-9999.0},
            new double[]{0.5},
            SNAPSHOT,
            100,
            100,
            true,
            -9999.0,
            null,
            0
        );

        assertTrue(lines.isEmpty());
    }

    @Test
    void generateCapsLineLengthAndStepCount() {
        List<FlowLineGenerator.FlowLine> lines = generator.generate(
            MESH,
            new double[]{4.0},
            new double[]{0.0},
            SNAPSHOT,
            100,
            100,
            true,
            null,
            null,
            0
        );

        assertTrue(lines.stream().allMatch(line -> line.totalLength() <= FlowLineGenerator.MAX_TRACE_LENGTH + 1e-9));
        assertTrue(lines.stream().allMatch(line -> line.points().size() <= FlowLineGenerator.MAX_TRACE_STEPS * 2 + 1));
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=FlowLineGeneratorTest test`

Expected: FAIL because `FlowLineGenerator` does not exist yet.

- [ ] **Step 3: Add the flowline generator**

Create `src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FlowLineGenerator {
    static final int SEED_SPACING = 36;
    static final int MAX_TRACE_STEPS = 80;
    static final double STEP_SIZE = 8.0;
    static final double MAX_TRACE_LENGTH = 420.0;
    private static final double MIN_SPEED = 1e-4;
    private static final double OCCUPANCY_CELL_SIZE = 16.0;

    public List<FlowLine> generate(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        if (mesh == null || uValues == null || vValues == null || snapshot == null || width <= 0 || height <= 0) {
            return List.of();
        }

        List<FlowLine> lines = new ArrayList<>();
        Set<String> occupied = new HashSet<>();
        for (int screenY = SEED_SPACING / 2; screenY < height; screenY += SEED_SPACING) {
            for (int screenX = SEED_SPACING / 2; screenX < width; screenX += SEED_SPACING) {
                if (isOccupied(occupied, screenX, screenY)) {
                    continue;
                }
                FlowVectorQuery.Result seed = FlowVectorQuery.query(
                    mesh,
                    uValues,
                    vValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    uFillValue,
                    vFillValue,
                    layerIndex
                );
                if (!seed.hasVelocity() || seed.speed() < MIN_SPEED) {
                    continue;
                }
                FlowLine line = traceLine(mesh, uValues, vValues, snapshot, elementCentered, uFillValue, vFillValue, layerIndex, seed);
                if (line.points().size() <= 1) {
                    continue;
                }
                lines.add(line);
                markOccupied(occupied, line);
            }
        }
        return lines;
    }

    private FlowLine traceLine(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex,
        FlowVectorQuery.Result seed
    ) {
        List<Point2D> backward = integrate(mesh, uValues, vValues, snapshot, elementCentered, uFillValue, vFillValue, layerIndex, seed, -1.0);
        List<Point2D> forward = integrate(mesh, uValues, vValues, snapshot, elementCentered, uFillValue, vFillValue, layerIndex, seed, 1.0);
        List<Point2D> merged = new ArrayList<>();
        for (int index = backward.size() - 1; index >= 0; index--) {
            merged.add(backward.get(index));
        }
        merged.add(new Point2D(seed.worldX(), seed.worldY()));
        merged.addAll(forward);
        return new FlowLine(merged, totalLength(merged));
    }

    private List<Point2D> integrate(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex,
        FlowVectorQuery.Result seed,
        double direction
    ) {
        List<Point2D> points = new ArrayList<>();
        double currentX = seed.worldX();
        double currentY = seed.worldY();
        double totalLength = 0.0;
        for (int step = 0; step < MAX_TRACE_STEPS && totalLength < MAX_TRACE_LENGTH; step++) {
            FlowVectorQuery.Result current = FlowVectorQuery.query(
                mesh,
                uValues,
                vValues,
                snapshot,
                snapshot.screenX(currentX),
                snapshot.screenY(currentY),
                elementCentered,
                uFillValue,
                vFillValue,
                layerIndex
            );
            if (!current.hasVelocity() || current.speed() < MIN_SPEED) {
                break;
            }
            double dirX = current.u() / current.speed() * direction;
            double dirY = current.v() / current.speed() * direction;
            double midX = currentX + dirX * STEP_SIZE * 0.5;
            double midY = currentY + dirY * STEP_SIZE * 0.5;
            FlowVectorQuery.Result midpoint = FlowVectorQuery.query(
                mesh,
                uValues,
                vValues,
                snapshot,
                snapshot.screenX(midX),
                snapshot.screenY(midY),
                elementCentered,
                uFillValue,
                vFillValue,
                layerIndex
            );
            if (!midpoint.hasVelocity() || midpoint.speed() < MIN_SPEED) {
                break;
            }
            double stepDirX = midpoint.u() / midpoint.speed() * direction;
            double stepDirY = midpoint.v() / midpoint.speed() * direction;
            double nextX = currentX + stepDirX * STEP_SIZE;
            double nextY = currentY + stepDirY * STEP_SIZE;
            Point2D next = new Point2D(nextX, nextY);
            if (!points.isEmpty() && next.distance(points.get(points.size() - 1)) < 1e-6) {
                break;
            }
            totalLength += next.distance(currentX, currentY);
            if (totalLength > MAX_TRACE_LENGTH) {
                break;
            }
            points.add(next);
            currentX = nextX;
            currentY = nextY;
        }
        return points;
    }

    private boolean isOccupied(Set<String> occupied, double screenX, double screenY) {
        return occupied.contains(cellKey(screenX, screenY));
    }

    private void markOccupied(Set<String> occupied, FlowLine line) {
        for (Point2D point : line.points()) {
            occupied.add(cellKey(point.getX(), point.getY()));
        }
    }

    private String cellKey(double x, double y) {
        long cellX = (long) Math.floor(x / OCCUPANCY_CELL_SIZE);
        long cellY = (long) Math.floor(y / OCCUPANCY_CELL_SIZE);
        return cellX + ":" + cellY;
    }

    private double totalLength(List<Point2D> points) {
        double total = 0.0;
        for (int index = 1; index < points.size(); index++) {
            total += points.get(index - 1).distance(points.get(index));
        }
        return total;
    }

    public record FlowLine(List<Point2D> points, double totalLength) {
        public FlowLine {
            points = List.copyOf(points);
        }
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=FlowLineGeneratorTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/render/FlowLineGenerator.java src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java
git commit -m "feat: add flowline generator"
```

### Task 4: Render Animated Highlight Bands With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/FlowLineOverlayRenderer.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowLineOverlayRendererTest.java`

- [ ] **Step 1: Write the failing flowline-renderer tests**

Create `src/test/java/com/example/netcdfviewer/render/FlowLineOverlayRendererTest.java`:

```java
package com.example.netcdfviewer.render;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FlowLineOverlayRendererTest {
    private final FlowLineOverlayRenderer renderer = new FlowLineOverlayRenderer();

    @Test
    void highlightedSegmentsChangeWhenPhaseChanges() {
        FlowLineGenerator.FlowLine line = new FlowLineGenerator.FlowLine(
            List.of(
                new Point2D(0.0, 0.0),
                new Point2D(20.0, 0.0),
                new Point2D(40.0, 0.0),
                new Point2D(60.0, 0.0)
            ),
            60.0
        );

        List<FlowLineOverlayRenderer.HighlightSegment> phaseA = renderer.computeHighlights(List.of(line), 0.0);
        List<FlowLineOverlayRenderer.HighlightSegment> phaseB = renderer.computeHighlights(List.of(line), 12.0);

        assertFalse(phaseA.isEmpty());
        assertFalse(phaseB.isEmpty());
        assertNotEquals(phaseA, phaseB);
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=FlowLineOverlayRendererTest test`

Expected: FAIL because `FlowLineOverlayRenderer` does not exist yet.

- [ ] **Step 3: Add the flowline overlay renderer**

Create `src/main/java/com/example/netcdfviewer/render/FlowLineOverlayRenderer.java`:

```java
package com.example.netcdfviewer.render;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class FlowLineOverlayRenderer {
    static final double HIGHLIGHT_LENGTH = 42.0;
    static final double HIGHLIGHT_SPEED = 18.0;
    private static final Color BASE_COLOR = Color.rgb(27, 60, 78, 0.28);
    private static final Color HIGHLIGHT_COLOR = Color.rgb(133, 233, 255, 0.88);

    public void render(GraphicsContext graphics, List<FlowLineGenerator.FlowLine> lines, double phase) {
        graphics.save();
        try {
            graphics.setStroke(BASE_COLOR);
            graphics.setLineWidth(1.1);
            for (FlowLineGenerator.FlowLine line : lines) {
                drawPolyline(graphics, line.points());
            }

            graphics.setStroke(HIGHLIGHT_COLOR);
            graphics.setLineWidth(2.2);
            for (HighlightSegment segment : computeHighlights(lines, phase)) {
                graphics.strokeLine(segment.start().getX(), segment.start().getY(), segment.end().getX(), segment.end().getY());
            }
        } finally {
            graphics.restore();
        }
    }

    List<HighlightSegment> computeHighlights(List<FlowLineGenerator.FlowLine> lines, double phase) {
        List<HighlightSegment> segments = new ArrayList<>();
        for (FlowLineGenerator.FlowLine line : lines) {
            if (line.points().size() < 2 || line.totalLength() <= 0.0) {
                continue;
            }
            double normalizedOffset = phaseOffset(line.totalLength(), phase);
            double highlightEnd = normalizedOffset + HIGHLIGHT_LENGTH;
            double traversed = 0.0;
            for (int index = 1; index < line.points().size(); index++) {
                Point2D start = line.points().get(index - 1);
                Point2D end = line.points().get(index);
                double segmentLength = start.distance(end);
                double segmentStart = traversed;
                double segmentEnd = traversed + segmentLength;
                if (segmentEnd >= normalizedOffset && segmentStart <= highlightEnd) {
                    segments.add(new HighlightSegment(start, end));
                }
                traversed = segmentEnd;
            }
        }
        return segments;
    }

    double phaseOffset(double lineLength, double phase) {
        if (lineLength <= 0.0) {
            return 0.0;
        }
        double cycle = Math.max(lineLength, HIGHLIGHT_LENGTH);
        double offset = (phase * HIGHLIGHT_SPEED) % cycle;
        return offset < 0 ? offset + cycle : offset;
    }

    private void drawPolyline(GraphicsContext graphics, List<Point2D> points) {
        for (int index = 1; index < points.size(); index++) {
            Point2D start = points.get(index - 1);
            Point2D end = points.get(index);
            graphics.strokeLine(start.getX(), start.getY(), end.getX(), end.getY());
        }
    }

    public record HighlightSegment(Point2D start, Point2D end) {
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=FlowLineOverlayRendererTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/render/FlowLineOverlayRenderer.java src/test/java/com/example/netcdfviewer/render/FlowLineOverlayRendererTest.java
git commit -m "feat: add animated flowline overlay renderer"
```

### Task 5: Wire The Flowline Overlay Into The UI And Render Pipeline With TDD

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write the failing UI/controller tests**

Append these tests to `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`:

```java
    @Test
    void loadingVelocityDatasetEnablesFlowLineToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                assertFalse(view.getFlowLineCheck().isDisable());
                assertFalse(view.getFlowLineCheck().isSelected());
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

    @Test
    void loadingDatasetWithoutVelocityPairDisablesFlowLineToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("DSD1211.nc"));

                assertTrue(view.getFlowLineCheck().isDisable());
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

    @Test
    void enablingFlowLineOverlayKeepsRenderPipelineAlive() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainView> viewRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                MainView view = new MainView();
                MainController controller = new MainController(stage, view);
                controller.initialize();
                stage.setScene(new Scene(view, 1440, 900));
                stage.show();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                viewRef.set(view);
                stageRef.set(stage);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                initLatch.countDown();
            }
        });

        assertTrue(initLatch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getFlowLineCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForRender(viewRef.get());
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(viewRef.get().getFlowLineCheck().isSelected());
    }
```

- [ ] **Step 2: Run the focused UI test to verify it fails**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`

Expected: FAIL because `MainView` has no `Flow lines` checkbox and `MainController` does not manage velocity-pair or flowline overlay state.

- [ ] **Step 3: Add the UI control**

Update `src/main/java/com/example/netcdfviewer/ui/MainView.java`:

```java
    // 流线叠加开关。
    private final CheckBox flowLineCheck = new CheckBox("Flow lines");
```

Initialize and add it to the right-side panel near the existing overlay toggles:

```java
        flowLineCheck.setDisable(true);
```

```java
        VBox rightPanel = new VBox(
            10,
            coordinateVariableLabel,
            connectivityVariableLabel,
            variableMetaLabel,
            visualizeButton,
            flowLineCheck,
            waveArrowCheck,
            colorMapLabel,
            colorMapCombo,
            depthLabel,
            depthSlider,
            layerInfoLabel,
            autoRangeCheck,
            rangeBox,
            rangeInfoLabel
        );
```

Add the getter:

```java
    public CheckBox getFlowLineCheck() {
        return flowLineCheck;
    }
```

- [ ] **Step 4: Wire controller state, cache invalidation, and animation**

Update `src/main/java/com/example/netcdfviewer/ui/MainController.java` with the new fields:

```java
    private final VelocityVariablePairFinder velocityVariablePairFinder = new VelocityVariablePairFinder();
    private final FlowLineGenerator flowLineGenerator = new FlowLineGenerator();
    private final FlowLineOverlayRenderer flowLineOverlayRenderer = new FlowLineOverlayRenderer();
    private VelocityVariablePair activeVelocityPair;
    private FlowOverlayFrame latestFlowOverlayFrame;
    private Timeline flowAnimationTimeline;
    private double flowAnimationPhase;
```

In `initialize()`:

```java
        view.getFlowLineCheck().setDisable(true);
        view.getFlowLineCheck().setSelected(false);
```

In `wireActions()`:

```java
        view.getFlowLineCheck().selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue) {
                stopFlowAnimation();
            }
            renderCurrentSelection();
        });
```

In `activateDataset(...)` and `clearActiveDatasetState()`:

```java
        activeVelocityPair = velocityVariablePairFinder.find(item.dataset()).orElse(null);
        latestFlowOverlayFrame = null;
        updateFlowLineControls();
```

```java
        activeVelocityPair = null;
        latestFlowOverlayFrame = null;
        view.getFlowLineCheck().setSelected(false);
        view.getFlowLineCheck().setDisable(true);
        stopFlowAnimation();
```

Add the control helper:

```java
    private void updateFlowLineControls() {
        boolean available = activeVelocityPair != null;
        if (!available) {
            view.getFlowLineCheck().setSelected(false);
        }
        view.getFlowLineCheck().setDisable(!available);
    }
```

Change `renderCurrentSelection()` to decide both overlays:

```java
            WaveVariablePair wavePair = view.getWaveArrowCheck().isSelected() ? activeWavePair : null;
            VelocityVariablePair flowPair = view.getFlowLineCheck().isSelected() ? activeVelocityPair : null;
            renderAsync(requestId, layerIndex, values, colorMap, displayRange, wavePair, flowPair);
```

Change the `renderAsync(...)` signature and task body so it prepares flowline caches:

```java
    private void renderAsync(
        long requestId,
        int layerIndex,
        double[] values,
        ColorMap colorMap,
        RangeStats displayRange,
        WaveVariablePair wavePair,
        VelocityVariablePair flowPair
    ) {
        Canvas canvas = view.getRenderCanvas();
        ParsedDataset dataset = currentDataset;
        VariableInfo variable = currentVariable;
        int width = Math.max(1, (int) Math.round(canvas.getWidth()));
        int height = Math.max(1, (int) Math.round(canvas.getHeight()));
        ViewportState.Snapshot snapshot = viewportState.snapshot();

        Task<RenderFrame> renderTask = new Task<>() {
            @Override
            protected RenderFrame call() throws Exception {
                var bufferedImage = imageRenderer.render(
                    width,
                    height,
                    dataset.mesh(),
                    values,
                    colorMap,
                    displayRange,
                    snapshot,
                    variable.elementCentered(),
                    variable.fillValue()
                );

                WaveOverlayFrame waveOverlayFrame = null;
                String overlayMessage = null;
                if (wavePair != null) {
                    try {
                        int waveLayerIndex = wavePair.resolveLayerIndex(layerIndex);
                        double[] directionValues = parser.readLayer(dataset, wavePair.directionVariable(), waveLayerIndex);
                        double[] wavelengthValues = parser.readLayer(dataset, wavePair.wavelengthVariable(), waveLayerIndex);
                        RangeStats wavelengthRange = RenderMath.computeRange(
                            wavelengthValues,
                            wavePair.wavelengthVariable().fillValue()
                        );
                        if (!wavelengthRange.empty()) {
                            waveOverlayFrame = new WaveOverlayFrame(
                                wavePair,
                                waveLayerIndex,
                                directionValues,
                                wavelengthValues,
                                wavelengthRange
                            );
                        }
                    } catch (Exception waveError) {
                        overlayMessage = "Wave arrows skipped: " + waveError.getMessage();
                    }
                }

                FlowOverlayFrame flowOverlayFrame = null;
                if (flowPair != null) {
                    try {
                        int flowLayerIndex = flowPair.resolveLayerIndex(layerIndex);
                        double[] uValues = parser.readLayer(dataset, flowPair.eastwardVariable(), flowLayerIndex);
                        double[] vValues = parser.readLayer(dataset, flowPair.northwardVariable(), flowLayerIndex);
                        List<FlowLineGenerator.FlowLine> lines = flowLineGenerator.generate(
                            dataset.mesh(),
                            uValues,
                            vValues,
                            snapshot,
                            width,
                            height,
                            flowPair.elementCentered(),
                            flowPair.eastwardVariable().fillValue(),
                            flowPair.northwardVariable().fillValue(),
                            flowLayerIndex
                        );
                        if (!lines.isEmpty()) {
                            flowOverlayFrame = new FlowOverlayFrame(flowPair, flowLayerIndex, lines, snapshot, width, height);
                        }
                    } catch (Exception flowError) {
                        overlayMessage = overlayMessage == null
                            ? "Flow lines skipped: " + flowError.getMessage()
                            : overlayMessage + "; Flow lines skipped: " + flowError.getMessage();
                    }
                }

                return new RenderFrame(
                    SwingFXUtils.toFXImage(bufferedImage, null),
                    waveOverlayFrame,
                    flowOverlayFrame,
                    overlayMessage
                );
            }
        };

        renderTask.setOnSucceeded(event -> {
            if (requestId != renderSequence || variable != currentVariable || dataset != currentDataset) {
                return;
            }
            RenderFrame frame = renderTask.getValue();
            latestFlowOverlayFrame = frame.flowOverlayFrame();
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.getGraphicsContext2D().drawImage(frame.image(), 0, 0, canvas.getWidth(), canvas.getHeight());
            if (latestFlowOverlayFrame != null) {
                flowLineOverlayRenderer.render(canvas.getGraphicsContext2D(), latestFlowOverlayFrame.lines(), flowAnimationPhase);
                startFlowAnimation();
            } else {
                stopFlowAnimation();
            }
            if (frame.waveOverlayFrame() != null) {
                waveArrowOverlayRenderer.render(
                    canvas.getGraphicsContext2D(),
                    dataset.mesh(),
                    frame.waveOverlayFrame().directionValues(),
                    frame.waveOverlayFrame().wavelengthValues(),
                    snapshot,
                    width,
                    height,
                    frame.waveOverlayFrame().pair().elementCentered(),
                    frame.waveOverlayFrame().pair().directionVariable().fillValue(),
                    frame.waveOverlayFrame().pair().wavelengthVariable().fillValue(),
                    frame.waveOverlayFrame().layerIndex(),
                    frame.waveOverlayFrame().wavelengthRange()
                );
            }
            coastlineOverlayRenderer.render(canvas.getGraphicsContext2D(), currentOverlay, snapshot);
            view.getColorBarCanvas().render(colorMap, displayRange);
            latestRenderQueryContext = new RenderQueryContext(
                dataset,
                variable,
                layerIndex,
                values.clone(),
                variable.elementCentered(),
                variable.fillValue(),
                snapshot
            );
            view.getCurrentVariableLabel().setText("Variable: " + variable.name() + " " + variable.dimensionSummary());
            view.getRangeInfoLabel().setText("Range: " + format(displayRange.min()) + " to " + format(displayRange.max()));
            updateLayerLabel(layerIndex);
            updateWindowTitle();
            view.getOverlayLabel().setVisible(false);
            view.getExportButton().setDisable(false);
            view.getExportPngMenuItem().setDisable(false);
            setStatus(frame.overlayMessage() == null ? "Rendered " + variable.name() : frame.overlayMessage());
        });

        renderTask.setOnFailed(event -> {
            if (requestId != renderSequence) {
                return;
            }
            stopFlowAnimation();
            Throwable error = renderTask.getException();
            renderPlaceholder("Could not render the selected variable: " + (error == null ? "unknown error" : error.getMessage()));
        });

        Thread worker = new Thread(renderTask, "render-" + requestId);
        worker.setDaemon(true);
        worker.start();
    }
```

Add animation helpers:

```java
    private void startFlowAnimation() {
        if (latestFlowOverlayFrame == null || !view.getFlowLineCheck().isSelected()) {
            return;
        }
        if (flowAnimationTimeline == null) {
            flowAnimationTimeline = new Timeline(new KeyFrame(Duration.millis(80), event -> {
                flowAnimationPhase += 0.08;
                repaintCurrentFrame();
            }));
            flowAnimationTimeline.setCycleCount(Animation.INDEFINITE);
        }
        if (flowAnimationTimeline.getStatus() != Animation.Status.RUNNING) {
            flowAnimationTimeline.play();
        }
    }

    private void stopFlowAnimation() {
        if (flowAnimationTimeline != null) {
            flowAnimationTimeline.stop();
        }
    }

    private void repaintCurrentFrame() {
        if (latestRenderQueryContext == null) {
            return;
        }
        Canvas canvas = view.getRenderCanvas();
        renderCurrentSelection();
    }
```

Extend the nested records:

```java
    private record RenderFrame(
        WritableImage image,
        WaveOverlayFrame waveOverlayFrame,
        FlowOverlayFrame flowOverlayFrame,
        String overlayMessage
    ) {
    }

    private record FlowOverlayFrame(
        VelocityVariablePair pair,
        int layerIndex,
        List<FlowLineGenerator.FlowLine> lines,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
    }
```

- [ ] **Step 5: Run the focused UI test to verify it passes**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "feat: add animated flowline overlay ui"
```

### Task 6: Run Regression And Packaging Verification

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`
- Test: `src/test/java/com/example/netcdfviewer/io/VelocityVariablePairFinderTest.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowVectorQueryTest.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowLineGeneratorTest.java`
- Test: `src/test/java/com/example/netcdfviewer/render/FlowLineOverlayRendererTest.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/NanhaiRenderingTest.java`

- [ ] **Step 1: Update packaging/runtime assertions**

Update `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java` to keep the packaging script assertion aligned with the current helper functions:

```java
        assertTrue(script.contains("function Get-ProjectVersion"));
        assertTrue(script.contains("function Get-ProjectArtifactId"));
        assertTrue(script.contains("function Find-WixDirectory"));
        assertTrue(script.contains("--app-version $appVersion"));
        assertTrue(script.contains("--vendor lwj"));
        assertTrue(script.contains("--icon"));
```

- [ ] **Step 2: Run the focused regression suite**

Run: `mvn -q "-Dtest=VelocityVariablePairFinderTest,FlowVectorQueryTest,FlowLineGeneratorTest,FlowLineOverlayRendererTest,MainControllerLoadFileTest,NanhaiRenderingTest,PackagedRuntimeCompatibilityTest" test`

Expected: PASS.

- [ ] **Step 3: Run the full Maven test suite**

Run: `mvn -q test`

Expected: PASS.

- [ ] **Step 4: Build the Windows installer**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1`

Expected: `target/installer/NetCDFViewer-<version>.exe` is created and the script ends with `Installer created under ...`.

- [ ] **Step 5: Commit packaging and test updates**

Run:

```bash
git add src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java scripts/package-exe.ps1
git commit -m "test: keep packaging checks aligned with flowline overlay"
```
