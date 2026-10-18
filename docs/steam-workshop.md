# Steam Workshop

FactoryScope is distributed through GitHub Releases, the Mindustry Mod Browser, and the
[Steam Workshop](https://steamcommunity.com/sharedfiles/filedetails/?id=3820843151) (item `3820843151`, first published
2026-10-16 with the unchanged 1.1.0 release). The Workshop item is updated separately, by hand, from the owner's
Steam client. This page records how Mindustry publishes a mod, how the Workshop folder is built from a release,
and the listing text.

## How Mindustry publishes a mod

Checked against the Mindustry v160.7 sources (`Mods.LoadedMod`, `ModsDialog`, and the desktop `SWorkshop`):

- Only a Steam client shows the publish (export) button, and only for a local mod that has no Workshop ID yet.
- Publishing is refused unless the mod is a **folder**, not a `.jar` or `.zip`, and that folder has a
  `preview.png` at its root.
- The folder itself is uploaded as the item content, and `preview.png` as the preview image. Steam requires
  the preview to be under 1 MB.
- A new item is created **Private**, titled with the mod's internal `name` (`factory-scope`), described with
  `mod.hjson`'s `description`, tagged `mod`, with the change note `<Created>`. Title, description and
  visibility are then edited on the Steam page.
- The client stores the new item ID in its own settings (`factory-scope-steamid`). From then on, the mod's
  Workshop button opens the listing and offers **Update**, which uploads the folder again with a change note
  of up to 400 characters. Leave **update description** unticked: it resets the page title to `factory-scope`
  and the description to `mod.hjson`'s.
- `icon.png` remains the in-game mod icon; `preview.png` is only its fallback.

The Workshop item, a Mod Browser install and a GitHub jar all carry the mod name `factory-scope`. Mindustry
refuses to load a second mod with the same name, so a player should keep only one of them installed.

## Building the Workshop folder

The Workshop content is the published release artifact, unpacked, plus the preview. Nothing is rebuilt.

1. Download `FactoryScope.jar` from the GitHub release and compare its SHA-256 with the asset digest shown
   on the release.
2. Extract every entry into an empty folder, for example `FactoryScope-Workshop`. Keep everything, including
   `META-INF/` and `classes.dex`; the folder must load exactly what the jar loads.
3. Copy [`branding/workshop-preview.png`](branding/workshop-preview.png) into that folder as `preview.png`.
4. Place the folder in the Steam client's mods directory,
   `<Steam library>/steamapps/common/Mindustry/saves/mods/`, after moving any other `factory-scope` copy
   out of it.

For a later release, delete the folder's contents except `preview.png` before extracting the new jar, so no
class from the previous version is left behind, then use **Update** (see below).

## Publishing

1. Close Mindustry, prepare the folder as above, and start Mindustry from Steam.
2. Open **Mods** and confirm that FactoryScope 1.1.0 is listed once, enabled, and without errors.
3. Press the publish (export) button on FactoryScope, accept the Steam Workshop legal agreement if Steam asks,
   and confirm. The item is created Private.
4. On the item's Steam page, set the title and description below and add the six captures from
   [`media/`](media/) in this order: Area Diagnostics, Supply Trace, Network overlay, PowerScope,
   LiquidScope (gas), Liquid filter.
5. Edit the `<Created>` change note to the first change note below.
6. While the item is still Private, test a real Workshop install: close Mindustry, move the local folder out of
   `saves/mods`, subscribe to the item, let Steam download it, start Mindustry, and confirm that only the
   Workshop copy is loaded. Check the building inspector, Area Diagnostics, Network, Supply Trace, PowerScope,
   and LiquidScope in a game.
7. Make the item Public, and add the Workshop link to the README's install section.

## Updating to a new release

1. Close Mindustry. In the Workshop folder, delete everything except `preview.png`, then extract the new
   release's `FactoryScope.jar` into it as described above.
2. Unsubscribe from the Workshop item and let Steam remove its download. While it is subscribed, Mindustry
   loads the Workshop copy instead of the local folder (`Mods.load` keeps the last mod found with a name, and
   Workshop content is listed after `saves/mods`), and **Update** would upload that old copy again. Start
   Mindustry from Steam.
3. Open **Mods** and confirm that FactoryScope shows the new version once, enabled, and without errors.
4. Open the mod's Workshop menu, choose **Update**, and paste the change note for that release below. Leave
   **update description** unticked.
