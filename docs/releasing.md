# Releasing FactoryScope

## How the game finds a release

Checked against the current sources rather than assumed, because getting it wrong strands players on a
version they cannot install.

**The Mod Browser index reads the default branch.** `Anuken/MindustryMods`
(`src/modupdater/ModUpdater.java`) searches for `mindustry-mod in:topics archived:false template:false`,
reads `default_branch` from the repository metadata, then fetches `mod.hjson` (or `mod.json`, from the
root or from `assets/`) **on that branch** and publishes its `version` field into `mods.json`. It skips
a repository only on `hideBrowser`; our `hidden: true` is the multiplayer flag and is not read here.

**The update prompt compares that indexed version.** `ModsDialog.refreshModUpdates` marks a mod as
updatable when `Strings.checkNewerSemver(entry.version, mod.meta.version)` - the browser listing against
what the player has installed. Nothing else is consulted.

**Installing a Java mod fetches the latest non-prerelease.** `ModsDialog.githubImportJavaMod` calls
`GET /repos/{repo}/releases/latest`, which GitHub defines as excluding drafts **and pre-releases**. From
the assets it takes the first whose name starts with `dexed` and ends in `.jar`, otherwise the first
`.jar` of any name - so `FactoryScope.jar` is found, and no rename is needed.

Three consequences, and the rule that follows from them:

- A version on the default branch with no matching release advertises an update nobody can install.
- A release marked **pre-release** is invisible to the installer. Publishing only a pre-release leaves
  the mod listed in the browser and impossible to install from it.
- Therefore: **the default branch may only ever name a version that has a published, non-prerelease
  release carrying a jar.** Caveats about a release belong in its notes, not in the pre-release flag.

## Branches

`main` is the released version and nothing else; `develop` carries the next one. Releasing means, in
order: the checks below pass on `develop`, the release is published from the artifact CI built for that
commit, and only then does `main` move to it - so the branch never names a version the release does not
yet provide.

## Before tagging

1. The documented clean test, artifact, acceptance, localization and benchmark tasks pass.
2. `gradlew acceptanceTest` passes against a real Mindustry client. Launcher portability is checked on
   Windows, Linux and macOS CI; the graphical real-client run is recorded only for platforms where it is
   actually exercised.
3. The isolated real-client smoke test passes; the current helper is `scripts/smoke-test.ps1` on Windows.
4. `mod.hjson` carries the new `version`; `minGameVersion` is the earliest build that provides every
   Mindustry API the mod uses. It need not repeat the patch version pinned in `build.gradle` when that
   API already exists earlier in the same major build line.
5. The README describes what the release actually does. Anything on the roadmap stays on the roadmap.
6. Translations are in step with the default bundle — `BundleTest` fails the build if they are not.

## The artifact

Release `build/libs/FactoryScope.jar`, produced by `gradlew deploy`. It contains the desktop classes and
the dexed Android classes.

Do not release `FactoryScopeDesktop.jar`. It is the same code without the dex, and Android will not load
it.

`deploy` needs an Android SDK. It prefers `ANDROID_HOME`; when that is unset it uses
`ANDROID_SDK_ROOT`. If both are set, `ANDROID_HOME` wins. The build selects the highest numerically
named installed build-tools directory containing the platform-appropriate `d8` executable, and the
highest installed Android platform containing `android.jar`. Malformed build-tools directory names are
ignored. D8 receives an explicit argument list, so SDK, project, dependency, and artifact paths may
contain spaces. `gradlew test` and `gradlew jar` do not require an Android SDK; only `jarAndroid` and
`deploy` do. CI builds the universal artifact, so release bytes should still come from the workflow run.

## Mod Browser

The [official Mindustry Mod Browser](https://github.com/Anuken/MindustryMods) indexes repositories that
carry the **`mindustry-mod`** GitHub topic. That topic has to be added on the repository settings page;
it cannot be set from the build. Without it the mod will not be listed no matter how the release is
tagged.

The indexer also expects a valid `mod.hjson` at the repository root, a README, and a release whose
attached jar is the mod itself.

For Build 160, the current indexer recognizes release titles tagged with `[v160]` and prefers a release
targeting the running game line. Use a title such as `FactoryScope X.Y.Z [v160]`; keep the Git tag as
`vX.Y.Z`. The mod metadata's minimum build is an independent compatibility floor, not the release title.

The indexer only downloads repository icons when the repository has at least two stars. It looks for
`icon.png` at the root or under `assets/` and scales it to 64 by 64. FactoryScope currently has fewer
than two stars, so an icon file would not appear in the browser yet.
