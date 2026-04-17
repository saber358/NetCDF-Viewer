# Wave Arrow Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional `wdir`/`wlen` wave arrow overlay so compatible NetCDF datasets can show wave direction and relative wavelength above the existing scalar render.

**Architecture:** Keep the scalar raster renderer unchanged and add a separate wave-pair detection helper plus a dedicated arrow overlay renderer. `MainController` stays the coordinator: it detects whether the active dataset exposes a compatible wave pair, loads the overlay data in the background render task, and draws arrows between the scalar base image and the coastline overlay.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing NetCDF parser/render pipeline, existing Maven packaging scripts

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java`
  Purpose: Immutable validated pair of `wdir` and `wlen` variables with shared-layer helpers.
- Create: `src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java`
  Purpose: Detect whether a dataset exposes a compatible exact-name `wdir`/`wlen` pair.
- Create: `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java`
  Purpose: Sample the current viewport, query wave direction and wavelength, build arrow glyphs, and draw them on the JavaFX canvas.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add one `Wave arrows` checkbox to the right control panel and expose it through a getter.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Manage wave overlay availability, load `wdir`/`wlen` layer values during the background render task, and paint arrows over the scalar image.
- Create: `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`
  Purpose: TDD coverage for exact-name matching and compatibility validation.
- Create: `src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java`
  Purpose: TDD coverage for arrow sampling, invalid-value skipping, and bounded length mapping.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Verify UI availability and render stability for the new overlay toggle.

### Task 1: Detect Compatible `wdir`/`wlen` Variables With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java`
- Create: `src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java`
- Test: `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`

- [ ] **Step 1: Write the failing pair-detection tests**

Create `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WaveVariablePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveVariablePairFinderTest {
    private final WaveVariablePairFinder finder = new WaveVariablePairFinder();

    @Test
    void findExactWavePairReturnsCompatiblePair() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("hs", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wlen", true, 0, false, -1, List.of("node"), List.of(12))
        ));

        assertTrue(pair.isPresent());
        assertEquals("wdir", pair.get().directionVariable().name());
        assertEquals("wlen", pair.get().wavelengthVariable().name());
        assertFalse(pair.get().layered());
    }

    @Test
    void findReturnsEmptyWhenOneVariableIsMissing() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void findReturnsEmptyWhenSpatialBasisDiffers() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 0, false, -1, List.of("node"), List.of(12)),
            variable("wlen", true, 0, true, -1, List.of("nele"), List.of(12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void findReturnsEmptyWhenLayerShapeDiffers() {
        Optional<WaveVariablePair> pair = finder.find(List.of(
            variable("wdir", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12)),
            variable("wlen", true, 1, false, 0, List.of("siglev", "node"), List.of(4, 12))
        ));

        assertTrue(pair.isEmpty());
    }

    @Test
    void resolveLayerIndexClampsToSupportedRange() {
        WaveVariablePair pair = new WaveVariablePair(
            variable("wdir", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12)),
            variable("wlen", true, 1, false, 0, List.of("siglay", "node"), List.of(4, 12))
        );

        assertEquals(0, pair.resolveLayerIndex(-5));
        assertEquals(2, pair.resolveLayerIndex(2));
        assertEquals(3, pair.resolveLayerIndex(20));
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

Run: `mvn -q -Dtest=WaveVariablePairFinderTest test`

Expected: FAIL because `WaveVariablePair` and `WaveVariablePairFinder` do not exist yet.

- [ ] **Step 3: Add the validated pair record**

Create `src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java`:

```java
package com.example.netcdfviewer.model;

import java.util.Objects;

