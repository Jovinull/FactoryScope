# Mindustry 160.5 item topology notes

These notes describe static routing only. They are based on the `v160.5` source, not block tooltips.
They intentionally do not use `acceptItem`: that method includes inventory and receiver state and is
not a safe topology query.

## Conveyor and Junction

`ConveyorBuild.updateTile` passes an item only to `front()`. Its side-loading rules affect where an
item is placed on the belt, not the forward structural exit. A conveyor is therefore one directed
input-to-front route for topology.

`JunctionBuild` buffers each incoming side independently, then sends it to `nearby(i + 2)`. A
junction has two crossing channels: east-west and north-south. It must never be represented as one
fully connected building node.

## Sorter

`SorterBuild.getTileTarget` uses the incoming side and configured `sortItem`. For a normal sorter,
the configured item takes the straight route and other items take one of the two side routes. An
inverted sorter reverses that condition. The selected side can alternate at runtime when both exits
accept, but both side exits remain structurally possible; the graph marks them conditional rather
than measuring which one is currently chosen.

## Bridges

Item bridges use their configured link, not proximity. A valid remote edge must be derived from the
stored link and must remain team-safe. `ItemBridge.linkValid()` also checks the configured range and,
in v160, whether the bridge permits links to other bridge block types. Each endpoint applies its own
`linkSameType` setting, so FactoryScope checks both blocks before adding a remote edge. An unlinked or
broken target has no remote topology edge.

An unlinked item bridge dumps to its neighbors, but `checkDump` refuses the sides a bridge linked into it
arrives from, so those sides are not outputs. The bridges linked into a bridge are found from the engine's
`incoming` list plus a search along its axes, so a bridge placed this frame is not missed.

## Duct Bridges (v160.7)

`DuctBridgeBuild` (a `DirectionBridge`) finds its link with `findLink()`: the first bridge of the same block
and team within `range` tiles straight ahead. With a link, `updateTile()` hands every item to it and never
moves one locally. Without one, it moves items out of its front with `moveForward`. `acceptItem` returns
false when there is no link, so an unlinked bridge receives only what linked bridges hand it; with a link it
rejects its front side and any side recorded in `occupied`, which holds the bridge linked in from that side.
FactoryScope derives the same sides from placement: every bridge whose own `findLink()` ends here.

## Unloaders (v160.7)

`Unloader.onProximityUpdate` keeps the neighbors that can trade: a neighbor can receive when it is not a core
or storage, and can give when `canUnload()` holds (with `allowCoreUnload` limiting cores and core-linked
storage) and it has an item module. Each tick the unloader moves one item of the configured kind, or of the
next kind in rotation, from a giving neighbor to a different receiving neighbor that accepts it. Anything a
giver holds can leave: storage contents, a crafter's products, and the inputs a crafter holds. Conveyors,
routers, item bridges and the other unloadable-false blocks never give; Duct Bridges, Stack Conveyors (not
while loading) and Mass Drivers can. A turret keeps item ammunition in `ammo`, not in its item module, so
there is nothing to take. FactoryScope models a giver's contribution accordingly: storage as an endpoint, a
product from the block's output, held inputs and transport contents as a continuation of what entered it.

`DirectionalUnloader` (the Duct Unloader) takes only from `back()` and gives only to `front()`, and its front
may be storage.

## Stack conveyors (v160.7)

`StackConveyorBuild.onProximityUpdate` sets `state` to load, move or unload from the neighboring blocks:
a loading dock is the first conveyor of a line, an unloading end has no stack conveyor in front. Only a
loading dock accepts items (`acceptItem`), and never from the block it faces. A conveyor that is not
unloading hands its whole stack to the stack conveyor in front, bypassing `acceptItem`. An unloading end
calls `dump` on every neighbor when `outputRouter` is set (Plastanium) and `moveForward` otherwise (Surge).
`state` is the engine's own structural result, not item state, so FactoryScope reads it.

## Mass Drivers (v160.7)

A driver's link is valid when it points at a driver of the same block and team within `range`
(`MassDriverBuild.linkValid`, which is not accessible and is replicated). Only a driver with a valid link
accepts items. Its update switches from idle to shooting before it would dump, so it never dumps unless a
driver is firing at it; a driver fired at dumps what it receives while idle or accepting, and with its own
link it also fires on. Power, reload, aim and the queue of waiting shooters decide when a shot happens. In
the engine a relay that is fired at continuously turns to receive every shot before its own reload ends,
so it may only dump until the incoming fire stops; the route remains structurally possible.

## Duct Router and limits

`DuctRouterBuild` accepts from its rear, sends its configured item forward, and sends other items to a
side exit. Those side exits are structurally possible alternatives, not a claim about the current choice.
The v160.5 Surge Router extends this block family and uses the same configured ports; its transient
loading/unloading state is intentionally not presented as a live route or rate.

Overflow gates keep their direct route preferred and their side routes conditional; underflow gates invert
that relationship. Overflow ducts use the same preferred-versus-fallback distinction.

## Modded item transports

FactoryScope recognizes a finite set of v160.5 runtime Build implementations. A modded block that reuses
one of those unchanged Build classes receives that structural model; a custom Build implementation is
reported as an unsupported interruption instead of being inferred from `hasItems` or its block name.
This does not validate mod-specific changes to block fields, configuration, or other engine behavior.

## Armored item transports

In v160.5, `ArmoredConveyorBuild.acceptItem` first applies ordinary conveyor capacity/lane and front-side
checks, then accepts a source when it is a Conveyor-family block or its facing edge aligns with the
receiver rotation. FactoryScope models the stable source-class/alignment rule and forward route; current
lane occupancy and item positions remain outside the snapshot model.

Armored `DuctBuild.acceptItem` requires an empty one-item buffer, then accepts either a rotating item
source whose block is marked as a duct and whose front points into the receiver, or any source whose
edge aligns with the receiver rotation. The conditions are alternatives: a Duct-family source need not
face the receiver when it enters through the aligned edge. FactoryScope models both structural
insertion cases and the duct's forward output; buffer occupancy is not represented as a route
constraint. A structural edge does not claim an item is currently present or moving.

Duct Bridges, Unloaders, Stack Conveyors and Mass Drivers are described above. Each is recognized by its
exact engine Build class; a mod that replaces that class remains an interruption.

## Scope

The item graph is resource-aware structural topology. Duct direction and bridge rules are kept in the
Mindustry adapter rather than assumed from conveyor code. Liquid and payload families are documented
separately in the support matrix.

## 159.7 to 160.5 compatibility check

- Conveyor's source change is visual-only; its forward item handoff is unchanged.
- Junction, Sorter, Inverted Sorter, and the supported gate routing code have no relevant transfer
  change in the compared releases.
- Router now keeps its output-rotation cycle per item. This changes runtime destination choice, not the
  set of structurally possible adjacent outputs represented by the graph.
- Duct adds cached previous/next and cap state for rendering; the item handoff direction remains the
  same.
- Item bridges add `linkSameType`; the adapter delegates validation to the engine's `linkValid()` so
  configured cross-type links are represented only when the engine allows them.
- Mass Drivers, armored transport, Stack Conveyors, Duct Bridges, and Unloaders remained explicitly
  unsupported in that migration. They are modeled since the v160.7 work described above.
