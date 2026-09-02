# Changelog

## 0.3.0 - Unreleased

### Added

- Area-scoped static item topology with directed, resource-aware routes and boundary continuations.
- Network view, resource filter, world overlay, and explicit partial coverage for unsupported transport.
- `gradlew networkBenchmark` for on-demand topology construction measurements.

### Topology limits

- Armored transport, Plastanium Stack Conveyors, Duct Bridges, Mass Drivers, unloaders, and unknown
  modded transport are reported as incomplete rather than inferred from nearby blocks.

### Not in this release

No measured throughput, transfer sampling, transport utilization, saturation, bottleneck detection,
root-cause correlation, or recommendations.

## 0.2.1 — 2026-08-30

### Fixed

- Area issue rows without a resource icon now reserve the icon column, so localized labels such as
  Brazilian Portuguese "Desativado" are not drawn underneath the placeholder.

## 0.2.0 — 2026-08-28

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
- **Locate** uncovers the world, marks the building with the game's own selection brackets, and leaves a
  small bar offering the way back to the report.
- `gradlew areaBenchmark` prints what an area analysis costs at 50, 250, 1000 and 4000 buildings.

### Changed

- The HUD button now arms one overlay for both gestures. A click still inspects one building exactly as
  before; a drag selects an area. Nothing was added to the HUD.
- Findings carry resource identity as well as a display name, so aggregation can group on something
  stable. No change to what a single-building diagnosis says.

### Fixed

- Cancelling a selection with the right mouse button no longer hands the same click to the game as the
  start of a block demolition.
- The click-or-drag threshold now scales with the UI scale, so it means the same physical distance on a
  dense display as on an ordinary one.
- An affected-building list longer than one page can be expanded rather than only saying how much is
  hidden.
- An area containing nothing FactoryScope can diagnose says so, instead of reporting no problems.

### Not in this release

No production-network tracing, no throughput measurement, no root-cause analysis and no recommendations.
An area report counts observations; it does not claim to know which building caused them.

## 0.1.1 — 2026-08-27

First public release. Single-building diagnostics for Mindustry v8 build 159.7. See the
[release notes](https://github.com/Jovinull/FactoryScope/releases/tag/v0.1.1).
