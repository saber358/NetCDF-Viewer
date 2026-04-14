# Click Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add left-click single-point query on the rendered mesh so users can inspect the value at the clicked position for both element-centered and node-centered variables.

**Architecture:** Keep `MainController` as the UI coordinator and add a new pure query helper under the render package for hit-testing and barycentric interpolation. Cache the latest successful render context inside the controller so point queries reuse in-memory layer values instead of reopening the NetCDF file.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing NetCDF parser/render pipeline

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java`
  Purpose: Pure geometry/value query helper that converts screen clicks to world coordinates, finds the containing triangle, and computes the queried value.
- Modify: `src/main/java/com/example/netcdfviewer/ui/ViewportState.java`
  Purpose: Add inverse coordinate helpers needed to map screen coordinates back to world coordinates.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Cache the latest render context, detect click-versus-drag interaction, invoke point queries, and update the status bar.
- Create: `src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java`
  Purpose: TDD coverage for hit-testing, miss behavior, node interpolation, element lookup, and invalid-value handling.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Add JavaFX-side regression coverage for click query status updates after loading a real dataset.

### Task 1: Build the Pure Query Helper With TDD

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/ViewportState.java`
- Test: `src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java`

- [ ] **Step 1: Write the failing helper test file**

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshPointQueryTest {
    private static final MeshData TRIANGLE_MESH = new MeshData(
        new double[]{0.0, 10.0, 0.0},
        new double[]{0.0, 0.0, 10.0},
        new int[][]{{0, 1, 2}}
    );
    private static final ViewportState.Snapshot SNAPSHOT = new ViewportState.Snapshot(1.0, 0.0, 0.0);

    @Test
    void queryReturnsElementValueForPointInsideTriangle() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{7.5},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(0, result.triangleIndex());
        assertEquals(7.5, result.value());
    }

    @Test
    void queryInterpolatesNodeValueInsideTriangle() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{10.0, 20.0, 40.0},
            SNAPSHOT,
            2.0,
            -2.0,
            false,
            null,
            0
        );

        assertTrue(result.hit());
        assertEquals(16.0, result.value(), 1e-9);
    }

    @Test
    void queryReturnsMissWhenPointIsOutsideMesh() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{7.5},
            SNAPSHOT,
            20.0,
            -20.0,
            true,
            null,
            0
        );

        assertFalse(result.hit());
        assertEquals(MeshPointQuery.Reason.NO_HIT, result.reason());
    }

    @Test
    void queryReturnsUnavailableWhenElementValueIsFillValue() {
        MeshPointQuery.Result result = MeshPointQuery.query(
            TRIANGLE_MESH,
            new double[]{-9999.0},
            SNAPSHOT,
            2.0,
            -2.0,
            true,
            -9999.0,
            0
        );

        assertTrue(result.hit());
        assertEquals(MeshPointQuery.Reason.INVALID_VALUE, result.reason());
    }

    @Test
    void screenPointIsConvertedBackToWorldCoordinates() {
        ViewportState.Snapshot snapshot = new ViewportState.Snapshot(2.0, 100.0, 200.0);

        assertEquals(5.0, snapshot.worldX(110.0), 1e-9);
        assertEquals(10.0, snapshot.worldY(180.0), 1e-9);
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run: `mvn -q -Dtest=MeshPointQueryTest test`

Expected: FAIL because `MeshPointQuery` does not exist yet and `ViewportState.Snapshot` does not expose inverse coordinate helpers.

- [ ] **Step 3: Add inverse viewport helpers**

Update `src/main/java/com/example/netcdfviewer/ui/ViewportState.java` to add the inverse mapping helpers:

```java
    public double worldX(double screenX) {
        // 将屏幕坐标 X 反算为世界坐标 X。
        return (screenX - translateX) / scale;
    }

    public double worldY(double screenY) {
        // 将屏幕坐标 Y 反算为世界坐标 Y。
        return (translateY - screenY) / scale;
    }

    public record Snapshot(double scale, double translateX, double translateY) {
        public double screenX(double worldX) {
            return worldX * scale + translateX;
        }

        public double screenY(double worldY) {
            return translateY - worldY * scale;
        }

        public double worldX(double screenX) {
            return (screenX - translateX) / scale;
        }

        public double worldY(double screenY) {
            return (translateY - screenY) / scale;
        }
    }
```

- [ ] **Step 4: Write the minimal query helper**

Create `src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java`:

```java
package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

public final class MeshPointQuery {
    private static final double TOLERANCE = 1e-9;

    private MeshPointQuery() {
    }

    public static Result query(
        MeshData mesh,
        double[] values,
        ViewportState.Snapshot snapshot,
        double screenX,
        double screenY,
        boolean elementCentered,
        Double fillValue,
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
                double value = triangleIndex < values.length ? values[triangleIndex] : Double.NaN;
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
                }
                return new Result(true, worldX, worldY, triangleIndex, value, layerIndex, Reason.HIT);
            }

            double value0 = values[triangle[0]];
            double value1 = values[triangle[1]];
            double value2 = values[triangle[2]];
            if (!RenderMath.isRenderableValue(value0, fillValue)
                || !RenderMath.isRenderableValue(value1, fillValue)
                || !RenderMath.isRenderableValue(value2, fillValue)) {
                return new Result(true, worldX, worldY, triangleIndex, Double.NaN, layerIndex, Reason.INVALID_VALUE);
            }

            double interpolated = barycentric.w1() * value0
                + barycentric.w2() * value1
                + barycentric.w3() * value2;
            return new Result(true, worldX, worldY, triangleIndex, interpolated, layerIndex, Reason.HIT);
        }
        return new Result(false, worldX, worldY, -1, Double.NaN, layerIndex, Reason.NO_HIT);
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
        double value,
        int layerIndex,
        Reason reason
    ) {
        public boolean hasValue() {
            return hit && reason == Reason.HIT && Double.isFinite(value);
        }
    }

    private record Barycentric(double w1, double w2, double w3, boolean inside) {
    }
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run: `mvn -q -Dtest=MeshPointQueryTest test`

Expected: PASS with all `MeshPointQueryTest` cases green.

- [ ] **Step 6: Commit the pure query helper**

```bash
git add src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java src/main/java/com/example/netcdfviewer/ui/ViewportState.java src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java
git commit -m "feat: add mesh point query helper"
```

### Task 2: Wire Click Query Into the Controller With TDD

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Add the failing UI-side test**

Append these tests to `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`:

```java
    @Test
    void clickQueryUpdatesStatusBarAfterDatasetRender() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

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
                openFile.invoke(controller, Path.of("ydw.nc"));

                waitForRender(view, "temp");

                double clickX = view.getCanvasHost().getWidth() * 0.5;
                double clickY = view.getCanvasHost().getHeight() * 0.5;
                view.getCanvasHost().getOnMousePressed().handle(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));
                view.getCanvasHost().getOnMouseReleased().handle(new MouseEvent(
                    MouseEvent.MOUSE_RELEASED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));

                assertTrue(view.getStatusLabel().getText().contains("Query"));
                assertTrue(view.getStatusLabel().getText().contains("triangle #"));
                stage.close();
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void clickQueryOutsideMeshShowsMissMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

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
                openFile.invoke(controller, Path.of("ydw.nc"));

                waitForRender(view, "temp");

                double clickX = -50.0;
                double clickY = -50.0;
                view.getCanvasHost().getOnMousePressed().handle(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));
                view.getCanvasHost().getOnMouseReleased().handle(new MouseEvent(
                    MouseEvent.MOUSE_RELEASED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));

                assertEquals("No mesh value at clicked location.", view.getStatusLabel().getText());
                stage.close();
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }
```

Also add the helper imports and utility method used by those tests:

```java
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
```

```java
    private static void waitForRender(MainView view, String variableName) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            if (!view.getOverlayLabel().isVisible()
                && view.getCurrentVariableLabel().getText().contains(variableName)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for render of " + variableName);
    }
