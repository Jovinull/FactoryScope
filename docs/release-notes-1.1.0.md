# FactoryScope 1.1.0 [v160]

FactoryScope 1.1 expands evidence-backed structural item-network coverage and adds four supported languages. It remains a read-only diagnostics tool: routes describe structural possibility, not observed transfer.

## Highlights

- Armored Conveyor and Armored Duct now model their verified Mindustry v160.5 item-insertion rules.
- Custom transport builds with unrecognized routing behavior are kept explicitly incomplete instead of being inferred as vanilla routes.
- Russian, Simplified Chinese, Korean, and Spanish are added alongside English and Portuguese (Brazil). Vanilla terminology is aligned with Mindustry v160.5 official localization bundles where applicable.
- Supply Trace handles large skipped-building sets more efficiently, and universal artifact creation has more robust Android SDK/D8 discovery.

## Coverage

Armored Conveyor models its source-sensitive insertion rule; Armored Duct models the verified duct-facing and aligned-edge cases. The support matrix documents what each structural edge means and its limits.

Other state-dependent families remain partial, including armored/reinforced liquid conduits, Duct Bridge, Direction Liquid Bridge, Unloader and Duct Unloader, Plastanium and Surge Conveyor, Bridge and Phase Conduit, and Mass Driver. Unit Cargo and payload logistics remain deferred. Unknown custom routing remains conservative.

## Languages

FactoryScope includes translations for English, Portuguese (Brazil), Russian, Simplified Chinese, Korean, and Spanish. Terminology for vanilla game concepts follows Mindustry v160.5 where applicable; no professional or native-speaker certification is claimed.

## Evidence model

Item and liquid routes are structural connectivity, not measured flow, throughput, or guaranteed delivery. Reachable producers do not prove sufficient supply or causality. FactoryScope does not rank root causes or recommend repairs.

## Validation and limitations

Targets Mindustry v8 Build 160 and was validated against v160.5. Desktop graphical acceptance was performed on Windows; launcher/path portability was checked on Windows, Linux, and macOS. The universal JAR's DEX was structurally validated, but Android runtime/touch was not validated. Remote multiplayer behavior was not independently validated.
