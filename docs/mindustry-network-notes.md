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

## Scope

The graph is item-only. Liquid conduits and payload logistics are outside 0.3. Ducts are item
transport, but their direction and bridge rules are kept in the Mindustry adapter rather than assumed
from conveyor code.