public record WaveVariablePair(
    VariableInfo directionVariable,
    VariableInfo wavelengthVariable
) {
    public WaveVariablePair {
        Objects.requireNonNull(directionVariable, "directionVariable");
        Objects.requireNonNull(wavelengthVariable, "wavelengthVariable");
        if (!directionVariable.plottable() || !wavelengthVariable.plottable()) {
            throw new IllegalArgumentException("Wave variables must both be plottable.");
        }
        if (directionVariable.nodeAxis() != wavelengthVariable.nodeAxis()) {
            throw new IllegalArgumentException("Wave variables must share the same spatial axis.");
        }
        if (directionVariable.elementCentered() != wavelengthVariable.elementCentered()) {
            throw new IllegalArgumentException("Wave variables must share the same mesh basis.");
        }
        if (directionVariable.layered() != wavelengthVariable.layered()) {
            throw new IllegalArgumentException("Wave variables must share the same layered shape.");
        }
        if (directionVariable.layered()
            && (!directionVariable.layerDimensionName().equals(wavelengthVariable.layerDimensionName())
            || directionVariable.layerCount() != wavelengthVariable.layerCount())) {
            throw new IllegalArgumentException("Wave variables must share the same layer dimension.");
        }
    }

    public boolean layered() {
        return directionVariable.layered();
    }

    public boolean elementCentered() {
        return directionVariable.elementCentered();
    }

    public int resolveLayerIndex(int requestedLayerIndex) {
        if (!layered()) {
            return 0;
        }
        return Math.max(0, Math.min(requestedLayerIndex, directionVariable.layerCount() - 1));
    }
}
```

- [ ] **Step 4: Add the exact-name finder**

Create `src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java`:

```java
package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.WaveVariablePair;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WaveVariablePairFinder {
    public Optional<WaveVariablePair> find(ParsedDataset dataset) {
        return find(dataset.variables());
    }

    Optional<WaveVariablePair> find(List<VariableInfo> variables) {
        VariableInfo direction = findExactName(variables, "wdir");
        VariableInfo wavelength = findExactName(variables, "wlen");
        if (direction == null || wavelength == null) {
            return Optional.empty();
        }
        if (!direction.plottable() || !wavelength.plottable()) {
            return Optional.empty();
        }
        if (direction.nodeAxis() != wavelength.nodeAxis()) {
            return Optional.empty();
        }
        if (direction.elementCentered() != wavelength.elementCentered()) {
            return Optional.empty();
        }
        if (direction.layered() != wavelength.layered()) {
            return Optional.empty();
        }
        if (direction.layered()
            && (!direction.layerDimensionName().equals(wavelength.layerDimensionName())
            || direction.layerCount() != wavelength.layerCount())) {
            return Optional.empty();
        }
        return Optional.of(new WaveVariablePair(direction, wavelength));
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

Run: `mvn -q -Dtest=WaveVariablePairFinderTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/model/WaveVariablePair.java src/main/java/com/example/netcdfviewer/io/WaveVariablePairFinder.java src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java
git commit -m "feat: detect compatible wave arrow variables"
```

### Task 2: Build the Arrow Overlay Renderer With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java`
- Test: `src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java`

- [ ] **Step 1: Write the failing renderer tests**

Create `src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveArrowOverlayRendererTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 64.0, 0.0},
        new double[]{0.0, 0.0, 64.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 64.0);
    private final WaveArrowOverlayRenderer renderer = new WaveArrowOverlayRenderer();

    @Test
    void sampleArrowsBuildsBoundedGlyphsForValidWaveField() {
        RangeStats wavelengthRange = RenderMath.computeRange(new double[]{12.0, 18.0, 24.0}, null);

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleArrows(
            TRIANGLE_MESH,
            new double[]{45.0, 45.0, 45.0},
            new double[]{12.0, 18.0, 24.0},
            SNAPSHOT,
            64,
            64,
            false,
            null,
            null,
            0,
            wavelengthRange
        );

        assertFalse(arrows.isEmpty());
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() >= WaveArrowOverlayRenderer.MIN_ARROW_LENGTH));
        assertTrue(arrows.stream().allMatch(arrow -> arrow.length() <= WaveArrowOverlayRenderer.MAX_ARROW_LENGTH));
    }

    @Test
    void sampleArrowsSkipsInvalidWaveValues() {
        RangeStats wavelengthRange = RenderMath.computeRange(new double[]{12.0, 18.0, 24.0}, -9999.0);

        List<WaveArrowOverlayRenderer.ArrowGlyph> arrows = renderer.sampleArrows(
            TRIANGLE_MESH,
            new double[]{-9999.0, -9999.0, -9999.0},
            new double[]{12.0, 18.0, 24.0},
            SNAPSHOT,
            64,
            64,
            false,
            -9999.0,
            -9999.0,
            0,
            wavelengthRange
        );

        assertTrue(arrows.isEmpty());
    }

    @Test
    void mapArrowLengthClampsIntoConfiguredPixelRange() {
        RangeStats wavelengthRange = new RangeStats(10.0, 30.0, 3);

        assertTrue(renderer.mapArrowLength(0.0, wavelengthRange) >= WaveArrowOverlayRenderer.MIN_ARROW_LENGTH);
        assertTrue(renderer.mapArrowLength(1000.0, wavelengthRange) <= WaveArrowOverlayRenderer.MAX_ARROW_LENGTH);
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=WaveArrowOverlayRendererTest test`

Expected: FAIL because `WaveArrowOverlayRenderer` does not exist yet.

- [ ] **Step 3: Add the renderer with a pure sampling path**

Create `src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class WaveArrowOverlayRenderer {
    static final int SAMPLE_SPACING = 28;
    static final double MIN_ARROW_LENGTH = 8.0;
    static final double MAX_ARROW_LENGTH = 26.0;
    private static final double HEAD_LENGTH = 5.0;
    private static final double HEAD_ANGLE_RADIANS = Math.toRadians(28.0);
    private static final Color ARROW_COLOR = Color.rgb(24, 92, 118, 0.72);

    public void render(
        GraphicsContext graphics,
        MeshData mesh,
        double[] directionValues,
        double[] wavelengthValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double directionFillValue,
        Double wavelengthFillValue,
        int layerIndex,
        RangeStats wavelengthRange
    ) {
        graphics.save();
        graphics.setStroke(ARROW_COLOR);
        graphics.setLineWidth(1.2);
        for (ArrowGlyph arrow : sampleArrows(
            mesh,
            directionValues,
            wavelengthValues,
            snapshot,
            width,
            height,
            elementCentered,
            directionFillValue,
            wavelengthFillValue,
            layerIndex,
            wavelengthRange
        )) {
            graphics.strokeLine(arrow.startX(), arrow.startY(), arrow.endX(), arrow.endY());
            drawHead(graphics, arrow);
        }
        graphics.restore();
    }

    List<ArrowGlyph> sampleArrows(
        MeshData mesh,
        double[] directionValues,
        double[] wavelengthValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double directionFillValue,
        Double wavelengthFillValue,
        int layerIndex,
        RangeStats wavelengthRange
    ) {
        if (mesh == null || wavelengthRange == null || wavelengthRange.empty()) {
            return List.of();
        }
        List<ArrowGlyph> arrows = new ArrayList<>();
        for (int screenY = SAMPLE_SPACING / 2; screenY < height; screenY += SAMPLE_SPACING) {
            for (int screenX = SAMPLE_SPACING / 2; screenX < width; screenX += SAMPLE_SPACING) {
                MeshPointQuery.Result directionResult = MeshPointQuery.query(
                    mesh,
                    directionValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    directionFillValue,
                    layerIndex
                );
                MeshPointQuery.Result wavelengthResult = MeshPointQuery.query(
                    mesh,
                    wavelengthValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    wavelengthFillValue,
                    layerIndex
                );
                if (!directionResult.hasValue() || !wavelengthResult.hasValue()) {
                    continue;
                }
                double length = mapArrowLength(wavelengthResult.value(), wavelengthRange);
                double radians = Math.toRadians(directionResult.value());
                double dx = Math.cos(radians) * length;
                double dy = -Math.sin(radians) * length;
                arrows.add(new ArrowGlyph(
                    screenX,
                    screenY,
                    screenX + dx,
                    screenY + dy,
                    length
                ));
            }
        }
        return arrows;
    }

    double mapArrowLength(double wavelength, RangeStats wavelengthRange) {
        double normalized = RenderMath.normalize(wavelength, wavelengthRange.min(), wavelengthRange.max());
        return MIN_ARROW_LENGTH + normalized * (MAX_ARROW_LENGTH - MIN_ARROW_LENGTH);
    }

    private void drawHead(GraphicsContext graphics, ArrowGlyph arrow) {
        double angle = Math.atan2(arrow.endY() - arrow.startY(), arrow.endX() - arrow.startX());
        double leftX = arrow.endX() - Math.cos(angle - HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double leftY = arrow.endY() - Math.sin(angle - HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double rightX = arrow.endX() - Math.cos(angle + HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double rightY = arrow.endY() - Math.sin(angle + HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        graphics.strokeLine(arrow.endX(), arrow.endY(), leftX, leftY);
        graphics.strokeLine(arrow.endX(), arrow.endY(), rightX, rightY);
    }

    record ArrowGlyph(double startX, double startY, double endX, double endY, double length) {
    }
}
```

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=WaveArrowOverlayRendererTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add src/main/java/com/example/netcdfviewer/render/WaveArrowOverlayRenderer.java src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java
git commit -m "feat: add wave arrow overlay renderer"
```

### Task 3: Wire the Overlay Into the UI and Render Pipeline With TDD

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write the failing UI/controller tests**

Append these tests to `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`:

```java
    @Test
    void loadingWaveDatasetEnablesWaveArrowToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, Path.of("HBHQY.nc"));

                assertFalse(view.getWaveArrowCheck().isDisable());
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
    void switchingBackToDatasetWithoutWavePairDisablesWaveArrowToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, Path.of("HBHQY.nc"));
                openFile.invoke(controller, Path.of("ydw.nc"));

                view.getDatasetList().getSelectionModel().select(1);
                assertTrue(view.getWaveArrowCheck().isDisable());
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
    void enablingWaveArrowOverlayKeepsRenderPipelineAlive() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainView> viewRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, Path.of("HBHQY.nc"));

                viewRef.set(view);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                initLatch.countDown();
            }
        });

        assertTrue(initLatch.await(15, TimeUnit.SECONDS));
        waitForRender(viewRef, errorRef);

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                MainView view = viewRef.get();
                view.getWaveArrowCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });

        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));
        waitForRender(viewRef, errorRef);
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    private static void waitForRender(AtomicReference<MainView> viewRef, AtomicReference<Throwable> errorRef) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            AtomicBoolean finished = new AtomicBoolean(false);
            Platform.runLater(() -> {
                try {
                    MainView view = viewRef.get();
                    if (view != null && !view.getOverlayLabel().isVisible()) {
                        finished.set(true);
                    }
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                } finally {
                    pollLatch.countDown();
                }
            });
            assertTrue(pollLatch.await(2, TimeUnit.SECONDS));
            if (errorRef.get() != null) {
                throw new AssertionError(errorRef.get());
            }
            if (finished.get()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for render completion.");
    }
