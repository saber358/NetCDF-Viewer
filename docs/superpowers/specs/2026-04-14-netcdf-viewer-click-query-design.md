# NetCDF Viewer Click Query Design

## Goal

Add single-point query support to the planar visualization so the user can click the rendered mesh and inspect the data value at that position.

The query must work with the current viewport transform, the currently selected variable, and the currently selected layer.

## Scope

Included:

- Left-click query on the main planar canvas
- Convert screen coordinates back to world coordinates
- Detect whether the click falls inside a rendered triangle
- For element-centered variables, return the triangle's raw value
- For node-centered variables, compute the clicked point value by barycentric interpolation inside the hit triangle
- Show query results in the bottom status bar
- Add automated tests for geometry and controller behavior

Excluded for this iteration:

- Hover query
- Persistent markers or annotations on the canvas
- Side panel query history
- Multi-point comparison
- Snapping to nearest node or nearest triangle when the click is outside the mesh
- Value query during file loading or incomplete render states

## User Experience

The query interaction is attached to the center render canvas.

- A left mouse click on the planar view triggers a point query
- Dragging continues to mean pan, so only a near-stationary press/release sequence counts as a click query
- Zoom and pan behavior remain unchanged

The first version reports results only through the bottom status bar. The message should include enough detail to be useful without interrupting the workflow.

Examples:

- `Query temp layer 3 at (121.3456, 31.2288): triangle #15284, value=17.4821`
- `Query hs at (122.0000, 30.5000): triangle #81, value=1.2384`
- `No mesh value at clicked location.`
- `Clicked triangle contains no valid value.`

The reported value is always the raw data value at the queried position. It is not clipped or transformed by the current manual display range.

## Architecture

The feature should follow the existing separation between UI orchestration, viewport state, and pure rendering/math helpers.

### Controller responsibilities

`MainController` remains the interaction coordinator.

It should:

- register click detection on the render host
- ignore clicks when no successful render context is available
- invoke a dedicated point-query helper with the latest render context
- update the status bar with the result text

`MainController` should not contain triangle hit-testing or interpolation math directly.

### Query helper responsibilities

Add a new pure computation helper dedicated to mesh point queries. It may live under `render` because it depends on mesh geometry and query math rather than JavaFX widgets.

The helper should:

- accept the mesh, current values, variable mode, fill value, viewport snapshot, and clicked screen coordinates
- convert the clicked screen coordinate into world space
- iterate triangles and find the first triangle that contains the world point
- compute the queried value for the hit triangle
- return a structured result object instead of preformatted UI text

The result object should include:

- hit or miss state
- world X and Y
- triangle index when hit
- queried value when valid
- layer index
- reason when hit failed or value is unavailable

### Render context snapshot

Point query should use the latest successful render data, not reopen the NetCDF file on every click.

`MainController` should cache a small immutable query context after a render succeeds. The context should contain:

- current dataset reference
- current variable reference
- current layer index
- the current layer `double[] values`
- whether the variable is element-centered
- the current `ViewportState.Snapshot`

The query context is replaced on every successful render and cleared when a file is unloaded, loading fails, or rendering falls back to a placeholder.

## Geometry And Value Rules

### Hit testing

The helper converts the clicked screen point to world coordinates using the current viewport snapshot.

For each triangle:

1. read the three world-space vertices from `MeshData`
2. compute barycentric coordinates
3. treat the point as inside when the barycentric coordinates are all within a small tolerance of the triangle bounds

The first valid containing triangle is returned.

### Element-centered variables

If the selected variable is element-centered:

- the triangle index maps directly to the queried value
- the returned query value is the raw triangle value

If the triangle's value is invalid according to existing render rules, the query returns a hit with an unavailable-value reason.

### Node-centered variables

If the selected variable is node-centered:

- read the three node values of the hit triangle
- require all three node values to be renderable
- compute the clicked value using barycentric interpolation

If any required node value is invalid, the query returns a hit with an unavailable-value reason.

### Miss behavior

If the click is outside the mesh, return a miss with world coordinates and a `no hit` reason. The controller maps this to `No mesh value at clicked location.`

## State And Error Handling

Point query is disabled in these situations:

- no dataset loaded
- dataset has no plottable mesh
- no plottable variable selected
- no completed render context is available yet
- a placeholder is currently shown because render preparation failed

These are not exceptional errors. The controller should only update the status bar with a short explanatory message.

The query helper itself should not depend on JavaFX controls and should not show dialogs.

## Testing Strategy

Follow TDD for both computation and integration behavior.

### Pure query tests

Add focused unit tests for:

- hit detection inside a known triangle
- miss detection outside the triangle mesh
- barycentric interpolation for node-centered values
- element-centered raw value lookup
- invalid-value handling for fill values or NaNs

### Controller/UI tests

Add JavaFX-side tests that verify:

- clicking after a successful render updates the status bar with a query result
- clicking outside the mesh updates the status bar with the miss message
- dragging to pan does not accidentally trigger a point query

The UI tests should reuse the existing test harness style already used by `MainControllerLoadFileTest` and related JavaFX tests.

## Implementation Notes

To keep the first iteration focused:

- do not add any new visible controls
- do not add query history storage
- do not add canvas overlays
- keep messages concise and formatted with the existing numeric formatting style

This design intentionally lays groundwork for future enhancements such as hover inspection, pinned samples, or an information panel, but those should remain out of scope for this change.
