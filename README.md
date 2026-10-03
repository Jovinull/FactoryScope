# FactoryScope

A factory diagnostics utility for Mindustry v8.

FactoryScope answers one question: **why is this factory not running at full capacity?**

Select a production block and it tells you what the building is doing, what it is waiting for, and which
input or output is responsible. It adds no blocks, no items, no units and no balance changes, and it
never modifies the state it inspects.

## What it does

- **Area diagnostics.** With the overlay armed, drag a rectangle instead of clicking. FactoryScope
  analyses every one of your buildings inside it and reports how many were selected, how many it can put
  production rates on, a count per status, and the problems ranked by how many buildings each affects —
  *"Sand shortage — 8 buildings"* rather than eight separate lines. Expand a group to see which
  buildings, open any of them in the ordinary panel, or send the view to one and come straight back.
- **Snapshot semantics.** An area report is taken once, when you release the drag, and re-taken when you
  press Refresh. Nothing is polled in the background.
- **Item network topology.** Open Network from an area report to see directed structural routes, remote
  item-bridge links, boundary continuations, and item-specific sorter paths. These routes are not current flow.
- **Supply Trace.** From a missing item in the building inspector, trace supported structural routes to
  reachable producers and see their existing diagnostic state. The player selects the area to analyze;
  traces distinguish known dead ends, area boundaries, storage endpoints, and transport the topology does
  not fully model.
- **PowerScope.** Inspect the actual Mindustry `PowerGraph` for a building or selected
  area. Reports show whole-grid generation, demand, delivered satisfaction, battery storage, and connected
  generator diagnostics. A selected area can intersect several independent grids.

