# Testing FactoryScope

Three layers, in order of how much they need, plus a benchmark that measures rather than checks.

## 1. Unit and integration tests

```
gradlew test
```

Needs nothing but JDK 17. Covers the diagnostic engine, rate arithmetic, number formatting and area
aggregation as pure logic, then boots a **headless Mindustry with real content** to place real blocks and
check the diagnosis and derived rates against the values the game itself computes. Also sweeps every
placeable vanilla block and stand-ins for common modded block shapes, asserting nothing throws, no rate
is unprintable, no cause is asserted without evidence, and that inspection never alters game state.

For area diagnostics specifically:

- `area/AreaSelectionTest` — tile arithmetic: the four drag directions normalising to one rectangle,
  inclusive endpoints, footprint intersection for odd and even block sizes, clamping to the world.
- `area/AreaAnalyzerTest` — the counting and grouping rules, driven by results the real
  `FactoryAnalyzer` produced from real snapshots. Fixtures that invented their own `DiagnosticResult`
  could pass against semantics the engine does not have.
- `probe/AreaProbeTest` — the spatial query against the engine: footprint intersection, multi-tile
  buildings collected exactly once, other teams never visited, out-of-world selections, and reference
  identity surviving (or correctly not surviving) destruction, replacement and team change.
- `probe/AreaModCompatibilityTest` — an area containing vanilla, modded, boosted and unrecognised
  crafters plus a block with no production model at all.

The headless boot downloads the Mindustry `assets.jar` once and unpacks the non-sprite part of it into
`build/mindustry-assets`; that is why the first `gradlew test` is slower than the rest.

Trace coverage includes `trace/TraceAnalyzerTest`, which exercises pure item-aware forward/reverse
traversal, unique endpoints, deterministic representative paths, storage distinction, boundaries,
unsupported transport, Junction channels and cycle termination. `probe/MindustryNetworkProbeTest` also
places a real Mindustry drill, conveyor and crafter to verify that a mined item can be traced as a
structural product without inventing a production rate. It also covers a non-crafter item consumer, a
multi-output AttributeCrafter's exact product constraints, and a producer outside the selected area.
Trace regressions cover local completeness when unrelated transport/diagnostic probes are skipped,
separate no-endpoint versus no-route findings, multiple producer/dead-end branches, and output boundary
and dead-end wording. An even-sized skipped multiblock fixture checks terminal adjacency against
Mindustry's asymmetric footprint. `TraceWordingTest` prevents causal or prescriptive language from
entering either trace bundle.

PowerScope is tested in `power/PowerGridAnalyzerTest` as a pure domain analyzer and in
`probe/MindustryPowerProbeTest` against real v160.5 PowerGraph, PowerNode, BeamNode, Power Diode,
battery, and modded-generator behavior. `PowerGridBatteryIntegrationTest` runs actual engine grid updates
to prove that battery discharge can keep delivered satisfaction high while generation remains below
demand, and that an empty battery does not. The client acceptance path opens PowerScope from both a
single-building inspector and Area Diagnostics, checks separate grids and battery role separation,
exercises a real BeamNode connection and a directional Power Diode between separate grids, then uses
Refresh and the electrical-connection overlay.

LiquidScope has pure tests in `liquid/LiquidTraceAnalyzerTest` and real v160.5 transport fixtures in
`probe/MindustryLiquidProbeTest`. They cover Conduit side acceptance/output direction, content-independent
topology, Liquid Junction channel isolation (including the engine destination oracle), router branches,
configured Liquid Bridge links, directed multi-liquid crafter outputs, filter accepted sets, placed pump
products, SolidPump products before first update, multi-liquid module snapshots, gas resource identity, and an
unsupported transport adjacent to a consumer. Production topology is separately audited to ensure it never
calls runtime `acceptLiquid()` to decide structural reachability.

The LiquidScope acceptance path enters from a missing liquid in the single-building panel, selects an explicit
area, follows Water to a real pump through a conduit, opens pump diagnostics, returns, refreshes, and uses the
Area Diagnostics Liquids filter/Trace controls. It also checks the structural-only wording, high-scale layout,
and every liquid-specific locale key. Real UI acceptance is run in both English and Brazilian Portuguese.

## 2. Acceptance suite

```
gradlew acceptanceTest
```

Needs **a Mindustry v8 client** and, for now, **Windows**. Pass `-MindustryPath` for an installed
client or `-MindustryJar` for an official desktop release jar.

This is the layer that catches what the other two cannot. `acceptance/` builds a second Mindustry mod,
`FactoryScopeAcceptance.jar`, which loads next to FactoryScope in a throwaway sandbox and drives the
inspector through Arc's own input dispatch — `Core.scene.touchDown` / `touchUp` at computed screen
positions, so the production HUD button, the production picker overlay and the production
tap-to-tile arithmetic are what actually run.

It covers:

- **Correct target selection.** Two crafters are placed the same distance above and below the camera. A
  conversion that confuses Arc's bottom-left screen origin with `Scene.stageToScreenCoordinates`, which
  flips Y, resolves each click to the other building. That defect shipped in 0.1.0; this is the
  regression test for it. Do not weaken it into a direct `FactoryScopeUI.inspect(building)` call — that
  bypasses the exact code that was broken.
