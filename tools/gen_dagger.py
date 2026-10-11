"""Mystischer 3D-Dolch als Totem der Unsterblichkeit (Modell + Texturen für das Paket "Pinkes HUD").

Der Dolch wird senkrecht gebaut und dann als Ganzes um -45° gedreht – so liegt er wie ein Vanilla-Schwert
diagonal im Bild, und die Halte-Positionen von Vanilla-Schwertern passen.
  - Klinge aus Obsidian mit hellen Schneiden, erhabener Mittelgrat mit glühenden, nach oben pulsierenden Runen
  - Parierstange mit Hörnern und Kristall, umwickelter Griff, Kristall-Knauf
  - zwei gekreuzte Rauch-Flächen mit animiertem, leicht glühendem Rauch

Aufruf: gen_dagger.py PACK_ASSETS_DIR
  (schreibt minecraft/items/totem_of_undying.json, nuit_chains/models/item/smoke_dagger.json
   und nuit_chains/textures/item/smoke_dagger/*)
Die Item-Definition (items/totem_of_undying.json) wird mit überschrieben – sonst gewinnen Pakete wie
"Enchantment Glows", die dort für Hand und Totem-Animation eigene 3D-Totems eintragen.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1]
TEX_DIR = os.path.join(OUT, "nuit_chains", "textures", "item", "smoke_dagger")
MODEL = os.path.join(OUT, "nuit_chains", "models", "item", "smoke_dagger.json")
ITEM_DEF = os.path.join(OUT, "minecraft", "items", "totem_of_undying.json")
os.makedirs(TEX_DIR, exist_ok=True)
os.makedirs(os.path.dirname(MODEL), exist_ok=True)
T = "nuit_chains:item/smoke_dagger/"


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], np.float64) / 255


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def save(name, rgb, alpha=None, anim=None):
    if alpha is None:
        alpha = np.ones(rgb.shape[:2])
    img = np.concatenate([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)[..., None]], -1)
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(TEX_DIR, name + ".png"))
    meta = os.path.join(TEX_DIR, name + ".png.mcmeta")
    if anim:
        with open(meta, "w") as f:
            json.dump({"animation": anim}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)


# ------------------------------------------------------------------ Maße (Dolch senkrecht, Mitte x=z=8)
BLADE = [(5.8, 7.2, 1.35), (7.2, 8.8, 1.55), (8.8, 10.6, 1.72), (10.6, 12.3, 1.66), (12.3, 13.8, 1.5),
         (13.8, 15.1, 1.27), (15.1, 16.3, 1.0), (16.3, 17.3, 0.74), (17.3, 18.2, 0.48), (18.2, 18.8, 0.26),
         (18.8, 19.3, 0.11)]
K = 0.82                      # ganzer Dolch etwas kleiner als ein Schwert
BLADE_Y0, BLADE_Y1, BLADE_W = 5.8, 19.3, 1.75
BT = 0.3                      # halbe Klingendicke
SPINE = (7.72, 8.28, 5.8, 17.6, 7.55, 8.45)
RUNES = (7.8, 8.2, 6.4, 16.6, 7.5, 8.5)


def blade_halfwidth(y):
    for y0, y1, w in BLADE:
        if y0 <= y < y1:
            return w
    return 0.0


# ------------------------------------------------------------------ Texturen
rng = np.random.default_rng(1337)

# Klinge 16x64: Obsidian, zur Schneide hin heller angeschliffen, polierter Streifen links
W, H = 16, 64
xs = (np.arange(W) + 0.5) / W * (2 * BLADE_W) - BLADE_W
ys = BLADE_Y1 - (np.arange(H) + 0.5) / H * (BLADE_Y1 - BLADE_Y0)
X, Y = np.meshgrid(xs, ys)
hw = np.vectorize(blade_halfwidth)(Y)
d = hw - np.abs(X)                                   # Abstand zur Schneide
core, mid = hexc("#230a31"), hexc("#3f1758")
bev, bev2, edge = hexc("#6a2c88"), hexc("#b47ad2"), hexc("#f6d8ff")
t = smooth(0.0, 0.9, np.minimum(d, 0.9))[..., None]
col = mid * (1 - t) + core * t                       # Mitte dunkel, zur Schneide hin mittel
b = (1 - smooth(0.05, 0.42, d))[..., None]
col = col * (1 - b) + (bev * 0.5 + bev2 * 0.5) * b
col = np.where((d < 0.12)[..., None], edge, col)
col = np.where(((d >= 0.12) & (d < 0.22))[..., None], bev2, col)
streak = np.exp(-((X + hw * 0.45) / 0.18) ** 2)[..., None] * 0.16 * (d > 0.3)[..., None]
col = col + streak * hexc("#c890ff")
grain = rng.normal(0, 0.018, (H, W, 1))
col = col + grain
save("blade", col)

# Mittelgrat 8x64: dunkel, Lichtkante links
W, H = 8, 64
u = (np.arange(W) + 0.5) / W
col = np.zeros((H, W, 3)) + hexc("#24102f")
col[:, 0] = hexc("#7b4a98")
col[:, 1] = hexc("#4b2763")
col[:, -1] = hexc("#140818")
save("spine", col + rng.normal(0, 0.015, (H, W, 1)))

# Runen 8x64, 16 Bilder: Glyphen, ein Leuchtpuls wandert nach oben
W, H, F = 8, 64, 16
glyph = np.zeros((H, W), bool)
for cell in range(H // 8):
    y0 = cell * 8 + 1
    g = np.zeros((6, 6), bool)
    g[:, 2:4] = rng.random() < 0.5
    for _ in range(4):
        kind = rng.integers(0, 4)
        r0, c0 = rng.integers(0, 5), rng.integers(0, 5)
        if kind == 0:
            g[r0, :] = True
        elif kind == 1:
            g[:, c0] = True
        elif kind == 2:
            for k in range(6):
                if 0 <= r0 - 2 + k < 6:
                    g[min(5, k), min(5, max(0, k + (c0 - 2)))] = True
        else:
            g[r0:r0 + 2, c0:c0 + 2] = True
    g = g | g[:, ::-1]                                # symmetrisch – wirkt wie echte Zeichen
    glyph[y0:y0 + 6, 1:7] = g
frames = []
hh = 1 - (np.arange(H) + 0.5) / H                    # 0 unten … 1 oben
for f in range(F):
    p = f / F * 1.5 - 0.25
    pulse = np.exp(-((hh - p) / 0.13) ** 2) + 0.6 * np.exp(-((hh - p + 0.75) / 0.13) ** 2)
    k = np.clip(0.25 + pulse, 0, 1.2)[:, None, None]
    lo, hi, white = hexc("#7a1460"), hexc("#ff3fc4"), hexc("#ffe2f7")
    c = np.where(k < 1, lo * (1 - k) + hi * k, hi * (2 - k * 1) + white * (k - 1))
    frames.append(np.broadcast_to(c, (H, W, 3)) * np.ones((H, W, 1)))
rgb = np.concatenate(frames, 0)
alpha = np.tile(glyph.astype(float), (F, 1))
save("runes", rgb, alpha, {"frametime": 2, "interpolate": True})

# Schneide 4x16: helles Silber-Rosa
W, H = 4, 16
col = np.zeros((H, W, 3)) + hexc("#e7c2f5")
col[:, 0] = hexc("#fff0ff")
col[:, -1] = hexc("#b07dc8")
col[rng.random((H, W)) < 0.08] = hexc("#ffffff")
save("edge", col)

# Metall 16x16: dunkles Violett-Metall mit Filigran und pinken Einlagen
W, H = 16, 16
col = np.zeros((H, W, 3)) + hexc("#3b2452")
yy, xx = np.mgrid[0:H, 0:W]
wave = np.abs(((yy + 3 * np.sin(xx / 16 * 2 * np.pi * 2)) % 6) - 3) < 0.7
col[wave] = hexc("#9c6cc0")
col[np.roll(wave, 1, 0) & ~wave] = hexc("#120818")
col[(xx % 8 == 3) & (yy % 6 == 1)] = hexc("#ff4fc8")
col[0, :] = hexc("#9a6ab8")
col[-1, :] = hexc("#100612")
save("metal", col + rng.normal(0, 0.015, (H, W, 1)))

# Griff 16x16: diagonal gewickeltes Leder mit pinkem Faden
col = np.zeros((H, W, 3))
band = ((xx + yy) % 6)
col[:] = hexc("#2c0f30")
col[band < 3] = hexc("#46183f")
col[band == 0] = hexc("#5c2352")
col[band == 5] = hexc("#170717")
col[((xx - yy) % 8 == 0)] = hexc("#ff5fcf")
save("grip", col)

# Kristall 8x8: facettierter pinker Edelstein
W, H = 8, 8
yy, xx = np.mgrid[0:H, 0:W]
r = np.abs(xx - 3.5) + np.abs(yy - 3.5)
col = np.zeros((H, W, 3)) + hexc("#b0127f")
col[r < 5] = hexc("#ff3fc4")
col[r < 3.2] = hexc("#ff9de6")
col[r < 1.6] = hexc("#fff0fb")
col[(xx < 3) & (yy < 3) & (r < 5)] = col[(xx < 3) & (yy < 3) & (r < 5)] * 0.6 + hexc("#ffffff") * 0.4
save("gem", col)

# Rauch 32x64, 24 Bilder: aufsteigende Schwaden mit dünnen Fäden, nahtlos geloopt
W, H, F = 32, 64, 24


def periodic_noise(shape, cells_x, cells_y, seed):
    """Glattes Rauschen, das in y periodisch ist (für das Hochziehen ohne Sprung)."""
    r = np.random.default_rng(seed)
    g = r.random((cells_y, cells_x + 1))
    g = np.concatenate([g, g[:1]], 0)                 # y periodisch
    im = Image.fromarray((g * 255).astype(np.uint8), "L")
    return im, g


def sample(g, x, y):
    """Bikubisch-ähnliches (weiches) Abtasten des Gitters g an x in [0,cx], y periodisch in [0,cy)."""
    cy = g.shape[0] - 1
    x = np.clip(x, 0, g.shape[1] - 1 - 1e-6)
    y = np.mod(y, cy)
    x0 = np.clip(np.floor(x).astype(int), 0, g.shape[1] - 2)
    y0 = np.floor(y).astype(int)
    fx, fy = x - x0, y - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a = g[y0, x0] * (1 - fx) + g[y0, x0 + 1] * fx
    b = g[y0 + 1, x0] * (1 - fx) + g[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


octaves = []
for o, (cx, cy) in enumerate([(4, 4), (8, 8), (16, 16)]):
    _, g = periodic_noise(None, cx, cy, 70 + o)
    octaves.append((g, cx, cy))
px = (np.arange(W) + 0.5) / W                         # 0..1 quer
py = (np.arange(H) + 0.5) / H                         # 0 oben … 1 unten
PX, PY = np.meshgrid(px, py)
smoke_frames, smoke_alpha = [], []
dark, violet, pink, light = hexc("#2e2238"), hexc("#6a3a86"), hexc("#d04aa8"), hexc("#ffc6ee")
for f in range(F):
    ph = f / F
    # Hochziehen: genau eine Periode pro Durchlauf -> nahtlos
    up = ph
    warp = 0.07 * np.sin(2 * np.pi * (PY * 1.3 + ph)) + 0.04 * np.sin(2 * np.pi * (PY * 2.7 - 2 * ph) + 1.3)
    n = 0
    tendr = 0
    for o, (g, cx, cy) in enumerate(octaves):
        v = sample(g, (PX + warp * (1 + o * 0.5)) * cx, (PY + up) * cy)
        n = n + v * [0.55, 0.3, 0.15][o]
        if o >= 1:
            tendr = tendr + (1 - np.abs(2 * v - 1)) ** 3 * [0, 0.7, 0.45][o]
    # Form: zur Mitte hin dichter, nach oben breiter, unten ein- und oben ausblenden
    width = 0.16 + 0.22 * (1 - PY)
    center = 0.5 + 0.06 * np.sin(2 * np.pi * (PY * 1.1 - ph))
    mask = np.exp(-((PX - center) / width) ** 2)
    mask *= smooth(1.0, 0.82, PY) * smooth(0.0, 0.3, PY)
    edge_fade = smooth(0.0, 0.08, PX) * smooth(1.0, 0.92, PX)
    body = smooth(0.3, 0.72, n)
    dens = np.clip((body * 0.8 + tendr * 1.1) * mask * edge_fade, 0, 1)
    c = dark[None, None] * (1 - dens[..., None]) + violet[None, None] * dens[..., None]
    hot = smooth(0.45, 0.95, dens)[..., None] * (0.55 + 0.45 * (1 - PY))[..., None]
    c = c * (1 - hot) + pink * hot
    c = c + light * (smooth(0.75, 1.0, dens)[..., None] * 0.5)
    smoke_frames.append(c)
    smoke_alpha.append(np.clip(dens * 0.85, 0, 0.85))
save("smoke", np.concatenate(smoke_frames, 0), np.concatenate(smoke_alpha, 0), {"frametime": 2, "interpolate": True})

# ------------------------------------------------------------------ Modell
ROT = {"origin": [8, 8, 8], "axis": "z", "angle": -45}


def r(v):
    return round(float(v), 4)


def box(x0, x1, y0, y1, z0, z1, faces, light=0, shade=True):
    k = lambda v: 8 + (v - 8) * K
    e = {"from": [r(k(x0)), r(k(y0)), r(k(z0))], "to": [r(k(x1)), r(k(y1)), r(k(z1))], "rotation": dict(ROT), "faces": faces}
    if light:
        e["light_emission"] = light
    if not shade:
        e["shade"] = False
    return e


def bu(x):
    return (x - 8 + BLADE_W) / (2 * BLADE_W) * 16


def bv(y):
    return (BLADE_Y1 - y) / (BLADE_Y1 - BLADE_Y0) * 16


elements = []
# Klinge
for y0, y1, w in BLADE:
    x0, x1 = 8 - w, 8 + w
    fb = [r(bu(x0)), r(bv(y1)), r(bu(x1)), r(bv(y0))]
    fe = [0, r(bv(y1)), 16, r(bv(y0))]
    elements.append(box(x0, x1, y0, y1, 8 - BT, 8 + BT, {
        "north": {"uv": fb, "texture": "#blade"},
        "south": {"uv": fb, "texture": "#blade"},
        "east": {"uv": fe, "texture": "#edge"},
        "west": {"uv": fe, "texture": "#edge"},
        "up": {"uv": [0, 0, 16, 2], "texture": "#edge"},
        "down": {"uv": [0, 0, 16, 2], "texture": "#edge"},
    }, light=2))
# Mittelgrat
sx0, sx1, sy0, sy1, sz0, sz1 = SPINE
elements.append(box(sx0, sx1, sy0, sy1, sz0, sz1, {
    "north": {"uv": [0, 0, 16, 16], "texture": "#spine"},
    "south": {"uv": [0, 0, 16, 16], "texture": "#spine"},
    "east": {"uv": [0, 0, 4, 16], "texture": "#edge"},
    "west": {"uv": [0, 0, 4, 16], "texture": "#edge"},
    "up": {"uv": [0, 0, 4, 2], "texture": "#edge"},
}))
# Runen (nur Vorder- und Rückseite, leuchten)
rx0, rx1, ry0, ry1, rz0, rz1 = RUNES
elements.append(box(rx0, rx1, ry0, ry1, rz0, rz1, {
    "north": {"uv": [0, 0, 16, 16], "texture": "#runes"},
    "south": {"uv": [0, 0, 16, 16], "texture": "#runes"},
}, light=15, shade=False))
# Parierstange + Hörner
MF = lambda uv: {k: {"uv": uv, "texture": "#metal"} for k in ("north", "south", "east", "west", "up", "down")}
elements.append(box(4.6, 11.4, 4.6, 5.8, 7.05, 8.95, {
    "north": {"uv": [0, 4, 16, 7], "texture": "#metal"}, "south": {"uv": [0, 4, 16, 7], "texture": "#metal"},
    "east": {"uv": [0, 4, 4, 7], "texture": "#metal"}, "west": {"uv": [0, 4, 4, 7], "texture": "#metal"},
    "up": {"uv": [0, 0, 16, 4], "texture": "#metal"}, "down": {"uv": [0, 12, 16, 16], "texture": "#metal"},
}))
# Enden der Parierstange: leicht zur Klinge hin hochgezogen
for x0, x1 in [(3.7, 4.7), (11.3, 12.3)]:
    elements.append(box(x0, x1, 4.8, 6.5, 7.25, 8.75, MF([4, 2, 8, 6])))
# Kristall in der Parierstange
GF = {k: {"uv": [0, 0, 16, 16], "texture": "#gem"} for k in ("north", "south", "east", "west", "up", "down")}
elements.append(box(7.3, 8.7, 4.7, 5.7, 6.8, 9.2, GF, light=15))
# Griff mit Ringen
elements.append(box(7.3, 8.7, -0.4, 4.6, 7.3, 8.7, {
    k: {"uv": [0, 0, 16, 16], "texture": "#grip"} for k in ("north", "south", "east", "west")}))
elements.append(box(7.12, 8.88, 4.15, 4.6, 7.12, 8.88, MF([0, 6, 16, 8])))
elements.append(box(7.12, 8.88, -0.4, 0.05, 7.12, 8.88, MF([0, 6, 16, 8])))
# Knauf: Krallen-Kappe und Kristall
elements.append(box(6.9, 9.1, -1.2, -0.4, 6.9, 9.1, MF([0, 8, 16, 11])))
elements.append(box(7.1, 8.9, -2.6, -1.2, 7.1, 8.9, GF, light=15))
elements.append(box(7.5, 8.5, -3.2, -2.6, 7.5, 8.5, GF, light=15))
# Rauch: zwei gekreuzte Flächen entlang der Klinge
SF = {"uv": [0, 0, 16, 16], "texture": "#smoke"}
elements.append(box(2.5, 13.5, 3, 22, 8, 8, {"north": dict(SF), "south": dict(SF)}, light=9, shade=False))
elements.append(box(8, 8, 3, 22, 2.5, 13.5, {"east": dict(SF), "west": dict(SF)}, light=9, shade=False))

model = {
    "gui_light": "front",
    "textures": {
        "particle": T + "blade",
        "blade": T + "blade", "spine": T + "spine", "runes": T + "runes", "edge": T + "edge",
        "metal": T + "metal", "grip": T + "grip", "gem": T + "gem", "smoke": T + "smoke",
    },
    "elements": elements,
    "display": {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    },
}
with open(MODEL, "w") as f:
    json.dump(model, f, indent=1)
os.makedirs(os.path.dirname(ITEM_DEF), exist_ok=True)
with open(ITEM_DEF, "w") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "nuit_chains:item/smoke_dagger"}}, f, indent=2)
print("Modell:", MODEL, "Elemente:", len(elements))