```

- [ ] **Step 2: Run the focused UI test to verify it fails**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`

Expected: FAIL because `MainView` has no wave-arrow checkbox and `MainController` does not manage overlay availability or rendering.

- [ ] **Step 3: Add the UI control**

Update `src/main/java/com/example/netcdfviewer/ui/MainView.java`:

```java
    // 波场箭头开关。
    private final CheckBox waveArrowCheck = new CheckBox("Wave arrows");
```

Add it to `build()` right after `visualizeButton` and disable it by default:

```java
        waveArrowCheck.setDisable(true);

        VBox rightPanel = new VBox(
            10,
            coordinateVariableLabel,
            connectivityVariableLabel,
            variableMetaLabel,
            visualizeButton,
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
    public CheckBox getWaveArrowCheck() {
        return waveArrowCheck;
    }
```

- [ ] **Step 4: Wire controller state and render-path integration**

Update `src/main/java/com/example/netcdfviewer/ui/MainController.java` with the new fields:

```java
    private final WaveVariablePairFinder waveVariablePairFinder = new WaveVariablePairFinder();
    private final WaveArrowOverlayRenderer waveArrowOverlayRenderer = new WaveArrowOverlayRenderer();
    private WaveVariablePair activeWavePair;
```

In `initialize()` and `wireActions()`:

