# U/V Flowline Overlay Design

## Goal

Use the velocity variables `u` and `v` to render animated flow lines above the existing scalar planar visualization.

The flow overlay must preserve the current scalar rendering workflow, coastline overlay, point query behavior, and PNG export behavior.

## Scope

Included:

- detect compatible velocity pairs from the active dataset
- prefer `u/v` and fall back to `ua/va` only when `u/v` is unavailable
- render flow lines as an overlay above the current scalar base layer
- animate the flow field by moving a highlight band along precomputed flow lines
- add one user toggle to enable or disable the flow overlay
- keep the overlay disabled by default
- regenerate flow lines when dataset, layer, viewport, or canvas size changes
- reuse cached flow lines when only scalar color styling changes
- include automated tests for pair detection, flowline generation, animation state, UI behavior, and export compatibility

Excluded for this iteration:

- a standalone vector-only rendering mode
- particle animation
- user-editable seed density, line thickness, or animation speed controls
- support for arbitrary velocity variable naming beyond the chosen priority rules
- time animation across multiple NetCDF time steps
- legend controls for velocity magnitude

## Confirmed Decisions

The design decisions confirmed during brainstorming are:

- render the flow field as an overlay above the current scalar base map
- use `u/v` first
- fall back to `ua/va` only when `u/v` does not exist
- animate the flow line itself with a moving highlight band
- do not use particles
- keep the overlay disabled by default
- expose the feature through a manual `Flow lines` checkbox

## Data Semantics

Local sample inspection shows the target datasets expose water velocity in the expected form.

Observed examples:

- `HBHQY.nc`
- `ydw.nc`
- `nanhai.nc`
- `20260331010000_alabo(1).nc`

Observed variable semantics:

- `u`
  - dimensions: `time siglay nele`
  - units: `meters s-1`
  - `long_name`: `Eastward Water Velocity`
  - `standard_name`: `eastward_sea_water_velocity`
- `v`
  - dimensions: `time siglay nele`
  - units: `meters s-1`
  - `long_name`: `Northward Water Velocity`
  - `standard_name`: `Northward_sea_water_velocity`

Observed fallback variables in `nanhai.nc`:

- `ua`
  - dimensions: `time nele`
  - `long_name`: `Vertically Averaged x-velocity`
- `va`
  - dimensions: `time nele`
  - `long_name`: `Vertically Averaged y-velocity`

This means the first version should treat the velocity field as an element-centered vector field. Flow integration must therefore sample velocity from the containing triangle, not from mesh nodes.

## User Experience

The scalar field remains the main render target.

The flow overlay is a secondary visualization layer with these rules:

- a new `Flow lines` checkbox appears in the right-side control panel
- the checkbox is disabled when no compatible velocity pair exists
- the checkbox is enabled when a compatible pair exists
- the checkbox defaults to unchecked every time the dataset lacks the previous overlay state
- when enabled, animated flow lines appear above the scalar field and below the coastline overlay
- when disabled, the application behaves exactly as it does today

The current point query continues to report the selected scalar variable only. The flow overlay is visual only and does not change query semantics.

PNG export should capture the current frame, including the current flow-line highlight position.

## Rendering Order

The draw order should be:

1. scalar rasterized image
2. flow line static base strokes
3. flow line animated highlight bands
4. coastline overlay

This keeps coastline readability intact while ensuring the flow layer remains visible against the scalar color field.

## Velocity Pair Detection

Add a dedicated velocity-pair finder separate from the wave-pair finder.

Priority order:

1. exact `u` + exact `v`
2. exact `ua` + exact `va`

The finder should only return a pair when both variables:

- are plottable
- share the same spatial basis
- share the same layer structure or both are single-layer
- are both element-centered or both node-centered

The flow overlay must use the chosen pair consistently for both streamline generation and animation.

## Flowline Generation

Flowline generation should be separated from animation.

### Seed placement

Seed points are placed in screen space on a regular coarse grid.

Reason:

- stable density across zoom levels
- avoids over-seeding large unstructured meshes
- matches the existing overlay approach used for wave arrows

Each seed point is projected back into world space. Only seeds that hit a valid triangle and have valid velocity values are used.

### Velocity sampling

Because current sample files store `u/v` on `nele`, the generator should sample velocity from the hit element.

For the first version:

- if the pair is element-centered, use the containing triangle value directly
- if a future dataset exposes node-centered velocity, barycentric interpolation may be used through the same query helper pattern

### Integration method

Use a second-order integration method instead of a simple Euler step.

Recommended approach:

- midpoint integration

Reason:

