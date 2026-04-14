# Coastline Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single coastline overlay layer that can be loaded from GeoJSON or shapefile geometry and drawn above the rendered NetCDF mesh.

**Architecture:** Introduce a small overlay model plus dedicated GeoJSON/shapefile loaders and a JavaFX overlay renderer. `MainController` owns the current overlay, exposes load/clear actions, and redraws the overlay after the base mesh render completes.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing Swing file dialogs and canvas-based rendering

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/overlay/OverlayPath.java`
  Purpose: Immutable world-coordinate overlay path model.
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlay.java`
  Purpose: Immutable overlay container with source path and parsed path list.
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlayLoader.java`
  Purpose: Facade that dispatches by file extension.
- Create: `src/main/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoader.java`
  Purpose: Parse supported GeoJSON geometries into overlay paths.
- Create: `src/main/java/com/example/netcdfviewer/overlay/ShapefileOverlayLoader.java`
  Purpose: Parse supported `.shp` line/polygon geometry records into overlay paths.
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlayRenderer.java`
  Purpose: Draw overlay paths above the rendered mesh.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add `Load Coastline...` and `Clear Coastline` menu items.
- Modify: `src/main/java/com/example/netcdfviewer/ui/SwingFileDialogs.java`
  Purpose: Add chooser support for coastline overlay files.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Wire overlay state, menu actions, and redraw behavior.
- Create: `src/test/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoaderTest.java`
  Purpose: TDD coverage for supported and ignored GeoJSON geometries.
- Create: `src/test/java/com/example/netcdfviewer/overlay/ShapefileOverlayLoaderTest.java`
  Purpose: TDD coverage for shapefile polyline parsing.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Regression tests for load/clear/preserve overlay behavior.

### Task 1: Add Failing Pure Parser Tests

**Files:**
- Create: `src/test/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoaderTest.java`
- Create: `src/test/java/com/example/netcdfviewer/overlay/ShapefileOverlayLoaderTest.java`

- [ ] Write failing GeoJSON tests for:
  - `LineString` becomes one overlay path
  - `Polygon` becomes ring stroke paths
  - `Point` geometry is ignored

- [ ] Write a failing shapefile parser test using a tiny generated `.shp` polyline fixture.

- [ ] Run: `mvn -q "-Dtest=GeoJsonOverlayLoaderTest,ShapefileOverlayLoaderTest" test`
  Expected: FAIL because the loaders and overlay model do not exist yet.

### Task 2: Implement Overlay Model and Loaders

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/overlay/OverlayPath.java`
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlay.java`
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlayLoader.java`
- Create: `src/main/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoader.java`
- Create: `src/main/java/com/example/netcdfviewer/overlay/ShapefileOverlayLoader.java`

- [ ] Add the immutable overlay model and extension-based facade.
- [ ] Implement the minimal GeoJSON parser needed for supported line/polygon geometry.
- [ ] Implement the minimal `.shp` parser for PolyLine/Polygon XY geometry records and their Z/M variants, ignoring unsupported records.

- [ ] Run: `mvn -q "-Dtest=GeoJsonOverlayLoaderTest,ShapefileOverlayLoaderTest" test`
  Expected: PASS with the pure parser tests green.

### Task 3: Add Failing Controller Tests for Overlay State

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] Add failing regression tests for:
  - loading an overlay keeps a non-null overlay state
  - switching datasets does not clear the overlay state
  - clearing the overlay resets the overlay state

- [ ] Use reflection to inspect the controller overlay field so the first controller tests stay small and deterministic.

- [ ] Run: `mvn -q -Dtest=MainControllerLoadFileTest test`
  Expected: FAIL because overlay UI hooks and controller state are missing.

### Task 4: Wire Overlay UI and Rendering

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/overlay/CoastlineOverlayRenderer.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/SwingFileDialogs.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`

- [ ] Add menu items for `Load Coastline...` and `Clear Coastline`.
- [ ] Add a file chooser helper that accepts `.geojson`, `.json`, and `.shp`.
- [ ] Add current-overlay state to the controller.
- [ ] Load overlay files through the new loader facade and preserve the overlay across dataset switches.
- [ ] Draw the overlay after the base canvas image is drawn, using the current viewport snapshot.
- [ ] Clear the overlay and redraw when the user invokes `Clear Coastline`.

- [ ] Run: `mvn -q -Dtest=MainControllerLoadFileTest test`
  Expected: PASS for the new overlay regression tests and the existing controller tests.

### Task 5: Full Verification

**Files:**
- Modify only if verification exposes regressions.

- [ ] Run: `mvn -q test`
  Expected: PASS with the full suite green.

- [ ] Review diff scope:
  Run: `git diff -- src/main/java/com/example/netcdfviewer/overlay src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/SwingFileDialogs.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/overlay src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Expected: only coastline overlay implementation and tests changed.

- [ ] Commit the feature branch with a focused message.

## Self-Review

- Spec coverage:
  - single overlay load/clear: Tasks 3 and 4
  - GeoJSON support: Tasks 1 and 2
  - shapefile support: Tasks 1 and 2
  - overlay redraw after render and dataset switching: Task 4
  - regression verification: Tasks 3 through 5
- Placeholder scan:
  - all tasks specify exact files and commands
- Type consistency:
  - `CoastlineOverlay`, `OverlayPath`, loader facade, and overlay renderer are the only shared concepts referenced across tasks
