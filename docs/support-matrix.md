# FactoryScope support matrix — Mindustry v160.5

This inventory is checked against the vanilla content declarations in [Mindustry v160.5 `Blocks.java`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/content/Blocks.java). The status describes the behavior FactoryScope models, not every behavior exposed by a similarly named modded block.

“Supported” means FactoryScope has a model for the listed vanilla behavior and regression coverage for
the evidence it presents. It does not mean every modded block or every state-dependent transfer is
modeled. “Partial” means the relevant building is surfaced as incomplete where reachable; FactoryScope
does not fill in an unknown route. “Deferred” means no current topology claim is made for that family.

## Diagnostic and grid views

| View | Status | Scope and evidence |
| --- | --- | --- |
| Single-building diagnostics | SUPPORTED | Reads the building's current Mindustry state and declared consumers; unknown consumer semantics remain explicitly limited. |
| Area Diagnostics | SUPPORTED | Uses the selected team's spatial index, snapshots the selected buildings, and groups their current diagnostic states. |
| PowerScope | SUPPORTED | Uses Mindustry's `PowerGraph` membership and aggregate metrics; it does not assign per-wire flow or a root cause. Remote multiplayer synchronization is not independently validated. |

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
| Armored Conveyor | PARTIAL | Its source-class and alignment acceptance is not represented as an ordinary conveyor. |
| Armored Duct (armored Duct block variant) | PARTIAL | Its armored insertion/blending rules are not represented as an ordinary duct. |
| Plastanium Conveyor and Surge Conveyor | PARTIAL | Their load/move/unload state machines are not flattened into an ordinary conveyor route. |
| Duct Bridge | PARTIAL | Remote-link, occupancy, and local-fallback semantics are surfaced as incomplete rather than inferred. |
| Unloader and Duct Unloader | PARTIAL | Dynamic source selection and storage extraction are not treated as a fixed producer route. |
| Mass Driver | PARTIAL | Reload, queue, target, and receiver state are not represented as an always-available edge. |
| Unknown modded item transport | PARTIAL | A transport-shaped block is not assumed to be a router or conveyor. |
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
| Plated Conduit and Reinforced Conduit (Armored Conduit variants) | PARTIAL | Their source-class and armored acceptance behavior is not approximated as ordinary Conduit. |
| Bridge Conduit and Phase Conduit (Liquid Bridge variants) | PARTIAL | The state-dependent remote-versus-local behavior is not reconstructed as a complete route. |
| Reinforced Bridge Conduit (Direction Liquid Bridge) | PARTIAL | Directional link search, occupied directions, and local fallback are not approximated as a normal bridge. |
| Unknown modded liquid transport | PARTIAL | `hasLiquids` or `outputsLiquid` alone does not establish internal routing. |

Liquids and Erekir gases share Mindustry's `Liquid` resource identity. Current module contents are a
separate snapshot annotation; they do not create or remove structural routes. LiquidScope does not
measure per-edge transfer, prove quantitative supply sufficiency, or assign cause.

## Platform validation

The desktop client has been exercised end to end. The universal artifact is DEX-built and structurally
checked, but Android runtime/touch behavior has not been validated on an Android device or emulator.
The acceptance launcher and its path/sandbox logic are tested on Windows, Linux, and macOS CI; graphical
Mindustry execution is validated only on platforms where it has actually been run.
