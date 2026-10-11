"""Pinkes Ketten-Totem (32x32 Pixel-Art): dunkler Violett-Kristall-Wächter mit glühenden Hörnern,
Kamm, schrägen Neon-Augen, glühendem Mund, Silber-Stacheln auf den Schultern, Herz-Kristall mit Adern,
Neon-Gürtel und zerrissenen Silberketten an den Fäusten.
Formen werden mit Licht von oben links schattiert und auf eine feste Palette gerastert.
Aufruf: gen_totem.py OUT_PNG [PREVIEW_PNG]"""
import sys
import numpy as np
from PIL import Image

N = 32
OUT = sys.argv[1]


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], float) / 255


K = hexc("#0e0214")                      # Umriss
RAMP = [hexc(c) for c in ("#1d0627", "#340f48", "#541b6e", "#7a2b9a", "#a54ac6", "#d687ee")]  # Violett, dunkel -> Glanz
R = hexc("#a0127a")                      # Glüh-Rand
P = hexc("#ff2fbf")                      # Neon-Pink
H = hexc("#ff92e2")                      # helles Pink
W = hexc("#fff1fb")                      # weißglühend
GLOW_RING = hexc("#55093f")              # Umriss um Glühteile
SQ = hexc("#4a2b5c")                     # Silber dunkel
SG = hexc("#9677ac")                     # Silber mittel
SS = hexc("#dcc9ea")                     # Silber hell
SW = hexc("#ffffff")

LIGHT = np.array([-0.45, -0.6, 0.66])
LIGHT /= np.linalg.norm(LIGHT)

col = np.zeros((N, N, 3))
filled = np.zeros((N, N), bool)
glow = np.zeros((N, N), bool)
layer = np.zeros((N, N), int)

ys, xs = np.mgrid[0:N, 0:N] + 0.5


def mirror(m):
    return m | m[:, ::-1]


def ellipse(cx, cy, rx, ry):
    return ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1.0


def rect(x0, y0, x1, y1):
    """x0..x1, y0..y1 inklusive (Pixel-Koordinaten)."""
    return (xs >= x0) & (xs < x1 + 1) & (ys >= y0) & (ys < y1 + 1)


def poly(pts):
    pts = np.array(pts, float)
    inside = np.zeros((N, N), bool)
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        inside ^= ((yi > ys) != (yj > ys)) & (xs < (xj - xi) * (ys - yi) / (yj - yi + 1e-9) + xi)
        j = i
    return inside


def pix(lst):
    m = np.zeros((N, N), bool)
    for x, y in lst:
        if 0 <= x < N and 0 <= y < N:
            m[y, x] = True
    return m


