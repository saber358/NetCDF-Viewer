# Built-In Coastline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bundle one default coastline GeoJSON into the application resources, enable it by default, and let users restore it from the menu after clearing or replacing the overlay.

**Architecture:** Reuse the existing GeoJSON coastline parsing path by adding a classpath-based loading entry point. `MainController` loads the built-in overlay on initialization and offers a `Use Built-in Coastline` action while keeping the existing external load/clear flow.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing overlay model and renderer

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/overlay/BuiltInCoastline.java`
  Purpose: Load the bundled GeoJSON coastline from classpath resources into a `CoastlineOverlay`.
- Modify: `src/main/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoader.java`
  Purpose: Add a shared parsing entry point for file-path and stream-based loading.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add `Use Built-in Coastline` menu item.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Load the built-in overlay at startup and restore it on demand.
- Create: `src/test/java/com/example/netcdfviewer/overlay/BuiltInCoastlineTest.java`
  Purpose: Verify the bundled coastline resource exists and parses.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Verify default overlay initialization and restore-after-clear behavior.
- Add resource: `src/main/resources/coastline/default-coastline.geojson`
  Purpose: Bundled default coastline embedded into the application and installer.

### Task 1: Add Failing Built-In Resource Tests

**Files:**
- Create: `src/test/java/com/example/netcdfviewer/overlay/BuiltInCoastlineTest.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] Add failing tests for:
  - bundled coastline resource exists on classpath
  - built-in coastline parser returns non-empty paths
  - controller initializes with non-null `currentOverlay`
  - `useBuiltInCoastline()` restores overlay after clear

- [ ] Run: `mvn -q "-Dtest=BuiltInCoastlineTest,MainControllerLoadFileTest" test`
  Expected: FAIL because no built-in resource loader or startup initialization exists yet.

### Task 2: Add Bundled Resource and Loader Support

**Files:**
- Add resource: `src/main/resources/coastline/default-coastline.geojson`
- Create: `src/main/java/com/example/netcdfviewer/overlay/BuiltInCoastline.java`
- Modify: `src/main/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoader.java`

- [ ] Copy the selected Natural Earth coastline GeoJSON into the resources directory.
- [ ] Add a stream-based GeoJSON loading path so classpath resources can reuse the same geometry extraction logic.
- [ ] Implement `BuiltInCoastline` to read the classpath resource and build a `CoastlineOverlay`.

- [ ] Run: `mvn -q "-Dtest=BuiltInCoastlineTest" test`
  Expected: PASS for the built-in resource tests.

### Task 3: Wire Built-In Overlay Into the UI and Controller

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] Add `Use Built-in Coastline` menu item to `File`.
- [ ] Load the built-in overlay during controller initialization.
- [ ] Add a controller method for restoring the built-in coastline and wire it to the menu item.
- [ ] Ensure `Clear Coastline` only clears the current overlay and does not break restore behavior.

- [ ] Run: `mvn -q "-Dtest=MainControllerLoadFileTest" test`
  Expected: PASS for the new built-in overlay controller tests and the existing controller tests.

### Task 4: Full Verification and Packaging

**Files:**
- Modify only if verification exposes regressions.

- [ ] Run: `mvn -q test`
  Expected: PASS with the full suite green.

- [ ] Run the Windows packaging script:
  `powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1`
  Expected: installer generation succeeds and includes the built-in coastline resource through the packaged jar.

- [ ] Review diff scope:
  `git diff -- src/main/resources/coastline/default-coastline.geojson src/main/java/com/example/netcdfviewer/overlay/BuiltInCoastline.java src/main/java/com/example/netcdfviewer/overlay/GeoJsonOverlayLoader.java src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/overlay/BuiltInCoastlineTest.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Expected: only built-in coastline resource/loading and related tests changed.

- [ ] Commit the feature branch with a focused message.

## Self-Review

- Spec coverage:
  - built-in resource bundled in app: Task 2
  - default startup activation: Task 3
  - restore built-in action: Task 3
  - full verification and installer rebuild: Task 4
- Placeholder scan:
  - all tasks have exact files and commands
- Type consistency:
  - `BuiltInCoastline`, stream-based `GeoJsonOverlayLoader`, and `useBuiltInCoastline()` are the stable interfaces used across tasks
