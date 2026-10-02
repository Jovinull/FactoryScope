# Supply Trace

Supply Trace combines two existing snapshots:

- `FactorySnapshot` and `DiagnosticResult` describe what each analyzed building currently reports.
- `NetworkGraph` describes supported, directed item routes in the selected area.

For an input trace, FactoryScope walks the item-filtered graph backwards from the target's input ports.
For an output trace, it walks forward from the target's output ports. It does not enumerate every path;
it returns each reachable producer, consumer, or storage endpoint once, with one deterministic shortest
representative path. A path is structural possibility, not evidence that items currently travel there.

Producer and consumer identity comes from item metadata in each building's `FactorySnapshot`, so
standard item-consuming blocks do not need to be crafters to serve as trace targets. Conventional
crafters use the outputs already collected by the diagnostic probe, including multiple declared item
products; external routes from a multi-output crafter retain that exact item set. Drills expose their
currently dominant mined item as a structural product, but this does not add a theoretical or observed rate.
Reachable producer status is copied from its existing `DiagnosticResult`; it is not recomputed by trace
logic. Storage remains a storage endpoint, never a producer merely because it may hold inventory.

## Interpreting a result

An input trace may show reachable in-area producers, storage endpoints, a continuation at the area
boundary, and interruptions at transport that is not fully modeled. These facts can coexist. The trace
never recursively scans beyond the selection.

A complete no-route conclusion requires a target snapshot with the requested item, available
diagnostics for every selected building, and no boundary or unsupported topology that could change the
conclusion. A target whose item ports are not modeled is distinguished from a building whose diagnostic
snapshot could not be read. If any of these checks fails, FactoryScope withholds the strong no-route
finding. The current completeness policy is conservative: any unsupported transport in the selected
area makes a trace incomplete, even if the block is not on its representative path.

A structural dead end is an in-area known route termination. It is not a suggested repair. A reachable
producer that is disabled or has another diagnostic problem is an independent current-state observation;
FactoryScope does not claim that it caused the target's shortage.

## Limits

Supply Trace covers item inputs and outputs only. It does not trace liquids, power, heat, or payloads. It
does not measure item movement, prove quantitative supply sufficiency, or claim that a reachable
producer currently supplies a consumer. Exact per-edge throughput remains deferred because the public
Mindustry API does not expose a trustworthy read-only transfer event with source, destination, item, and
amount; see [the throughput research](throughput.md).

Refresh rebuilds both diagnostics and topology before recomputing the trace. World changes discard the
report and its trace state.
