"""Erzeugt die Zusatz-Texturen für Tag/Nacht und Nebel:
  fog_puff.png  – 2x2 weiche Nebelschwaden (weiß, Form steckt im Alpha)
  sun.png       – heller Kern mit rosa-pfirsichfarbenem Lichthof (wird additiv gezeichnet)
  moon.png      – blasser Rosé-Mond mit Kratern und pinkem Schein
  white.png     – weiße Fläche für den Tag-Himmel
Aufruf: gen_sky_extras.py OUT_DIR"""
import sys
import os
import numpy as np
from PIL import Image

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(424242)


def value_noise(size, cells, seed):
    r = np.random.default_rng(seed)
    g = r.random((cells + 1, cells + 1)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8), "L").resize((size, size), Image.BICUBIC)
    return np.asarray(im).astype(np.float32) / 255


def fbm(size, seed, octaves=5, base=4):
    out = np.zeros((size, size), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        out += value_noise(size, base * 2 ** o, seed + o * 17) * amp
        tot += amp
        amp *= 0.55
    return out / tot


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def save(arr, name):
    Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(OUT, name))
    print("saved", name)


# ---------------- Nebelschwaden (2x2 Atlas, je 256 px) ----------------
C = 256
atlas = np.zeros((2 * C, 2 * C, 4), np.float32)
yy, xx = np.mgrid[0:C, 0:C].astype(np.float32)
xx = (xx + 0.5) / C * 2 - 1
yy = (yy + 0.5) / C * 2 - 1
for v in range(4):
    r = np.random.default_rng(100 + v)
    shape = np.zeros((C, C), np.float32)
    # mehrere weiche Klumpen ergeben eine unregelmäßige Wolkenform
    for k in range(8 + v):
        cx, cy = r.uniform(-0.42, 0.42), r.uniform(-0.3, 0.32)
        rad = r.uniform(0.28, 0.48)
        shape += np.exp(-((xx - cx) ** 2 + ((yy - cy) * 1.25) ** 2) / (2 * rad * rad))
    shape = shape / shape.max()          # weiche Vereinigung der Klumpen
    n = fbm(C, 300 + v * 11, octaves=4, base=3)
    a = shape * (0.6 + 1.3 * (n - 0.5))
    a = smooth(0.06, 0.95, a) * 0.85
    # zum Rand hin sicher auf 0 (kein Überlaufen in die Nachbarzelle)
    edge = np.minimum.reduce([xx + 1, 1 - xx, yy + 1, 1 - yy])
    a *= smooth(0.0, 0.18, edge)
    rgb = np.ones((C, C, 3), np.float32) * (0.92 + 0.08 * n[..., None])
    oy, ox = (v >> 1) * C, (v & 1) * C
    atlas[oy:oy + C, ox:ox + C, :3] = rgb
    atlas[oy:oy + C, ox:ox + C, 3] = a
save(atlas, "fog_puff.png")

# ---------------- Sonne ----------------
S = 256
yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
r = np.sqrt((xx + 0.5 - S / 2) ** 2 + (yy + 0.5 - S / 2) ** 2)
R0 = 20.0
core = smooth(R0 + 1.5, R0 - 1.5, r)
corona = 0.62 * np.exp(-np.maximum(0, r - R0) / 9) + 0.3 * np.exp(-np.maximum(0, r - R0) / 30) + 0.12 * np.exp(-r / 70)
fade = smooth(S / 2 - 2, S / 2 - 30, r)
alpha = np.clip(core + corona * (1 - core), 0, 1) * fade
col = np.zeros((S, S, 3), np.float32)
inner = np.array([1.0, 0.98, 0.99])
halo = np.array([1.0, 0.72, 0.80])
far = np.array([1.0, 0.62, 0.55])
t = np.clip((r - R0) / 70, 0, 1)[..., None]
col[:] = halo * (1 - t) + far * t
col = col * (1 - core[..., None]) + inner * core[..., None]
save(np.concatenate([col, alpha[..., None]], -1), "sun.png")

# ---------------- Mond ----------------
M = 256
yy, xx = np.mgrid[0:M, 0:M].astype(np.float32)
dx, dy = xx + 0.5 - M / 2, yy + 0.5 - M / 2
r = np.sqrt(dx * dx + dy * dy)
RM = 62.0
disc = smooth(RM + 1.2, RM - 1.2, r)
# Krater und Meere
surf = fbm(M, 77, octaves=6, base=5)
maria = smooth(0.44, 0.7, fbm(M, 91, octaves=4, base=3))
crat = np.zeros((M, M), np.float32)
cr = np.random.default_rng(55)
for k in range(16):
    ang, rr = cr.uniform(0, 2 * np.pi), RM * np.sqrt(cr.uniform(0, 0.8))
    cx, cy, cs = M / 2 + np.cos(ang) * rr, M / 2 + np.sin(ang) * rr, cr.uniform(2.0, 7)
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    crat += -0.12 * smooth(cs, cs * 0.5, d) + 0.07 * smooth(cs * 1.3, cs, d) * smooth(cs * 0.8, cs, d)
z = np.sqrt(np.clip(1 - (r / RM) ** 2, 0, 1))
limb = 0.55 + 0.45 * z
bright = np.clip((0.86 + 0.3 * (surf - 0.5) - 0.2 * maria + crat) * limb, 0.25, 1.1)
base = np.array([0.99, 0.86, 0.96])
moon_rgb = base * bright[..., None]
glow = (0.35 * np.exp(-np.maximum(0, r - RM) / 10) + 0.12 * np.exp(-np.maximum(0, r - RM) / 32)) * smooth(M / 2 - 2, M / 2 - 24, r)
glow_rgb = np.array([1.0, 0.45, 0.82])
alpha = np.clip(disc * np.clip(bright, 0, 1) + glow * (1 - disc), 0, 1)
rgb = moon_rgb * disc[..., None] + glow_rgb * (1 - disc[..., None])
save(np.concatenate([np.clip(rgb, 0, 1), alpha[..., None]], -1), "moon.png")

# ---------------- weiß ----------------
save(np.ones((16, 16, 4), np.float32), "white.png")
