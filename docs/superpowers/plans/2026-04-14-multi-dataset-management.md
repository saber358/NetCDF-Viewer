# Multi-Dataset Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users load multiple NetCDF files in one window, switch the active dataset, and remove loaded datasets they no longer need.

**Architecture:** Add a dataset-session list to the left panel and keep one active dataset driving the existing variable/meta/render pipeline. The controller owns the loaded-dataset collection, duplicate detection, fallback selection after removal, and empty-state restoration.

**Tech Stack:** Java 17, JavaFX 21, JUnit 5, existing NetCDF parser/render pipeline

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/ui/LoadedDatasetItem.java`
  Purpose: Lightweight UI/controller record for one loaded dataset session.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add the loaded-datasets list and remove button to the left panel.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Replace the single-dataset assumption with dataset-session management.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Add regression tests for add/select/remove/duplicate behavior.

### Task 1: Add Failing Multi-Dataset Tests

**Files:**
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] Write failing JavaFX tests for:
  - loading two datasets creates two dataset-list items
  - selecting another dataset updates `datasetLabel`
  - removing the active dataset falls back to another dataset
  - re-opening the same file does not duplicate the list

- [ ] Run: `mvn -q -Dtest=MainControllerLoadFileTest test`
  Expected: FAIL because the current UI/controller do not expose loaded-dataset management.

### Task 2: Add Dataset List UI

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/ui/LoadedDatasetItem.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`

- [ ] Add a lightweight dataset-item record with display label and `ParsedDataset`.
- [ ] Add `ListView<LoadedDatasetItem>` and a `Remove` button to `MainView`.
- [ ] Expose getters for the dataset list and remove button.
- [ ] Keep layout minimal by placing `Loaded Datasets` above the existing variable list.

- [ ] Re-run: `mvn -q -Dtest=MainControllerLoadFileTest test`
  Expected: still FAIL, but now for controller behavior rather than missing UI hooks.

### Task 3: Implement Controller Dataset Management

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`

- [ ] Add controller state for loaded datasets and active dataset selection.
- [ ] Change open/drag-drop behavior from “replace current dataset” to “append dataset”.
- [ ] Add duplicate-path detection and switch-to-existing behavior.
- [ ] Wire dataset-list selection changes to update the active dataset.
- [ ] Add remove-dataset behavior with fallback selection or full empty-state reset.
- [ ] Reset viewport and point-query cache when switching datasets.

- [ ] Re-run: `mvn -q -Dtest=MainControllerLoadFileTest test`
  Expected: PASS for the new dataset-management tests and existing controller tests.

### Task 4: Full Verification

**Files:**
- Modify as needed only if verification reveals regressions.

- [ ] Run: `mvn -q test`
  Expected: PASS with the full suite green.

- [ ] Review diff scope:
  Run: `git diff -- src/main/java/com/example/netcdfviewer/ui/LoadedDatasetItem.java src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Expected: only multi-dataset-management code and tests changed.

- [ ] Commit feature branch changes with a focused message.

## Self-Review

- Spec coverage:
  - append loading: Task 3
  - dataset selection: Task 3
  - dataset removal: Task 3
  - duplicate prevention: Task 3
  - UI dataset list: Task 2
  - regression tests: Task 1 and Task 4
- Placeholder scan:
  - all tasks are mapped to concrete files and verification commands
- Type consistency:
  - `LoadedDatasetItem`, dataset list getters, and controller selection/removal flow are the stable interface across tasks
