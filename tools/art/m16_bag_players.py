"""The inside of the Comically Large Bag, seen by a player carried in it (client/bag/BagPlayerClient).

A 16:9 full-screen overlay drawn like the vanilla pumpkin blur: dark burlap woven over and under in 4 px cells, lit
warm around the drawstring opening at the top (gathered into folds, the rope running round its rim and one slack
loop sagging across it), pin-prick light through the weave and a few frayed holes. Transparent where the world shows.
"""
import math

from texlib import c, lerp, new, rng, save, value_noise

W, H = 256, 144
CX, CY, RX, RY = 128.0, 50.0, 80.0, 37.0

FABRIC = [c(h) for h in ("#0E0804", "#160E07", "#1F150A", "#2A1C0E", "#382613", "#4A3318", "#5F431F", "#7A5729",
                          "#976D35", "#B48545")]
ROPE = [c(h) for h in ("#2E2412", "#4C3C1F", "#75603A", "#A48C5C", "#CDB582")]
LIGHT = c("#E8C890")
FIBER = c("#C9A266")

HOLES = [(30, 104, 5.0), (220, 98, 4.2), (72, 128, 3.0), (186, 126, 3.4), (238, 26, 2.6), (16, 44, 2.8), (150, 136, 2.2)]


def rim_radius(theta, wob):
    """The opening's edge in ellipse units: puckered by the drawstring gathers."""
    return 1.0 + 0.06 * math.sin(theta * 11.0 + 0.7) + 0.035 * math.sin(theta * 23.0 + 2.1) + 0.05 * (wob - 0.5)


def opening(x, y, wob):
    dx = (x + 0.5 - CX) / RX
    dy = (y + 0.5 - CY) / RY
    theta = math.atan2(dy, dx)
    return math.hypot(dx, dy) / rim_radius(theta, wob), theta


