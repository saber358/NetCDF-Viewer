# Invalid Geographic Coordinate Warning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Warn users before loading NetCDF files whose coordinates look like longitude/latitude but fall outside valid geographic ranges.

**Architecture:** Add a small validation result inside `MainController` so loading and basemap decisions use the same range rules. Keep parsing unchanged. Add tests against existing local samples and a synthetic non-geographic domain.

**Tech Stack:** Java 17, JavaFX Alert, JUnit 5, Maven.

---

### Task 1: Validation Result

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that call a static validation helper by reflection:

```java
@Test
void hydDoesNotNeedInvalidGeographicWarning() throws Exception {
    NetcdfDatasetParser parser = new NetcdfDatasetParser();
    ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("HYD.nc"));

    Object result = invalidGeographicCoordinateWarning(dataset);

    assertFalse((Boolean) result.getClass().getDeclaredMethod("warningRequired").invoke(result));
}

@Test
void dsdNeedsInvalidGeographicWarningBecauseLatitudeRangeIsInvalid() throws Exception {
    NetcdfDatasetParser parser = new NetcdfDatasetParser();
    ParsedDataset dataset = parser.open(SampleDatasetPaths.resolve("DSD1211.nc"));

    Object result = invalidGeographicCoordinateWarning(dataset);

    assertTrue((Boolean) result.getClass().getDeclaredMethod("warningRequired").invoke(result));
    assertTrue((Boolean) result.getClass().getDeclaredMethod("geographicNames").invoke(result));
    assertFalse((Boolean) result.getClass().getDeclaredMethod("validLatitude").invoke(result));
}
```

- [ ] **Step 2: Verify tests fail**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -q -Dtest=MainControllerLoadFileTest test
```

Expected: fail because `invalidGeographicCoordinateWarning` does not exist.

- [ ] **Step 3: Implement validation result**

Add `InvalidGeographicCoordinateWarning` record and `invalidGeographicCoordinateWarning(ParsedDataset dataset)` in `MainController`.

- [ ] **Step 4: Verify focused tests pass**

Run the same focused Maven command.

Expected: pass.

### Task 2: Load Confirmation

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write failing test for dialog text**

Test that the warning message includes file name, coordinate names, expected ranges, actual ranges, failed axis, and consequences.

- [ ] **Step 2: Verify test fails**

Expected: fail because message builder does not exist.

- [ ] **Step 3: Implement dialog message and confirmation**

On load success, before `addLoadedDataset`, call a confirmation helper. If the user cancels, set status to canceled and do not add the item.

- [ ] **Step 4: Verify focused tests pass**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -q -Dtest=MainControllerLoadFileTest test
```

Expected: pass.

### Task 3: Regression

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update documentation**

Add one sentence under basemap notes: geographic-looking files with invalid coordinate ranges prompt before loading.

- [ ] **Step 2: Run full tests**

Run:

```powershell
$env:JAVA_HOME='D:\ProgramFile\JAVA\jdk-21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"; mvn -q test
```

Expected: pass.