```java
        view.getWaveArrowCheck().setDisable(true);
        view.getWaveArrowCheck().setSelected(false);
```

```java
        view.getWaveArrowCheck().selectedProperty().addListener((obs, oldValue, newValue) -> renderCurrentSelection());
```

In `activateDataset(...)` and `clearActiveDatasetState()`:

```java
        activeWavePair = waveVariablePairFinder.find(item.dataset()).orElse(null);
        updateWaveArrowControls();
```

```java
        activeWavePair = null;
        view.getWaveArrowCheck().setSelected(false);
        view.getWaveArrowCheck().setDisable(true);
```

Add the helper:

```java
    private void updateWaveArrowControls() {
        boolean available = activeWavePair != null;
        if (!available) {
            view.getWaveArrowCheck().setSelected(false);
        }
        view.getWaveArrowCheck().setDisable(!available);
    }
```

Change `renderCurrentSelection()` so it passes the active pair into the background render:

```java
            WaveVariablePair wavePair = view.getWaveArrowCheck().isSelected() ? activeWavePair : null;
            renderAsync(requestId, layerIndex, values, colorMap, displayRange, wavePair);
```

Change `renderAsync(...)` to return both the base image and optional wave overlay data:

```java
    private void renderAsync(
        long requestId,
        int layerIndex,
        double[] values,
        ColorMap colorMap,
        RangeStats displayRange,
        WaveVariablePair wavePair
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
                String waveOverlayMessage = null;
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
                        waveOverlayMessage = "Wave arrows skipped: " + waveError.getMessage();
                    }
                }

                return new RenderFrame(
                    SwingFXUtils.toFXImage(bufferedImage, null),
                    waveOverlayFrame,
                    waveOverlayMessage
                );
            }
        };

        renderTask.setOnSucceeded(event -> {
            if (requestId != renderSequence || variable != currentVariable || dataset != currentDataset) {
                return;
            }
            RenderFrame frame = renderTask.getValue();
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.getGraphicsContext2D().drawImage(frame.image(), 0, 0, canvas.getWidth(), canvas.getHeight());
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
            setStatus(frame.waveOverlayMessage() == null ? "Rendered " + variable.name() : frame.waveOverlayMessage());
        });

        renderTask.setOnFailed(event -> {
            if (requestId != renderSequence) {
                return;
            }
            Throwable error = renderTask.getException();
            renderPlaceholder("Could not render the selected variable: " + (error == null ? "unknown error" : error.getMessage()));
        });

        Thread worker = new Thread(renderTask, "render-" + requestId);
        worker.setDaemon(true);
        worker.start();
    }
```

