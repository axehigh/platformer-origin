#!/usr/bin/env python3
"""
Pure-Python (no Pillow/ImageMagick required) helper for writing tiny flat-color
pixel-art PNGs, and for decoding a PNG back into an ASCII preview so you can
sanity-check the result before dropping it into assets/gfx/.

Why this exists: this sandbox has no PIL/Pillow and no ImageMagick installed,
so game icon placeholders are built by hand-encoding raw RGBA rows straight
into a minimal PNG (IHDR/IDAT/IEND chunks, zlib-compressed, filter type 0 per
row). This is the exact approach used to build assets/gfx/exit_gate.png.

Usage (see SKILL.md for the full workflow):

    from generate_png import write_png, preview_png, T

    W = (90, 90, 100, 255)   # example: stone frame color
    D = (50, 50, 60, 255)    # example: dark interior color

    rows = [
        [T]*8,
        [T, W, W, W, W, W, W, T],
        [T, W, D, D, D, D, W, T],
        [T, W, D, D, D, D, W, T],
        [T, W, D, D, D, D, W, T],
        [T, W, D, D, D, D, W, T],
        [T, W, W, W, W, W, W, T],
        [T]*8,
    ]
    write_png("my_icon.png", rows)
    preview_png("my_icon.png")
"""
import struct
import sys
import zlib

# Fully-transparent RGBA — use this for the border/background of every icon
# (matches the transparent 1px/edge border already used by chest.png,
# torch.png, dagger.png, exit_gate.png, etc.)
T = (0, 0, 0, 0)


def write_png(path, rows):
    """
    Writes `rows` (a list of H rows, each a list of W (r, g, b, a) 0-255 tuples)
    to `path` as an 8-bit RGBA PNG. All rows must have equal length (the icon's
    pixel width); len(rows) is the icon's pixel height.
    """
    height = len(rows)
    width = len(rows[0])
    for row in rows:
        assert len(row) == width, "all rows must have the same width"

    raw = bytearray()
    for row in rows:
        raw.append(0)  # filter type 0 (None) for every scanline
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    idat = zlib.compress(bytes(raw), 9)

    def chunk(ctype, data):
        out = struct.pack(">I", len(data)) + ctype + data
        crc = zlib.crc32(ctype + data) & 0xffffffff
        return out + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", idat)
        + chunk(b"IEND", b"")
    )

    with open(path, "wb") as f:
        f.write(png)


def read_png(path):
    """Decodes an 8-bit RGBA (color type 6) PNG back into (width, height, rows)."""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    width = height = bit_depth = color_type = None
    idat = b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8].decode("ascii")
        chunk_data = data[pos + 8:pos + 8 + length]
        if ctype == "IHDR":
            width, height, bit_depth, color_type = struct.unpack(">IIBB", chunk_data[:10])
        elif ctype == "IDAT":
            idat += chunk_data
        pos += 8 + length + 4
    assert bit_depth == 8 and color_type == 6, "only 8-bit RGBA PNGs are supported"

    raw = zlib.decompress(idat)
    bpp = 4
    stride = width * bpp
    idx = 0
    prev = bytearray(stride)
    rows = []

    def paeth(a, b, c):
        p = a + b - c
        pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        elif pb <= pc:
            return b
        return c

    for _ in range(height):
        filt = raw[idx]
        idx += 1
        row = bytearray(raw[idx:idx + stride])
        idx += stride
        out = bytearray(stride)
        if filt == 0:
            out = row
        else:
            for x in range(stride):
                a = out[x - bpp] if x >= bpp else 0
                b = prev[x]
                c = prev[x - bpp] if x >= bpp else 0
                if filt == 1:
                    out[x] = (row[x] + a) & 0xff
                elif filt == 2:
                    out[x] = (row[x] + b) & 0xff
                elif filt == 3:
                    out[x] = (row[x] + (a + b) // 2) & 0xff
                elif filt == 4:
                    out[x] = (row[x] + paeth(a, b, c)) & 0xff
                else:
                    raise ValueError("unsupported filter type " + str(filt))
        rows.append(out)
        prev = out

    pixel_rows = []
    for row in rows:
        pixel_row = []
        for x in range(width):
            o = x * 4
            pixel_row.append(tuple(row[o:o + 4]))
        pixel_rows.append(pixel_row)

    return width, height, pixel_rows


def preview_png(path):
    """Prints an ASCII-art preview of `path` plus its color legend, for a quick sanity check."""
    width, height, pixel_rows = read_png(path)
    chars = ".#*+@%oOX0123456789"
    palette = {}
    next_char = 0
    lines = []
    for row in pixel_rows:
        line = ""
        for color in row:
            if color not in palette:
                palette[color] = chars[next_char % len(chars)]
                next_char += 1
            line += palette[color]
        lines.append(line)

    print(f"{path}: {width}x{height}, {len(palette)} colors")
    for line in lines:
        print(line)
    print("legend:", {v: k for k, v in palette.items()})


if __name__ == "__main__":
    if len(sys.argv) == 2:
        preview_png(sys.argv[1])
    else:
        print("Usage: python3 generate_png.py <path-to-existing.png>  (prints an ASCII preview)")
        print("For writing a new PNG, import write_png(path, rows) from this module instead.")