- materially more stable than single-step Euler
- still simple enough for a focused first implementation

### Bidirectional tracing

Each final flow line is built by:

- integrating forward from the seed point
- integrating backward from the seed point
- joining both paths into one polyline

This produces a centered, continuous flow line instead of a one-sided tail.

### Stop conditions

Stop integrating when any of these happens:

- the trace leaves the mesh
- the current sample point lands on invalid or missing velocity values
- the local speed falls below a minimum threshold
- the trace exceeds the configured maximum step count
- the trace exceeds the configured maximum visual length
- the trace loops back into a recently visited neighborhood

### De-duplication

After generating one flow line, write its covered screen footprint into an occupancy mask.

Future seed points are skipped when they are too close to already accepted lines.

This avoids dense line stacking and keeps the result readable.

## Animation Model

Animation should not regenerate flow lines every frame.

Instead:

1. build the static polyline once for the current dataset, layer, viewport, and canvas
2. compute cumulative segment length along each polyline
3. animate a highlight window that moves along the cumulative length domain

Visual structure:

- one subtle dark base stroke for the entire line
- one brighter semi-transparent moving band for the current animated segment

The moving band should:

- repeat cyclically
- move in the forward flow direction
- preserve line geometry and only change phase over time

This gives a stable animated flow-field effect with lower per-frame cost than particle systems.

## Rebuild Triggers

The expensive flow-line cache should be rebuilt when any geometry-affecting input changes:

- active dataset changes
- selected layer changes
- viewport pan changes
- viewport zoom changes
- canvas width or height changes
- the `Flow lines` checkbox changes from off to on
- the selected velocity pair changes because the active dataset changed

The cache should be reused when only scalar styling changes:

- color map changes
- scalar manual range changes
- scalar auto range toggles

In those cases the base scalar image may redraw, but the flowline geometry cache should remain valid if viewport and velocity inputs are unchanged.

## Architecture

Keep the feature split into small focused units.

### Velocity pair finder

Responsibility:

- detect `u/v` or `ua/va`
- validate compatibility
- expose one immutable pair object

### Flow query helper

Responsibility:

- sample the velocity vector at an arbitrary screen or world location
- resolve the containing triangle
- return velocity magnitude and direction components

This may reuse or mirror the point-query helper structure already present in `render`.

### Flowline generator

Responsibility:

- place seeds
- integrate forward and backward
- apply stop rules
- return immutable polylines and cumulative-length metadata

This unit should not know about JavaFX controls.

### Flowline animator/renderer

Responsibility:

- draw cached base polylines
- compute animated highlight phase from current time
- draw the moving highlight band for the current frame

### Controller integration

`MainController` remains the orchestrator.

It should:

- detect the active velocity pair
- manage the `Flow lines` control state
- request flowline cache generation when needed
- trigger per-frame redraw for highlight animation while the overlay is enabled
- stop animation when the overlay is disabled or when no valid flow cache exists

## Error Handling

If flowline generation fails:

- scalar rendering must still succeed
- coastline rendering must still succeed
- the flow overlay is skipped
- the status bar may show a short non-fatal message

If the overlay is enabled but the velocity range is effectively zero:

- skip flowline generation
- keep the checkbox enabled
- show no flow lines for that state

## Testing Strategy

Use TDD.

### Velocity-pair detection tests

Add focused tests for:

- `u/v` pair is detected when both exist
- `ua/va` is used only when `u/v` is absent
- incompatible dimensions reject pairing
- layered and single-layer cases resolve correctly

### Flowline generation tests

Add focused tests for:

- valid velocity field generates non-empty flowlines
- zero or invalid velocity values stop traces
- maximum step and length limits are enforced
- duplicate seed suppression reduces overlapping lines

### Animation tests

Add focused tests for:

- highlight phase changes the visible band position
- animation direction follows the forward flow direction
- no per-frame geometry regeneration is required for simple phase updates

### UI/controller tests

Add JavaFX-side tests for:

- `Flow lines` is enabled only when a compatible velocity pair exists
- enabling the overlay does not break scalar rendering
- layer switching rebuilds the flow overlay when `u/v` is layered
- disabling the overlay stops the animated redraw loop

### Export/runtime tests

Add or update tests for:

- PNG export includes the flow overlay in the captured frame
- packaged runtime still supports the updated packaging script and render path

## Implementation Notes

Keep the first version tight:

- one flow overlay toggle
- one seed-density strategy
- one integration method
- one highlight style
- no settings panel for line density or animation speed yet

This version should establish the correct data model, cache boundaries, and animation loop. Further controls can be added later without reworking the core structure.
