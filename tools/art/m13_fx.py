"""Revision 2 effect textures: particle sprites (textures/particle/) and render-effect masks (textures/effect/).

Particles are drawn smooth (soft alpha falloff) rather than as hard pixel art: most of them are rendered with
additive blending, so their alpha ramp is what makes them read as light.
"""
import math

from texlib import new, rng, save


def _soft(img, cx, cy, radius, color, power=2.0, core=0.0, core_color=None):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / radius
            if d >= 1.0:
                continue
            a = (1.0 - d) ** power
            r, g, b = color[:3]
            if core_color is not None and d < core:
                t = 1.0 - d / core
                r = int(r + (core_color[0] - r) * t)
                g = int(g + (core_color[1] - g) * t)
                b = int(b + (core_color[2] - b) * t)
            alpha = int(round(255 * a * color[3] / 255))
            old = px[x, y]
            if alpha > old[3]:
                px[x, y] = (r, g, b, alpha)


def light_mote():
    img = new(16, 16)
    _soft(img, 8, 8, 8, (255, 214, 140, 255), power=1.8, core=0.45, core_color=(255, 255, 240))
    save(img, "particle/light_mote")


def light_streak():
    """A falling drop of sunlight: a thin vertical streak, its head at the bottom bright, its tail fading upwards."""
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        along = (y + 0.5) / 16.0          # 0 at the top (tail) .. 1 at the bottom (head)
        length = along ** 1.6 * (1.0 - max(0.0, along - 0.88) / 0.12 * 0.6)
        for x in range(16):
            across = abs(x + 0.5 - 8.0) / 1.6
            a = math.exp(-across * across) * length
            if a > 0.02:
                hot = math.exp(-across * across * 3.0) * along
                px[x, y] = (255, int(232 + 23 * hot), int(176 + 72 * hot), int(255 * min(1.0, a)))
    save(img, "particle/light_streak")


def solar_glint():
    for frame, (arm, glow) in enumerate(((7.5, 5.5), (5.0, 4.0))):
        img = new(16, 16)
        px = img.load()
        _soft(img, 8, 8, glow, (255, 220, 120, 255), power=1.5, core=0.6, core_color=(255, 255, 235))
        for y in range(16):
            for x in range(16):
                dx, dy = abs(x + 0.5 - 8), abs(y + 0.5 - 8)
                ray = max(0.0, 1.0 - min(dx, dy) / 0.9) * max(0.0, 1.0 - max(dx, dy) / arm)
                a = int(255 * min(1.0, ray * 1.2))
                if a > px[x, y][3]:
                    px[x, y] = (255, 246, 210, a)
        save(img, f"particle/solar_glint_{frame}")


def crimson_spark():
    for frame, (radius, core) in enumerate(((4.5, 0.55), (3.0, 0.4))):
        img = new(16, 16)
        _soft(img, 8, 8, radius, (255, 60, 40, 255), power=1.4, core=core, core_color=(255, 235, 200))
        save(img, f"particle/crimson_spark_{frame}")


def shadow_mote():
    r = rng("shadow_mote")
    for frame, radius in enumerate((7.5, 6.5, 5.0, 3.5)):
        img = new(16, 16)
        px = img.load()
        for y in range(16):
            for x in range(16):
                n = 0.85 + 0.3 * r.random()
                d = math.hypot(x + 0.5 - 8, y + 0.5 - 8) / (radius * n)
                if d >= 1.0:
                    continue
                a = int(235 * (1.0 - d) ** 1.2)
                # deep violet-black with a faint crimson cast at the rim
                rim = max(0.0, d - 0.6) / 0.4
                px[x, y] = (int(18 + 60 * rim), int(8 + 4 * rim), int(26 + 10 * rim), a)
        save(img, f"particle/shadow_mote_{frame}")


# The melee slashes copy the vanilla sweep (8 opaque frames, 32x32): a wide flat arc above the centre that is drawn
# in from its right tip, sweeps across and is drawn out towards its left tip. Only the colours are ours.
SWEEP_WINDOWS = ((0.0, 0.12), (0.0, 0.45), (0.0, 0.75), (0.0, 1.0), (0.15, 1.0), (0.4, 1.0), (0.65, 1.0), (0.88, 1.0))


