# Releasing FactoryScope

## Branches

`main` is the released version and nothing else. Mindustry's mod updater reads the `version` in
`mod.hjson` on the repository's default branch, so an unreleased version sitting there advertises an
update that does not exist. Feature work happens on `develop`, which may carry the next version number;
it reaches `main` only as part of releasing it.

Releasing means, in order: the checks below pass on `develop`, `develop` is merged into `main`, the tag
is cut from `main`, and the release is published from the artifact CI built for that commit.

## Before tagging

1. `gradlew clean test jar` passes.
2. `gradlew acceptanceTest` passes against a real Mindustry install. CI cannot run it — it needs a
   graphical client — so it is on whoever cuts the release.
3. `scripts/smoke-test.ps1` passes against a real Mindustry install.
4. `mod.hjson` carries the new `version`, and `minGameVersion` still matches the Mindustry release the
   build is pinned to in `build.gradle`.
5. The README describes what the release actually does. Anything on the roadmap stays on the roadmap.
6. Translations are in step with the default bundle — `BundleTest` fails the build if they are not.

## The artifact

Release `build/libs/FactoryScope.jar`, produced by `gradlew deploy`. It contains the desktop classes and
the dexed Android classes.

Do not release `FactoryScopeDesktop.jar`. It is the same code without the dex, and Android will not load
it.

`deploy` needs `ANDROID_HOME` and `d8` from the Android SDK build tools. CI has both, so the release
artifact is normally taken from the workflow run rather than built locally.

## Mod Browser

The [official Mindustry Mod Browser](https://github.com/Anuken/MindustryMods) indexes repositories that
carry the **`mindustry-mod`** GitHub topic. That topic has to be added on the repository settings page;
it cannot be set from the build. Without it the mod will not be listed no matter how the release is
tagged.

The indexer also expects a valid `mod.hjson` at the repository root, a README, and a release whose
attached jar is the mod itself.
