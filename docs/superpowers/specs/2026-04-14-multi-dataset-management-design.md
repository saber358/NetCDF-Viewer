# NetCDF Viewer Multi-Dataset Management Design

## Goal

Extend the desktop viewer so users can keep multiple NetCDF datasets loaded at the same time, switch which dataset is currently active, and remove datasets that are no longer needed.

The feature should preserve the existing single-window workflow and avoid turning the app into a tabbed or multi-window interface.

## Scope

Included:

- Opening additional `.nc` files without clearing previously loaded datasets
- Showing a list of loaded datasets in the left panel
- Selecting the active dataset from that list
- Refreshing the existing variable list, metadata panels, and render area to match the active dataset
- Removing a loaded dataset from memory
- Handling removal of the active dataset and fallback selection of another loaded dataset
- Preventing duplicate entries when the same file path is added twice
- Adding automated tests for add/select/remove behavior

Excluded for this iteration:

- Saving or restoring dataset sessions between app launches
- Reordering loaded datasets manually
- Loading datasets in background batches
- Comparing datasets side-by-side
- Per-dataset independent viewport persistence
- Renaming datasets in the UI

## Chosen UX

The app remains a single main window.

The left panel gains a new `Loaded Datasets` section above the existing variable list:

- a `ListView` that shows the loaded dataset filenames
- a `Remove` button for deleting the currently selected dataset

Interaction rules:

- `Open...`, the toolbar `Open` button, and drag-and-drop all behave as **append dataset**
- selecting an item in `Loaded Datasets` makes it the active dataset
- the existing variable list and detail panels always reflect the active dataset only
- deleting the active dataset automatically selects a remaining dataset if one exists
- deleting the last dataset returns the UI to the initial empty state

If the user attempts to open a file that is already loaded, the app does not create a duplicate row. Instead it switches to the existing dataset and updates the status bar.

## Architecture

### Dataset session model

The controller currently assumes there is exactly one loaded dataset. That becomes an explicit collection:

- an ordered list of loaded dataset sessions
- one selected session representing the active dataset

Each session contains:

- the parsed `ParsedDataset`
- a stable display label derived from the filename
- the absolute source path for duplicate detection

The active session drives all existing UI state:

- current variable list
- current dataset summary and metadata
- current render target
- current point-query context

### UI responsibilities

`MainView` remains a view-only class. It should expose:

- a dataset `ListView`
- a remove-dataset button

The rest of the left panel structure can stay intact. The new controls should be inserted above the existing variable list to keep the current mental model:

1. choose dataset
2. choose variable
3. inspect metadata

### Controller responsibilities

`MainController` becomes responsible for dataset-session orchestration:

- append a newly parsed dataset to the loaded collection
- prevent duplicates by absolute path
- react to dataset-list selection changes
- populate the variable list and metadata from the active dataset
- remove the selected dataset and choose fallback state
- clear render/query/export state when no active dataset remains

The rendering pipeline stays single-dataset. The active dataset is simply switched before rendering.

### State reset strategy

When switching datasets, the controller should reset transient single-dataset state:

- clear current variable selection
- reset viewport to auto-fit
- clear point-query cache until the next render completes

This keeps the first version predictable and avoids carrying zoom/pan from one mesh to another.

## Error Handling

- Failed dataset parse should not disturb already loaded datasets
- Removing a dataset should never affect the underlying file on disk
- If a dataset has no plottable mesh, it can still remain in the list and expose metadata
- Duplicate-add attempts should report a short status message such as `Dataset already loaded. Switched to existing entry.`

## Testing Strategy

Follow TDD.

Add controller/UI tests covering:

- loading two different datasets results in two loaded-dataset entries
- selecting a different loaded dataset updates the current dataset label and variable list
- removing the active dataset selects another remaining dataset
- removing the last dataset returns the UI to empty-state placeholders
- re-opening the same file does not create duplicate entries

Existing rendering and dataset parsing tests should continue to pass unchanged.

## Why This Approach

I considered three approaches:

1. left-side loaded dataset list
2. top-level tabs per dataset
3. one window per dataset

The left-side list is the best fit because it reuses the current single-render workflow and needs the least structural change to your existing JavaFX layout. It adds management for multiple datasets without forcing users into a heavier navigation model.
