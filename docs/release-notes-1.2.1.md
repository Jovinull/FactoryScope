# FactoryScope 1.2.1 [v160]

FactoryScope 1.2.1 makes probe failures visible in Network and traces. When the topology probe cannot
read a building, reports now explain that evidence is incomplete instead of leaving an unexplained gap.

## Fixes

- Network reports when buildings in the selected area could not be read.
- Supply Trace and LiquidScope identify unreadable buildings reached by a route and offer a Locate action.
- Traces that touch unreadable buildings remain incomplete; they do not claim a complete route, endpoint,
  or proven dead end there.
- Added the messages in English, Brazilian Portuguese, Russian, Simplified Chinese, Korean, and Spanish.

## Validation

Validated against Mindustry v160.7 in all six locales, with the isolated load smoke test and the official
dedicated-server multiplayer acceptance run. The universal JAR was built and checked by the Build workflow;
its SHA-256 is published with the release asset.

FactoryScope remains read-only. The warnings describe limits in the evidence it could read; they do not
alter the factory or infer a cause.
