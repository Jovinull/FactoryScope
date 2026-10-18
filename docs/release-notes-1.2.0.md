# FactoryScope 1.2.0 [v160]

FactoryScope 1.2 models production for every remaining vanilla production family, models every remaining vanilla item and liquid transport, and is validated in real multiplayer. It remains a read-only diagnostics tool: routes describe structural possibility, not observed transfer.

## Highlights

- Production rates and stop conditions for drills, burst drills, plasma bores, cliff crushers, pumps, water and oil extractors, separators, generators and reactors, unit factories, reconstructors, unit assemblers, and turrets, derived from the engine's own values and rules.
- New verdicts: nothing to extract, ore or walls too hard for the drill, no unit selected, unit cap reached, waiting for a unit to upgrade, no ammunition, payload shortage, and a loaded turret that is ready with no target.
- Every remaining vanilla transport is modeled in Network, Supply Trace, and LiquidScope: Duct Bridges, Unloaders and Duct Unloaders, Plastanium and Surge Conveyors, Mass Drivers, Plated and Reinforced Conduits, Bridge and Phase Conduits, and Reinforced Bridge Conduits.
- Supply Trace and LiquidScope list a dead end only where a route actually stops. The free sides of a fed conveyor, conduit, or router are no longer reported.
- Multiplayer is validated against an official v160.7 dedicated server.

## Production models

Rates come from the values and rules the engine itself uses for each family. What only an estimate can describe is marked as an estimate: a separator's per-item output, which is random in the game, and a turret's fire rate and ammunition use while it has targets. Separators, plasma bores, and cliff crushers are also structural item producers in Network and Supply Trace.

## Transport coverage

Each newly modeled family follows the engine's own link, acceptance, and state rules, and each is checked twice against the running v160.7 engine: the topology says where a resource can go, then the engine moves it and must deliver exactly there. Two engine behaviors are worth knowing: an Unloader cannot take a turret's ammunition, and a Mass Driver relay under continuous fire only unloads what it receives until the fire stops.

Unit Cargo and payload logistics remain deferred. The sandbox Item and Liquid Sources stay outside the transport models. Unknown custom routing remains conservative: a modded transport with its own routing code is shown as incomplete rather than guessed.

## Fixes

- A loaded item turret was reported as short of items, and an idle one as not consuming. Its ammunition is now read from the turret itself, and inspection never reorders it.
- A turret with no ammunition is counted once in Area Diagnostics, not once per accepted ammunition, and its ammunition is no longer repeated as an item shortage.
- An unlinked item bridge was treated as able to dump toward a bridge linked into it, which the engine refuses.
- A failure to read a building's state while the topology was built went unnoticed, and an unreadable building just outside the area was assumed to supply any item. Such a failure is now recorded and logged, and a trace that touches it is never reported as complete, as a proven missing route, or as a dead end there.

## Evidence model

Item and liquid routes are structural connectivity, not measured flow, throughput, or guaranteed delivery. Reachable producers do not prove sufficient supply or causality. FactoryScope does not rank root causes or recommend repairs.

## Validation and limitations

Targets Mindustry v8 Build 160 and was validated against v160.7:

- 405 automated tests, including engine-differential tests that run each transport family in the v160.7 engine, plus 20 launcher tests and the D8 tooling checks.
- The complete real-client acceptance suite in all six supported languages: 818 checks each, no failures.
- Multiplayer acceptance: an official v160.7 dedicated server and client in fog of war, 59 checks with no failures. The client reaches the same diagnoses, area reports, traces, and power grids as the server, and enemy buildings hidden by fog stay out of reach.
- Targeted mutations confirm that the probe-failure evidence is caught by its tests, and the new dead-end tests fail under the previous rule.

Desktop graphical acceptance was performed on Windows; launcher/path portability was checked on Windows, Linux, and macOS. The universal JAR's DEX was structurally validated, but Android runtime and touch were not validated.
