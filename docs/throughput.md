# Observed throughput

Observed throughput is a count of successful item movement during simulation time. It is not a block's
theoretical output, its current expected output, or the change in its item inventory.

## Engine boundary

Mindustry 159.7 fires `Trigger.beforeGameUpdate`, updates the simulation, then fires
`Trigger.afterGameUpdate` while `Logic.update` is running an unpaused game. `state.tick` advances from
the simulation delta in that interval. A monitor must use this boundary and simulation time; wall time
must not dilute a rate while the game is paused.

`ItemModule.updateFlow()` is not a network monitor. Its working flow buffers are static and shared, and
it records additions to an item module. That is neither safe to enable for an area of buildings nor
sufficient to identify a transport edge.

## Attribution

An edge rate is exact only when a successful transfer can be tied to that exact directed port pair and
item. A building-level ingress count with multiple possible inputs remains aggregated. Unsupported or
ambiguous routes are unavailable, never zero or split across neighbours.

The rolling window stores bounded counts by simulation-time bucket. A topology refresh, new selection,
or world change ends the session because measurements belong to one graph snapshot.
