# FactoryScope visual identity

The project mark is the root-level [`icon.png`](../icon.png). Its graphite plate, amber inspection
reticle, and three-node route are intended to read as an analysis tool, not a new game faction or
content pack. The 256-pixel square release asset was reviewed at 64 pixels, the size used by the
MindustryMods indexer. [`icon-source.png`](branding/icon-source.png) is the high-resolution raster
master for future derivatives.

The raster was created from a custom logo brief with the built-in image-generation tool and then
selected for its crisp silhouette and restrained palette. It contains no baked-in text, copied game
art, or third-party logo. There is no editable vector master; use the committed high-resolution PNG
as the visual reference for future revisions and keep any derivative focused on the same geometry
and palette.

The separate Mod Browser cache and its `hasIcon` gate are documented in
[`mod-browser-icon.md`](mod-browser-icon.md).

The README captures in [`media/`](media/) show real FactoryScope UI in Mindustry v160.5. They are
kept separate from the icon so screenshots are not mistaken for mod-package artwork.

## Steam Workshop preview

[`workshop-preview.png`](branding/workshop-preview.png) is the 1024-pixel square image uploaded as the
Workshop item's `preview.png` (see [steam-workshop.md](steam-workshop.md)). Unlike the icon and the README
captures, it is a composite: the icon master, the title set in Mindustry's own UI font, and a darkened,
blurred crop of the real [`network-overlay.png`](media/network-overlay.png) capture as background. It is
regenerated with:

```text
python tools/branding/workshop-preview.py docs/branding/icon-source.png docs/media/network-overlay.png <font.woff> docs/branding/workshop-preview.png
```

`<font.woff>` is `fonts/font.woff` extracted from the official Mindustry desktop jar; it is not committed.
Steam requires the preview to stay under 1 MB.
