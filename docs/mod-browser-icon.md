# Mindustry mod icon behavior

FactoryScope now ships one root-level `icon.png`. Mindustry v160.5 loads a mod icon from that
filename (with root-level `preview.png` as its fallback), so the same file is used by the game and
is included at the root of the desktop and universal JARs.

The official [MindustryMods indexer](https://github.com/Anuken/MindustryMods) is a separate system.
Its current updater source checks for either `<repository>/icon.png` or
`<repository>/assets/icon.png`, scales a fetched image to 64 by 64 pixels, and only attempts the
fetch when the GitHub repository has at least two stars. The generated `hasIcon` field reflects
whether that cached icon file exists. See
[`ModUpdater.java`](https://github.com/Anuken/MindustryMods/blob/master/src/modupdater/ModUpdater.java#L179-L187)
and [the field construction](https://github.com/Anuken/MindustryMods/blob/master/src/modupdater/ModUpdater.java#L245-L261).

Before this presentation update, FactoryScope had no icon at either accepted repository path and had
zero stars. This branch fixes the icon-path omission; the updater can read that asset after it reaches
the default branch. As observed on 2026-10-04, the repository still had zero stars and the public
index entry still reported `hasIcon: false`; the updater's two-star gate is therefore the remaining
external condition. No index-side icon can be forced from this repository while that gate is unmet.
If the threshold is reached, the indexer must run again before its cached `hasIcon` state changes.

The same index snapshot still listed FactoryScope as `0.7.0`, although the public `1.0.0` release had
already been published. MindustryMods documents a periodic refresh, so this version discrepancy is
consistent with propagation delay; it is distinct from the icon gate. The current entry can be
checked in [`mods.json`](https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json).

These assets serve different purposes:

- **Root `icon.png`:** in-game mod icon and an accepted source path for the indexer.
- **`docs/media/`:** README and repository screenshots; these are not the in-game icon.
- **MindustryMods `hasIcon`:** an indexer-generated flag for its own cached 64-pixel icon, subject to
  its star gate and refresh schedule.
- **Steam Workshop preview:** a separate upload asset; FactoryScope has no Steam Workshop release.

The icon uses a project-owned generated raster mark and no Mindustry game artwork. See
[`branding.md`](branding.md) for its design/source note.