def _sweep_set(name, body, rim, under):
    cx, cy, rx, ry = 16.0, 18.5, 12.5, 8.5
    a0, a1 = 12.0, 168.0
    for frame, (t0, t1) in enumerate(SWEEP_WINDOWS):
        img = new(32, 32)
        px = img.load()
        thin = 1.0 if frame < 5 else (0.8 if frame == 5 else 0.6)
        for y in range(32):
            for x in range(32):
                fx, fy = (x + 0.5 - cx) / rx, (cy - (y + 0.5)) / ry
                if fy <= 0:
                    continue
                r = math.hypot(fx, fy)
                ang = math.degrees(math.atan2(fy, fx))
                if not (a0 <= ang <= a1):
                    continue
                t = (ang - a0) / (a1 - a0)          # 0 at the right tip, 1 at the left tip
                if not t0 <= t <= t1:
                    continue
                w = 0.52 * thin * max(0.18, math.sin(math.pi * t)) ** 0.7
                if r > 1.0 or r < 1.0 - w:
                    continue
                d = (1.0 - r) / w                   # 0 at the outer edge, 1 at the inner edge
                col = rim if d < 0.34 else (under if d > 0.8 else body)
                px[x, y] = (col[0], col[1], col[2], 255)
        save(img, f"particle/{name}_{frame}")


def umbral_slash():
    # Umbral Steel: the steel's own palette, a slate-blue blade with a lavender-violet edge
    _sweep_set("umbral_slash", (68, 75, 115), (169, 166, 220), (46, 51, 80))


def eclipse_slash():
    # the Eclipser keeps its black blade with a crimson edge
    _sweep_set("eclipse_slash", (10, 7, 14), (226, 32, 48), (120, 14, 28))


def umbral_shard():
    shapes = [
        [(7, 2), (11, 7), (8, 14), (4, 9)],
        [(4, 4), (12, 3), (10, 12)],
        [(8, 1), (13, 9), (6, 13), (3, 6)],
    ]
    for frame, poly in enumerate(shapes):
        img = new(16, 16)
        px = img.load()
        for y in range(16):
            for x in range(16):
                if _inside(poly, x + 0.5, y + 0.5):
                    edge = _edge_distance(poly, x + 0.5, y + 0.5)
                    px[x, y] = (169, 166, 220, 255) if edge < 1.0 else (57, 63, 97, 255)  # umbral lavender on navy
        save(img, f"particle/umbral_shard_{frame}")


def mist():
    r = rng("mist")
    for frame in range(3):
        img = new(32, 32)
        px = img.load()
        blobs = [(16 + r.uniform(-5, 5), 16 + r.uniform(-3, 3), r.uniform(10, 15)) for _ in range(5)]
        for y in range(32):
            for x in range(32):
                a = 0.0
                for bx, by, br in blobs:
                    d = math.hypot(x + 0.5 - bx, (y + 0.5 - by) * 1.4) / br
                    if d < 1.0:
                        a = max(a, (1.0 - d) ** 2)
                if a > 0:
                    px[x, y] = (196, 206, 218, int(170 * a))
        save(img, f"particle/mist_{frame}")


def _inside(poly, x, y):
    inside = False
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            inside = not inside
    return inside


def _edge_distance(poly, x, y):
    best = 99.0
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        dx, dy = x2 - x1, y2 - y1
        t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.hypot(x - (x1 + t * dx), y - (y1 + t * dy)))
    return best


def effect_masks():
    """Soft masks sampled by the effect render types (white RGB, alpha carries the shape)."""
    # radial glow: used for halos, flares and impact blooms
    img = new(32, 32)
    _soft(img, 16, 16, 16, (255, 255, 255, 255), power=2.2)
    save(img, "effect/glow")
    # beam cross-section: bright centre line fading to the edges (u across the beam, v along it)
    img = new(32, 32)
    px = img.load()
    for y in range(32):
        for x in range(32):
            d = abs(x + 0.5 - 16) / 16.0
            a = max(0.0, 1.0 - d) ** 1.6
            px[x, y] = (255, 255, 255, int(255 * a))
    save(img, "effect/beam")
    # ring: thin bright circle for shockwaves and target markers
    img = new(64, 64)
    px = img.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x + 0.5 - 32, y + 0.5 - 32)
            a = max(0.0, 1.0 - abs(d - 28) / 3.5)
            if a > 0:
                px[x, y] = (255, 255, 255, int(255 * a ** 1.5))
    save(img, "effect/ring")


def paint():
    light_mote()
    light_streak()
    solar_glint()
    crimson_spark()
    shadow_mote()
    umbral_slash()
    eclipse_slash()
    umbral_shard()
    mist()
    effect_masks()
    vignette()


def vignette():
    """Screen-edge vignette (ScreenFx): transparent centre, opaque corners; tinted at draw time."""
    img = new(64, 64)
    px = img.load()
    for y in range(64):
        for x in range(64):
            dx = (x + 0.5 - 32) / 32.0
            dy = (y + 0.5 - 32) / 32.0
            d = math.sqrt(dx * dx * 0.8 + dy * dy)
            a = max(0.0, min(1.0, (d - 0.45) / 0.75))
            px[x, y] = (255, 255, 255, int(255 * a ** 1.6))
    save(img, "effect/vignette")
