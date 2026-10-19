# FactoryScope 1.3.0 [v160]

FactoryScope 1.3.0 extends Network and Supply Trace from items to typed payloads and models vanilla
Unit Cargo item routes. The evidence remains read-only and structural: a route means a resource can
travel through the modeled connections, not that it is moving or has enough throughput.

## Network and Supply Trace

- Trace identified whole-block and unit payloads through payload conveyors, routers, reinforced
  variants, eligible storage loaders/unloaders, deconstructors, constructors, reconstructors, unit
  assemblers, and configured payload mass-driver links.
- Keep item and payload channels separate, preserve router selections, accepted payloads, and transport
  size limits, and expose supported payload producers and requirements to traces.
- Model Unit Cargo as an item route from a same-team loader to unload points configured for that item.
  Peers outside the selected area appear as boundary continuations and are not recursively inspected.
- Continue to mark unknown or unreadable transport as incomplete rather than guessing its behavior.

## Validation

The release is gated by the complete headless test suite, universal-JAR artifact checks, real-client
acceptance across all six supported locales, isolated smoke launch, and multiplayer acceptance against
the official Mindustry v160.7 dedicated server. The README and support matrix record the precise
coverage and limits.
