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

Armored Conveyors and Armored Ducts have source-sensitive acceptance rules. Plastanium Stack Conveyors have
load, move and unload states derived from neighbouring blocks and stored items. Mass Drivers depend on
configured links and a separate state machine. Unloaders choose a source and destination across their full
neighbouring set rather than behaving as a simple storage output. FactoryScope 0.3 records all of these as
unsupported transport rather than approximating them as ordinary conveyors.

## Scope

The graph is item-only. Liquid conduits and payload logistics are outside 0.3. Ducts are item
transport, but their direction and bridge rules are kept in the Mindustry adapter rather than assumed
from conveyor code.

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
