# Built-In Coastline Design

## Goal

Bundle one default coastline dataset into the application resources so the installed application can display a coastline overlay without requiring the user to manually pick a file from disk.

The built-in coastline must be packaged inside the jar and therefore inside the generated `.exe` installer output.

## Scope

Included:

- Embed one default coastline GeoJSON resource into `src/main/resources`
- Load the built-in coastline automatically during application startup
- Keep the built-in coastline enabled by default when users open a dataset
- Add a menu action to switch back to the built-in coastline after clearing it or after loading an external coastline file
- Preserve the existing external `Load Coastline...` and `Clear Coastline` actions
- Add automated tests for built-in resource availability and controller behavior

Excluded for this iteration:

- Embedding shapefile resources for runtime use
- Multiple built-in coastline themes
- User preference persistence for built-in coastline visibility
- Runtime toggles for built-in coastline style
- Projection conversion

## Runtime Behavior

At startup:

- the controller loads the built-in coastline resource from the application classpath
- the coastline becomes the current overlay state
- the app does not require user input to make the default coastline available

If no NetCDF dataset is open yet, the coastline is simply ready in memory.

When the first dataset is rendered, the built-in coastline appears automatically above the mesh.

Users can still:

- clear the current coastline overlay
- load an external coastline file from disk
- restore the built-in coastline through a dedicated menu action

## Chosen Resource Format

The built-in coastline uses GeoJSON.

Why:

- single file
- easy to package inside the jar
- already supported by the current overlay loader
- simpler than embedding the full shapefile file set

The previously downloaded Natural Earth coastline GeoJSON becomes the bundled default resource.

## UI Changes

Under `File`, keep:

- `Load Coastline...`
- `Clear Coastline`

Add:

- `Use Built-in Coastline`

Behavior:

- on startup, built-in coastline is active by default
- if the user loads an external coastline, it replaces the current overlay
- if the user chooses `Use Built-in Coastline`, the external overlay is replaced by the built-in one
- if the user chooses `Clear Coastline`, the overlay is removed entirely until they load another one or restore the built-in version

## Architecture

### Built-in resource support

Add a small built-in coastline helper that:

- reads a GeoJSON file from classpath resources
- parses it through the same overlay model used for external coastline files
- returns a `CoastlineOverlay` instance with a synthetic internal source path and display name

This avoids duplicating coastline parsing logic.

### Controller changes

`MainController` becomes responsible for:

- loading the built-in coastline during initialization
- exposing a `useBuiltInCoastline()` action
- keeping the built-in coastline as the default overlay state until replaced or cleared

### Loader changes

The GeoJSON loader needs an additional entry point that can read from an `InputStream` or already-parsed JSON tree, not only from a disk `Path`.

That enables:

- external file loading from path
- built-in resource loading from classpath

while still sharing the same geometry extraction logic.

## Testing Strategy

Use TDD.

Add tests for:

- built-in GeoJSON resource exists on the classpath
- built-in coastline parses into non-empty overlay paths
- controller initializes with a non-null current overlay
- clearing the coastline resets overlay state
- using the built-in coastline after clearing restores overlay state

Run the full Maven suite and rebuild the Windows installer after implementation.
