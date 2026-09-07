#!/usr/bin/env python3
"""Draws the app icon.

The Android build ships a vector mic on a navy field (`ic_launcher_foreground`
over `#11203A`); this renders the same thing at 1024x1024 as the single PNG
Xcode wants, without pulling in an image library.
"""
import math
import struct
import zlib
from pathlib import Path

SIZE = 1024
BACKGROUND = (0x11, 0x20, 0x3A)
FOREGROUND = (0xFF, 0xFF, 0xFF)
ACCENT = (0x2E, 0x6B, 0xE6)


def coverage(x, y, shape, samples=4):
    """Box-samples `shape` so the glyph edges are not staircases."""
    hits = 0
    step = 1.0 / samples
    for sy in range(samples):
        for sx in range(samples):
            if shape(x + (sx + 0.5) * step, y + (sy + 0.5) * step):
                hits += 1
    return hits / (samples * samples)


def mic_shape(px, py):
    """A microphone: capsule head, open arc, stem and base — in a 24x24 box."""
    # Map pixels into the 24x24 viewport the Android vector uses, centred.
    u = (px - SIZE / 2) / (SIZE * 0.42) * 12 + 12
    v = (py - SIZE / 2) / (SIZE * 0.42) * 12 + 12

    # Capsule head: x in 9..15, y in 2..14, radius 3.
    if 9 <= u <= 15:
        if 5 <= v <= 11:
            return True
        if v < 5 and (u - 12) ** 2 + (v - 5) ** 2 <= 9:
            return True
        if v > 11 and (u - 12) ** 2 + (v - 11) ** 2 <= 9:
            return True

    # Open arc under it: outer radius 7, inner 5.5, lower half only.
    d = math.hypot(u - 12, v - 11)
    if v >= 11 and 5.5 <= d <= 7:
        return True

    # Stem and base.
    if 11.1 <= u <= 12.9 and 18 <= v <= 21:
        return True
    if 8.5 <= u <= 15.5 and 21 <= v <= 22.6:
        return True

    return False


def render():
    rows = []
    for y in range(SIZE):
        row = bytearray()
        for x in range(SIZE):
            alpha = coverage(x, y, mic_shape)
            if alpha <= 0:
                # A soft vertical wash keeps the flat navy from looking dead.
                t = y / SIZE * 0.18
                pixel = tuple(
                    int(BACKGROUND[i] + (ACCENT[i] - BACKGROUND[i]) * t) for i in range(3)
                )
            else:
                pixel = tuple(
                    int(BACKGROUND[i] * (1 - alpha) + FOREGROUND[i] * alpha) for i in range(3)
                )
            row += bytes(pixel)
        rows.append(bytes(row))
    return rows


def write_png(path, rows):
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)  # 8-bit RGB
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    Path(path).write_bytes(png)


if __name__ == "__main__":
    target = Path(__file__).resolve().parents[1] / "App/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    write_png(target, render())
    print(f"wrote {target} ({target.stat().st_size} bytes)")