```

- [ ] **Step 2: Run the focused UI test to verify it fails**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`

Expected: FAIL because no click-query context exists yet and the status bar text will not contain the expected query output.

- [ ] **Step 3: Add render query context and click handling**

Update `src/main/java/com/example/netcdfviewer/ui/MainController.java` with these structural changes:

1. Add imports:

```java
import com.example.netcdfviewer.render.MeshPointQuery;
```

2. Add fields near the existing controller state:

```java
    private Point2D clickAnchor;
    private RenderQueryContext latestRenderQueryContext;
    private static final double CLICK_TOLERANCE = 4.0;
```

3. Clear cached query state when opening a new file or falling back to a placeholder:

```java
        latestRenderQueryContext = null;
```

Place that line in `loadFile(...)` before the background task starts, and in `renderPlaceholder(...)` before disabling export.

4. Extend `wireMouseNavigation()` so press/release can distinguish click from drag:

```java
        view.getCanvasHost().setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragAnchor = new Point2D(event.getX(), event.getY());
                clickAnchor = dragAnchor;
            }
        });
```

```java
        view.getCanvasHost().setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && clickAnchor != null) {
                Point2D releasePoint = new Point2D(event.getX(), event.getY());
                if (releasePoint.distance(clickAnchor) <= CLICK_TOLERANCE) {
                    queryAtScreenPoint(releasePoint.getX(), releasePoint.getY());
                }
            }
            dragAnchor = null;
            clickAnchor = null;
        });
```

5. Cache the successful render context inside `renderTask.setOnSucceeded(...)` before updating status:

