# Checked Dataset Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add checkbox-controlled multi-dataset scalar rendering while keeping a single active dataset for controls, metadata, query, and vector overlays.

**Architecture:** Keep `loadedDatasets` as the ordered session list and `currentDataset` as the active dataset. Add a source-path keyed render-enabled set and render checked datasets into one base image, with basemap drawn once and scalar layers drawn in list order.

**Tech Stack:** Java 21, JavaFX, Maven, JUnit 5, NetCDF parser/rendering classes already in the repo.

---

## File Structure

- Modify `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  - Add checked-render state.
  - Add dataset-list checkbox cells.
  - Add multi-dataset scalar render preparation.
  - Keep vector overlays tied to the active dataset.
- Modify `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  - No structural change expected. Existing `datasetList` remains the UI anchor.
- Modify `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  - Add focused tests for render-enabled state and list behavior.
- Modify `README.md`
  - Document checkbox rendering behavior.
- Modify `CHANGELOG.md`
  - Add changelog entry for checked multi-dataset rendering.

## Task 1: Render-Enabled State Tests

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`

- [ ] **Step 1: Write failing tests for default enabled state and toggle state**

Add tests near existing multi-dataset tests:

```java
@Test
void newlyOpenedDatasetsAreEnabledForRenderingByDefault() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            MainView view = new MainView();
            MainController controller = new MainController(new Stage(), view);
            controller.initialize();

            Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
            openFile.setAccessible(true);
            openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
            openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

            assertEquals(2, view.getDatasetList().getItems().size());
            assertTrue(isDatasetRenderEnabled(controller, view.getDatasetList().getItems().get(0)));
            assertTrue(isDatasetRenderEnabled(controller, view.getDatasetList().getItems().get(1)));
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
void renderEnabledStateCanBeDisabledWithoutRemovingDataset() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            MainView view = new MainView();
            MainController controller = new MainController(new Stage(), view);
            controller.initialize();

            Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
            openFile.setAccessible(true);
            openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

            LoadedDatasetItem item = view.getDatasetList().getItems().get(0);
            setDatasetRenderEnabled(controller, item, false);

            assertEquals(1, view.getDatasetList().getItems().size());
            assertFalse(isDatasetRenderEnabled(controller, item));
            assertTrue(view.getDatasetLabel().getText().contains("ydw.nc"));
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

Add reflection helpers at the end of the test class:

```java
private static boolean isDatasetRenderEnabled(MainController controller, LoadedDatasetItem item) throws Exception {
    Method method = MainController.class.getDeclaredMethod("isDatasetRenderEnabled", LoadedDatasetItem.class);
    method.setAccessible(true);
    return (boolean) method.invoke(controller, item);
}

