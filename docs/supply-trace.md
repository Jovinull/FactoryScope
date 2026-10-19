# Supply Trace

Supply Trace combines factory evidence and a structural resource graph:

- `FactorySnapshot` and `DiagnosticResult` describe what each analyzed building currently reports.
- `NetworkGraph` describes supported, directed item and payload routes in the selected area.

For an input trace, FactoryScope walks the resource-filtered graph backwards from the target's input
ports. For an output trace, it walks forward from the target's output ports. Item and payload ports
are separate, so item routes cannot be mistaken for whole-block or unit routes. Trace does not
enumerate every path; it returns each reachable producer, consumer, or storage endpoint once, with
one deterministic shortest representative path. A path is structural possibility, not evidence that
the resource currently travels there.

Item producer and consumer identity comes from item metadata in each building's `FactorySnapshot`, so
standard item-consuming blocks do not need to be crafters to serve as trace targets. Conventional
crafters use the outputs already collected by the diagnostic probe, including multiple declared item
products; external routes from a multi-output crafter retain that exact item set. Drills expose their
currently dominant mined item as a structural product, but this does not add a theoretical or observed rate.
Reachable producer status is copied from its existing `DiagnosticResult`; it is not recomputed by trace
logic. Storage remains a storage endpoint, never a producer merely because it may hold inventory.

Payload producers and consumers use identified unit or block payloads from the same snapshot. Unit
assemblers and constructors expose selected products; reconstructors expose their accepted upgrade
inputs and a product when a unit is held. Deconstructors accept only payloads that fit their size limit
and carry build requirements. Direct unit factory output is not treated as a payload-transport source.
Unit Cargo is an item route: a loader's
item inputs can reach same-team unload points configured for that item, and an unload point forwards
only its configured item into the local item network.

## Interpreting a result

An input trace may show reachable in-area producers, storage endpoints, a continuation at the area
boundary, and interruptions at transport that is not fully modeled. These facts can coexist. An
interruption identifies its building and can be located without leaving the trace's navigation context.
The trace never recursively scans beyond the selection.

A complete trace conclusion requires a target snapshot with the requested resource, a modeled target port,
available diagnostics for buildings that can affect the traversed item-specific subgraph, and no
relevant boundary or unsupported continuation. Unrelated unsupported transport elsewhere in the area
does not invalidate a local result. If a failed diagnostic probe has no recoverable building identity,
FactoryScope remains conservative and withholds a complete conclusion. A target whose resource ports are not
modeled is distinguished from a building whose diagnostic snapshot could not be read. A building whose state
could not be read while the topology was built is listed in every trace that reaches it, with a Locate action,
and the Network report notes that some buildings in the area could not be read. LiquidScope traces list them
the same way.

"No supported in-area producer or storage route reaches this input" is reserved for a complete search
with no compatible topology path to such an endpoint. If a route reaches a known in-area dead end, the
trace reports that dead end and separately states that no producer or storage endpoint is reachable; it
does not call the whole structure disconnected. Likewise, an output trace distinguishes the absence of a
downstream consumer/storage endpoint from the absence of any outgoing structural route. A known local dead
end can remain proven even when another relevant branch is incomplete.

Boundary continuations retain the resource kind when the adjacent outside producer or transport
semantics are known. A multi-output producer only continues the resources declared in its snapshot.
A supported resource-aware transport projects its configured constraints onto the boundary port.
Unit Cargo links to an out-of-area peer are shown as a continuation and are never recursively
traversed. Unknown or unreadable evidence must not be turned into a complete no-route claim.

A structural dead end is a building where a known route stops inside the area: the route enters it, and
nothing reachable from that entry inside the building leads on to another building or past the area's known
edge. A side no route uses is not a dead end by itself: the free sides of a conveyor whose back is fed, or
the other outputs of a router that does feed the target, belong to a route that continues. Each route
through a building is judged on its own, so the unfed crossing of a Junction is a dead end even when its
other crossing is fed. LiquidScope traces apply the same rule to conduits and liquid routers.

A dead end is not a suggested repair. A reachable producer that is disabled or has another diagnostic
problem is an independent current-state observation; FactoryScope does not claim that it caused the
target's shortage.

## Limits

Supply Trace covers item and payload inputs and outputs. Liquids are handled by LiquidScope; power and
heat are outside Supply Trace. It does not measure item or payload movement, prove quantitative supply
sufficiency, or claim that a reachable producer currently supplies a consumer. Exact per-edge throughput remains deferred because the public
Mindustry API does not expose a trustworthy read-only transfer event with source, destination, item, and
amount; see [the throughput research](throughput.md).

Refresh rebuilds both diagnostics and topology before recomputing the trace. World changes discard the
report and its trace state.
