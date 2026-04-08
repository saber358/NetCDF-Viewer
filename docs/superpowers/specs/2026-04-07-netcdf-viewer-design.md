# NetCDF Viewer Design

## Goal

Build a Windows desktop application that opens unstructured-grid NetCDF files, identifies 2D scalar variables or depth-layered scalar variables, and renders planar triangle-filled views with depth switching.

The application must ship with at least one example `.nc` file in the working folder that the app can load successfully and use for demonstration.

## Scope

Included:

- Open local `.nc` files from menu or drag and drop
- Inspect dimensions, variables, and global attributes
- Detect coordinate variables, triangle connectivity variables, and plottable scalar variables
- Render scalar data on a 2D plane from triangle connectivity
- Switch depth layers for `[depth, cell]` style variables
- Show color bar and current layer min/max
- Export the current view to PNG
- Build with Maven and package for Windows with `jpackage`

Excluded for this iteration:

- Automatic Delaunay reconstruction when no connectivity variable exists
- Geographic reprojection beyond simple lon/lat planar usage
- Contour lines
- OpenGL acceleration
- Installer signing

## Chosen Architecture

The application uses Java 17+ with JavaFX for the desktop UI and Canvas-based rendering. NetCDF parsing is handled by `edu.ucar:netcdf4` and related UCAR modules from the NetCDF-Java 5.x line.

The code is split into five bounded areas:

- `app` bootstraps JavaFX and application lifecycle
- `io` parses NetCDF metadata and numeric arrays into a view-friendly domain model
- `model` stores mesh, variables, and dataset selection state
- `render` converts scalar values to colors and paints the mesh plus color bar
- `ui` contains the JavaFX scene graph, controllers, and user interaction wiring

## Data Assumptions

The first supported data shape is a node-centered triangular mesh with these patterns:

- coordinate variables: `x/y` or `lon/lat`
- connectivity variable: 2D integer array with trailing dimension size `3`
- scalar variable: `[node]` or `[depth, node]`

The reader will not hardcode exact names. It will score likely candidates using:

- variable name matches such as `x`, `y`, `lon`, `lat`, `element`, `elements`, `connectivity`, `nv`, `tri`
- dimension compatibility with coordinate count
- integer type for connectivity arrays

Connectivity index base is auto-detected:

- if the minimum index is `0`, use zero-based indexing
- if the minimum index is `1`, convert to zero-based indexing
- otherwise raise a validation error

If no compatible coordinate pair or triangle connectivity array is found, the file still opens for metadata browsing but the app disables planar visualization and shows a clear message.

## UI Design

The main window uses a `BorderPane`.

- Top: menu bar and a small toolbar with Open, Export PNG, and Reset View
- Left: dataset inspector with file summary, variables list, and attributes tab
- Center: visualization canvas with pan/zoom support
- Right: control panel with variable selector, depth slider, colormap selector, auto/manual range controls, and current layer statistics
- Bottom: status bar with file name, current variable, depth level, and cursor/value messages

The variable list visually distinguishes:

- plottable scalar variables
- depth-layered scalar variables
- non-plottable variables shown as informational only

## Rendering Design

The renderer precomputes screen-space geometry inputs from:

- `double[] x`
- `double[] y`
- `int[][] triangles`

For each selected variable layer:

1. extract the scalar array for the current depth
2. compute filtered min/max ignoring missing values and NaNs
3. map node values to colors through the selected color map
4. compute one triangle fill color from the average of the three node values
5. draw each triangle on the JavaFX `Canvas`
6. draw an adjacent color bar with tick labels

This favors implementation simplicity and predictable performance over full per-pixel interpolation. The design remains acceptable for large but not extreme meshes and can be upgraded later if needed.

## Error Handling

The application surfaces errors in three levels:

- non-fatal validation warnings in the UI status area
- modal error dialog for file-open and parsing failures
- structured exceptions inside the parser with user-friendly messages

Examples:

- missing coordinates
- connectivity dimension not equal to 3
- variable dimensions do not match node count
- unsupported numeric type

## Example Data Requirement

The project must include a documented verification step against one of the `.nc` files already present in the folder. A file qualifies as the shipped example only if:

- the parser detects coordinates and triangle connectivity
- at least one plottable scalar variable is available
- the main visualization loads without runtime error

If none of the existing files qualify, the project is still delivered but the README must explicitly say that example data is pending and the packaging result is not yet demonstration-ready. The implementation effort should first try to validate the local files before introducing any external sample.

## Testing Strategy

Use JUnit 5 and TDD for non-UI logic. Test coverage must include:

- candidate detection for coordinate and connectivity variables
- connectivity base normalization from 1-based to 0-based
- plottable variable detection for `[node]` and `[depth, node]`
- min/max filtering with NaNs and fill values
- color map interpolation
- parsing at least one real local `.nc` file when available

UI behavior is verified with focused unit coverage for controller logic and manual runtime verification for rendering and packaging.

## Packaging

The build produces:

- runnable fat or modular application artifact from Maven
- Windows `.exe` package with `jpackage`

The packaging path assumes a local JDK with `jpackage` available. The README will include exact commands for:

- `mvn clean package`
- `jpackage ... --type exe`

## Risks And Choices

The main product risk is dataset variability. NetCDF files in the folder may use naming conventions or dimensional layouts that differ from the baseline assumption. To manage that risk, the reader is built around heuristics plus explicit validation feedback instead of fixed variable names.

The main technical choice is to support robust metadata inspection even when a file is not plottable. That keeps the app useful for investigation while making visualization requirements strict and predictable.
