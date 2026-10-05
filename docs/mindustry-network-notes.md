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

Directional Duct Bridges are explicitly unsupported in 0.3. Their remote ingress and local fallback
behaviour cannot be represented by the same ports as a normal Duct without inventing a local route, so
they mark the area topology partial rather than adding an approximation.

## Duct Router and limits

`DuctRouterBuild` accepts from its rear, sends its configured item forward, and sends other items to a
side exit. Those side exits are structurally possible alternatives, not a claim about the current choice.
The v160.5 Surge Router extends this block family and uses the same configured ports; its transient
loading/unloading state is intentionally not presented as a live route or rate.

Overflow gates keep their direct route preferred and their side routes conditional; underflow gates invert
that relationship. Overflow ducts use the same preferred-versus-fallback distinction.

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

Plastanium Stack Conveyors have load, move and unload states derived from neighboring blocks and item
state. Mass Drivers depend on configured links and a separate transfer state machine. Unloaders choose
a source and destination across their neighboring set rather than behaving as a simple storage output.
These remain explicit unsupported interruptions rather than ordinary conveyor routes.

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
- Mass Drivers, armored transport, Stack Conveyors, Duct Bridges, and Unloaders remain explicitly
  unsupported; this migration does not broaden their topology claims.
