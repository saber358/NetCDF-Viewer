# NetCDF Viewer 1.0.1 Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release NetCDF Viewer `1.0.1` with user attribution for `lwj`, a new ocean-technology icon, and matching Windows packaging metadata.

**Architecture:** Keep the branding update isolated from parsing and rendering behavior. Centralize app metadata in one small class, surface it through the JavaFX shell, and wire packaging/version metadata from the same release value so the executable, dialog text, and build artifacts stay consistent.

**Tech Stack:** Java 17+, Maven, JavaFX, JUnit 5, PowerShell `jpackage`, classpath resources

---

### Task 1: Centralize Release Metadata

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/AppMetadata.java`
- Modify: `src/main/java/com/example/netcdfviewer/App.java`
- Test: `src/test/java/com/example/netcdfviewer/smoke/AppBootstrapTest.java`

- [ ] **Step 1: Write the failing test**

Add assertions to `AppBootstrapTest` for the new metadata surface:

```java
assertEquals("1.0.1", AppMetadata.VERSION);
assertEquals("lwj", AppMetadata.AUTHOR_NAME);
assertEquals("2762692204@qq.com", AppMetadata.AUTHOR_EMAIL);
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: FAIL because `AppMetadata` does not exist yet

- [ ] **Step 3: Write minimal implementation**

Create `AppMetadata` with constants for:

```java
public static final String APP_NAME = "NetCDF Viewer";
public static final String VERSION = "1.0.1";
public static final String AUTHOR_NAME = "lwj";
public static final String AUTHOR_EMAIL = "2762692204@qq.com";
```

Update `App.java` to reuse `AppMetadata.APP_NAME`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: PASS

### Task 2: Add Footer Attribution And Help/About Entry

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Test: `src/test/java/com/example/netcdfviewer/smoke/AppBootstrapTest.java`

- [ ] **Step 1: Write the failing UI test**

Extend `AppBootstrapTest` to verify:

```java
assertEquals("Author: lwj | 2762692204@qq.com", view.getAuthorLabel().getText());
assertEquals("Help", view.getHelpMenu().getText());
assertEquals("About", view.getAboutMenuItem().getText());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: FAIL because the footer label and Help/About controls do not exist

- [ ] **Step 3: Write minimal implementation**

Update `MainView` to:

- create a `Help` menu and `About` menu item
- expose getters for those controls
- add a right-aligned static footer label with the exact author string

Update `MainController` to wire the `About` menu item.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: PASS

### Task 3: Add About Dialog Content Contract

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Test: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write the failing dialog-content test**

Add a focused test that calls a package-visible or static content builder and verifies:

```java
assertTrue(content.contains("NetCDF Viewer"));
assertTrue(content.contains("1.0.1"));
assertTrue(content.contains("lwj"));
assertTrue(content.contains("2762692204@qq.com"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`
Expected: FAIL because the about content builder does not exist

- [ ] **Step 3: Write minimal implementation**

Add a small helper in `MainController`, for example:

```java
static String aboutContent() { ... }
```

Use that helper from the `About` dialog action so the tested content matches the shown content.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=MainControllerLoadFileTest test`
Expected: PASS

### Task 4: Add Icon Resources And Stage Icon Wiring

**Files:**
- Create: `src/main/resources/icons/app-icon.png`
- Create: `src/main/resources/icons/app-icon.ico`
- Modify: `src/main/java/com/example/netcdfviewer/App.java`
- Test: `src/test/java/com/example/netcdfviewer/smoke/AppBootstrapTest.java`

- [ ] **Step 1: Write the failing icon test**

Add assertions that the classpath resource exists:

```java
assertNotNull(App.class.getResource("/icons/app-icon.png"));
assertNotNull(App.class.getResource("/icons/app-icon.ico"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: FAIL because the icon resources are missing

- [ ] **Step 3: Write minimal implementation**

- generate a simple ocean-technology icon asset
- save PNG and ICO versions under `src/main/resources/icons/`
- update `App.start(Stage)` to load `/icons/app-icon.png` and add it to `stage.getIcons()`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AppBootstrapTest test`
Expected: PASS

### Task 5: Update Build And Packaging Metadata

**Files:**
- Modify: `pom.xml`
- Modify: `scripts/package-exe.ps1`
- Modify: `README.md`
- Test: `src/test/java/com/example/netcdfviewer/runtime/PackagedRuntimeCompatibilityTest.java`

- [ ] **Step 1: Write the failing metadata test**

Add a test that reads `scripts/package-exe.ps1` as text and verifies it contains:

```java
assertTrue(script.contains("netcdf-viewer-1.0.1.jar"));
assertTrue(script.contains("--app-version 1.0.1"));
assertTrue(script.contains("--vendor lwj"));
assertTrue(script.contains("--icon"));
assertTrue(script.contains("app-icon.ico"));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=PackagedRuntimeCompatibilityTest test`
Expected: FAIL because the packaging script still targets `1.0.0` and no icon

- [ ] **Step 3: Write minimal implementation**

Update:

- `pom.xml` version to `1.0.1`
- packaging script jar name and app version to `1.0.1`
- packaging vendor to `lwj`
- packaging script to pass the `.ico` path through `--icon`
- README jar/install references to `1.0.1`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=PackagedRuntimeCompatibilityTest test`
Expected: PASS

### Task 6: Full Regression And Packaging Verification

**Files:**
- Verify: `src/main/java/com/example/netcdfviewer/...`
- Verify: `scripts/package-exe.ps1`
- Verify: `target/installer/NetCDFViewer-1.0.1.exe`

- [ ] **Step 1: Run the full test suite**

Run: `mvn -q test`
Expected: PASS with no test failures

- [ ] **Step 2: Run the packaging script**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1`
Expected: installer generation succeeds

- [ ] **Step 3: Verify installer output**

Run: `Get-ChildItem .\target\installer | Select-Object Name,Length,LastWriteTime`
Expected: `NetCDFViewer-1.0.1.exe` is present

- [ ] **Step 4: Record release state**

Confirm in the final summary that:

- footer attribution is present
- Help/About exists
- icon resources are bundled
- packaging metadata is updated to `1.0.1`
