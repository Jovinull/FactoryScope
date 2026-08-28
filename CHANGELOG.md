# Changelog

## 0.2.0 — unreleased

In development on the `develop` branch. `main` still carries the released 0.1.1.

### Added

- **Area diagnostics.** Drag a rectangle with the FactoryScope overlay armed and get one report for
  everything inside it: how many buildings were selected, how many have a production model, a count per
  status, and the problems ranked by how many buildings each one affects.
- **Issue grouping.** Eight factories short of sand become one line reading *"Sand shortage — 8
  buildings"*, not eight separate entries. Grouping is by content identity, so it does not change with
  the game language, and no building is ever counted twice in one group.
- **Drill-down.** Expand a group to see the buildings it affects, open any of them in the existing
  single-building panel without losing the report behind it, or move the view to one.
- **Refresh** re-runs the whole analysis over the same bounds, including the spatial query, so buildings
  built or destroyed since are picked up.
- `gradlew areaBenchmark` prints what an area analysis costs at 50, 250, 1000 and 4000 buildings.

### Changed

- The HUD button now arms one overlay for both gestures. A click still inspects one building exactly as
  before; a drag selects an area. Nothing was added to the HUD.
- Findings carry resource identity as well as a display name, so aggregation can group on something
  stable. No change to what a single-building diagnosis says.

### Not in this release

No production-network tracing, no throughput measurement, no root-cause analysis and no recommendations.
An area report counts observations; it does not claim to know which building caused them.

## 0.1.1 — 2026-08-27

First public release. Single-building diagnostics for Mindustry v8 build 159.7. See the
[release notes](https://github.com/Jovinull/FactoryScope/releases/tag/v0.1.1).