An area report is a snapshot of diagnostic observations; Network maps static item topology, and Supply
Trace correlates that topology with the same diagnostic snapshots. Trace does not measure item movement
or prove that a reachable producer is supplying enough material; see
[what it does not support](#what-it-does-not-support).

## Single-building diagnostics

- **A single verdict per building.** Every inspection ends in one primary diagnosis: running, disabled,
  cannot operate here, output blocked, item shortage, liquid shortage, power limited, block condition, or
  an explicit statement that the cause could not be determined. A building the game itself switched off,
  such as one outside the playable area, is told apart from one a player or a logic processor turned off.
  Secondary findings are kept and shown underneath.
- **Efficiency in context.** Current efficiency, the efficiency the building would reach if it were
  unblocked, plus any overdrive or block-specific multiplier in play.
- **Production rates for crafting blocks.** Theoretical output at full efficiency against what the
  building is producing right now, in items or liquid units per second.
- **Inputs in detail.** Every mandatory input with the amount held and the amount required, power
  satisfaction, and the state of the grid the building is attached to. Optional and boost inputs are
  listed separately and are never counted as shortages.
- **Output buffers**, including which one stopped the line.
- **Modded blocks.** Analysis is driven by the consumers a block declares rather than by a list of known
  blocks, so any crafter built on Mindustry's standard consumers is understood. Unrecognised consumer
  types are reported as unrecognised instead of being guessed at or ignored.
- **English and Brazilian Portuguese.**

## What it does not support

- **Measured logistics.** Network routes are structural possibility only: no transfer sampling,
  throughput, bottleneck, or root-cause analysis. An area report can say that eight buildings are short
  of sand because it counted eight buildings that each report a sand shortage. It cannot say that sand
  production is the cause, and it does not pretend to. Supply Trace finds structurally reachable
  producers and reports their current diagnostics, but it does not establish that they are supplying a
  consumer or that their output is quantitatively sufficient.
- **Per-wire power flow.** PowerScope reads Mindustry's pooled `PowerGraph`; ordinary electrical links
  show established connectivity, not directional flow or a measured amount. Power Diodes remain explicit
  cross-grid connectors without an attributed transfer quantity. PowerScope reports evidence, not a root
  cause, battery-depletion estimate, or recommendation. Remote multiplayer grid synchronization has not
  been independently validated.
- **Some transport families.** Armored conveyors and ducts, Plastanium Stack Conveyors, Duct Bridges,
  Mass Drivers, unloaders, and unknown modded transport are marked as incomplete topology rather than
  being approximated. Item bridges, ordinary conveyors, junctions, routers, sorters, gates, ducts, and
  duct routers are covered.
- **Item/liquid production rates outside `GenericCrafter`.** That covers conventional crafting blocks on
  both planets. Drills, pumps, generators, unit factories and the rest are inspected but get no modeled
  item/liquid production rates. PowerScope separately reports current electrical generation exposed by
  the engine.
- **Current production estimates.** Every rate is derived from the current game state at the instant you
  look. It is not an observed average of item movement.
- **History, alerts, or recommendations.**

Fog of war is respected, and an area selection queries only your own team's index, so it can never
report a building you could not already see.

**Platforms.** Desktop is validated end to end. The release jar carries the dexed classes Android needs
and loads, but nothing has yet run it on a device — touch behaviour in particular is unverified. Reports
from Android players are welcome on the issue tracker.

## Requirements

**Mindustry v8, build 160 or later.** The dependency is pinned to release `v160.5`; the mod declares
`minGameVersion: 160`.

The mod is marked client-side only, so it takes no part in the multiplayer mod handshake: you can join a
vanilla server with FactoryScope installed, and the server does not need to have it.

## Installing

Download `FactoryScope.jar` from the [releases page](https://github.com/Jovinull/FactoryScope/releases),
then either use *Mods → Import mod* in game, or drop it in your mods folder:

| Platform | Folder |
| --- | --- |
| Steam (any OS) | `<install directory>/saves/mods/` |
| Windows | `%APPDATA%\Mindustry\mods\` |
| Linux | `~/.local/share/Mindustry/mods/` |
| macOS | `~/Library/Application Support/Mindustry/mods/` |
| Android | *Mods → Import mod* |

Restart Mindustry afterwards.

## Using the inspector

1. Enter any game.
2. Press the FactoryScope button in the bottom-left corner of the HUD.
3. Then either:
   - **click or tap a building** to diagnose that one, or
   - **drag a rectangle** to diagnose everything of yours inside it.
4. Read the report. The single-building panel keeps refreshing while it is open; an area report is a
   snapshot with a Refresh button.
5. Close it with the close button or the usual back gesture.

A press that never travels far, or never leaves its starting tile, is a click — a shaky hand will not
turn a click into an area, and the threshold grows with your UI scale. To cancel without selecting
anything: press the FactoryScope button again, tap empty ground, or right-click. Escape and the Android
back key cancel too, though they open the pause menu as they do everywhere else in the game. Everything
works with a mouse and with touch, and nothing of FactoryScope stays in the input path once the overlay
is gone.

## Building from source

Requires JDK 17. Everything else is fetched by the Gradle wrapper.

```
gradlew.bat clean test jar     # Windows
./gradlew clean test jar       # Linux and macOS
```

### Which artifact is which

| Artifact | Task | Runs on |
| --- | --- | --- |
| `build/libs/FactoryScopeDesktop.jar` | `jar` | Desktop only |
| `build/libs/FactoryScope.jar` | `deploy` | Desktop **and** Android |

`deploy` runs `d8` from the Android SDK build tools to produce the dexed classes Android needs, so it
requires `ANDROID_HOME` and `d8` on the path. Continuous integration builds it on every push. The desktop
jar is for quick local testing and is not a substitute for a release.

### Tests

```
gradlew test              # unit + headless-Mindustry integration tests, needs only a JDK
gradlew acceptanceTest    # drives the inspector in a real Mindustry client, needs a local install
gradlew verifyArtifacts   # checks the built jars carry only production code
gradlew areaBenchmark     # prints what an area analysis costs at 50 to 4000 buildings
gradlew networkBenchmark  # prints static network construction cost at 50 to 4000 buildings
gradlew powerBenchmark    # prints PowerGraph snapshot/model cost at 50 to 4000 buildings
```

`scripts/smoke-test.ps1` builds, installs into a throwaway sandbox and confirms this version loads in the
real client without errors. Your saves, settings and installed mods are never touched.

[docs/testing.md](docs/testing.md) explains what each layer covers and why the acceptance suite exists.

## Reporting bugs

Open an issue at <https://github.com/Jovinull/FactoryScope/issues>. A useful report includes the
Mindustry build, the block you inspected, what FactoryScope said, what you expected instead, and the
relevant part of `last_log.txt` from your Mindustry data folder.

## Project status

- **0.1 complete:** single-building diagnostics.
- **0.2 complete:** area diagnostics, issue aggregation, and affected-building navigation.
- **0.3 complete:** static, item-aware network topology inside a selected area.
- **0.4 complete:** network-aware diagnostics and Supply Trace.
- **0.5 complete:** PowerScope reads Mindustry's engine-maintained power grids.
- **0.6 complete:** LiquidScope models structural liquid/gas routes, producer and consumer roles, current buffer contents, area boundaries, and partial transport. It does not measure pipe flow or establish supply sufficiency.

Supply Trace and LiquidScope correlate known topology with existing per-building diagnostics. They report
structural possibilities, not current material flow, quantitative supply sufficiency, or root cause. LiquidScope
shows current liquid-buffer contents separately; a buffer's contents do not change the structural route.
Armored Conduits, Liquid Bridges, Direction Liquid Bridges, and unknown modded liquid transport remain
explicitly incomplete where their routing is not modeled. Remote multiplayer replication of liquid state
and bridge configuration has not been validated.

Exact per-edge throughput is deferred. Mindustry 160.5 exposes no public transfer event that identifies
the source building, destination building, resource, and amount for a successful transfer. Inventory changes
and module-level flow averages cannot prove which item or liquid route carried the material. LiquidScope does
not use `LiquidModule.getFlowRate()` as pipe throughput.

## Notes for contributors

- [docs/mindustry-notes.md](docs/mindustry-notes.md) — the parts of the v160.5 source the diagnosis
  depends on, including the order in which the game decides efficiency and why a couple of formulas are
  reimplemented rather than called. Read it before changing anything under `analysis/` or `probe/`.
- [docs/architecture.md](docs/architecture.md) — how the pieces fit, why the area feature reuses the
  single-building engine rather than duplicating it, and where the line is between what FactoryScope
  observes and what it refuses to claim.
- [docs/testing.md](docs/testing.md) — the test layers and how to run them.
- [docs/releasing.md](docs/releasing.md) — what a release needs.

## License

GPL-3.0, matching Mindustry itself. See [LICENSE](LICENSE).
