"""Galaxy-Katana als Totem der Unsterblichkeit (Modell + Texturen für das Paket "Pinkes HUD").

Das Katana wird senkrecht gebaut (Schneide zeigt nach +x, Rücken nach -x, Spitze nach oben) und dann als Ganzes
um -45° gedreht – so liegt es wie ein Vanilla-Schwert diagonal im Bild und die Halte-Positionen passen.
  - leicht gebogene Klinge mit schräger Spitze: Weltraum mit pink-violetten Nebeln und funkelnden Sternen,
    helle Schneide, dunkler Rücken
  - vom Rücken lösen sich Pixel-Partikel und treiben davon (animiert, dazu ein paar schwebende Würfel)
  - schwarz gewickelter Griff mit violett-pinken Rauten, dunkles Stichblatt mit pinkem Rand

Aufruf: gen_katana.py PACK_ASSETS_DIR
  (schreibt minecraft/items/totem_of_undying.json, nuit_chains/models/item/galaxy_katana.json
   und nuit_chains/textures/item/galaxy_katana/*)
Die Item-Definition wird mit überschrieben – sonst gewinnen Pakete wie "Enchantment Glows",
die dort für Hand und Totem-Animation eigene Totems eintragen.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1]
NAME = "galaxy_katana"
TEX_DIR = os.path.join(OUT, "nuit_chains", "textures", "item", NAME)
MODEL = os.path.join(OUT, "nuit_chains", "models", "item", NAME + ".json")
ITEM_DEF = os.path.join(OUT, "minecraft", "items", "totem_of_undying.json")
os.makedirs(TEX_DIR, exist_ok=True)
os.makedirs(os.path.dirname(MODEL), exist_ok=True)
T = "nuit_chains:item/" + NAME + "/"
rng = np.random.default_rng(2026)


def hexc(h):
    return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], np.float64) / 255


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def save(name, rgb, alpha=None, frame_h=None, frametime=2, interpolate=True):
    if alpha is None:
        alpha = np.ones(rgb.shape[:2])
    img = np.concatenate([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)[..., None]], -1)
    Image.fromarray((img * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(TEX_DIR, name + ".png"))
    meta = os.path.join(TEX_DIR, name + ".png.mcmeta")
    if frame_h:
        # Bilder sind nicht quadratisch -> Breite/Höhe angeben, sonst schneidet Minecraft falsch
        anim = {"frametime": frametime, "interpolate": interpolate, "width": rgb.shape[1], "height": frame_h}
        with open(meta, "w") as f:
            json.dump({"animation": anim}, f, indent=2)
    elif os.path.exists(meta):
        os.remove(meta)


def value_noise(w, h, cx, cy, seed):
    g = np.random.default_rng(seed).random((cy + 1, cx + 1)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)
    return np.asarray(im).astype(np.float64) / 255


def fbm(w, h, seed, base=(3, 12), octaves=5):
    out = np.zeros((h, w))
    amp = tot = 0
    a = 1.0
    for o in range(octaves):
        out += value_noise(w, h, base[0] * 2 ** o, base[1] * 2 ** o, seed + o * 31) * a
        tot += a
        a *= 0.55
    return out / tot


# ------------------------------------------------------------------ Maße (senkrecht, Mitte x=z=8)
BASE_Y, TIP_Y = 4.2, 19.3          # Klinge von der Manschette bis zur Spitze
BACK, EDGE0 = -0.85, 0.9            # Rücken / Schneide relativ zur Mittellinie (unten)
BEND = 11.0                          # Krümmung: so viele Grad dreht die Klinge bis zur Spitze zum Rücken hin
EDGE_STRIP = 0.36                    # Breite der hellen Schneide
K = 0.84                             # Gesamtgröße (passt so diagonal in den Slot, mit etwas Rand)
SEG_MAIN, SEG_TIP = 12, 6           # Abschnitte der Klinge / der schrägen Spitze
L_BLADE = TIP_Y - BASE_Y

# Mittellinie als Bogen: der Winkel wächst gleichmäßig -> runde Krümmung ohne Stufen
_s = np.linspace(0, L_BLADE, 400)
_ang = np.radians(BEND) * _s / L_BLADE
_cx = -np.concatenate([[0], np.cumsum(np.sin(_ang[:-1]) * np.diff(_s))])
_cy = BASE_Y + np.concatenate([[0], np.cumsum(np.cos(_ang[:-1]) * np.diff(_s))])


def arc(sv):
    """Punkt (x-Versatz, y) und Winkel (Grad) der Mittellinie bei Bogenlänge sv."""
    return np.interp(sv, _s, _cx), np.interp(sv, _s, _cy), BEND * sv / L_BLADE


def curve(y):
    """x-Versatz der Mittellinie auf Höhe y (für Partikel und Splitter)."""
    return np.interp(y, _cy, _cx)


def edge_at(y):
    """Abstand der Schneide von der Mittellinie: verjüngt sich, an der Spitze (Kissaki) läuft sie zum Rücken."""
    t = (y - BASE_Y) / (TIP_Y - BASE_Y)
    e = EDGE0 - 0.25 * t
    kis = smooth(0.86, 1.0, t)
    return e * (1 - kis) + (BACK + 0.08) * kis


def back_at(y):
    t = (y - BASE_Y) / (TIP_Y - BASE_Y)
    return BACK + 0.12 * t


# ------------------------------------------------------------------ Texturen
# Klinge: Galaxie, 16 x 160 pro Bild, 8 Bilder (Sterne funkeln, Nebel schimmert)
BW, BH, BF = 16, 160, 8
neb1 = fbm(BW * 2, BH, 11, base=(2, 10))[:, ::2]
neb2 = fbm(BW * 2, BH, 23, base=(3, 16))[:, ::2]
dust = fbm(BW * 2, BH, 37, base=(2, 8))[:, ::2]
yy, xx = np.mgrid[0:BH, 0:BW]
deep = hexc("#0a0520") * (1 - yy[..., None] / BH) + hexc("#180a33") * (yy[..., None] / BH)
pink, violet, blue = hexc("#ff3fb8"), hexc("#8a3cff"), hexc("#3d63ff")
band = np.sin((yy / BH * 2.2 + neb1 * 1.8) * np.pi * 2) * 0.5 + 0.5      # schräge Nebelbänder
nebula = smooth(0.45, 0.85, neb2) * (0.35 + 0.65 * band)
base_col = deep + violet * (nebula * 0.85)[..., None] + pink * (smooth(0.55, 0.85, neb2 * band) * 0.75)[..., None] \
    + blue * (smooth(0.5, 0.78, neb1) * (1 - band) * 0.55)[..., None]
base_col *= (1 - 0.55 * smooth(0.55, 0.75, dust))[..., None]               # dunkle Staubbahnen
n_stars = 170
sx, sy = rng.integers(0, BW, n_stars), rng.integers(0, BH, n_stars)
sb = rng.random(n_stars) ** 2.2
sph = rng.random(n_stars)
scol = np.where((rng.random(n_stars) < 0.3)[:, None], hexc("#ffb3ea"), hexc("#ffffff"))
frames = []
for f in range(BF):
    c = base_col.copy()
    shimmer = 0.9 + 0.1 * np.sin(2 * np.pi * (f / BF + neb1 * 2))
    c *= shimmer[..., None]
    tw = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * (f / BF + sph)))
    for i in range(n_stars):
        v = np.clip(0.35 + 0.9 * sb[i], 0, 1.2) * tw[i]
        c[sy[i], sx[i]] = np.maximum(c[sy[i], sx[i]], scol[i] * v)
        if sb[i] > 0.75:   # helle Sterne mit kleinem Kreuz
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Y = sx[i] + dx, sy[i] + dy
                if 0 <= X < BW and 0 <= Y < BH:
                    c[Y, X] = np.maximum(c[Y, X], scol[i] * v * 0.45)
    frames.append(c)
save("blade", np.concatenate(frames, 0), frame_h=BH, frametime=3)

# Schneide 8 x 64: helles Lavendel-Weiß, zur Kante hin heller
W, H = 8, 64
col = np.zeros((H, W, 3))
u = (np.arange(W) + 0.5) / W
col[:] = (hexc("#9d8bff")[None, :] * (1 - u[:, None]) + hexc("#f3e9ff")[None, :] * u[:, None])[None, :, :]
col[rng.random((H, W)) < 0.05] = hexc("#ffffff")
save("edge", col)

# Rücken (Mune) 4 x 16: dunkles Metall mit feiner Lichtkante
col = np.zeros((16, 4, 3)) + hexc("#1b1428")
col[:, 0] = hexc("#5b4a78")
save("back", col)

# Griff 16 x 32: schwarze Wicklung mit violett-pinken Rauten (wie Tsuka-Ito)
W, H = 16, 32
yy, xx = np.mgrid[0:H, 0:W]
col = np.zeros((H, W, 3)) + hexc("#0e0b14")
cell = 8
dxr = np.abs(((xx + 0.5) % cell) - cell / 2)
dyr = np.abs(((yy + 0.5) % cell) - cell / 2)
diamond = dxr + dyr < cell * 0.32
inner = dxr + dyr < cell * 0.18
wrapline = np.abs(((xx + yy) % cell) - cell / 2) < 0.8
col[wrapline] = hexc("#2a2236")
col[diamond] = hexc("#5c2d8f")
col[inner] = hexc("#c13c9e")
col[(yy % cell == 0)] = col[(yy % cell == 0)] * 0.6
save("grip", col)

# Metall 16 x 16: dunkles Stichblatt mit pinkem Rand und Gravur
W = H = 16
yy, xx = np.mgrid[0:H, 0:W]
col = np.zeros((H, W, 3)) + hexc("#26202e")
col[(xx + yy) % 5 == 0] = hexc("#3a3046")
r = np.maximum(np.abs(xx - 7.5), np.abs(yy - 7.5))
col[r > 6.5] = hexc("#8e2c78")
col[(r > 5.5) & (r <= 6.5)] = hexc("#15101b")
save("metal", col)

# Manschette (Habaki) 8 x 8: helles Rosé-Gold
col = np.zeros((8, 8, 3)) + hexc("#d9a3c8")
col[:, 0] = hexc("#fff0fa")
col[:, -1] = hexc("#8a5a7c")
col[3] = hexc("#b07aa2")
save("collar", col)

# Würfel-Splitter 8 x 8: leuchtende Galaxie-Pixel
col = np.zeros((8, 8, 3)) + hexc("#7a2dff")
col[1:7, 1:7] = hexc("#c043ff")
col[2:6, 2:6] = hexc("#ff7ad6")
col[3:5, 3:5] = hexc("#ffffff")
save("shard", col)

# ------------------------------------------------------------------ Modell-Hilfen
ROT = {"origin": [8, 8, 8], "axis": "z", "angle": -45}


def r(v):
    return round(float(v), 4)


def km(v):
    return 8 + (v - 8) * K


def box(x0, x1, y0, y1, z0, z1, faces, light=0, shade=True):
    e = {"from": [r(km(x0)), r(km(y0)), r(km(z0))], "to": [r(km(x1)), r(km(y1)), r(km(z1))],
         "rotation": dict(ROT), "faces": faces}
    if light:
        e["light_emission"] = light
    if not shade:
        e["shade"] = False
    return e


def to_screen(px, py):
    """Modellpunkt (lokal, senkrecht) -> Position im Slot nach Drehung (0..16)."""
    x, y = km(px) - 8, km(py) - 8
    c = np.cos(np.radians(-45)); s = np.sin(np.radians(-45))
    return 8 + x * c - y * s, 8 + x * s + y * c


def bv(y):
    return (TIP_Y - y) / (TIP_Y - BASE_Y) * 16


elements = []


def rbox(px, py, x0, x1, length, z0, z1, ang, faces, light=0, shade=True):
    """Quader, der am Mittellinien-Punkt (px, py) beginnt und um ang Grad (zum Rücken hin) geneigt ist."""
    qx, qy = to_screen(px, py)
    e = {"from": [r(qx + x0 * K), r(qy), r(km(z0))], "to": [r(qx + x1 * K), r(qy + length * K), r(km(z1))],
         "rotation": {"origin": [r(qx), r(qy), 8], "axis": "z", "angle": r(-45 + ang)}, "faces": faces}
    if light:
        e["light_emission"] = light
    if not shade:
        e["shade"] = False
    return e


# ------------------------------------------------------------------ Klinge (Bogen aus geneigten Abschnitten)
cuts = np.concatenate([np.linspace(0, 0.86, SEG_MAIN + 1)[:-1], np.linspace(0.86, 1.0, SEG_TIP + 1)]) * L_BLADE
for i in range(len(cuts) - 1):
    s0, s1 = cuts[i], cuts[i + 1]
    sm = (s0 + s1) / 2
    x0c, y0c, _ = arc(s0)
    _, ym, ang = arc(sm)
    seg_len = (s1 - s0) * 1.04                      # etwas Überlappung, damit außen keine Fugen bleiben
    b_, e_ = back_at(ym), edge_at(ym)
    split = max(b_ + 0.12, e_ - EDGE_STRIP)
    first, last = i == 0, i == len(cuts) - 2
    v0, v1 = 16 * s1 / L_BLADE, 16 * s0 / L_BLADE
    fu = [0, r(16 - v0), r(16 * (split - b_) / (EDGE0 - BACK)), r(16 - v1)]
    faces = {"north": {"uv": fu, "texture": "#blade"}, "south": {"uv": fu, "texture": "#blade"},
             "west": {"uv": [0, 0, 4, 16], "texture": "#back"}}
    if first:
        faces["down"] = {"uv": [0, 0, 4, 2], "texture": "#back"}
    if last:
        faces["up"] = {"uv": [0, 0, 4, 2], "texture": "#back"}
    if e_ - split <= 0.02:
        faces["east"] = {"uv": [0, 0, 4, 16], "texture": "#back"}
    elements.append(rbox(8 + x0c, y0c, b_, split, seg_len, 7.72, 8.28, ang, faces, light=6))
    if e_ - split > 0.02:
        ef = {"north": {"uv": [0, 0, 16, 16], "texture": "#edge"}, "south": {"uv": [0, 0, 16, 16], "texture": "#edge"},
              "east": {"uv": [12, 0, 16, 16], "texture": "#edge"}}
        if first:
            ef["down"] = {"uv": [12, 0, 16, 2], "texture": "#edge"}
        if s1 >= 0.86 * L_BLADE - 1e-6 or last:
            ef["up"] = {"uv": [12, 0, 16, 2], "texture": "#edge"}
        elements.append(rbox(8 + x0c, y0c, split, e_, seg_len, 7.85, 8.15, ang, ef, light=8))

# ------------------------------------------------------------------ Habaki, Tsuba, Griff
CF = {k: {"uv": [0, 0, 16, 16], "texture": "#collar"} for k in ("north", "south", "east", "west", "up", "down")}
elements.append(box(8 + BACK - 0.05, 8 + EDGE0 + 0.05, 3.5, BASE_Y, 7.62, 8.38, CF, light=3))
MF = {k: {"uv": [0, 0, 16, 16], "texture": "#metal"} for k in ("north", "south", "east", "west", "up", "down")}
MS = {k: {"uv": [2, 2, 14, 14], "texture": "#metal"} for k in ("east", "west", "north", "south")}
elements.append(box(5.4, 10.6, 2.85, 3.6, 6.5, 9.5, MF))
elements.append(box(6.1, 9.9, 2.85, 3.6, 5.9, 10.1, MF))
elements.append(box(6.95, 9.05, 2.45, 2.85, 7.25, 8.75, CF, light=2))          # Fuchi
GF = {k: {"uv": [0, 0, 16, 16], "texture": "#grip"} for k in ("north", "south", "east", "west")}
elements.append(box(7.1, 8.9, -2.5, 2.45, 7.35, 8.65, GF))                     # Tsuka
elements.append(box(6.98, 9.02, -3.0, -2.5, 7.25, 8.75, dict(MF, **{"down": {"uv": [4, 4, 12, 12], "texture": "#metal"}})))  # Kashira

# ------------------------------------------------------------------ schwebende Splitter (3D-Würfel)
SH = {k: {"uv": [0, 0, 16, 16], "texture": "#shard"} for k in ("north", "south", "east", "west", "up", "down")}
for k in range(5):
    t = 0.45 + 0.5 * rng.random()
    y = BASE_Y + t * (TIP_Y - BASE_Y) - 0.5
    base_x = 8 + curve(y) + back_at(y)
    dist = 0.5 + 2.6 * rng.random() * t
    x = base_x - dist
    s = 0.3 - 0.16 * (dist / 3.1) + 0.04 * rng.random()
    zc = 8 + (rng.random() - 0.5) * 1.6
    sx_, sy_ = to_screen(x, y + dist * 0.5)
    if not (0.6 < sx_ < 15.4 and 0.6 < sy_ < 15.4):
        continue
    elements.append(box(x - s / 2, x + s / 2, y + dist * 0.5 - s / 2, y + dist * 0.5 + s / 2, zc - s / 2, zc + s / 2, SH, light=14))

# ------------------------------------------------------------------ Partikel-Flächen (animiert)
# Fläche A in der Klingenebene (vom Rücken weg), Fläche B quer dazu. Jeder Pixel weiß, wo er im Modell liegt –
# Partikel, die aus dem Slot ragen würden, werden weggelassen.
PX0, PX1, PY0, PY1 = 8 - 6.0, 8 + 0.2, 7.5, 20.2
PW, PHt, PF = 40, 80, 16


def particle_frames(seed, side):
    rr = np.random.default_rng(seed)
    n = 95
    t0 = 0.3 + 0.7 * rr.random(n) ** 0.6                 # wo am Rücken (0 Wurzel … 1 Spitze)
    ph = rr.random(n)
    spd = 0.35 + 0.9 * rr.random(n) ** 1.5               # viele bleiben nah am Rücken
    size = rr.choice([1, 1, 2], n)
    hue = rr.random(n)
    frames, alphas = [], []
    for f in range(PF):
        c = np.zeros((PHt, PW, 3))
        a = np.zeros((PHt, PW))
        for i in range(n):
            age = (f / PF + ph[i]) % 1.0
            if age > 0.88:
                continue
            y = BASE_Y + t0[i] * (TIP_Y - BASE_Y - 0.6)
            if side:
                x = 8 + (2.3 * age * spd[i]) * (1 if i % 2 else -1)      # quer: nach beiden Seiten
                yb = y
            else:
                x = 8 + curve(y) + back_at(y) - 0.1 - 3.4 * age * spd[i]
                yb = y
            yb = yb + 1.4 * age * spd[i]
            px = int((x - PX0) / (PX1 - PX0) * PW) if not side else int((x - (8 - 2.35)) / 4.7 * PW)
            py = int((PY1 - yb) / (PY1 - PY0) * PHt)
            # Slot-Grenze prüfen (nur Fläche A liegt in der Bildebene)
            if not side:
                sx_, sy_ = to_screen(x, yb)
                if not (0.4 < sx_ < 15.6 and 0.4 < sy_ < 15.6):
                    continue
            fade = 1 - smooth(0.45, 0.88, age)
            colr = (hexc("#ffffff") * (1 - hue[i]) + hexc("#ff6fd2") * hue[i]) if age < 0.25 else \
                (hexc("#c062ff") * (1 - hue[i]) + hexc("#ff3fb8") * hue[i])
            colr = colr * (0.35 + 0.65 * fade) + hexc("#2a1050") * (1 - fade) * 0.4
            sz = max(1, size[i] - int(age * 3))                # zerfallen beim Wegtreiben
            for dy in range(sz):
                for dx in range(sz):
                    X, Y = px + dx, py + dy
                    if 0 <= X < PW and 0 <= Y < PHt:
                        c[Y, X] = colr
                        a[Y, X] = 1
        frames.append(c)
        alphas.append(a)
    return np.concatenate(frames, 0), np.concatenate(alphas, 0)


pa, aa = particle_frames(5, False)
save("dust", pa, aa, frame_h=PHt, frametime=2, interpolate=False)
pb, ab = particle_frames(9, True)
save("dust_side", pb, ab, frame_h=PHt, frametime=2, interpolate=False)
DF = {"uv": [0, 0, 16, 16], "texture": "#dust"}
elements.append(box(PX0, PX1, PY0, PY1, 8.0, 8.0, {"north": dict(DF), "south": dict(DF)}, light=14, shade=False))
xm = 8 + curve(14) + back_at(14)
DS = {"uv": [0, 0, 16, 16], "texture": "#dust_side"}
elements.append(box(xm, xm, PY0, PY1, 8 - 2.35, 8 + 2.35, {"east": dict(DS), "west": dict(DS)}, light=14, shade=False))

model = {
    "gui_light": "front",
    "textures": {"particle": T + "blade", "blade": T + "blade", "edge": T + "edge", "back": T + "back",
                 "grip": T + "grip", "metal": T + "metal", "collar": T + "collar", "shard": T + "shard",
                 "dust": T + "dust", "dust_side": T + "dust_side"},
    "elements": elements,
    "display": {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 3.5, 0.5], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 3.5, 0.5], "scale": [0.75, 0.75, 0.75]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 2.4, 1.13], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 2.4, 1.13], "scale": [0.55, 0.55, 0.55]},
    },
}
with open(MODEL, "w") as f:
    json.dump(model, f, indent=1)
os.makedirs(os.path.dirname(ITEM_DEF), exist_ok=True)
with open(ITEM_DEF, "w") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "nuit_chains:item/" + NAME}}, f, indent=2)
print("Modell:", MODEL, "Elemente:", len(elements))
