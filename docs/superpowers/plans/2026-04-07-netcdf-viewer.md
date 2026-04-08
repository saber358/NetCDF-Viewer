# NetCDF Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java desktop application that opens local unstructured-triangle NetCDF files, inspects metadata, renders scalar planes with depth switching, and can be packaged into a Windows `.exe`.

**Architecture:** Use a JavaFX desktop shell around a parser-and-renderer core. Keep NetCDF parsing, mesh/domain model, rendering, and UI/controller wiring separated so that parsing and color/range logic can be covered with JUnit before UI assembly.

**Tech Stack:** Java 17+, Maven, JavaFX, NetCDF-Java 5.x, JUnit 5, `jpackage`

---

### Task 1: Bootstrap Maven Project

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/example/netcdfviewer/App.java`
- Create: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Create: `src/test/java/com/example/netcdfviewer/smoke/AppSmokeTest.java`
- Create: `README.md`

- [ ] **Step 1: Write the failing smoke test**

```java
package com.example.netcdfviewer.smoke;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppSmokeTest {
    @Test
    void appClassExposesTitleConstant() {
        assertEquals("NetCDF Viewer", com.example.netcdfviewer.App.APP_NAME);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AppSmokeTest test`
Expected: FAIL because `com.example.netcdfviewer.App` does not exist yet

- [ ] **Step 3: Write minimal implementation**

Create a Maven project with JavaFX and JUnit dependencies, add `App.APP_NAME`, and provide a minimal JavaFX `Application` subclass plus a placeholder `MainView`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AppSmokeTest test`
Expected: PASS

### Task 2: Add Domain Model And Parser Tests

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/model/MeshData.java`
- Create: `src/main/java/com/example/netcdfviewer/model/ScalarLayer.java`
- Create: `src/main/java/com/example/netcdfviewer/model/VariableInfo.java`
- Create: `src/main/java/com/example/netcdfviewer/io/ParsedDataset.java`
- Create: `src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java`
- Create: `src/test/java/com/example/netcdfviewer/io/NetcdfDatasetParserTest.java`

- [ ] **Step 1: Write failing parser tests**

Cover:

- connectivity normalization from 1-based indexing
- plottable variable classification for `[node]` and `[depth, node]`
- rejection for unsupported dimensions

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=NetcdfDatasetParserTest test`
Expected: FAIL because parser/model classes do not exist

- [ ] **Step 3: Write minimal implementation**

Implement:

- immutable model records or final classes for mesh and variable metadata
- parser helpers that detect coordinate candidates, triangle connectivity, and plottable scalar variables
- index-base normalization and validation errors

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=NetcdfDatasetParserTest test`
Expected: PASS

### Task 3: Add Color Mapping And Range Tests

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/render/ColorMap.java`
- Create: `src/main/java/com/example/netcdfviewer/render/ColorMaps.java`
- Create: `src/main/java/com/example/netcdfviewer/render/RangeStats.java`
- Create: `src/main/java/com/example/netcdfviewer/render/RenderMath.java`
- Create: `src/test/java/com/example/netcdfviewer/render/RenderMathTest.java`

- [ ] **Step 1: Write failing render logic tests**

Cover:

- min/max ignores `NaN`
- normalization clamps to `[0, 1]`
- color maps return deterministic endpoints

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=RenderMathTest test`
Expected: FAIL because render classes do not exist

- [ ] **Step 3: Write minimal implementation**

Implement:

- range calculation with optional fill-value filtering
- `Jet`, `Viridis`, and `Greys` maps
- value-to-color conversion helpers used by the renderer

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=RenderMathTest test`
Expected: PASS

### Task 4: Add Real File Parsing Test

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/io/NetcdfDatasetParser.java`
- Create: `src/test/java/com/example/netcdfviewer/io/LocalSampleDatasetTest.java`

- [ ] **Step 1: Write a failing integration-style test for one local `.nc` file**

The test should:

- enumerate candidate `.nc` files in the project root
- parse files until one passes the visualization requirements
- assert that at least one local file exposes coordinates, triangles, and a plottable scalar variable

- [ ] **Step 2: Run test to verify it fails or exposes real incompatibility**

Run: `mvn -q -Dtest=LocalSampleDatasetTest test`
Expected: either FAIL because parser support is incomplete or FAIL because no local file is compatible

- [ ] **Step 3: Extend parser implementation**

Add the minimum parsing support needed for the actual local dataset layout, while keeping heuristic detection generic.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=LocalSampleDatasetTest test`
Expected: PASS with one qualifying local sample file identified

### Task 5: Build JavaFX UI Shell

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Create: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Create: `src/main/java/com/example/netcdfviewer/ui/ColorBarCanvas.java`
- Create: `src/main/java/com/example/netcdfviewer/ui/ViewportState.java`
- Create: `src/main/java/com/example/netcdfviewer/render/TriangleRenderer.java`

- [ ] **Step 1: Write failing controller-focused tests where practical**

Cover:

- selecting a non-plottable variable disables visualization
- selecting a depth-layered variable enables the depth slider

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MainControllerTest test`
Expected: FAIL because controller behavior is not implemented

- [ ] **Step 3: Write minimal UI implementation**

Implement:

- file-open action and drag/drop hook
- metadata panel
- variable selector, depth slider, color map selector
- status bar updates
- render canvas and color bar refresh
- export PNG action

- [ ] **Step 4: Run focused tests and package build**

Run: `mvn test`
Expected: PASS

### Task 6: Package And Document

**Files:**
- Modify: `pom.xml`
- Modify: `README.md`
- Create: `scripts/package-exe.ps1`

- [ ] **Step 1: Write the packaging script and documentation**

Document:

- prerequisites
- Maven build command
- `jpackage` command
- where the `.exe` output appears
- which sample `.nc` file was validated successfully

- [ ] **Step 2: Run full verification**

Run: `mvn clean test package`
Expected: PASS and application artifact produced

- [ ] **Step 3: Run packaging command**

Run: `powershell -ExecutionPolicy Bypass -File .\\scripts\\package-exe.ps1`
Expected: Windows package or a clear blocker such as missing JavaFX runtime packaging flag

- [ ] **Step 4: Record outcome**

Update the README with the exact successful commands and any remaining packaging caveats.
