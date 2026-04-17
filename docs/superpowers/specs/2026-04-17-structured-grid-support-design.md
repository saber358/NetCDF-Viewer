# Structured Grid Support Design

## Goal

Upgrade the application so it can load and visualize standard-grid NetCDF datasets in addition to the existing unstructured triangle-mesh datasets.

The upgraded system must preserve the current feature set:

- scalar planar rendering
- layered variable switching
- point query
- multi-dataset management
- coastline overlay
- wave-direction overlay
- flow overlay
- PNG export
- packaged runtime behavior

The implementation must not hardcode variable names or dimension names as the primary recognition strategy. Variable and coordinate recognition must be metadata-driven first, with controlled heuristics as fallback.

## Scope

Included:

- add a structured-grid spatial model beside the existing triangle-mesh model
- detect horizontal coordinates for standard grids without relying on one specific variable naming convention
- support both auto-detected coordinates and user-selected coordinate overrides
- render structured-grid scalar variables
- support point query on structured grids
- support coastline overlay on structured grids
- support vector sampling for structured-grid wave and flow overlays
- support layered structured-grid variables
- keep current unstructured-grid behavior unchanged
- add automated tests for parsing, rendering, point query, overlay compatibility, UI behavior, export, and packaging

Excluded for this iteration:

- full CF/UGRID/SGRID compliance certification
- arbitrary time-axis animation
- 3D volume rendering
- arbitrary non-spatial business-dimension slicing beyond the current single-layer model
- user-defined reprojection engine

## Confirmed Decisions

The design decisions confirmed during brainstorming are:

- structured-grid support is a first-class capability, not a temporary triangle-conversion workaround
- existing project organization and code style must be preserved
- variable names and dimension names must not be hardcoded as the main matching rule
- users may manually choose horizontal coordinates when auto-detection finds multiple valid candidates
- manual coordinate choice must be constrained to valid horizontal-coordinate candidates only
- all existing end-user features should continue to work for structured grids through the same controller workflow

## Problem Statement

The current system is built around one spatial assumption:

- a dataset becomes renderable only after the parser finds a coordinate pair and a triangle connectivity variable

This works for unstructured datasets but rejects standard-grid files such as ROMS and WRF examples where:

- coordinates are defined by axes or coordinate variables
- horizontal layout is rectilinear or curvilinear
- no triangle connectivity array exists

This means the current architecture couples:

- parseability
- geometry type
- variable plottability
- point query semantics
- overlay sampling semantics

too tightly to the unstructured-mesh case.

## Target Dataset Classes

The new structured-grid path should support these horizontal layouts:

1. rectilinear grid
   - 1D horizontal coordinate arrays such as `x(nx)` and `y(ny)`
   - or `lon(nx)` and `lat(ny)`

2. curvilinear grid
   - 2D coordinate arrays such as `lon(ny,nx)` and `lat(ny,nx)`

3. staggered structured grids
   - scalar variables on one horizontal basis
   - vector components on compatible but shifted bases such as ROMS `rho/u/v`

The system should treat these as structured grids when they expose enough horizontal-coordinate semantics to place values in space.

Boundary:

- files with no reliable horizontal coordinate semantics may still open as metadata-only datasets
- such files are considered loaded successfully, but not renderable

This keeps the product honest while still meeting the requirement that standard-grid datasets should load through the application.

## Recognition Strategy

Coordinate and variable recognition must follow this priority order:

1. metadata-driven recognition
   - `coordinates` attribute
   - `axis`
   - `standard_name`
   - `units`
   - dimension reuse and shape compatibility

2. structural heuristics
   - axis monotonicity
   - shape relationship to candidate variables
   - shared horizontal basis

3. controlled naming fallback
   - names such as `lon`, `lat`, `x`, `y`, `XLONG`, `XLAT`
   - names such as `u`, `v`, `wdir`, `wlen` only after metadata and structure checks

Variable naming alone must never be sufficient when shape or coordinate compatibility disagrees.

## User Experience

The UI should stay close to the current workflow.

### Dataset activation

When a structured-grid dataset is loaded:

- the dataset appears in the existing loaded-dataset list
- metadata remains visible as today
- renderable variables appear in the same variable list

### Coordinate mode

Add a small coordinate selection section for datasets that expose structured-grid coordinate candidates.

Fields:

- `Coordinate mode`: `Auto` or `Manual`
- `X coordinate`
- `Y coordinate`

Rules:

- `Auto` is the default
- the manual selectors are enabled only when more than one valid horizontal coordinate pair exists
- the selectors list only valid horizontal-coordinate candidates
- depth/time/other business dimensions are never presented as X/Y choices
- coordinate choice is scoped to the active dataset

This gives users control without letting them produce invalid plots.

### Existing overlays

The existing overlay toggles remain where they are:

- `Wave arrows`
- `Flow lines`

