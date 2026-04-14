# Coastline Overlay Design

## Goal

Add a lightweight coastline/reference-boundary overlay feature for the planar NetCDF visualization.

Users should be able to load one coastline file and see it drawn above the current rendered mesh. The overlay must follow the same viewport transform as the mesh, so pan, zoom, dataset switching, and re-rendering keep the coastline aligned.

## Scope

Included:

- Load a single coastline overlay file at a time
- Clear the loaded overlay
- Support GeoJSON `.geojson` and `.json` files
- Support shapefile `.shp` line and polygon geometry records
- Draw line and polygon boundaries as strokes only
- Keep the overlay in memory while switching between loaded NetCDF datasets
- Add automated parser and UI/controller regression tests

Excluded for this iteration:

- Coordinate reprojection
- Multiple overlay layers
- Per-dataset overlay binding
- Attribute-driven styling
- Filled land polygons
- Editing overlay color/width from the UI
- Reading shapefile `.dbf` attributes
- Supporting point-only overlays

## Coordinate Assumption

The first implementation assumes the overlay coordinates and NetCDF mesh coordinates are already in the same coordinate system.

Examples:

- lon/lat coastline over lon/lat mesh
- projected coastline over projected mesh using the same projected units

If coordinates do not match, the overlay may draw in the wrong position. This version should not attempt to detect or transform coordinate reference systems.

## User Experience

Add two menu actions under `File`:

- `Load Coastline...`
- `Clear Coastline`

`Load Coastline...` opens a file chooser for `.geojson`, `.json`, and `.shp`.

After a successful load:

- the overlay is drawn above the mesh render
- the status bar reports the loaded filename and number of paths
- the overlay remains active while users switch between loaded `.nc` datasets

`Clear Coastline` removes the overlay from memory and redraws the current view without the overlay.

If no dataset has been loaded yet, the overlay can still be loaded and remembered. It will draw after a dataset is rendered.

## Architecture

### Data model

Add a pure overlay model that stores:

- source path
- display name
- a list of coordinate paths

Each coordinate path is an ordered list of world-coordinate points.

### Loading

Add an overlay loader facade that chooses the parser based on file extension:

- `.geojson` and `.json` use a small GeoJSON parser
- `.shp` uses a small shapefile parser for shape types `PolyLine`, `Polygon`, `PolyLineZ`, `PolygonZ`, `PolyLineM`, and `PolygonM`

The loader returns the shared overlay model.

### Rendering

Add a `CoastlineOverlayRenderer` that draws each overlay path onto the JavaFX canvas using the current `ViewportState.Snapshot`.

The renderer should be called after the base `TriangleImageRenderer` output is drawn on the canvas. This keeps the coastline visible above the scalar mesh.

When there is no overlay, the renderer does nothing.

### Controller

`MainController` owns the current overlay state:

- load overlay from file chooser
- clear overlay
- redraw overlay after render success
- preserve overlay while switching active datasets

It should not parse GeoJSON or shapefile contents directly.

## GeoJSON Rules

Supported geometry:

- `LineString`
- `MultiLineString`
- `Polygon`
- `MultiPolygon`
- `GeometryCollection`
- `Feature`
- `FeatureCollection`

Unsupported geometry:

- `Point`
- `MultiPoint`
- any geometry without valid coordinate arrays

Polygon rings are converted to stroke paths. No filling is performed.

## Shapefile Rules

Supported shape types:

- `3` PolyLine
- `5` Polygon
- `13` PolyLineZ
- `15` PolygonZ
- `23` PolyLineM
- `25` PolygonM

Unsupported records are ignored.

The first implementation reads `.shp` geometry directly and does not require `.dbf` attribute parsing. Missing `.shx` or `.dbf` files should not block drawing, because the overlay renderer only needs geometry.

## Error Handling

- Unsupported file extensions show an error dialog and keep the current overlay unchanged
- Parse failures show an error dialog and keep the current overlay unchanged
- Files with no supported geometry show a clear error message
- Clearing an overlay when none is loaded is harmless

## Testing Strategy

Use TDD.

Add pure parser tests for:

- GeoJSON `LineString`
- GeoJSON `Polygon`
- ignored GeoJSON point geometry
- shapefile polyline geometry

Add UI/controller tests for:

- overlay load action updates overlay status
- clearing overlay removes it
- dataset switching preserves the overlay state

Run the full Maven test suite before merging.
