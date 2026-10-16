# FactoryScope support matrix — Mindustry v160.5 to v160.7

This inventory is checked against the vanilla content declarations in [Mindustry v160.5 `Blocks.java`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/content/Blocks.java). The status describes the behavior FactoryScope models, not every behavior exposed by a similarly named modded block.

It was re-checked for v160.7. Between [v160.5 and v160.7](https://github.com/Anuken/Mindustry/compare/v160.5...v160.7)
no content declaration, transport, power, liquid, or crafter source changed; the only world-block changes are
Directional Unloader save deserialization and the core's configuration UI, neither of which affects the modeled
topology. The headless engine tests and the real-client acceptance suite pass against v160.7, and so does the
multiplayer acceptance run against an official v160.7 dedicated server ([testing](testing.md#multiplayer-acceptance)).

“Supported” means FactoryScope has a model for the listed vanilla behavior and regression coverage for
the evidence it presents. It does not mean every modded block or every state-dependent transfer is
modeled. “Partial” means the relevant building is surfaced as incomplete where reachable; FactoryScope
does not fill in an unknown route. “Deferred” means no current topology claim is made for that family.

## Diagnostic and grid views

| View | Status | Scope and evidence |
| --- | --- | --- |
| Single-building diagnostics | SUPPORTED | Reads the building's current Mindustry state and declared consumers. Blocks of a modeled production family also get rates, verified stop conditions, and details (see below); other blocks and unknown consumer semantics remain explicitly limited. |
| Area Diagnostics | SUPPORTED | Uses the selected team's spatial index, snapshots the selected buildings, and groups their current diagnostic states. |
| PowerScope | SUPPORTED | Uses Mindustry's `PowerGraph` membership and aggregate metrics; it does not assign per-wire flow or a root cause. On a dedicated server the client's graph gives the server's membership and satisfaction (validated against v160.7). |

## Production models

A production model is the set of engine rules FactoryScope applies to a block family to report rates, stop
conditions, and details. Each current rate is the value the engine itself computes for the block's status bar or
power graph; the nominal rate is derived from the block's declared timing at 100% efficiency. A stop condition is
reported only when it is a check the engine makes before the block produces. Figures that depend on conditions
FactoryScope cannot observe are marked as estimates (≈) in the panel.

| Family | Vanilla blocks | Rates | Verified stop conditions | Details |
| --- | --- | --- | --- | --- |
| Crafters (`GenericCrafter`, `AttributeCrafter`, `HeatCrafter`, `HeatProducer`) | 31 | Exact, from `getProgressIncrease` and `scaleOutput` | Full output buffer | Cycle time, block speed and condition multipliers |
| Drills (`Drill`, `BurstDrill`) | 6 | The engine's drill speed (`lastDrillSpeed`) | No mineable ore; ore too hard for the tier or excluded (named); full buffer (a burst drill needs room for a whole burst) | Ore, ore tiles, drill tier, speed with full boost, warm-up |
| Plasma bores (`BeamDrill`) | 2 | The engine's drill speed, split by the walls each laser reaches | No mineable ore wall; walls too hard or excluded (named); full buffer | Lasers on ore walls, drill tier, speed with full boost |
| Cliff crushers (`WallCrafter`) | 2 | The engine's efficiency (`lastEfficiency`) | No suitable wall in front; full buffer | Wall yield, speed with full boost |
| Pumps (`Pump`) | 4 | `amount × pumpAmount × edelta()`, capped by free capacity as the engine caps it | No liquid under the pump; full tank | Liquid tile yield |
| Extractors (`SolidPump`, `Fracker`) | 2 | The engine's pump speed (`lastPump`) | Full tank | Ground yield |
| Separators (`Separator`) | 2 | The cycle rate is exact; per-item rates are **estimates** (each cycle draws one result at random, weighted by its stack amount) | Full shared output buffer | — |
| Generators and reactors (`ConsumeGenerator`, `SolarGenerator`, `ThermalGenerator`, `NuclearReactor`, `ImpactReactor`, `VariableReactor`, `HeaterGenerator`) | 14 | The engine's power production (`getPowerProduction`), plus declared liquid by-products | Fuel and other inputs through the consumers; light, heat under the block and fuel quality as a block condition | Generation efficiency, fuel use while running, light, heat, fuel stored, reactor heat and instability coloured by the engine's own thresholds, warm-up, explode-when-full tanks |
| Unit factories (`UnitFactory`) | 6 | `efficiency × unitBuildSpeed ÷ plan time` | No unit selected; unit cap reached; finished unit unable to leave | Unit, progress, units of that type against the cap |
| Reconstructors (`Reconstructor`) | 8 | `efficiency × unitBuildSpeed ÷ constructTime` | No upgradable unit inside (accepted units are named); unit cap reached; finished unit unable to leave | Unit inside and its upgrade, progress |
| Unit assemblers (`UnitAssembler`) | 3 | The same, scaled by the share of drones in position | Unit cap reached; spawn area occupied; payload requirements as inputs | Drones in position, progress |
| Turrets (`ItemTurret`, `LiquidTurret`, `PowerTurret`, `ContinuousTurret`, `ContinuousLiquidTurret`, `LaserTurret`, `PointDefenseTurret`, `TractorBeamTurret`) | 28 | Turrets produce nothing; shots and ammunition use while firing are **estimates** that assume continuous fire at full efficiency without coolant | No usable ammunition (accepted ammunition is named); loaded and reloaded with no target, reported as normal | Ammunition held and loaded, reload, heat requirement, whether it is engaging |

Other blocks - projectors, menders, cores and storage, incinerators, constructors, payload and unit logistics -
keep limited diagnostics: their state and declared requirements, without rates or block-specific verdicts.

A model applies only to a building whose runtime build class is the engine's own. A mod block that reuses a
vanilla build class gets the vanilla model; a mod that replaces the build class may have changed exactly the rules
the model relies on, so its buildings keep the general diagnostics.

## Item topology and Supply Trace

| Vanilla family | Status | Scope and limitation |
| --- | --- | --- |
| Conveyor and titanium conveyor | SUPPORTED | Directed item route with the engine's front output and accepted non-front inputs. |
| Distributor and Router | SUPPORTED | Structural multi-side routing; no branch rate or arbitration claim. |
| Junction | SUPPORTED | Preserves the engine's straight-through channels. |
| Sorter and inverted sorter | SUPPORTED | Models structural branches and selected-item constraints; branch choice and throughput are not measured. |
| Overflow / underflow gate | SUPPORTED | Models the gate's resource-independent structural branch possibilities. |
| Duct, duct router, overflow duct, underflow duct, Surge Router | SUPPORTED | Models standard duct ports and configured item constraints. Surge Router shares Duct Router port/configuration semantics; its transient loading/unloading state is not reported. |
| Item Bridge and Phase Conveyor | SUPPORTED | Uses the configured, engine-valid link; a remote edge means structural connectivity, not current transfer. |
| Drill, conventional crafter, known consumer, core/storage | SUPPORTED | Uses captured producer/consumer resource identities and keeps storage distinct from production. |
| Separator, Plasma Bore, Cliff Crusher | SUPPORTED | Structural producers of the items they can output: every possible separator result, the ore of each wall a bore reaches, and the crusher's declared item. A separator's product is random per cycle; the route claims only that it can carry each result. |
| Armored Conveyor | SUPPORTED | Models the forward route and Mindustry's source-sensitive insertion: Conveyor sources may enter through non-front sides, while other sources require the engine-aligned side. Transient belt capacity/position is not modeled. |
| Armored Duct | SUPPORTED | Models the forward route; v160.5 accepts a rotating Duct source facing into it, or any source at the engine-aligned edge. Transient one-item occupancy is not modeled. |
| Plastanium Conveyor and Surge Conveyor | PARTIAL | Their load/move/unload state machines are not flattened into an ordinary conveyor route. |
| Duct Bridge | PARTIAL | Remote-link, occupancy, and local-fallback semantics are surfaced as incomplete rather than inferred. |
| Unloader and Duct Unloader | PARTIAL | Dynamic source selection and storage extraction are not treated as a fixed producer route. |
| Mass Driver | PARTIAL | Reload, queue, target, and receiver state are not represented as an always-available edge. |
| Unknown modded item transport | PARTIAL | A custom runtime Build class is an interruption. Blocks that reuse a recognized vanilla Build class use that structural model, but mod-specific field/configuration changes are not independently verified. |
| Unit Cargo Loader and Unit Cargo Unload Point | DEFERRED | These move units/cargo rather than item resources and are outside the item topology model. |
| Payload Conveyor/Router, reinforced payload variants, Payload/Large Payload Mass Driver, Payload Loader/Unloader | DEFERRED | Payload logistics move whole blocks/units rather than item or liquid resources and are outside the Network resource model. |

## Liquid topology and LiquidScope

| Vanilla family | Status | Scope and limitation |
| --- | --- | --- |
| Conduit and Pulse Conduit | SUPPORTED | Models three non-front structural inputs and the rotation-facing output, independent of current buffer contents. |
| Liquid Junction and Reinforced Liquid Junction | SUPPORTED | Keeps north/south and east/west channels separate. |
| Liquid Router/Container/Tank and reinforced variants | SUPPORTED | Models distinct-side structural routing and separately reports stored liquid; storage is not production. |
| Mechanical, Rotary, Impulse, and Reinforced Pump | SUPPORTED | Uses the placed pump's current product identity; output is a producer role, not measured pipe transfer. |
| Water Extractor (Solid Pump) and Oil Extractor (Fracker) | SUPPORTED | Uses the declared Solid Pump result and actual placement state; output is not measured network transfer. |
| Conventional crafter liquid outputs | SUPPORTED | Keeps products resource-specific and applies each product's declared output direction. |
| Liquid by-products from power generators | SUPPORTED | Uses the generator's declared `outputLiquid` identity; this does not model a transport rate through the connected network. |
| Declared liquid consumers and enumerable filters | SUPPORTED | Uses exact resource identities; dynamic requirements are re-read on Refresh and optional inputs remain optional. |
| Plated Conduit and Reinforced Conduit (Armored Conduit variants) | PARTIAL | Their `acceptLiquid` rule combines the ordinary conduit direction/resource state with source class, source-relative alignment, and a proximity-membership exception; this combination is not yet represented as static topology. |
| Bridge Conduit and Phase Conduit (Liquid Bridge variants) | PARTIAL | The state-dependent remote-versus-local behavior is not reconstructed as a complete route. |
| Reinforced Bridge Conduit (Direction Liquid Bridge) | PARTIAL | Directional link search, occupied directions, and local fallback are not approximated as a normal bridge. |
| Unknown modded liquid transport | PARTIAL | A custom runtime Build class is an interruption. A block reusing a recognized vanilla Conduit, Liquid Junction, or Liquid Router Build uses that structural model, but mod-specific field/configuration changes are not independently verified. |

Liquids and Erekir gases share Mindustry's `Liquid` resource identity. Current module contents are a
separate snapshot annotation; they do not create or remove structural routes. LiquidScope does not
measure per-edge transfer, prove quantitative supply sufficiency, or assign cause.

## Platform validation

The desktop client has been exercised end to end. The universal artifact is DEX-built and structurally
checked, but Android runtime/touch behavior has not been validated on an Android device or emulator.
The acceptance launcher and its path/sandbox logic are tested on Windows, Linux, and macOS CI; graphical
Mindustry execution is validated only on platforms where it has actually been run.