Add the nested records near the end of `MainController`:

```java
    private record RenderFrame(
        WritableImage image,
        WaveOverlayFrame waveOverlayFrame,
        String waveOverlayMessage
    ) {
    }

    private record WaveOverlayFrame(
        WaveVariablePair pair,
        int layerIndex,
        double[] directionValues,
        double[] wavelengthValues,
        RangeStats wavelengthRange
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
git commit -m "feat: add wave arrow overlay ui"
```

### Task 4: Run Regression And Package Verification

**Files:**
- Modify: none
- Test: `src/test/java/com/example/netcdfviewer/io/WaveVariablePairFinderTest.java`
- Test: `src/test/java/com/example/netcdfviewer/render/WaveArrowOverlayRendererTest.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/NanhaiRenderingTest.java`

- [ ] **Step 1: Run the focused regression suite**

Run: `mvn -q "-Dtest=WaveVariablePairFinderTest,WaveArrowOverlayRendererTest,MainControllerLoadFileTest,NanhaiRenderingTest" test`

Expected: PASS.

- [ ] **Step 2: Run the full Maven test suite**

Run: `mvn -q test`

Expected: PASS.

- [ ] **Step 3: Build the Windows installer**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1`

Expected: `target/installer/NetCDFViewer-<version>.exe` is created and the script ends with `Installer created under ...`.

- [ ] **Step 4: Smoke-check the packaged app runtime**

Run: `mvn -q -Dtest=PackagedRuntimeCompatibilityTest test`

Expected: PASS.