private static void setDatasetRenderEnabled(MainController controller, LoadedDatasetItem item, boolean enabled) throws Exception {
    Method method = MainController.class.getDeclaredMethod("setDatasetRenderEnabled", LoadedDatasetItem.class, boolean.class);
    method.setAccessible(true);
    method.invoke(controller, item, enabled);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -Dtest=MainControllerLoadFileTest#newlyOpenedDatasetsAreEnabledForRenderingByDefault,MainControllerLoadFileTest#renderEnabledStateCanBeDisabledWithoutRemovingDataset test
```

Expected: FAIL with `NoSuchMethodException` for `isDatasetRenderEnabled` or `setDatasetRenderEnabled`.

- [ ] **Step 3: Add render-enabled state helpers**

Add imports:

```java
import java.util.HashSet;
import java.util.Set;
```

Add field near `loadedDatasets`:

```java
private final Set<Path> renderEnabledDatasetPaths = new HashSet<>();
```

Add helpers near dataset methods:

```java
private boolean isDatasetRenderEnabled(LoadedDatasetItem item) {
    return item != null && renderEnabledDatasetPaths.contains(normalizePath(item.sourcePath()));
}

private void setDatasetRenderEnabled(LoadedDatasetItem item, boolean enabled) {
    if (item == null) {
        return;
    }
    Path path = normalizePath(item.sourcePath());
    if (enabled) {
        renderEnabledDatasetPaths.add(path);
    } else {
        renderEnabledDatasetPaths.remove(path);
    }
    view.getDatasetList().refresh();
    renderCurrentSelection();
}
```

In `addLoadedDataset`, add the normalized source path to `renderEnabledDatasetPaths` before selecting the new item:

```java
renderEnabledDatasetPaths.add(path);
```

In `removeSelectedDataset`, remove the selected path:

```java
renderEnabledDatasetPaths.remove(normalizePath(selected.sourcePath()));
```

In `clearActiveDatasetState`, clear the set only when no datasets remain:

```java
renderEnabledDatasetPaths.clear();
```

- [ ] **Step 4: Run task tests and verify pass**

Run the same Maven command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src\main\java\com\example\netcdfviewer\ui\MainController.java src\test\java\com\example\netcdfviewer\ui\MainControllerLoadFileTest.java
git commit -m "feat: track checked dataset render state"
```

## Task 2: Dataset Checkbox Cell

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write failing test for checkbox cell**

Add:

```java
@Test
void datasetListUsesCheckboxCellsForRenderToggle() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            MainView view = new MainView();
            MainController controller = new MainController(new Stage(), view);
            controller.initialize();

            assertTrue(view.getDatasetList().getCellFactory().call(view.getDatasetList()) instanceof MainController.DatasetCell);
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

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -Dtest=MainControllerLoadFileTest#datasetListUsesCheckboxCellsForRenderToggle test
```

Expected: FAIL because `DatasetCell` does not exist or the cell factory is not set.

- [ ] **Step 3: Add checkbox cell**

Add JavaFX imports:

```java
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
```

In `initialize`, after `view.getDatasetList().setItems(loadedDatasets);`:

```java
view.getDatasetList().setCellFactory(list -> new DatasetCell());
```

Add inner class before `VariableCell`:

```java
final class DatasetCell extends ListCell<LoadedDatasetItem> {
    private final CheckBox checkBox = new CheckBox();
    private final Label label = new Label();
    private final HBox content = new HBox(8, checkBox, label);
    private LoadedDatasetItem currentItem;

    DatasetCell() {
        content.setAlignment(Pos.CENTER_LEFT);
        checkBox.setOnAction(event -> {
            if (currentItem != null) {
                setDatasetRenderEnabled(currentItem, checkBox.isSelected());
            }
        });
    }

    @Override
    protected void updateItem(LoadedDatasetItem item, boolean empty) {
        super.updateItem(item, empty);
        currentItem = item;
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        label.setText(item.displayName());
        checkBox.setSelected(isDatasetRenderEnabled(item));
        setText(null);
        setGraphic(content);
    }
}
```

- [ ] **Step 4: Run task test and verify pass**

Run the same Maven command from Step 2.

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src\main\java\com\example\netcdfviewer\ui\MainController.java src\test\java\com\example\netcdfviewer\ui\MainControllerLoadFileTest.java
git commit -m "feat: add dataset render checkbox cells"
```

## Task 3: Multi-Dataset Scalar Composition

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write failing tests for render source selection**

Add tests:

```java
@Test
void checkedDatasetsAreCollectedInListOrderForRendering() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            MainView view = new MainView();
            MainController controller = new MainController(new Stage(), view);
            controller.initialize();

            Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
            openFile.setAccessible(true);
            openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
            openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

            List<?> sources = collectRenderableDatasets(controller);

            assertEquals(2, sources.size());
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
void uncheckedDatasetsAreExcludedFromRendering() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            MainView view = new MainView();
            MainController controller = new MainController(new Stage(), view);
            controller.initialize();

            Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
            openFile.setAccessible(true);
            openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
            openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

            setDatasetRenderEnabled(controller, view.getDatasetList().getItems().get(0), false);

            List<?> sources = collectRenderableDatasets(controller);

            assertEquals(1, sources.size());
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

Add helper:

```java
private static List<?> collectRenderableDatasets(MainController controller) throws Exception {
    Method method = MainController.class.getDeclaredMethod("collectRenderableDatasets");
    method.setAccessible(true);
    return (List<?>) method.invoke(controller);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -Dtest=MainControllerLoadFileTest#checkedDatasetsAreCollectedInListOrderForRendering,MainControllerLoadFileTest#uncheckedDatasetsAreExcludedFromRendering test
```

Expected: FAIL with `NoSuchMethodException` for `collectRenderableDatasets`.

- [ ] **Step 3: Add render source records and collection**

Add helpers:

```java
private List<RenderableDataset> collectRenderableDatasets() {
    return loadedDatasets.stream()
        .filter(this::isDatasetRenderEnabled)
        .map(this::toRenderableDataset)
        .flatMap(Optional::stream)
        .toList();
}

private Optional<RenderableDataset> toRenderableDataset(LoadedDatasetItem item) {
    ParsedDataset dataset = item.dataset();
    VariableInfo variable = dataset == currentDataset && currentVariable != null
        ? currentVariable
        : dataset.variables().stream()
            .filter(VariableInfo::plottable)
            .findFirst()
            .orElse(null);
    if (variable == null || !variable.plottable()) {
        return Optional.empty();
    }
    SpatialDomain spatialDomain = dataset == currentDataset ? activeSpatialDomain : dataset.spatialDomain();
    if (spatialDomain == null) {
        return Optional.empty();
    }
    int layerIndex = dataset == currentDataset && variable.layered()
        ? (int) Math.round(view.getDepthSlider().getValue())
        : 0;
    return Optional.of(new RenderableDataset(item, dataset, spatialDomain, variable, layerIndex));
}

private record RenderableDataset(
    LoadedDatasetItem item,
    ParsedDataset dataset,
    SpatialDomain spatialDomain,
    VariableInfo variable,
    int layerIndex
) {
}
```

- [ ] **Step 4: Replace single base image build with composite build**

Change `renderCurrentSelection` so it:

- collects `List<RenderableDataset> renderableDatasets`
- shows `renderPlaceholder("未勾选要渲染的数据。");` when there are loaded datasets but none checked
- reads layer values for each renderable dataset
- computes a merged automatic range with a helper
- passes render layers to `renderAsync`

Add records:

```java
private record RenderLayer(
    RenderableDataset source,
    double[] values,
    RangeStats computedRange
) {
}
```

Add helper:

```java
private RangeStats mergeRanges(List<RenderLayer> layers) {
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    for (RenderLayer layer : layers) {
        if (layer.computedRange().empty()) {
            continue;
        }
        min = Math.min(min, layer.computedRange().min());
        max = Math.max(max, layer.computedRange().max());
    }
    if (!Double.isFinite(min) || !Double.isFinite(max)) {
        return new RangeStats(0.0, 0.0, true);
    }
    return new RangeStats(min, max, false);
}
```

Update `buildBaseImage` or add `buildCompositeBaseImage`:

```java
private BufferedImage buildCompositeBaseImage(
    List<RenderLayer> layers,
    ColorMap colorMap,
    RangeStats displayRange,
    ViewportState.Snapshot snapshot,
    int width,
    int height,
    BasemapLayer basemapLayer,
    double basemapOpacity
) {
    BufferedImage image = basemapLayer == null
        ? new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB)
        : basemapRenderer.render(width, height, snapshot, basemapLayer, basemapOpacity);
    Graphics2D graphics = image.createGraphics();
    try {
        if (basemapLayer == null) {
            graphics.setColor(Color.decode("#F8FBFD"));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        }
        for (RenderLayer layer : layers) {
            BufferedImage scalarImage = buildScalarImage(
                layer.source().spatialDomain(),
                layer.source().dataset(),
                layer.source().variable(),
                layer.values(),
                colorMap,
                displayRange,
                snapshot,
                width,
                height,
                true
            );
            graphics.drawImage(scalarImage, 0, 0, null);
        }
    } finally {
        graphics.dispose();
    }
    return image;
}
```

Refactor the scalar part of `buildBaseImage` into `buildScalarImage(...)`, preserving existing structured-grid and triangle renderer calls.

- [ ] **Step 5: Keep active overlay and query behavior**

Keep `latestRenderQueryContext` tied to `currentDataset`, `currentVariable`, and active dataset values. If the active dataset is not in render layers, set query context to null after successful render and set status with:

```java
"已渲染勾选数据；当前活动数据未参与渲染。"
```

Only schedule wave, flow, and wind overlays when the active dataset is render-enabled:

```java
boolean activeDatasetRendering = loadedDatasets.stream()
    .filter(item -> item.dataset() == currentDataset)
    .findFirst()
    .map(this::isDatasetRenderEnabled)
    .orElse(false);
WaveVariablePair wavePair = activeDatasetRendering && view.getWaveArrowCheck().isSelected() ? activeWavePair : null;
VelocityVariablePair flowPair = activeDatasetRendering && view.getFlowLineCheck().isSelected() ? activeVelocityPair : null;
WindVariablePair windPair = activeDatasetRendering && view.getWindBarbCheck().isSelected() ? activeWindPair : null;
```

- [ ] **Step 6: Run focused controller tests**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -Dtest=MainControllerLoadFileTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src\main\java\com\example\netcdfviewer\ui\MainController.java src\test\java\com\example\netcdfviewer\ui\MainControllerLoadFileTest.java
git commit -m "feat: render checked datasets together"
```

## Task 4: Documentation, Full Verification, Packaging

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `Release/NetCDFViewer-1.1.4.exe`

- [ ] **Step 1: Update README**

Update feature text to mention:

```markdown
- 左侧已加载数据列表支持勾选控制渲染；新打开的 `.nc` 默认参与绘图，取消勾选后仅从画布移除，不卸载数据。
- 可同时勾选多个 `.nc`，标量场会按列表顺序叠加到底图上。
```

- [ ] **Step 2: Update CHANGELOG**

Add under `Unreleased` or topmost version section:

```markdown
- 已加载数据列表新增渲染勾选框，支持多个 `.nc` 标量场同时叠加显示。
- 新打开数据默认参与渲染，取消勾选后保留在列表中但从画布移除。
```

- [ ] **Step 3: Run full tests**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q test
```

Expected: PASS.

- [ ] **Step 4: Repackage exe**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1
```

Expected: new executable copied to `Release\NetCDFViewer-1.1.4.exe`.

- [ ] **Step 5: Commit**

```powershell
git add README.md CHANGELOG.md Release\NetCDFViewer-1.1.4.exe
git commit -m "chore: document checked dataset rendering"
```

## Self-Review

- Spec coverage: checklist UI, default enabled state, unchecked removal from render, multiple checked datasets, active dataset separation, vector overlay boundary, docs, tests, and packaging are covered.
- Placeholder scan: no placeholder markers or deferred implementation notes remain.
- Type consistency: helper names used by tests match planned private methods in `MainController`.
