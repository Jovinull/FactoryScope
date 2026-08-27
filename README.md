# FactoryScope

A factory diagnostics utility for Mindustry v8.

FactoryScope answers one question: **why is this factory not running at full capacity?**

Select a production block and it tells you what the building is doing, what it is waiting for, and which
input or output is responsible. It adds no blocks, no items, no units and no balance changes, and it
never modifies the state it inspects.

## What 0.1.x supports

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

- **Anything beyond the selected building.** No conveyor tracing, no area or sector analysis, no
  bottleneck search. FactoryScope will not tell you which upstream machine starved this one.
- **Full production modelling outside `GenericCrafter`.** That covers the conventional crafting blocks on
  both planets. Drills, pumps, generators, unit factories and the rest are inspected — inputs, power,
  efficiency, verdict — but get no production rates, and the panel says so.
- **Measured throughput.** Every rate is derived from the current game state at the instant you look. It
  is labelled as such and never presented as an observed average.
- **History, alerts, or recommendations.**

Fog of war is respected: a building the local player cannot legitimately see cannot be inspected.

## Requirements

**Mindustry v8, build 159.7.** The dependency is pinned to that release and the mod declares
`minGameVersion: 159.7`.

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
3. Select a production block. Selecting anywhere else cancels.
4. Read the diagnosis. The panel keeps refreshing while it is open.
5. Close it with the close button or the usual back gesture.

Selection is a single tap or click, so the same flow works with a mouse and with touch. Nothing is
required from the keyboard, and nothing of FactoryScope stays in the input path once the panel is closed.

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

`gradlew test` runs three layers:

- unit tests for the diagnostic engine, the rate arithmetic and the number formatting, which need nothing
  but a JVM;
- integration tests that boot a headless Mindustry with real content, place real blocks and check the
  diagnosis and derived rates against the values the game itself computes;
- sweeps over every placeable vanilla block, and over stand-ins for the block shapes other mods ship,
  asserting that nothing throws, no rate is unprintable, no cause is asserted without evidence, and that
  inspection never alters game state.

`scripts/smoke-test.ps1` goes further on Windows: it builds the mod, finds the local Mindustry
installation, launches it with its working directory in a throwaway sandbox, and checks the log for this
version initialising cleanly. Your saves, settings and installed mods are never read or written. Add
`-Install` to also copy the jar into your real mods folder.

## Reporting bugs

Open an issue at <https://github.com/Jovinull/FactoryScope/issues>. A useful report includes the
Mindustry build, the block you inspected, what FactoryScope said, what you expected instead, and the
relevant part of `last_log.txt` from your Mindustry data folder.

## Roadmap

None of the following is implemented.

- v0.2 — area diagnostics: analyse a selected region rather than a single block
- v0.3 — conveyor throughput
- v0.4 — power network inspector
- v0.5 — liquid network diagnostics
- v0.6 — schematic analysis
- v0.7 — alerts for factories that stall

## Notes for contributors

`docs/mindustry-notes.md` records the parts of the v159.7 source the diagnosis depends on, including the
order in which the game decides efficiency and why a couple of formulas are reimplemented rather than
called. Read it before changing anything under `analysis/` or `probe/`. `docs/releasing.md` covers what a
release needs.

## License

GPL-3.0, matching Mindustry itself. See [LICENSE](LICENSE).