5. On the item's Steam page, replace the description with the listing text below if it changed.
6. Close Mindustry, remove the local folder from `saves/mods`, subscribe again, and confirm that the Workshop
   copy loads with the new version.

## Listing text

### Title

```text
FactoryScope — Factory Diagnostics
```

### Description

```text
[h1]FactoryScope[/h1]

[b]A debugger for your Mindustry factory.[/b]

FactoryScope is a read-only diagnostics and troubleshooting mod for Mindustry.

Instead of adding new blocks, resources, units, or production chains, FactoryScope helps you understand the factory you already built.

[h2]Building Diagnostics[/h2]

Inspect a building and see its current operating state, recognized input shortages, output limitations, and production rates for crafters, drills, pumps, extractors, separators, generators, reactors, unit factories, and turrets, derived from the game's own values and rules. Values that only an estimate can describe are marked as estimates.

[h2]Area Diagnostics[/h2]

Select an area of your factory and get one report covering the buildings inside it.

Repeated findings are grouped together, making it easier to identify patterns across larger production areas.

[h2]Network[/h2]

Inspect resource-aware item connections across a selected factory area. Every vanilla item transport is modeled, including bridges, unloaders, plastanium and surge conveyors, and mass drivers.

FactoryScope keeps modded transport it does not understand explicitly incomplete instead of guessing how it behaves.

[h2]Supply Trace[/h2]

Follow an item's structural route toward reachable producers, storage, boundaries, known dead ends, and unsupported transport.

Use Locate, Inspect, Return, and Refresh to navigate through the evidence without losing your current report.

[h2]PowerScope[/h2]

Inspect Mindustry's power grids using engine-maintained state, including generation, demand, satisfaction, battery storage, and connected grid members.

[h2]LiquidScope[/h2]

Inspect liquid and gas routes, producers, consumers, storage, and structural connectivity, including bridge, phase, and armored conduits.

[h2]Read-only by design[/h2]

FactoryScope does not modify your:

[list]
[*] blocks
[*] inventories
[*] items
[*] liquids
[*] power
[*] game rules
[*] production
[*] world state
[/list]

It is an inspection and troubleshooting tool, not an automation or cheat mod.

[h2]Evidence matters[/h2]

FactoryScope deliberately distinguishes structural connectivity from measured resource movement.

A visible route means FactoryScope has evidence that the connection can exist.

It does not automatically mean:

[list]
[*] resources are currently moving through that connection
[*] the route has enough throughput
[*] a reachable producer provides enough material
[*] that route caused the building's current problem
[/list]

FactoryScope does not currently measure exact per-edge throughput, quantitative supply sufficiency, or rank root causes.

[h2]Languages[/h2]

FactoryScope supports:

[list]
[*] English
[*] Portuguese (Brazil)
[*] Russian
[*] Simplified Chinese
[*] Korean
[*] Spanish
[/list]

[h2]Compatibility[/h2]

FactoryScope 1.2.0 targets Mindustry v8 Build 160+.

The 1.2.0 release was validated in the real game against Mindustry 160.7, in every supported language, and in multiplayer with an official dedicated server.

Unit cargo and payload logistics are not modeled yet, and modded transport with its own routing code is shown as incomplete rather than guessed.

[h2]Open Source[/h2]

FactoryScope is open source under GPL-3.0.

[url=https://github.com/Jovinull/FactoryScope]GitHub Repository[/url]

[url=https://github.com/Jovinull/FactoryScope/issues/new/choose]Report an Issue[/url]

Feedback is especially useful when you find incorrect diagnostics, unusual factory layouts, or transport that FactoryScope does not yet understand.
```

### First change note

```text
FactoryScope 1.1.0 — Initial Steam Workshop Release

- Building and Area Diagnostics
- Structural item Network inspection
- Supply Trace
- PowerScope
- LiquidScope
- Verified Armored Conveyor and Armored Duct routing support
- English, Portuguese (Brazil), Russian, Simplified Chinese, Korean, and Spanish localization
- Compatibility and diagnostic hardening for Mindustry v8

FactoryScope is read-only and does not modify factory state.
```

### 1.2.0 change note

At most 400 characters, for the in-game **Update** dialog.

```text
FactoryScope 1.2.0

- Production rates and stop reasons for drills, pumps, extractors, separators, generators, reactors, unit factories and turrets
- Every vanilla item and liquid transport is now modeled: duct bridges, unloaders, plastanium and surge conveyors, mass drivers, bridge and armored conduits
- Supply Trace lists only real dead ends
- Validated in multiplayer on Mindustry 160.7
```