Their availability should now depend on:

- compatible variable semantics
- compatible horizontal basis
- compatible layer structure

not on one specific geometry type.

## Architecture

The system must separate spatial representation from UI orchestration.

### Core idea

Introduce a common spatial-domain abstraction with two concrete implementations:

- triangle mesh domain
- structured grid domain

The controller should operate on the abstraction, not directly on `MeshData`.

### Proposed model split

Add a new domain layer in `model/`:

- `SpatialDomain`
- `TriangleDomain`
- `StructuredGridDomain`
- `CoordinateBinding`
- `HorizontalBasis`
- `VectorVariablePair` or equivalent basis-aware vector descriptor

Responsibilities:

- describe how values map into 2D space
- describe how point query resolves
- describe how vector sampling resolves
- expose spatial bounds for viewport fitting

`MeshData` remains for triangle datasets, but no longer acts as the only spatial carrier.

### Parsed dataset

`ParsedDataset` should be upgraded to carry:

- the spatial domain abstraction
- selected coordinate binding
- available coordinate bindings

instead of only:

- `mesh`
- `xVariableName`
- `yVariableName`
- `connectivityVariableName`

Compatibility note:

- unstructured datasets still expose triangle metadata through the new abstraction
- existing UI text may continue to show mesh details where applicable

### Variable description

`VariableInfo` currently models plottability around one spatial axis and one optional layer axis.

For structured-grid support it must be upgraded to store:

- horizontal-basis binding
- geometry kind
- whether the variable is scalar or vector-component compatible
- whether the variable is cell-centered or node-centered within its geometry

The old `nodeAxis` + `elementCentered` pair is too triangle-specific to cover structured-grid cases cleanly.

## Parsing Design

The parser should follow a two-phase flow.

### Phase 1: discover spatial candidates

For each dataset:

1. inspect variables and dimensions
2. find coordinate variables and coordinate pairs
3. classify candidate domains:
   - triangle mesh
   - rectilinear structured grid
   - curvilinear structured grid
4. build candidate horizontal bindings

### Phase 2: classify variables against candidate domains

For each numeric variable:

1. identify whether its effective shape matches a candidate horizontal basis
2. identify one optional layer dimension
3. mark it plottable when one horizontal basis matches and no unsupported extra dimensions remain
4. attach the chosen basis identifier to the variable metadata

This produces plottable variables without binding the code to one geometry family.

## Structured Grid Semantics

Structured-grid support must handle:

- scalar variables on the main horizontal basis
- vector components on compatible staggered bases

### Scalar fields

Supported examples:

- `temp(depth, y, x)`
- `salt(depth, y, x)`
- `zeta(y, x)`
- `T2(y, x)`
- `EvapDuctHeight(y, x)`

### Vector fields

Supported examples:

- rectilinear same-basis pairs such as `U10/V10`
- staggered-basis pairs such as ROMS `u/v`

For staggered pairs, the runtime should resolve a sampling basis that can interpolate both components onto a common query location.

This means:

- detection must not require both components to have identical raw shapes
- it must require them to belong to a compatible structured-grid family

## Rendering Design

### Scalar rendering

Add a structured-grid renderer beside the current triangle renderer.

Structured-grid renderer responsibilities:

- convert horizontal coordinates to screen coordinates
- draw scalar cells or sample raster tiles
- respect fill values
- support layered variables

Rendering strategy by grid type:

- rectilinear grids: render as cells derived from adjacent axis intervals
- curvilinear grids: render as quadrilateral patches or decomposed triangles internally

Internal implementation may triangulate quads for drawing, but that is a renderer detail only. It must not redefine the dataset as an unstructured triangle mesh in the data model.

### Viewport fit

Viewport fitting must move from `MeshData` bounds to `SpatialDomain` bounds.

This keeps one fit workflow for both:

- triangle datasets
- structured-grid datasets

## Point Query Design

Point query must also use the spatial abstraction.

### Triangle domain

Keep current barycentric query behavior.

### Structured domain

Add structured-grid point query with these rules:

- rectilinear grids: resolve the containing cell by axis lookup
- curvilinear grids: resolve the containing cell by local quad search
- cell-centered values return the cell value directly
- node-centered values return bilinear-style interpolation for rectilinear grids or local quad interpolation for curvilinear grids

Returned result fields should remain conceptually the same:

- hit or not
- world coordinate
- resolved cell index or equivalent identifier
- value
- layer index

The status-bar wording may be generalized from `triangle #...` to `cell #...` when the active dataset is structured.

## Coastline Overlay

Coastline overlay already works in world coordinates.

If viewport fitting and world-to-screen mapping are moved behind the spatial abstraction, coastline rendering can remain mostly unchanged.

Required behavior:

- overlay aligns with structured-grid plots
- overlay continues to update with zoom, pan, dataset switch, and export

