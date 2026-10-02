# PowerScope (0.5 development)

PowerScope snapshots the **actual Mindustry `PowerGraph`** for every power grid intersecting a selected
building or area. It does not create a second electrical-network simulation. Area membership is shown for
scope, but generation, demand, satisfaction, batteries, and established connections describe the whole
engine graph, including its visible same-team members outside the selected rectangle.

## Quantities and meaning

| Display | Source and meaning |
| --- | --- |
| Generation | Mindustry's current producer output, normalized to power/second. Battery discharge is not included. |
| Demand | The current `PowerGraph` request from consumers that currently `shouldConsumePower`, normalized to power/second. Batteries are not consumers here. |
| Satisfaction | `PowerGraph.getSatisfaction()` from the latest graph update. Mindustry may include energy drawn from batteries, so satisfaction can be full while generation remains below demand. |
| Stored / capacity | Read-only current status and capacity of enabled batteries. This is energy/storage, not a rate. |
| Recent balance | The engine's 60-sample window of generation minus demand, with its Power Diode energy adjustment. It is not a per-cable rate and does not count battery discharge as generation. |

PowerGraph generation and demand getters return frame-integrated values (`building.delta()` is included).
PowerScope normalizes those values to the project's existing power/second convention. `PowerGraph`
updates consumer satisfaction after using batteries; its balance sample is recorded before battery use.
Those are deliberately separate facts. “Battery reserves present” only means a current generation deficit
and stored reserves were both observed; it does not quantify which consumer or cable received them.

When Mindustry's team cheat-power rule is active, graph totals other than the engine's synthetic
satisfaction value can be stale. PowerScope labels the grid cheat-powered and hides those aggregate
metrics. If any graph member cannot be safely inspected due to team/fog/identity state, aggregate values
are hidden rather than computed from a partial graph. Non-finite engine totals are likewise unavailable,
never converted to a reassuring zero or a "no demand" conclusion.

## Membership and connections

Grid identity and member roles come from `Building.power.graph` and the graph's `all`, `producers`,
`consumers`, and `batteries` collections. A report uses transient Java identity only to group live graph
references during one probe; the immutable report retains only FactoryScope-owned `BuildingRef` values.
Graph IDs are deterministic ordinals scoped to that one report, never persistent identities.

The overlay copies established ordinary connections from `Building.getPowerConnections(...)` and draws
each undirected pair once. A line means electrical connectivity only; it does not mean that energy moves
in a particular direction or at a particular rate. PowerNode configuration and BeamNode's maintained
links are read from engine state, not re-derived from range rules.

Power Diodes are not normal graph edges. Mindustry keeps the grids on either side separate and applies a
directional, battery-percentage-dependent transfer. PowerScope presents the diode as a distinct
conditional cross-grid relation, and never invents a transfer amount or merges the two summaries.

## Evidence and limits

- Generator and consumer membership is taken from the engine graph, so conventional modded power blocks
  participate without a vanilla class-name table.
- Generator condition annotations reuse `FactoryAnalyzer`; PowerScope does not reimplement fuel,
  efficiency, or disabled-state logic.
- A generator diagnostic and a grid deficit may be shown together, but PowerScope does not call one the
  cause of the other.
- Area summaries include complete connected-grid totals and separately show selected-member counts.
- Grid merge/split changes are picked up on Refresh; a report is otherwise a snapshot. World changes
  dispose of it.
- Ordinary power is pooled by Mindustry. There is no per-wire flow, per-generator attribution of the
  deficit, battery depletion prediction, or recommended fix.
- Remote multiplayer visibility/synchronization is not yet independently validated. PowerScope remains
  client-side and introduces no server component.
- Android runtime/touch remains unvalidated; the release pipeline validates the universal DEX artifact.

## Source reviewed

Mindustry v8 Build 160.5 sources:

- [`PowerGraph`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/power/PowerGraph.java)
- [`PowerModule`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/modules/PowerModule.java)
- [`BuildingComp#getPowerConnections`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/entities/comp/BuildingComp.java)
- [`PowerNode`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/power/PowerNode.java)
- [`BeamNode`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/power/BeamNode.java)
- [`PowerDiode`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/power/PowerDiode.java)
- [`PowerGenerator`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/blocks/power/PowerGenerator.java)
- [`ConsumePower`](https://github.com/Anuken/Mindustry/blob/v160.5/core/src/mindustry/world/consumers/ConsumePower.java)

The exact conversion and battery semantics are also covered by `PowerGridAnalyzerTest`,
`MindustryPowerProbeTest`, and `PowerGridBatteryIntegrationTest`.
