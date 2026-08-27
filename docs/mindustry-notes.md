# Mindustry v159.7 notes

Findings from reading the v159.7 sources that FactoryScope depends on. They are recorded here because
none of them is obvious from the API surface, and getting any of them wrong produces a diagnosis that
looks plausible and is wrong.

## How efficiency is actually decided

`Building.updateConsumption()` runs in this order:

1. **No consumers, or `cheating()`** — everything short-circuits:
   `potentialEfficiency = enabled && productionValid() ? 1 : 0`, and
   `efficiency = shouldConsume() ? potentialEfficiency : 0`. No consumer is evaluated at all.
2. **`!enabled`** — `efficiency`, `potentialEfficiency` and `optionalEfficiency` are all forced to zero
   before anything else happens. A disabled building therefore carries no information about its inputs;
   FactoryScope reports "disabled" and keeps any input findings as secondary.
3. **`update = shouldConsume() && productionValid()`** is computed, then `efficiency` is set to the
   *minimum* of `Consume.efficiency(build)` over `block.nonOptionalConsumers`. Optional consumers only
   feed `optionalEfficiency`.
4. `potentialEfficiency = efficiency` is assigned **here**, before the multiplier below.
5. If `update` is false, `efficiency` is forced to zero — even when every input is present.
6. `updateEfficiencyMultiplier()` multiplies `efficiency` and `optionalEfficiency` (but **not**
   `potentialEfficiency`) by `efficiencyScale()`.

Two consequences the mod relies on:

- The limiting input is the one with the lowest satisfaction, because efficiency is a minimum. Anything
  above that minimum is not responsible and must not be blamed.
- `efficiency / potentialEfficiency` isolates `efficiencyScale()` exactly, but only while the gate at
  step 5 is open. Otherwise the ratio is zero and meaningless.

## `cheating()` is not the sandbox gamemode

`TeamComp.cheating()` returns `team.rules().cheat` — the per-team rule described as "blocks don't
require power or resources". The **sandbox gamemode sets `rules.infiniteResources`, which is a different
flag**: it only makes construction free, and crafters in a sandbox game still need their materials.

Only the team `cheat` rule short-circuits `updateConsumption`, so that is the only case where
FactoryScope suppresses input blame.

## `enabled` is not always the player

`Building.checkAllowUpdate()` sets `enabled = false` whenever `allowUpdate()` is false, which happens when
the building belongs to `Team.derelict`, when the block does not support the current environment, or when
it stands outside a limited map area with `disableOutsideArea` on. The last case is not exotic: the
campaign map Ground Zero limits its playable area, and anything built beyond it is switched off by the
game the moment it appears.

Reporting that as "a player or a logic processor turned this off" sends the player looking for a switch
that does not exist, so FactoryScope reads `allowUpdate()` alongside `enabled` and reports the two
separately.

## `shouldConsume()` and blocked output

`Building.shouldConsume()` returns `enabled` by default, which tells you nothing. `GenericCrafterBuild`
overrides it, and its version is what makes "output blocked" provable:

```java
if(outputItems != null){
    for(var output : outputItems){
        if(items.get(output.item) + output.amount > itemCapacity) return false;
    }
}
if(outputLiquids != null && !ignoreLiquidFullness){
    // with dumpExtraLiquid, only *all* outputs being full stops the crafter
    // without it, a single full output is enough
}
return enabled;
```

So for a `GenericCrafter`, `enabled && !shouldConsume()` means the output buffer cannot take another
cycle, and nothing else. For any other block type the same expression only means "the block declined to
consume", with a block-specific reason FactoryScope has not verified — it says so instead of guessing.

## Units

Mindustry counts in ticks. One second of game time is 60 ticks, and `Time.delta` is the length of the
current frame *in ticks* (1 at 60 FPS, 2 at 30 FPS).

| Value | Stored as | Per second |
| --- | --- | --- |
| `GenericCrafter.craftTime` | ticks per cycle | `60 / craftTime` cycles |
| `LiquidStack.amount` (input and output) | units per tick | `amount * 60` |
| `ConsumePower.usage` | power per tick | `usage * 60` |
| `ItemStack.amount` (crafter output) | items per cycle | `amount * cyclesPerSecond` |

`Building.delta()` is `Time.delta * timeScale`, so an overdriven block consumes and produces
proportionally more. `edelta()` is `efficiency * delta()`.

## Why the liquid formula is reimplemented instead of called

`ConsumeLiquid.efficiency(build)` divides the stored amount by `amount * build.edelta()`, and `edelta()`
contains the building's **current** efficiency. During `updateConsumption` that is harmless, because the
game pins `efficiency = 1` before evaluating consumers. Calling the same method from the interface is
not harmless: once a factory has stopped for any reason its efficiency is zero, `edelta()` is zero, and
every liquid consumer reports zero — which would make FactoryScope blame the water supply of a factory
that is really out of sand.

FactoryScope therefore reproduces the formula with efficiency pinned to 1, exactly as the update loop
does. `MindustryFactoryProbeTest` asserts the derived value against `Building.efficiency` on a real
cryofluid mixer to keep the two in step.

`efficiencyScale()` is part of the game's divisor and is included, except when it is zero: a heat-starved
crafter is a block condition, not a liquid shortage, and is reported as one.

## Production rates come from `getProgressIncrease`

`GenericCrafterBuild.updateTile()` advances `progress` by `getProgressIncrease(craftTime)` each frame and
crafts at 1. Subclasses override that method — `AttributeCrafter` multiplies by its terrain boost, and the
base crafter throttles it when a liquid output is nearly full. Reading the current rate from the same call
therefore picks up every subclass behaviour for free, instead of re-deriving a formula per block type.

Liquid output uses `outputAmount * getProgressIncrease(1f)` per frame, which is why liquid stack amounts
are per-tick figures.

## Consumers that are excluded from the calculation

`Consume.ignore()` removes a consumer from `nonOptionalConsumers`, `optionalConsumers` and
`updateConsumers` alike. `ConsumePower` returns true when it is `buffered`, so a buffered power consumer
can never limit efficiency. FactoryScope treats any `ignore()` consumer as non-mandatory for the same
reason.

## Where the game keeps its data

`Vars.loadSettings()` calls `settings.setDataDirectory(Core.files.local("saves/"))` when
`Version.isSteam`, so a Steam installation stores settings, mods and `last_log.txt` in a `saves` folder
next to the executable rather than in `%APPDATA%\Mindustry`. `scripts/smoke-test.ps1` uses this: it
launches the game with its working directory set to a temporary folder, which gives the test its own
data directory without touching the player's.

Non-Steam builds honour the `MINDUSTRY_DATA_DIR` environment variable and the `mindustry.data.dir`
system property (`ClientLauncher.setup`).
