# Throughput research

FactoryScope reports current production estimates and static item topology. It does not report observed
item movement. Exact per-edge throughput remains deferred until Mindustry exposes evidence that can be
attributed to a source, destination, and item without changing game behaviour.

## Mindustry update and flow APIs

Mindustry 160.5 fires `Trigger.beforeGameUpdate`, updates the simulation, then fires
`Trigger.afterGameUpdate` inside `Logic.update`. These hooks provide simulation boundaries, not transfer
events. The public `EventType` API contains no item-transfer event with source, destination, and item.
Supported blocks call destination methods such as `handleItem()` directly during their updates.

`ItemModule.updateFlow()` is not a network monitor. It samples additions accumulated for each item and
returns an item-module average, but its flow windows, accumulation arrays, display values, bit set, and
timer are static and shared. The result cannot distinguish a transfer from production or another item
addition, and it cannot attribute an addition to a particular transport edge. `remove()` does not
provide a matching per-edge event either.

Inventory deltas are also insufficient. An item can enter and leave a Router in one interval, leaving the
same inventory count; a producer can offload directly to a neighbour; and a building with multiple valid
inputs does not reveal which route delivered its inventory increase. Sampling less frequently can miss
several transfers or collapse distinct paths into one change.

## Conservative support

No transport family currently has exact per-edge throughput support. FactoryScope will not infer an edge
rate from nominal capacity, theoretical production, inventory deltas, or equal distribution across
possible routes. A future measurement feature may report aggregate building additions only if its source
and semantics can be independently proven; it must keep that separate from edge-attributed movement.

Instrumentation that replaces block implementations, wraps transfer methods, mutates inventory, or
writes engine-private state violates FactoryScope's observational contract and is not an acceptable
substitute for a public transfer event.
