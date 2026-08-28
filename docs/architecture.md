# How FactoryScope is put together

Two rules explain most of the structure.

**The diagnosis is decided in one place.** `FactoryAnalyzer` is the only code that decides why a factory
is in the state it is in. Everything else either feeds it or reads its answer.

**Nothing that reasons knows about Mindustry.** The packages that make decisions take plain data and
return plain data, so they can be tested without a game, a window or a graphics driver.

## The single-building pipeline

```
Building                     a live Mindustry entity
   |
   v
MindustryFactoryProbe        the only class that reads game types
   |
   v
FactorySnapshot              an immutable picture, no Mindustry types
   |
   v
FactoryAnalyzer              pure logic: which findings apply, in which order
   |
   v
DiagnosticResult             a primary finding plus every secondary one
   |
   v
FactoryScopePanel            words, colours, icons
```

`FactorySnapshot` is the seam. It exists so that the rules in `FactoryAnalyzer` can be written against
values with names, and checked against fixtures, instead of against a live building whose fields change
under the test.

## The area pipeline

Area diagnostics adds a collection step at the front and an aggregation step at the back. The middle is
untouched.

```
AreaSelection                normalized, inclusive tile bounds
   |
   v
AreaProbe.collect            one spatial query, per team
   |
   v
for each Building  --->  MindustryFactoryProbe  --->  FactoryAnalyzer
   |                                                        |
   |                                                        v
   |                                                  DiagnosticResult
   v                                                        |
AreaEntry  <--------------------------------------------- --+
   |
   v
AreaAnalyzer                 pure logic: counts, groups, ordering
   |
   v
AreaDiagnosticResult
   |
   v
AreaDiagnosticsDialog
```

`AreaAnalyzer` never decides *why* anything is broken. It receives results that already exist and does
three things to them: counts buildings by status, groups equivalent findings, and orders the groups. If
it ever grew a rule about shortages, the two analysers could disagree, and the area view would start
contradicting the panel a player opens from it.

Each building is probed once and analysed once per scan. A refresh re-runs the whole thing, including the
spatial query, so buildings added or destroyed since the last look are picked up.

## Finding the buildings

`AreaProbe` queries `Vars.indexer.eachBlock(Team, Rect, ...)`, which reads the team's `buildingTree`
quadtree. Two consequences, both wanted:

- Cost scales with the buildings near the selection rather than with the map. `Groups.build` is declared
  without `spatial`, so it has no quadtree at all and cannot be queried by rectangle; iterating it would
  be a full scan of every building in the world for every selection.
- The query starts from one team's index, so a building belonging to anyone else is never visited. An
  area selection cannot become a way to see through the fog of war.

The quadtree answers with block hitboxes, so the exact tile-footprint test is applied afterwards rather
than trusted from the query rectangle. A building belongs to an area when its footprint shares at least
one tile with it, and it appears exactly once however many of its tiles are inside.

## Coordinates

Every conversion from a pointer position to the world goes through `WorldCoords.fromStage`. There is one
implementation, and the reason is written above it: Arc reports input with a bottom-left origin, which is
what `Camera.unproject` expects, while `Scene.stageToScreenCoordinates` flips Y for platform input APIs.
Mixing the two mirrors every selection about the middle of the screen, which is the defect that shipped
in 0.1.0.

`AreaSelection` holds **tile** coordinates and says so. World coordinates only appear at the edges: in
the overlay that reads the pointer, in the rectangle that gets drawn, and in the rectangle handed to the
spatial query.

## Identity, not references

An area result stores `BuildingRef` — tile position, block id, team — rather than live `Building`
objects. A report outlives the factory it describes: the player reads it while the game runs on, and a
block can be destroyed and rebuilt in between. Holding entities would keep dead ones reachable, and
resolving a coordinate without re-checking identity would silently navigate to whatever was built there
since. `AreaProbe.resolve` re-checks both, and returns nothing when the building is gone.

Resource identity works the same way. `ResourceRef` carries the internal content id as well as the
localized name, and grouping uses the id. Grouping by display name would mean that switching the game to
another language changed which shortages counted as the same shortage, and that two modded items with
the same name silently merged.

## What FactoryScope does not know

It has no model of how factories feed each other. It sees conveyors, routers and bridges the way it sees
a wall: as buildings with no production model. It does not follow them.

So an area result can say *"eight buildings are short of sand"*, because it counted eight buildings that
each report a sand shortage. It cannot say *"sand production is the bottleneck"* — that is a claim about
a network, and there is no network here to make it from. The wording in the interface is chosen to keep
that line clear, and the same restraint applies to any new text.

Following production between buildings is the next milestone, not this one.
