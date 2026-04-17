# Wave Arrow Overlay Design

## Goal

Use wave metadata variables `wdir` and `wlen` to draw a wave-direction arrow overlay on top of the existing planar scalar render.

The overlay must help the user see wave direction and relative wavelength distribution without replacing the current scalar field workflow.

## Scope

Included:

- detect whether the active dataset exposes both `wdir` and `wlen`
- treat `wdir` as wave direction and `wlen` as wavelength when the file metadata confirms that meaning
- draw an arrow overlay above the existing scalar canvas
- use `wdir` to control arrow orientation
- use `wlen` to control arrow length within a bounded visual range
- add one UI toggle to show or hide the wave arrow overlay
- keep pan, zoom, layer selection, coastline overlay, export, and point query working
- add automated tests for dataset pairing logic and controller/UI behavior

Excluded for this iteration:

- animated particles or streamlines
- true wave phase animation
- user-editable arrow density, width, or color
- multi-variable vector composition from `u/v` components
- legend for arrow length
- persistence of overlay toggle state between launches

## Data Semantics

Local sample inspection confirms the intended meaning for the target files used in this project.

Observed examples:

- `20260331010000_alabo(1).nc`
- `HBHQY.nc`

For both files:

- `wdir`
  - dimensions: `time node`
  - units: `degree`
  - `long_name`: `Wave Direction`
- `wlen`
  - dimensions: `time node`
  - units: `meter`
  - `long_name`: `Wavelength`

This means the first implementation should treat the overlay as a node-based wave field overlay.

The feature should only activate when:

- both variables exist in the same dataset
- both variables are plottable on the same mesh basis
- both variables resolve to the same layer or single-layer selection as the current render context

If these conditions are not met, the overlay stays unavailable.

## User Experience

The current scalar variable remains the main render target.

The wave arrow overlay is optional and sits above:

- the scalar color field
- below or alongside the existing coastline overlay without obscuring the map

Interaction rules:

- if the dataset has no compatible `wdir/wlen`, the toggle is disabled
- if the dataset has compatible `wdir/wlen`, the toggle is enabled
- when enabled, arrows appear on top of the current view
- when disabled, rendering stays exactly as it works today
- changing zoom or pan redraws arrows at the new viewport
- changing the current layer updates the overlay when `wdir/wlen` are layered

Point query keeps reporting the currently selected scalar variable value only. It does not switch to querying arrow metadata.

## Visual Rules

The overlay should prioritize readability over density.

### Sampling

Do not draw one arrow per mesh node.

Instead:

- sample screen space on a coarse grid
- convert each sample point back to world space
- query the local mesh value at that point for `wdir` and `wlen`
- skip samples that do not hit the mesh or contain invalid values

This keeps the arrow count stable as datasets grow.

### Arrow geometry

Each arrow consists of:

- one shaft
- one simple arrowhead

Orientation:

- use `wdir` in degrees
- convert degrees to screen angle using the same world-to-screen orientation already used by the canvas

Length:

- normalize `wlen` against the valid range of the current `wlen` layer
- map the normalized value into a bounded pixel length range
- recommended initial range: roughly 8 to 26 pixels

Styling:

- single overlay color
- semi-opaque dark cyan or blue-gray stroke
- thin shaft with small head

The style should stay visible over bright and dark scalar colors without overpowering the base render.

## Architecture

The new feature should stay separate from the existing scalar renderer.

### Dataset pairing support

Add a small helper under `io` or `model` to identify a compatible wave arrow pair for a dataset.

The helper should:

- search dataset variables for `wdir` and `wlen`
- prefer exact names first
- verify compatible spatial basis
- verify compatible layer structure
- expose the matched `VariableInfo` pair in a small immutable result object

This keeps pairing logic out of `MainController`.

### Overlay render support

Add a dedicated renderer under `render` for wave arrows.

It should:

- accept the mesh
- accept the current `wdir` layer values and `wlen` layer values
- accept the viewport snapshot
- accept canvas size
- accept style and density constants
- draw arrows directly onto the JavaFX canvas graphics context after the scalar image is painted

It should not know about JavaFX controls, file loading, or controller state.

### Controller changes

`MainController` should:

- detect wave arrow availability for the active dataset
- keep the matched pair for the active dataset
- read `wdir` and `wlen` layer values alongside the current scalar render when the overlay is enabled
- trigger arrow redraw whenever the base render changes
- update the UI toggle enabled state

The controller should not contain arrow geometry math.

## Rendering Order

The draw order should be:

1. scalar rasterized image
2. wave arrow overlay, if enabled and available
3. coastline overlay

This preserves the current coastline readability while keeping arrows above the color field.

## Error Handling

If wave overlay preparation fails:

- the base scalar render should still succeed
- the arrow overlay should be skipped
- the status bar should show a short non-fatal message

Invalid `wdir` or `wlen` values should only suppress individual arrows, not fail the whole frame.

## Testing Strategy

Follow TDD.

### Pairing tests

Add focused tests for:

- dataset exposes exact `wdir/wlen` pair and pairing succeeds
- missing one variable disables pairing
- incompatible dimensions reject pairing
- layered pair uses the same layer basis

### Renderer tests

Add focused tests for:

- renderer skips arrows when values are invalid
- renderer produces drawable samples for valid direction and wavelength values
- arrow length mapping stays within configured bounds

Pure rendering math should stay testable without JavaFX stage setup where possible.

### Controller/UI tests

Add JavaFX-side tests that verify:

- opening a compatible dataset enables the wave arrow toggle
- toggling the overlay does not break base rendering
- switching datasets updates toggle availability
- layered redraw continues to work with the overlay on

## Implementation Notes

Keep the first iteration narrow:

- one toggle
- one arrow style
- one density strategy
- exact-name pairing for `wdir/wlen` first

Future versions can add animation, styling controls, and richer wave legends, but those are out of scope here.
