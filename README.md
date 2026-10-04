# FactoryScope

FactoryScope is a read-only diagnostics mod for Mindustry v8. It helps answer **what a factory is
doing, what it currently needs, and what evidence is available about its connected production
systems**. It does not change blocks, resources, power, or world state.

## Features

- **Building diagnostics** explain current operating state, efficiency, recognized input shortages,
  output limits, and supported production estimates.
- **Area Diagnostics** summarizes selected friendly buildings and groups their reported findings.
- **Network** shows supported, resource-aware item topology in a selected area.
- **Supply Trace** follows structural item routes to reachable producers, storage, boundaries, and
  unsupported transport.
- **PowerScope** reads Mindustry's actual `PowerGraph` and reports grid-level generation, demand,
  satisfaction, recent balance, battery storage, and visible connected members.
- **LiquidScope** shows supported structural liquid and gas routes, declared producers and consumers,
  and current stored liquids.

The [support matrix](docs/support-matrix.md) describes which vanilla transport families are modeled,
partial, or deferred.

## What the evidence means

Building diagnostics are based on the building state Mindustry exposes when it is inspected. The
single-building panel refreshes while open; area, Network, PowerScope, and LiquidScope reports are
snapshots rebuilt with **Refresh**. Production rates are derived from the current game state, not
measured over time.

Item and liquid routes describe **structural possibility**, not current movement. PowerScope reads
engine-maintained pooled grid values; an ordinary electrical connection is not a measurement of flow
through a wire. A reachable producer does not prove that supply is quantitatively sufficient or that it
caused another building's state. Boundaries and unsupported transport are shown as incomplete evidence,
not silently treated as dead ends.

FactoryScope does not provide per-edge item or liquid throughput, per-wire power flow, quantitative
supply sufficiency, battery-depletion predictions, root-cause ranking, or repair recommendations.
Some vanilla families—including armored transport, stack conveyors, bridges, unloaders, and Mass
Drivers—remain partial; unknown modded transport is conservative as well. The support matrix explains
what is modeled and why.
Mindustry's client-side power and liquid state has not been independently validated in remote
multiplayer.

## Install

Use Mindustry's **Mods → Import mod** flow, or download `FactoryScope.jar` from the
[GitHub releases](https://github.com/Jovinull/FactoryScope/releases) and place it in the game's `mods`
folder. The Mod Browser listing may update after the release is published; the release JAR is the
manual fallback.

FactoryScope targets Mindustry v8 Build 160 (`minGameVersion: 160`) and is built against v160.5. It is
marked client-side, so no server-side FactoryScope component is required. Desktop graphical validation
has been performed on Windows. Linux and macOS have launcher/path CI coverage, not real graphical
Mindustry validation. The universal JAR contains a structurally validated DEX, but Android runtime and
touch have not been independently validated. See [open Android validation issue #1](https://github.com/Jovinull/FactoryScope/issues/1).

## Use

In a game, activate the FactoryScope button below the wave/editor HUD layout area. Click or tap a
building for its current diagnostics, or drag a rectangle for Area Diagnostics. From there, open
Network, PowerScope, or LiquidScope; trace a missing item or liquid where supported; and use **Locate**,
**Inspect**, and **Return** to navigate without losing the report. Use **Refresh** to rebuild snapshot
evidence after the factory changes.

The interface is available in English and Brazilian Portuguese. The input path handles pointer and
touch-style Scene events, but that is not Android runtime validation.

## Development and testing

Requires JDK 17. The Gradle wrapper fetches the build dependencies.

```text
gradlew.bat clean test acceptanceLauncherTest jar acceptanceJar verifyArtifacts  # Windows
./gradlew clean test acceptanceLauncherTest jar acceptanceJar verifyArtifacts    # Linux/macOS
gradlew.bat acceptanceTest                                                       # Windows
./gradlew acceptanceTest                                                         # Linux/macOS
gradlew.bat areaBenchmark networkBenchmark traceBenchmark powerBenchmark liquidBenchmark  # Windows
./gradlew areaBenchmark networkBenchmark traceBenchmark powerBenchmark liquidBenchmark    # Linux/macOS
```

`acceptanceTest` needs the Mindustry v160.5 desktop JAR. Benchmarks are informational and have no
timing pass/fail thresholds.

The portable `acceptanceTest` launches a real Mindustry v160.5 desktop client in an isolated sandbox.
Launcher/path tests run on Windows, Linux, and macOS; graphical acceptance is claimed only for
platforms actually exercised. For an explicit client and exact artifact:

```text
./gradlew acceptanceTest -PmindustryJar="/games/Mindustry.jar" -PmodJar="/artifacts/FactoryScope.jar" -Plocale=pt-BR
```

The universal release JAR is built by `deploy` and includes `classes.dex`; `jar` produces a desktop-only
JAR. CI supplies Android build tools for the universal artifact. Additional test, acceptance, artifact,
and benchmark details are in [docs/testing.md](docs/testing.md). See
[docs/releasing.md](docs/releasing.md) for release procedure and
[docs/architecture.md](docs/architecture.md) for the evidence boundaries between probes, models, and UI.

## After 1.0

Additional transport coverage and Android, multiplayer, or future Mindustry-version validation may be
explored as evidence and test environments allow. Exact per-edge throughput remains deferred until it
can be attributed defensibly to a source, destination, and resource; no version or schedule is promised.

## License

GPL-3.0. See [LICENSE](LICENSE).
