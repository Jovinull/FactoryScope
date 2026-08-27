# FactoryScope

A factory diagnostics utility for Mindustry v8.

FactoryScope answers one question: **why is this factory not running at full capacity?**

Select a production block and it tells you what the building is doing, what it is waiting for, and which
of its inputs or outputs is responsible. It adds no blocks, no items, no units and no balance changes,
and it never modifies the game state it inspects.

## Features

- **A verdict, not a dump.** Every inspection ends in a single primary diagnosis: running, disabled,
  output blocked, item shortage, liquid shortage, power limited, block condition, or an explicit
  admission that the cause could not be determined.
- **Efficiency in context.** Current efficiency, the efficiency the building would reach if it were
  unblocked, and any time scale or block-specific multiplier that is in play.
- **Production rates.** For crafting blocks: theoretical output at full efficiency versus what the
  building is producing right now, in items or liquid units per second.
- **Input detail.** Every mandatory input with the amount held and the amount required, plus power
  satisfaction and the state of the grid it is attached to. Optional and boost inputs are listed
  separately and never counted as shortages.
- **Output buffers.** What is sitting in the block's own buffer and whether it is what stopped the line.
- **Works with modded blocks.** Analysis is driven by the consumers a block declares, not by a list of
  known blocks, so any crafter built on Mindustry's standard consumers is understood. Unrecognised
  consumer types are reported honestly rather than ignored or guessed at.

## Supported version

Built and tested against **Mindustry v8 build 159.7**. The dependency is pinned to that release.

The mod is marked as client-side only in `mod.hjson`, so it does not affect multiplayer compatibility.

## Installation

1. Download `FactoryScope.jar` (or build it yourself, see below).
2. Put it in your Mindustry mods folder:
   - **Steam / Windows**: `<Steam library>\steamapps\common\Mindustry\saves\mods\`
   - **Other desktop builds**: `%APPDATA%\Mindustry\mods\` on Windows,
     `~/.local/share/Mindustry/mods/` on Linux, `~/Library/Application Support/Mindustry/mods/` on macOS
   - **Android**: use the in-game *Mods → Import mod* button
3. Restart Mindustry.

You can also use *Mods → Import mod* on desktop and pick the jar.

## Using the inspector

1. Enter any game.
2. Press the FactoryScope button in the bottom-left corner of the HUD.
3. Select a production block. Selecting anywhere else cancels.
4. Read the diagnosis. The panel refreshes while it is open.
5. Close it with the close button or the usual back/escape gesture.

Selection is a single tap or click, so the same flow works on desktop and on touch devices. There is no
keyboard shortcut to memorise and nothing stays active once the panel is closed.

## Building from source

Requires JDK 17. Everything else is fetched by the Gradle wrapper.

```
gradlew.bat clean test jar     # Windows
./gradlew clean test jar       # Linux and macOS
```

The desktop mod is written to `build/libs/FactoryScopeDesktop.jar`.

`gradlew deploy` produces the combined desktop + Android artifact, and needs the Android SDK build tools
(`d8`) on the path. Continuous integration builds that artifact on every push.

### Tests

`gradlew test` runs two layers:

- unit tests for the diagnostic engine and the rate arithmetic, which need nothing but a JVM;
- integration tests that boot a headless Mindustry with real content, place real blocks, and check the
  diagnosis and the derived rates against the values the game itself computes.

`scripts/smoke-test.ps1` goes one step further on Windows: it builds the mod, finds the local Mindustry
installation, launches it in a throwaway sandbox directory and reports whether the mod initialised
cleanly. It never reads or writes your saves, settings or installed mods. Add `-Install` to also copy the
jar into your real mods folder.

## Current limitations

- Full production analysis covers `GenericCrafter` and its subclasses, which is the conventional crafting
  block in Mindustry. Other buildings are inspected too, but without a production model: they show their
  inputs, power and efficiency, and are labelled as limited diagnostics.
- Rates are derived from the current game state, not measured over time. They are labelled as such and
  are never presented as observed throughput.
- Analysis covers the selected building only. FactoryScope does not trace conveyors and will not tell you
  which upstream machine starved it.
- Consumer types added by other mods are handled generically. When such a consumer is limiting production
  and the building is already stopped, the reading cannot be fully trusted and the panel says so instead
  of asserting a cause.
- Fog of war is respected: a building the local player cannot legitimately see cannot be inspected.

## Roadmap

None of the following is implemented yet.

- v0.2 — area diagnostics: analyse a selected region rather than a single block
- v0.3 — conveyor throughput
- v0.4 — power network inspector
- v0.5 — liquid network diagnostics
- v0.6 — schematic analysis
- v0.7 — alerts for factories that stall

## Notes for contributors

`docs/mindustry-notes.md` records the parts of the v159.7 source the diagnosis depends on, including the
exact order in which the game decides efficiency and why some formulas are reimplemented rather than
called. Read it before changing anything in `analysis/` or `probe/`.