## Wave Overlay Design

The current wave-pair finder is name-driven and basis-strict.

This must be generalized.

### Detection

Wave-pair detection should look for:

- direction-like variables
- magnitude/length-like variables
- compatible horizontal basis
- compatible layer structure

Metadata such as `standard_name`, `long_name`, `units`, and shape compatibility should drive the decision before variable-name fallback.

### Rendering

Wave arrows on structured grids should sample on a regular screen-space seed lattice, just as today, but query vectors through the structured-grid vector sampler.

## Flow Overlay Design

Flow support must also become geometry-agnostic.

### Detection

Flow-pair detection should identify compatible eastward/northward vector components by:

- semantics
- units
- shape compatibility
- shared structured-grid family

Variable names like `u/v`, `ua/va`, `U10/V10` are only fallback signals, not the primary contract.

### Sampling

Flowline generation should use a unified vector-query interface:

- triangle implementation: current barycentric logic
- structured implementation: rectilinear or curvilinear interpolation

### Staggered grids

For staggered structured grids such as ROMS:

- do not require `u` and `v` to share identical raw dimensions
- require them to map onto compatible staggered horizontal bases
- interpolate both components onto a common query location during sampling

This is the critical change that allows structured-grid flow overlays to work correctly.

## Export Design

PNG export should remain a snapshot of the current composite view.

No export UX change is required.

The only requirement is that the render pipeline feeding the snapshot now works for both geometry families.

## Error Handling

Error handling should stay permissive at dataset-load time.

Rules:

- metadata-only datasets still load
- non-renderable variables remain visible as informational entries
- unsupported overlays degrade gracefully
- scalar rendering failure in one dataset does not corrupt multi-dataset state

If a dataset is structured but coordinate detection is ambiguous:

- auto mode chooses the highest-confidence coordinate pair
- manual mode lets the user override to another valid candidate
- invalid manual combinations are blocked in the UI instead of failing later in rendering

## Project Organization Rules

The implementation must follow these organization constraints:

- no large geometry-specific branches inside `MainController`
- parser responsibilities remain in `io/`
- geometry/domain objects remain in `model/`
- rendering/query logic remains in `render/`
- UI classes only coordinate controls and state transitions
- shared contracts should be extracted before feature logic is duplicated

This is required to keep the codebase maintainable once both geometry families are supported.

## Testing Strategy

Use TDD.

### Parser tests

Add tests for:

- rectilinear structured-grid detection
- curvilinear structured-grid detection
- coordinate binding auto-selection
- manual coordinate override validation
- metadata-first recognition over naming fallback
- structured-grid scalar variables marked plottable without triangle connectivity

### Renderer tests

Add tests for:

- rectilinear scalar rendering
- curvilinear scalar rendering
- layered structured-grid rendering
- coastline alignment on structured grids

### Query tests

Add tests for:

- point query hit/miss on structured grids
- node-centered interpolation
- cell-centered direct value lookup

### Overlay tests

Add tests for:

- structured-grid wave arrow sampling
- same-basis vector pair detection
- staggered-grid vector pair detection
- structured-grid flowline generation

### UI/controller tests

Add tests for:

- coordinate selector state in auto/manual mode
- structured-grid dataset load path
- overlay control enablement on structured-grid datasets
- mixed multi-dataset sessions across triangle and structured datasets

### Export/runtime tests

Add tests for:

- PNG export with structured-grid scalar render
- PNG export with structured-grid overlays
- packaged runtime probe for at least one structured-grid sample

## Implementation Phases

Recommended execution order:

1. spatial abstraction and parsed-dataset model refactor
2. structured-grid parser and coordinate binding discovery
3. structured-grid scalar rendering and viewport fit
4. structured-grid point query
5. generalized vector-pair detection
6. structured-grid wave overlay
7. structured-grid flow overlay, including staggered sampling
8. controller polish, export validation, and runtime packaging checks

## Risks

Main risks:

- overloading `VariableInfo` with too much geometry-specific state
- letting `MainController` absorb geometry branching
- treating staggered grids as if raw shapes must match exactly
- falling back to name-based recognition too early
- silently allowing users to choose invalid coordinate axes

The design avoids these by:

- moving geometry semantics into dedicated domain objects
- keeping controller logic orchestration-only
- making basis compatibility explicit
- putting metadata and structure ahead of names
- constraining manual coordinate choice to validated candidates

## Success Criteria

The upgrade is complete when:

- unstructured triangle datasets still behave exactly as before
- standard-grid datasets with valid horizontal coordinate semantics load successfully
- structured-grid scalar variables render in the main view
- point query works on structured grids
- coastline overlay aligns correctly on structured grids
- wave and flow overlays work on structured grids through generalized vector sampling
- no feature depends on one specific variable name or dimension name as the only recognition rule
- tests and packaging pass on both geometry families
