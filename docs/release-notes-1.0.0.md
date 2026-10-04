# FactoryScope 1.0.0 [v160] — release draft

FactoryScope is a read-only Mindustry factory diagnostics mod. Version 1.0 brings together building and
area diagnostics, item Network and Supply Trace, PowerScope, and LiquidScope.

## What it does

- Explains current building diagnostics and groups findings across a selected area.
- Shows supported structural item routes and traces items to reachable producers, storage, boundaries,
  or unsupported transport.
- Reads Mindustry's actual power grids and reports generation, demand, satisfaction, recent balance,
  and battery reserves separately.
- Shows supported structural liquid and gas routes, declared producers and consumers, and current
  stored liquids.

Routes describe structural possibility, not current movement. A reachable source does not prove
quantitative supply sufficiency or cause. Power connections are not per-wire flow measurements.

## Limitations and validation

Per-edge throughput, supply-sufficiency predictions, root-cause ranking, and repair recommendations are
not provided. Some vanilla transport families and unknown modded transports remain partial; consult the
[support matrix](support-matrix.md). Android runtime/touch, remote multiplayer behavior, and Linux/macOS
graphical Mindustry execution have not been independently validated. The universal JAR's DEX is
structurally validated; that is not Android runtime validation.

Tested against Mindustry v8 Build 160.5 and released for the Build 160 line. Desktop graphical acceptance
was performed on Windows; portable launcher/path tests run on Windows, Linux, and macOS.

## Install

Import `FactoryScope.jar` through Mindustry's Mods screen, or download it from the
[GitHub releases](https://github.com/Jovinull/FactoryScope/releases). The Mod Browser index may take a
scheduled refresh to show a new release.
