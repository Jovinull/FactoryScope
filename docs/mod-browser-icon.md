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

The official [`mods.json`](https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json)
currently records FactoryScope with **5 stars** and **`hasIcon: true`** (checked 2026-10-18). The
two-star threshold is met, and the indexer's cached icon is present. The index can still lag behind
repository changes until its next refresh.

These assets serve different purposes:

- **Root `icon.png`:** in-game mod icon and an accepted source path for the indexer.
- **`docs/media/`:** README and repository screenshots; these are not the in-game icon.
- **MindustryMods `hasIcon`:** an indexer-generated flag for its own cached 64-pixel icon. FactoryScope's
  current official entry has `hasIcon: true`.
- **Steam Workshop preview:** a separate upload asset, [`branding/workshop-preview.png`](branding/workshop-preview.png),
  shipped as `preview.png` only in the Workshop folder; see [steam-workshop.md](steam-workshop.md).

The icon uses a project-owned generated raster mark and no Mindustry game artwork. See
[`branding.md`](branding.md) for its design/source note.