def fabric_pixel(x, y, light, noise):
    """One pixel of over-under burlap: threads in 4 px cells, a pin hole at every crossing."""
    cx, cy = x % 4, y % 4
    over_h = ((x // 4) + (y // 4)) % 2 == 0
    if cx == 3 and cy == 3:
        return None  # pin hole
    if over_h:
        shade = (0.55, 1.0, 0.7, 0.25)[cy]
        shade *= 0.92 + 0.16 * (((x * 7 + y * 3) % 5) / 4.0)
    else:
        shade = (0.55, 1.0, 0.7, 0.25)[cx]
        shade *= 0.92 + 0.16 * (((y * 7 + x * 3) % 5) / 4.0)
    v = light * shade * (0.8 + 0.4 * noise)
    return FABRIC[max(0, min(len(FABRIC) - 1, int(v * len(FABRIC))))]


def paint():
    r = rng("bag_inside")
    noise = value_noise(W, H, r, cell=16, octaves=3, tile=False)
    wobble = value_noise(64, 1, r, cell=8, octaves=2, tile=True)[0]
    img = new(W, H)
    px = img.load()
    for y in range(H):
        for x in range(W):
            d, theta = opening(x, y, wobble[int((math.atan2((y + 0.5 - CY) / RY, (x + 0.5 - CX) / RX) + math.pi) / (2 * math.pi) * 63.99)])
            if d < 1.0:
                continue  # the way out: the world shows through
            # light falls in through the opening and dies off into the bottom of the sack; corners are darkest
            out = d - 1.0
            light = 0.16 + 0.95 * math.exp(-out * 2.4)
            ex = (x + 0.5 - W / 2) / (W / 2)
            ey = (y + 0.5 - H / 2) / (H / 2)
            light *= 1.0 - 0.35 * min(1.0, (ex * ex + ey * ey) * 0.5)
            # gathers: the drawstring pulls the cloth into folds that run into the opening
            gather = math.sin(theta * 14.0 + noise[y][x] * 4.0)
            light *= 1.0 + 0.35 * gather * math.exp(-out * 1.3)
            col = fabric_pixel(x, y, min(1.0, light), noise[y][x])
            if col is None:
                # pin hole: light leaks through the weave near the opening; deeper down it is just a darker crossing
                leak = max(0.0, min(1.0, (light - 0.3) * 1.4)) * (0.6 + 0.8 * noise[y][x])
                leak = min(1.0, leak)
                if (x * 13 + y * 7) % 5 == 0:
                    leak *= 0.3  # the weave is never quite even
                tone = lerp(FABRIC[0], LIGHT, leak)
                px[x, y] = (tone[0], tone[1], tone[2], int(255 - 165 * leak * leak))
            else:
                px[x, y] = col

    # the drawstring round the rim, in and out of its eyelets
    for i in range(1400):
        t = i / 1400.0 * 2.0 * math.pi
        if math.sin(t * 9.0) < -0.45:
            continue  # threaded through the cloth here
        k = rim_radius(t, wobble[int(t / (2 * math.pi) * 63.99)]) * 1.05
        _rope_dot(px, CX + math.cos(t) * RX * k, CY + math.sin(t) * RY * k, t * 9.0, lit=0.9)

    # one slack loop of rope sagging across the bottom of the opening, dark against the light outside
    a0, a1 = math.radians(160), math.radians(25)
    p0 = (CX + math.cos(a0) * RX * 1.02, CY + math.sin(a0) * RY * 1.02)
    p1 = (CX + math.cos(a1) * RX * 1.02, CY + math.sin(a1) * RY * 1.02)
    for i in range(500):
        s = i / 499.0
        x = p0[0] + (p1[0] - p0[0]) * s
        y = p0[1] + (p1[1] - p0[1]) * s + math.sin(s * math.pi) * 22.0
        _rope_dot(px, x, y, s * 60.0, lit=0.45)

    # frayed holes: bright rims, a fibre or two still across
    for hx, hy, hr in HOLES:
        for y in range(int(hy - hr - 3), int(hy + hr + 4)):
            for x in range(int(hx - hr - 3), int(hx + hr + 4)):
                if not (0 <= x < W and 0 <= y < H):
                    continue
                ang = math.atan2(y - hy, x - hx)
                rr = hr * (1.0 + 0.25 * math.sin(ang * 5.0 + hx) + 0.12 * math.sin(ang * 9.0 + hy))
                dd = math.hypot(x + 0.5 - hx, y + 0.5 - hy)
                if dd < rr:
                    px[x, y] = (0, 0, 0, 0)
                elif dd < rr + 1.2:
                    px[x, y] = lerp(FABRIC[7], LIGHT, 0.35)
                elif dd < rr + 2.4:
                    px[x, y] = FABRIC[5]
        for _ in range(1):
            ang = r.random() * math.pi
            for s in range(-int(hr) - 1, int(hr) + 2):
                x = int(hx + math.cos(ang) * s)
                y = int(hy + math.sin(ang) * s + 0.6 * math.sin(s))
                if 0 <= x < W and 0 <= y < H and px[x, y][3] == 0:
                    px[x, y] = FIBER

    # stray fibres poking into the opening from its rim
    for _ in range(46):
        t = r.random() * 2.0 * math.pi
        k = rim_radius(t, wobble[int(t / (2 * math.pi) * 63.99)])
        x0, y0 = CX + math.cos(t) * RX * k, CY + math.sin(t) * RY * k
        length = 3 + r.random() * 7
        bend = (r.random() - 0.5) * 0.9
        for s in range(int(length)):
            u = s / length
            x = int(x0 - math.cos(t + bend * u) * s * 0.9)
            y = int(y0 - math.sin(t + bend * u) * s * 0.55)
            if 0 <= x < W and 0 <= y < H and px[x, y][3] == 0:
                px[x, y] = (FIBER[0], FIBER[1], FIBER[2], 230 - int(120 * u))
    save(img, "gui/bag_inside")


def _rope_dot(px, x, y, phase, lit):
    """A 3 px twisted-rope cross-section: diagonal strands, lit side up."""
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            if abs(ox) + abs(oy) > 1:
                continue
            ix, iy = int(x) + ox, int(y) + oy
            if not (0 <= ix < W and 0 <= iy < H):
                continue
            twist = 0.5 + 0.5 * math.sin(phase * 1.7 + (ix + iy) * 1.3)
            v = lit * (0.55 + 0.45 * twist) * (1.15 if oy < 0 else 0.85 if oy > 0 else 1.0)
            px[ix, iy] = ROPE[max(0, min(len(ROPE) - 1, int(v * len(ROPE))))]