- Clicking empty terrain, and clicking an unsupported block.
- A target destroyed while its panel is open.
- Repeated activate → select → close cycles, asserting scene element counts return to baseline.
- A world change while the panel is open.
- Panel layout at several scene sizes and UI scales.
- Every user-facing string resolving in the active locale, including the formatted ones, whose missing
  keys render as `???key???` rather than as an error.

Area diagnostics adds, through the same dispatch:

- **The four drag directions.** Each corner-to-corner drag must report exactly the tile rectangle the
  pointer covered, and select exactly the buildings inside it — two decoys sit just outside, so a
  selection that is merely too generous fails too. The camera is far from the world origin and the check
  asserts that, because a conversion that dropped the camera offset would otherwise pass at the origin.
- **Click against drag.** A plain click still opens the single-building panel; a two-pixel wobble is
  still a click.
- **A multi-tile building clipped by the edge** of the selection, appearing once, and the same building
  correctly excluded when the selection stops one tile short.
- **A mixed area** — running, starved, output-blocked, disabled, multi-finding and unsupported buildings
  in one rectangle — checked against the counts, the grouping and the ranking a player reads.
- **The same rectangle at two zoom levels**, which catches a conversion that folds the camera scale in
  at the wrong point, and **a drag that runs off the edge of the map**, driven with the view moved to
  the world corner.
- Healthy and empty areas, dragging across configurable blocks without opening their own dialogs,
  refreshing after buildings were added and removed, expanding an issue group and opening one of its
  buildings, repeated use, and world changes during and after a selection.
- **Layout**, at three scene sizes and UI scales, plus a deliberately crowded report — several different
  shortages at once — on the smallest scene at the largest UI scale, which is where long labels clip
  first.

The suite drives the real overlay end to end. It does not call an internal "select this rectangle"
method, for the same reason the single-building test does not call `inspect(building)`: that would skip
the code most likely to be wrong.

The Supply Trace acceptance path uses the real building inspector, starts area selection from a missing
item, follows Sand from a real Drill through three real conveyors to a Silicon Smelter, opens the
producer's diagnostics, and returns to the trace and Network. The harness also resolves every trace
string and formatted label in the active locale. It checks that Network and Supply Trace remain
scrollable at 1280x720 with 2x UI scale and that endpoint rows use the available width after Refresh.
Captures include the normal trace and the same trace at high UI scale. A separate complete Mindustry
headless integration fixture independently checks the same structural path.

Results are written to the game log as `[HARNESS]` lines; `scripts/acceptance-test.ps1` reads them and
turns them into an exit code. Your saves, settings and installed mods are never touched.

Run the script directly if you want the options:

```
powershell -ExecutionPolicy Bypass -File scripts\acceptance-test.ps1 -KeepSandbox
powershell -ExecutionPolicy Bypass -File scripts\acceptance-test.ps1 -MindustryJar C:\Games\Mindustry.jar -ModJar build\libs\FactoryScope.jar
```

On a non-Windows machine `gradlew acceptanceTest` fails with a clear message rather than silently
passing. Porting the launcher is a small, welcome contribution: the Mindustry discovery logic lives in
`scripts/mindustry.ps1` and the harness itself is plain Java.

## 3. Load smoke test

```
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

Builds, finds the local Mindustry install, launches it in a sandbox and checks the log for **this
version** initialising with no errors. `-MindustryJar` selects an official client jar and `-ModJar`
selects the exact mod artifact to test. `-Install` copies the built jar into your real mods folder and
is only available with `-MindustryPath` or automatic install discovery.

## Benchmark

```
gradlew areaBenchmark
gradlew networkBenchmark
gradlew traceBenchmark
gradlew powerBenchmark
gradlew liquidBenchmark
```

Prints what one area analysis costs at 50, 250, 1000 and 4000 buildings, split into spatial collection,
probing, diagnosis, aggregation and building the report model. The network and trace benchmarks measure
graph construction and reverse tracing at the same sizes. They assert nothing about time: a
wall-clock threshold in a test suite fails on a loaded machine and passes on a fast one, which teaches a
maintainer to ignore it. A regression shows up as a number that moved.

It is excluded from `gradlew test` by a JUnit tag, so an ordinary test run is not slowed by it.

`powerBenchmark` measures engine-graph grouping, immutable snapshot/connection capture, and a lightweight
PowerScope presentation model for connected 50/250/1000/4000-node PowerNode grids. Timing is reported,
not gated. `liquidBenchmark` separately reports area collection, liquid snapshot probing, structural graph
construction, and resource-specific trace costs at the same building counts; it has no wall-clock pass/fail
threshold.

## Artifact checks

```
gradlew verifyArtifacts
```

Inspects whichever jars have been built and fails if one carries classes outside `factoryscope/`, any
acceptance or test code, duplicate entries, or is missing `mod.hjson` or a bundle. The universal jar must
also contain `classes.dex`. CI runs this after `deploy`.

## What is not covered

Android runtime behaviour. The universal jar is built and structurally verified by CI, but nothing has
run it on a device or emulator. See the release notes for the current status.
