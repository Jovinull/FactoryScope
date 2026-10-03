# Mindustry v160.5 liquid topology notes

LiquidScope describes structural possibilities. It does not sample or predict liquid transfer. There is
no engine-maintained liquid graph comparable to `PowerGraph`; buildings call liquid transfer methods
with live buffer, capacity, team, pressure, and receiver-state checks. Those methods are useful as an
engine oracle in tests, but their current result is not a static topology query.

## Verified behavior and support matrix

| Family | Structural model | Current status | Why |
| --- | --- | --- | --- |
| Conduit | Any of the three non-front sides may enter; output is front-only | Supported | `acceptLiquid` also checks the current buffer, so only its directional side rule is used for topology |
| Liquid Junction and Reinforced Liquid Junction | Independent straight-through N–S and E–W channels | Supported | Both vanilla blocks use Mindustry's `LiquidJunction`; `getLiquidDestination` preserves the incoming direction, and disabled runtime state is not structural topology |
| Liquid Router/Container/Tank and reinforced variants | All-side storage/router; structurally connect each distinct input/output side | Supported | These vanilla blocks use `LiquidRouter`; current contents affect acceptance and dumping, not the persistent set of possible routes |
| Liquid Bridge | Not approximated as a router or simple remote edge | Partial / interruption | `ItemBridgeBuild.updateTile()` chooses remote `updateTransport()` for a valid configured link and local `doDump()` only when the link is invalid; input/output sides also depend on that link and registered incoming bridges |
| GenericCrafter liquid output | One resource-specific output route for each declared `LiquidStack`, using that stack's `liquidOutputDirections` after rotation; `-1` means unrestricted dump sides | Supported | `dumpOutputs` passes each product's own direction to `dumpLiquid` |
| Mechanical/Rotary/Impulse/Reinforced Pump | Product from the live placement's `liquidDrop`; external output may dump on neighboring sides | Supported | These vanilla blocks use `Pump`; floor/footprint state is placement-specific and is read at Refresh |
| SolidPump / Fracker | Product from configured `result`; inherits Pump's liquid routing | Supported | `SolidPumpBuild.updateTile()` assigns `liquidDrop = result` and uses Pump's dump behavior; the configured product is available before its first update, and Fracker remains a SolidPump output family |
| `ConsumeGenerator` / `ThermalGenerator` liquid by-product | Exact declared `outputLiquid` product | Supported | The probe records declared product identity only; no network transfer rate is inferred |
| Exact `ConsumeLiquid` / `ConsumeLiquids` | Declared resource identities, one requirement per declared liquid | Supported | Consumer stacks are static block metadata |
| `ConsumeLiquidFilter` / coolant filter | Enumerate the filter's accepted content set, preserving each `Liquid` identity | Supported when enumerable | Current `getConsumed` is only a buffer choice and cannot define all structural choices; conventional consumers without liquid-output behavior are terminal input endpoints |
| `ConsumeLiquidsDynamic` | Evaluate the current building-specific `LiquidStack[]` on each Refresh | Supported when evaluation succeeds | Dynamic requirements are a snapshot fact, not a permanently cached block property |
| Plated/Reinforced Conduit (`ArmoredConduit`) | Not approximated as Conduit | Partial / interruption | Acceptance depends on source class/alignment and armored-blending rules |
| Reinforced Bridge Conduit (`DirectionLiquidBridge`) | Not approximated as LiquidBridge | Partial / interruption | It uses forward link search, per-direction occupancy, and a local forward fallback when unlinked |
| Unknown/modded liquid transport | No guessed internal route | Partial / interruption | Liquid-output-capable blocks with unknown routing are not traversed; `hasLiquids` alone does not establish transport semantics |

Liquids and gases both use Mindustry's `Liquid` content type. Resource identity is the content identity,
never a localized name or the module's `current()` selection. Storage is independent of production: a
router/tank may be both a transport participant and a storage endpoint, but its contents do not make it
a producer.

## Transfer methods are runtime operations

`Building.acceptLiquid`, `moveLiquid`, `moveLiquidForward`, and `dumpLiquid` are not used by production
topology construction. They apply transient checks such as current liquid compatibility, capacity,
team, fullness, direction, bridge warmup, or pressure. A Conduit currently holding Oil must therefore
retain the same structural Water edges it would have while empty; the displayed Oil amount is a separate
snapshot fact.

Conduit forwards only toward its rotation, while its side acceptance rejects the front/output side. Its
front may also leak into a valid empty world tile when the block's `leaks` flag allows it, so a terminal
port is described as a route termination, not automatically as a blockage. A Liquid Junction resolves
the destination straight through from the source side; its two crossing channels must not be collapsed
to one building node. A Liquid Router calls `dumpLiquid` and has no fixed directional choice or
distribution guarantee.

LiquidBridge is ItemBridge-backed and does not have stable all-side router semantics. With a valid link,
`updateTile()` uses the remote transport path; when that link is invalid, it uses local dumping instead.
Its local input and output eligibility also depend on the configured direction and `incoming` links.
LiquidScope therefore marks the family partial and emits a navigable interruption instead of inventing
local router branches or treating a remote link as a complete port model. DirectionLiquidBridge is different: it searches forward for a
matching directional bridge, records occupied entry directions, and falls back to front movement when
there is no remote link. Until those rules are represented and tested as distinct states, it remains an
explicit partial interruption.

## Stored liquid and flow tracking

`LiquidModule.current()` is documented by the engine as only valid for single-liquid modules. It is the
last received/loaded selection, not a complete inventory listing. LiquidScope reads every positive
content amount using `LiquidModule.each()` and stores only immutable resource references and amounts.

The engine's optional `LiquidModule.updateFlow()` / `getFlowRate()` is module-level, aggregates positive
module additions/handling over a window, and has no source, destination, or edge identity. It also
allocates/shared-caches flow windows intended for the engine's own liquid display lifecycle. LiquidScope
does not enable it and does not label it pipe throughput.

## Scope and limitations

Graphs are collected for the selected area only. A directly adjacent, resource-compatible supported route
outside the area is reported as a boundary continuation; it is not recursively scanned. An unsupported
transport immediately outside the area is an unsupported interruption, not a proven boundary route.
Unreadable dynamic requirements/products remain resource-scoped incomplete continuations; they are not
converted to dead ends. An unrelated unsupported branch for Oil does not poison a Water-only trace.
Structural route does not mean current movement, quantitative sufficiency, blockage, or root cause.

The behavior above was checked against the pinned Mindustry v8 Build 160.5 source. The engine implementation
is available in the official [`Conduit`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/liquid/Conduit.java),
[`LiquidJunction`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/liquid/LiquidJunction.java),
[`LiquidRouter`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/liquid/LiquidRouter.java),
[`LiquidBridge`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/liquid/LiquidBridge.java),
[`DirectionLiquidBridge`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/distribution/DirectionLiquidBridge.java),
[`LiquidModule`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/modules/LiquidModule.java),
[`Pump`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/production/Pump.java),
[`SolidPump`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/production/SolidPump.java),
[`Fracker`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/production/Fracker.java),
[`GenericCrafter`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/production/GenericCrafter.java),
and the [`liquid consumer` implementations](https://github.com/Anuken/Mindustry/tree/v160.5/core/src/mindustry/world/consumers).