```java
            latestRenderQueryContext = new RenderQueryContext(
                dataset,
                variable,
                layerIndex,
                values.clone(),
                variable.elementCentered(),
                variable.fillValue(),
                snapshot
            );
```

6. Add the query method and formatting helpers:

```java
    private void queryAtScreenPoint(double screenX, double screenY) {
        RenderQueryContext context = latestRenderQueryContext;
        if (context == null) {
            setStatus("Point query is not available until a render completes.");
            return;
        }

        MeshPointQuery.Result result = MeshPointQuery.query(
            context.dataset().mesh(),
            context.values(),
            context.snapshot(),
            screenX,
            screenY,
            context.elementCentered(),
            context.fillValue(),
            context.layerIndex()
        );

        if (!result.hit() || result.reason() == MeshPointQuery.Reason.NO_HIT) {
            setStatus("No mesh value at clicked location.");
            return;
        }
        if (!result.hasValue()) {
            setStatus("Clicked triangle contains no valid value.");
            return;
        }

        StringBuilder text = new StringBuilder()
            .append("Query ")
            .append(context.variable().name());
        if (context.variable().layered()) {
            text.append(" layer ").append(result.layerIndex() + 1);
        }
        text.append(" at (")
            .append(format(result.worldX()))
            .append(", ")
            .append(format(result.worldY()))
            .append("): triangle #")
            .append(result.triangleIndex())
            .append(", value=")
            .append(format(result.value()));
        setStatus(text.toString());
    }
```

7. Add the cached context record near the bottom of the controller:

```java
    private record RenderQueryContext(
        ParsedDataset dataset,
        VariableInfo variable,
        int layerIndex,
        double[] values,
        boolean elementCentered,
        Double fillValue,
        ViewportState.Snapshot snapshot
    ) {
    }
```

- [ ] **Step 4: Run the focused UI test to verify it passes**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`

Expected: PASS with the new click query tests green and the existing about/load tests still green.

- [ ] **Step 5: Commit the controller wiring**

```bash
git add src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "feat: wire click query into main controller"
```

### Task 3: Verify the Full Feature and Clean Up

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java` (only if the drag assertion still needs refinement)
- Test: `src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Add a drag regression test if click-versus-drag still lacks explicit coverage**

If `MainControllerLoadFileTest` does not yet cover drag suppression, add:

```java
    @Test
    void dragPanDoesNotTriggerPointQueryStatus() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

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
                openFile.invoke(controller, Path.of("ydw.nc"));
                waitForRender(view, "temp");

                String previousStatus = view.getStatusLabel().getText();
                view.getCanvasHost().getOnMousePressed().handle(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED, 200, 200, 200, 200, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));
                view.getCanvasHost().getOnMouseDragged().handle(new MouseEvent(
                    MouseEvent.MOUSE_DRAGGED, 260, 260, 260, 260, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));
                view.getCanvasHost().getOnMouseReleased().handle(new MouseEvent(
                    MouseEvent.MOUSE_RELEASED, 260, 260, 260, 260, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));

                assertEquals(previousStatus, view.getStatusLabel().getText());
                stage.close();
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }
```

- [ ] **Step 2: Run the two focused test classes**

Run: `mvn -q -Dtest=MeshPointQueryTest,MainControllerLoadFileTest test`

Expected: PASS with all point-query computation and controller regression tests green.

- [ ] **Step 3: Run the full project test suite**

Run: `mvn -q test`

Expected: PASS. Existing sample-dataset, render, runtime, and smoke tests remain green, with only the pre-existing JavaFX unnamed-module warning allowed.

- [ ] **Step 4: Review the diff for scope**

Run: `git diff -- src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java src/main/java/com/example/netcdfviewer/ui/ViewportState.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

Expected: The diff only covers click query helpers, controller wiring, and tests. No unrelated UI or packaging changes.

- [ ] **Step 5: Commit the verification cleanup**

```bash
git add src/main/java/com/example/netcdfviewer/render/MeshPointQuery.java src/main/java/com/example/netcdfviewer/ui/ViewportState.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/render/MeshPointQueryTest.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "test: verify click query interaction"
```

## Self-Review

- Spec coverage check:
  - left-click query: Task 2
  - screen-to-world conversion: Task 1
  - triangle hit-testing: Task 1
  - element raw value lookup: Task 1
  - node barycentric interpolation: Task 1
  - status bar reporting: Task 2
  - click-versus-drag behavior: Task 2 and Task 3
  - full regression verification: Task 3
- Placeholder scan:
  - Removed vague “add tests” or “handle edge cases” wording and replaced it with concrete test code and explicit status messages.
- Type consistency:
  - `MeshPointQuery.query(...)`, `MeshPointQuery.Result`, `MeshPointQuery.Reason`, `RenderQueryContext`, `ViewportState.Snapshot.worldX/worldY`, and `MainController.queryAtScreenPoint(...)` are used consistently across all tasks.