def shade(mask, lid, cx, cy, rx, ry, vy=1.0, bias=0.0, ramp=RAMP, top=5):
    """Form mit Licht von oben links schattieren (Ellipsoid-Normale), auf die Palette gerastert."""
    yy, xx = np.where(mask)
    nx = (xx + 0.5 - cx) / rx
    ny = (yy + 0.5 - cy) / ry * vy
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0.08, 1))
    n = np.stack([nx, ny, nz], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    lum = np.clip(n @ LIGHT, 0, 1) * 0.72 + 0.3 + bias
    idx = np.clip(np.round(lum * (top + 0.4) - 0.7), 0, top).astype(int)
    col[yy, xx] = np.array(ramp)[idx]
    filled[yy, xx] = True
    glow[yy, xx] = False
    layer[yy, xx] = lid


def put(mask, c, is_glow=True, lid=None):
    col[mask] = c
    filled[mask] = True
    glow[mask] = is_glow
    if lid is not None:
        layer[mask] = lid


def blend(mask, c, t):
    m = mask & filled & ~glow
    col[m] = col[m] * (1 - t) + c * t


def sym(lst):
    """Pixel links + gespiegelt rechts."""
    return lst + [(N - 1 - x, y) for x, y in lst]


# ---------- Ketten (hinten) ----------
def chain(cx, flip):
    """Glieder von der Faust abwärts; Glanz immer links. flip: Bruchkante außen."""
    o = -1 if not flip else 1   # Außenseite
    parts = [
        ((cx, 20), SQ),
        ((cx, 21), SG), ((cx - 1, 22), SS), ((cx + 1, 22), SQ), ((cx - 1, 23), SG), ((cx + 1, 23), SQ), ((cx, 24), SQ),
        ((cx, 25), SS), ((cx, 26), SG),
        ((cx, 27), SG), ((cx - 1, 28), SS), ((cx + 1, 28), SQ),
    ]
    for (x, y), c in parts:
        put(pix([(x, y)]), c, False, 1)
    # Gebrochenes Glied: die Außenseite reißt ab und glüht
    put(pix([(cx - o, 29)]), SG if o > 0 else SS, False, 1)
    put(pix([(cx + o, 29)]), R, True, 1)
    put(pix([(cx + o, 30)]), P, True, 1)
    put(pix([(cx + o, 31)]), H, True, 1)


chain(2, False)
chain(N - 3, True)

# ---------- Arme & Fäuste ----------
arms = mirror(rect(3, 16, 10, 18))
shade(arms, 2, 16, 17, 30, 2.2, vy=1.0)
fist_l = ellipse(2.2, 17.6, 2.3, 2.9)
shade(fist_l, 3, 2.2, 17.4, 2.6, 3.0)
shade(fist_l[:, ::-1], 3, N - 2.2, 17.4, 2.6, 3.0)
for x, y in sym([(3, 16), (3, 18)]):             # Finger-Fugen
    col[y, x] = RAMP[0]

# ---------- Körper ----------
torso = poly([(10.6, 14), (21.4, 14), (20.4, 23.2), (11.6, 23.2)])
shade(torso, 4, 16, 17, 7.0, 10, vy=0.6)
robe = poly([(11.6, 25), (20.4, 25), (21.2, 31), (10.8, 31)])
shade(robe, 5, 16, 26, 6.0, 10, vy=0.4, bias=-0.12)
for x in (13, 18):                                # Falten
    blend(rect(x, 26, x, 30), RAMP[0], 0.55)
belt = rect(11, 23, 20, 24)
shade(belt, 6, 16, 23.5, 7, 3, bias=-0.1)

# ---------- Schulterpanzer + Silber-Stacheln ----------
pauld = mirror(ellipse(8.3, 15.4, 3.5, 2.6))
shade(pauld, 7, 8.0, 15.0, 3.8, 3.0)
pauldR = pauld & (xs > 16)
shade(pauldR, 7, N - 8.3, 15.0, 3.8, 3.0)
spike_l = [((6, 13), SG), ((7, 13), SQ), ((8, 13), SQ), ((6, 12), SS), ((7, 12), SG), ((5, 11), SS), ((6, 11), SQ),
           ((5, 10), SS), ((5, 9), SW), ((4, 8), SW),
           ((10, 13), SG), ((10, 12), SS), ((11, 11), SW)]
for (x, y), c in spike_l:
    put(pix([(x, y)]), c, False, 8)
    xr = N - 1 - x                                 # rechts: Glanz bleibt links -> dunkler
    cr = {tuple(SW): SS, tuple(SS): SG, tuple(SG): SQ, tuple(SQ): SQ}[tuple(c)]
    put(pix([(xr, y)]), cr, False, 8)

# ---------- Hals & Kopf ----------
neck = rect(13, 13, 18, 14)
shade(neck, 9, 16, 13.5, 4, 3, bias=-0.25)
head = rect(10, 5, 21, 12) & ~pix(sym([(10, 5), (10, 12)]))
shade(head, 10, 15.5, 7.5, 7.5, 7.0, vy=0.8)
jaw = rect(12, 13, 19, 13)
shade(jaw, 10, 15.5, 9, 6.5, 6.0, bias=-0.1)

# Hörner: dunkler Kristall, Spitze glüht (t = 0 Ansatz .. 1 Spitze)
horn = [(9, 5, 0.0), (10, 4, 0.05), (11, 4, 0.0), (8, 4, 0.15), (9, 4, 0.1), (7, 3, 0.3), (8, 3, 0.3), (9, 3, 0.25),
        (6, 2, 0.5), (7, 2, 0.45), (5, 1, 0.7), (6, 1, 0.65), (5, 0, 0.9)]
for x, y, t in horn:
    for xx, light_side in ((x, True), (N - 1 - x, False)):
        if t < 0.4:
            c = RAMP[3] if (light_side and t > 0.05) else RAMP[2] if light_side else RAMP[1]
            if y == 3 and light_side:
                c = RAMP[4]
            put(pix([(xx, y)]), c, False, 11)
        else:
            c = R if t < 0.55 else P if t < 0.68 else H if t < 0.85 else W
            put(pix([(xx, y)]), c, True, 11)

# Kamm in der Mitte (glühender Splitter)
put(pix(sym([(15, 1)])), W)
put(pix(sym([(15, 2)])), H)
put(pix([(15, 3), (16, 3)]), P)
put(pix([(14, 3), (17, 3)]), R)
put(pix([(14, 4), (17, 4)]), R)
put(pix([(15, 4), (16, 4)]), P)

# Brauen (finster, zur Mitte hin abfallend)
put(pix(sym([(11, 7), (12, 7), (13, 8), (14, 8)])), K, False)
# Augen
put(pix(sym([(11, 8), (12, 8)])), P)
put(pix(sym([(12, 9), (14, 9)])), P)
put(pix(sym([(13, 9)])), W)
put(pix(sym([(11, 9)])), R)
put(pix(sym([(13, 10), (14, 10)])), R)
# Mund: glühender Spalt mit Zähnen
put(pix(sym([(13, 11), (15, 11)])), P)
put(pix(sym([(14, 11)])), H)
put(pix(sym([(14, 12), (15, 12)])), R)
put(pix(sym([(13, 12)])), K, False)
# Stirn-Kristall
put(pix(sym([(15, 6)])), H)
put(pix(sym([(15, 7)])), R)

# ---------- Herz-Kristall + Adern ----------
gem = [(15, 16), (14, 17), (15, 17), (13, 18), (14, 18), (15, 18), (14, 19), (15, 19), (15, 20)]
for x, y in gem:
    put(pix([(x, y)]), H if (x + y) <= 32 else P)
    put(pix([(N - 1 - x, y)]), P if (x + y) <= 31 else R)
put(pix([(15, 17)]), W)
put(pix([(14, 18)]), W)
ring = pix(sym([(15, 15), (14, 16), (13, 17), (12, 18), (13, 19), (14, 20), (15, 21)]))
blend(ring, R, 0.55)
veins = pix(sym([(11, 17), (10, 16), (12, 19), (11, 20), (12, 21), (13, 22), (15, 22)]))
blend(veins, P, 0.6)

# ---------- Neon-Gürtel mit Schnalle ----------
put(rect(11, 23, 20, 23), P)
put(pix(sym([(12, 23), (15, 23)])), H)
put(pix(sym([(15, 24)])), W)
put(pix(sym([(14, 24)])), R)
# Saum unten glüht
blend(rect(10, 30, 21, 30), P, 0.7)
glow[rect(10, 30, 21, 30) & filled] = True

# ---------- Fugen zwischen Formen, Lichtkanten, Umriss außen ----------
def shifted(a, dx, dy, fill):
    out = np.full_like(a, fill)
    sy = slice(max(0, -dy), N - max(0, dy))
    dy_ = slice(max(0, dy), N - max(0, -dy))
    sx = slice(max(0, -dx), N - max(0, dx))
    dx_ = slice(max(0, dx), N - max(0, -dx))
    out[dy_, dx_] = a[sy, sx]
    return out


# Pixel, die an eine darüberliegende Form grenzen, etwas abdunkeln (Tiefe)
seam = np.zeros((N, N), bool)
for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
    seam |= filled & shifted(filled, dx, dy, False) & (shifted(layer, dx, dy, 0) > layer)
m = seam & ~glow
col[m] = col[m] * 0.62

ring_k = np.zeros((N, N), bool)
ring_g = np.zeros((N, N), bool)
for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
    nb = shifted(filled, dx, dy, False)
    nbg = shifted(filled & glow, dx, dy, False)
    nbs = shifted(filled & ~glow, dx, dy, False)
    ring_k |= ~filled & nbs
    ring_g |= ~filled & nbg
col[ring_g] = GLOW_RING
col[ring_k] = K
filled |= ring_k | ring_g

img = np.concatenate([col, filled[..., None].astype(float)], -1)
out = Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA")
out.save(OUT)
print("saved", OUT)
if len(sys.argv) > 2:
    big = out.resize((512, 512), Image.NEAREST)
    small = out.resize((64, 64), Image.NEAREST)
    prev = Image.new("RGBA", (1024 + 48 + 160, 512 + 32), (0, 0, 0, 255))
    for i, bgc in enumerate([(139, 139, 139, 255), (30, 10, 36, 255)]):
        tile = Image.new("RGBA", (512, 512), bgc)
        tile.alpha_composite(big)
        prev.paste(tile, (16 + i * (512 + 16), 16))
        st = Image.new("RGBA", (64, 64), bgc)
        st.alpha_composite(small)
        prev.paste(st, (1072 + 16, 16 + i * 80))
    prev.save(sys.argv[2])
