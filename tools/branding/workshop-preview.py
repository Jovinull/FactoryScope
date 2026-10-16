"""Builds the 1024x1024 Steam Workshop preview for FactoryScope.

Usage: python -I workshop_preview.py <icon-source.png> <network-overlay.png> <font.woff> <out.png>

The background is a crop of a real, unedited FactoryScope capture (the structural item-route overlay in
Mindustry), darkened and softened so it reads as texture behind the mark and the title.
"""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

SIZE = 1024
ACCENT = (255, 211, 127)   # Mindustry Pal.accent
WHITE = (255, 255, 255)
MUTED = (176, 186, 197)


def plate_mask(icon):
    """Alpha mask that keeps the chamfered plate and drops the square corners around it."""
    w, h = icon.size
    rgb = icon.convert("RGB")
    bg = rgb.getpixel((3, 3))

    def differs(p):
        return sum(abs(a - b) for a, b in zip(p, bg)) > 40

    mid = h // 2
    left = next(x for x in range(w) if differs(rgb.getpixel((x, mid))))
    right = next(x for x in range(w - 1, -1, -1) if differs(rgb.getpixel((x, mid))))
    top = next(y for y in range(h) if differs(rgb.getpixel((w // 2, y))))
    bottom = next(y for y in range(h - 1, -1, -1) if differs(rgb.getpixel((w // 2, y))))
    # the chamfer: first border pixel along the top edge, measured from the left
    chamfer = next(x for x in range(w) if differs(rgb.getpixel((x, top + 2)))) - left

    scale = 4
    big = Image.new("L", (w * scale, h * scale), 0)
    l, r, t, b, c = (v * scale for v in (left, right + 1, top, bottom + 1, chamfer))
    ImageDraw.Draw(big).polygon(
        [(l + c, t), (r - c, t), (r, t + c), (r, b - c), (r - c, b), (l + c, b), (l, b - c), (l, t + c)], fill=255)
    return big.resize((w, h), Image.LANCZOS)


def background(capture):
    # the conveyor carrying the amber route overlay lands in a band below the subtitle
    crop = capture.convert("RGB").crop((830, 152, 1369, 691))
    bg = crop.resize((SIZE, SIZE), Image.NEAREST).filter(ImageFilter.GaussianBlur(4.5))

    # dark behind the mark and title, lifted over the conveyor band, dark again under the footer
    def level(t):
        if t < 0.76:
            return 0.17
        if t < 0.86:
            return 0.17 + 0.43 * (t - 0.76) / 0.10
        if t < 0.90:
            return 0.60
        return 0.60 - 0.30 * (t - 0.90) / 0.10

    shade = Image.new("L", (SIZE, SIZE))
    px = shade.load()
    for y in range(SIZE):
        value = int(255 * level(y / (SIZE - 1)))
        for x in range(SIZE):
            px[x, y] = value
    bg = ImageChops.multiply(bg, Image.merge("RGB", (shade, shade, shade)))

    # soft vignette
    vignette = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(vignette).ellipse((-180, -120, SIZE + 180, SIZE + 260), fill=255)
    vignette = vignette.filter(ImageFilter.GaussianBlur(140))
    return Image.composite(bg, Image.new("RGB", (SIZE, SIZE), (8, 9, 11)), vignette)


def centered_text(draw, y, text, font, fill, spacing=0, shadow=True):
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + spacing * (len(text) - 1)
    x = (SIZE - total) / 2
    for ch, w in zip(text, widths):
        if shadow:
            draw.text((x + 3, y + 4), ch, font=font, fill=(0, 0, 0))
        draw.text((x, y), ch, font=font, fill=fill)
        x += w + spacing
    return total


def main(icon_path, capture_path, font_path, out_path):
    canvas = background(Image.open(capture_path)).convert("RGBA")

    icon = Image.open(icon_path).convert("RGB")
    mask = plate_mask(icon)
    mark_size = 420
    mark = icon.resize((mark_size, mark_size), Image.LANCZOS)
    mark_mask = mask.resize((mark_size, mark_size), Image.LANCZOS)
    mx, my = (SIZE - mark_size) // 2, 92

    shadow = Image.new("L", (SIZE, SIZE), 0)
    shadow.paste(mark_mask, (mx, my + 16))
    shadow = shadow.filter(ImageFilter.GaussianBlur(22))
    canvas = Image.composite(Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255)), canvas,
                             shadow.point(lambda v: int(v * 0.75)))
    canvas.paste(mark, (mx, my), mark_mask)

    draw = ImageDraw.Draw(canvas)
    size = 120
    while draw.textlength("FACTORYSCOPE", font=ImageFont.truetype(font_path, size)) + 4 * 11 > 860:
        size -= 2
    title = ImageFont.truetype(font_path, size)
    subtitle = ImageFont.truetype(font_path, 60)
    small = ImageFont.truetype(font_path, 32)

    width = centered_text(draw, 556, "FACTORYSCOPE", title, WHITE, spacing=4)
    rule_y = 556 + size + 24
    draw.rectangle(((SIZE - width * 0.55) / 2, rule_y, (SIZE + width * 0.55) / 2, rule_y + 4), fill=ACCENT)
    centered_text(draw, rule_y + 24, "Factory Diagnostics", subtitle, ACCENT, spacing=2)
    centered_text(draw, 958, "MINDUSTRY V8", small, MUTED, spacing=8)

    canvas.convert("RGB").save(out_path, optimize=True)


if __name__ == "__main__":
    main(*sys.argv[1:5])
