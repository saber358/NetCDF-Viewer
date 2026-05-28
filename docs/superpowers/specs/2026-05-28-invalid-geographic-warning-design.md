# Invalid Geographic Coordinate Warning Design

## Goal

When a NetCDF dataset exposes coordinates that look like longitude and latitude but the parsed coordinate range is not valid geographic bounds, the app asks the user whether to continue loading.

## Scope

- Trigger only after a dataset is successfully parsed.
- Trigger only when coordinate names look geographic and the spatial range is outside legal longitude or latitude bounds.
- Do not block ordinary projected or local-plane coordinates that are not named like geographic axes.
- If the user cancels, do not add the dataset to the loaded dataset list.
- If the user continues, load normally. Existing basemap logic still skips basemap rendering for invalid geographic ranges.

## Warning Content

The dialog explains:

- File name.
- Parsed coordinate variable names.
- Expected longitude and latitude ranges.
- Actual X and Y ranges.
- Which axis failed validation.
- Likely causes: swapped coordinates, wrong coordinate variable names, or non-geographic coordinates.
- Consequences: scalar rendering and point query can still work, but online basemap is skipped and geographic overlays may not align.

## User Flow

```text
[Open NetCDF] ──┬── Parse dataset
                ├── Coordinate range valid ── Load dataset
                ├── Not geographic-looking ── Load dataset
                └── Geographic-looking but invalid ── Confirm dialog
                                                    ├── Cancel ── Stop load
                                                    └── Continue ── Load dataset
```

## Implementation Notes

- Keep validation in `MainController` near the existing `isGeographicDomain` method.
- Use JavaFX `Alert.AlertType.CONFIRMATION`.
- Reuse the same validation result for the load-time warning and basemap eligibility where practical.
- Keep tests focused on the validation result and DSD1211/HYD sample behavior.

## Test Cases

- `HYD.nc` uses `lon/lat` and has valid bounds, so it does not need a warning.
- `DSD1211.nc` uses `lon/lat` but has `Y=116.x`, so it needs a warning.
- A non-geographic-looking spatial domain outside the geographic bounds should not need the warning.
