# NetCDF Viewer 1.0.1 Branding Update Design

**Date:** 2026-04-08

**Status:** Approved for specification review

## Goal

Ship a `1.0.1` release of NetCDF Viewer that adds explicit author attribution for `lwj <2762692204@qq.com>`, replaces the application icon with a custom ocean-technology themed icon, and updates the Windows packaging metadata so the new branding is visible both inside the application and in the generated installer.

## Scope

This update is intentionally limited to branding, metadata, and packaging polish. It does not change NetCDF parsing, rendering behavior, dataset compatibility, or any visualization workflow.

Included:

- Upgrade the application version from `1.0.0-SNAPSHOT` / `1.0.0` to `1.0.1`
- Add author information to the main UI footer area
- Add a formal `Help -> About` dialog with app and author details
- Introduce a custom ocean-technology application icon
- Use the new icon for the JavaFX window and Windows installer
- Update packaging metadata to use the new version and author identity
- Update documentation where versioned commands or artifact names are shown

Excluded:

- Changes to parsing or rendering logic
- New visualization features
- New export formats
- Installer UI customization beyond version/vendor/icon metadata

## User-Facing Design

### 1. Main Window Attribution

The existing bottom status bar remains the primary location for transient status messages. A second static author label is added on the same bar so author attribution is always visible without disturbing the main workspace.

Presentation:

- Left side: existing status text, unchanged in behavior
- Right side: `Author: lwj | 2762692204@qq.com`

This keeps the UI clean while satisfying the requirement that the software includes the user's information.

### 2. Help Menu And About Dialog

Add a new `Help` menu to the menu bar with an `About` item.

The `About` dialog displays:

- Product name: `NetCDF Viewer`
- Version: `1.0.1`
- Author: `lwj`
- Email: `2762692204@qq.com`
- Short description: `Desktop viewer for unstructured-triangle NetCDF planar visualization`

The dialog should use a standard JavaFX information alert or a small custom dialog built from existing UI primitives. The content should be concise and readable. No external browser links are needed.

### 3. Icon Direction

The new icon follows the chosen `ocean technology` direction:

- Deep blue to teal color palette
- Abstract wave motion
- Subtle triangle mesh motif
- Small highlight or focus point to suggest measurement / visualization

The icon must work at small sizes, so the composition should stay simple and high-contrast. The same visual source is used to derive:

- a PNG resource for the JavaFX stage icon
- a Windows `.ico` file for `jpackage`

## Technical Design

### Application Versioning

Update all versioned locations so the release is consistent:

- Maven project version in `pom.xml` -> `1.0.1`
- Packaging script jar name -> `netcdf-viewer-1.0.1.jar`
- `jpackage --app-version` -> `1.0.1`
- Installer output file becomes `NetCDFViewer-1.0.1.exe`
- Any README commands or references that include versioned jar names are updated

### App Metadata

Replace packaging vendor metadata with the user's identity rather than the previous placeholder vendor string.

Recommended packaging metadata:

- Vendor: `lwj`
- Description remains product-oriented

If supported cleanly by the current `jpackage` invocation, icon metadata should also be attached through `--icon`.

### Resource Layout

Add branding assets under the standard resources tree so they are included in both development runs and packaged builds.

Planned resources:

- `src/main/resources/icons/app-icon.png`
- `src/main/resources/icons/app-icon.ico`

The JavaFX application loads the PNG icon from classpath resources when the stage starts.

### UI Integration

Planned code changes:

- `App.java`
  - load and apply the stage icon from resources
- `MainView.java`
  - extend the menu bar with `Help -> About`
  - update the bottom status bar layout to include a right-aligned static author label
- `MainController.java`
  - wire the `About` menu action
  - show the about dialog content with the new version and author info

The implementation should preserve the current layout stability fixes. The footer update must not reintroduce the center-panel resizing issue.

## Testing Strategy

This is a behavior change, so regression tests must be added first and made to fail before implementation.

Coverage targets:

- Main view exposes an `About` menu item and author label text
- About dialog content creation includes `lwj`, `2762692204@qq.com`, and `1.0.1`
- Packaging script references `1.0.1`, updated jar name, vendor `lwj`, and icon path
- Existing smoke and UI tests still pass after the footer/menu changes

Verification commands after implementation:

```powershell
mvn -q test
powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1
```

Expected results:

- full test suite passes
- a new installer is produced under `target\installer`
- installer filename is `NetCDFViewer-1.0.1.exe`

## Risks And Mitigations

### Risk: Footer layout changes disturb the stabilized center panel

Mitigation:

- keep footer changes limited to the existing bottom container
- rerun the existing layout stability test

### Risk: Icon path works in IDE but not in packaged runtime

Mitigation:

- load JavaFX icon from classpath resource, not file-system relative path
- verify packaged installer generation with `jpackage`

### Risk: Windows installer icon requires `.ico` specifically

Mitigation:

- generate both PNG and ICO assets and use `.ico` explicitly in `jpackage`

## Acceptance Criteria

The `1.0.1` release is complete when all of the following are true:

- The application window launches with the new custom icon
- The Windows installer uses the new icon
- The bottom status bar visibly shows `Author: lwj | 2762692204@qq.com`
- The menu bar contains `Help -> About`
- The About dialog shows product name, version `1.0.1`, author, and email
- The Maven build, tests, and packaging process all complete successfully
- The generated installer is `NetCDFViewer-1.0.1.exe`
