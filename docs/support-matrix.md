# FactoryScope support matrix — Mindustry v160.5

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
| Junction | SUPPORTED | Preserves the engine's straight-through channels. |
| Router and sorter / inverted sorter | SUPPORTED | Models structural branches and the selected-item constraints; branch choice and throughput are not measured. |
| Overflow / underflow gate | SUPPORTED | Models the gate's resource-independent structural branch possibilities. |
| Duct, duct router, overflow duct, underflow duct | SUPPORTED | Models the standard non-armored v160.5 duct families and their configured directions/constraints. |
| Item Bridge family | SUPPORTED | Uses the configured, engine-valid link; a remote edge means structural connectivity, not current transfer. |
| Drill, conventional crafter, known consumer, core/storage | SUPPORTED | Uses captured producer/consumer resource identities and keeps storage distinct from production. |
| Armored Conveyor | PARTIAL | Its source-class and alignment acceptance is not represented as an ordinary conveyor. |
| Armored Duct | PARTIAL | Its armored insertion/blending rules are not represented as an ordinary duct. |
| Plastanium / Surge Stack Conveyor | PARTIAL | Its load/move/unload state machine is not flattened into a conveyor route. |
| Duct Bridge and Direction Bridge | PARTIAL | Remote-link, occupancy, and local-fallback semantics are surfaced as incomplete rather than inferred. |
| Unloader and Duct Unloader | PARTIAL | Dynamic source selection and storage extraction are not treated as a fixed producer route. |
| Mass Driver | PARTIAL | Reload, queue, target, and receiver state are not represented as an always-available edge. |
| Unknown modded item transport | PARTIAL | A transport-shaped block is not assumed to be a router or conveyor. |
| Payload conveyors, routers, loaders, and mass drivers | DEFERRED | Payload logistics move whole blocks/units rather than item or liquid resources and are outside the Network resource model. |

## Liquid topology and LiquidScope

| Vanilla family | Status | Scope and limitation |
| --- | --- | --- |
| Conduit and Pulse Conduit | SUPPORTED | Models three non-front structural inputs and the rotation-facing output, independent of current buffer contents. |
| Liquid Junction | SUPPORTED | Keeps north/south and east/west channels separate. |
| Liquid Router, Container, Tank | SUPPORTED | Models distinct-side structural routing and separately reports stored liquid; storage is not production. |
| Pump, Solid Pump, Fracker | SUPPORTED | Uses the placed pump's current product identity; output is a producer role, not measured pipe transfer. |
| Conventional crafter liquid outputs | SUPPORTED | Keeps products resource-specific and applies each product's declared output direction. |
| Liquid by-products from power generators | SUPPORTED | Uses the generator's declared `outputLiquid` identity; this does not model a transport rate through the connected network. |
| Declared liquid consumers and enumerable filters | SUPPORTED | Uses exact resource identities; dynamic requirements are re-read on Refresh and optional inputs remain optional. |
| Armored Conduit | PARTIAL | Its source-class and armored acceptance behavior is not approximated as ordinary Conduit. |
| Liquid Bridge / Phase Conduit | PARTIAL | The state-dependent remote-versus-local behavior is not reconstructed as a complete route. |
| Direction Liquid Bridge | PARTIAL | Directional link search, occupied directions, and local fallback are not approximated as a normal bridge. |
| Unknown modded liquid transport | PARTIAL | `hasLiquids` or `outputsLiquid` alone does not establish internal routing. |

Liquids and Erekir gases share Mindustry's `Liquid` resource identity. Current module contents are a
separate snapshot annotation; they do not create or remove structural routes. LiquidScope does not
measure per-edge transfer, prove quantitative supply sufficiency, or assign cause.

## Platform validation

The desktop client has been exercised end to end. The universal artifact is DEX-built and structurally
checked, but Android runtime/touch behavior has not been validated on an Android device or emulator.
The acceptance launcher and its path/sandbox logic are tested on Windows, Linux, and macOS CI; graphical
Mindustry execution is validated only on platforms where it has actually been run.
