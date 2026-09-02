# Mindustry 159.7 item topology notes

These notes describe static routing only. They are based on the `v159.7` source, not block tooltips.
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
stored link and must remain team-safe. An unlinked or broken target has no remote topology edge.

Directional Duct Bridges are explicitly unsupported in 0.3. Their remote ingress and local fallback
behaviour cannot be represented by the same ports as a normal Duct without inventing a local route, so
they mark the area topology partial rather than adding an approximation.

## Duct Router and limits

`DuctRouterBuild` accepts from its rear, sends its configured item forward, and sends other items to a
side exit. Those side exits are structurally possible alternatives, not a claim about the current choice.

Plastanium Stack Conveyors have load, move and unload states derived from neighbouring blocks and stored
items. Mass Drivers depend on configured links and a separate state machine. Unloaders choose a source and
destination across their full neighbouring set rather than behaving as a simple storage output. FactoryScope
0.3 records all three as unsupported transport rather than approximating them as ordinary conveyors.

## Scope

The graph is item-only. Liquid conduits and payload logistics are outside 0.3. Ducts are item
transport, but their direction and bridge rules are kept in the Mindustry adapter rather than assumed
from conveyor code.
