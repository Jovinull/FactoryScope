# Testing FactoryScope

Three layers, in order of how much they need.

## 1. Unit and integration tests

```
gradlew test
```

Needs nothing but JDK 17. Covers the diagnostic engine, rate arithmetic and number formatting as pure
logic, then boots a **headless Mindustry with real content** to place real blocks and check the diagnosis
and derived rates against the values the game itself computes. Also sweeps every placeable vanilla block
and stand-ins for common modded block shapes, asserting nothing throws, no rate is unprintable, no cause
is asserted without evidence, and that inspection never alters game state.

The headless boot downloads the Mindustry `assets.jar` once and unpacks the non-sprite part of it into
`build/mindustry-assets`; that is why the first `gradlew test` is slower than the rest.

## 2. Acceptance suite

```
gradlew acceptanceTest
```

Needs **a local Mindustry v8 install** and, for now, **Windows**.

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
- Every user-facing string resolving in the active locale.

Results are written to the game log as `[HARNESS]` lines; `scripts/acceptance-test.ps1` reads them and
turns them into an exit code. Your saves, settings and installed mods are never touched.

Run the script directly if you want the options:

```
powershell -ExecutionPolicy Bypass -File scripts\acceptance-test.ps1 -KeepSandbox
```

On a non-Windows machine `gradlew acceptanceTest` fails with a clear message rather than silently
passing. Porting the launcher is a small, welcome contribution: the Mindustry discovery logic lives in
`scripts/mindustry.ps1` and the harness itself is plain Java.

## 3. Load smoke test

```
powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
```

Builds, finds the local Mindustry install, launches it in a sandbox and checks the log for **this
version** initialising with no errors. `-Install` also copies the jar into your real mods folder.

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
