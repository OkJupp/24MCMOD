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
SEG_MAIN, SEG_TIP = 12, 8           # Abschnitte der Klinge / der schrägen Spitze
YOKOTE = 0.87                        # ab hier (Anteil der Klingenlänge) beginnt die schräge Spitze
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
    if t <= YOKOTE:
        return EDGE0 - 0.25 * t
    ek, bt = EDGE0 - 0.25 * YOKOTE, BACK + 0.12
    u = (t - YOKOTE) / (1 - YOKOTE)
    return ek + (bt - ek) * u ** 1.7                 # Fukura: erst flach, dann zur Spitze hin rund einziehend


def back_at(y):
    t = (y - BASE_Y) / (TIP_Y - BASE_Y)
    return BACK + 0.12 * t


# ------------------------------------------------------------------ Texturen
# Energie-Welle: läuft in einem Durchlauf vom Griff zur Spitze (0 = Klingenansatz, 1 = Spitze)
WF = 16                                    # Bilder pro Durchlauf (je 2 Ticks -> 1,6 s)


def wave(pos, f):
    p = f / WF * 1.35 - 0.15
    return np.exp(-((pos - p) / 0.07) ** 2) + 0.45 * np.exp(-((pos - p + 0.55) / 0.09) ** 2)


# Klinge: Galaxie, 16 x 160 pro Bild (Sterne funkeln, Nebel schimmert, Energie-Welle)
BW, BH, BF = 16, 160, WF
neb1 = fbm(BW * 2, BH, 11, base=(2, 10))[:, ::2]
neb2 = fbm(BW * 2, BH, 23, base=(3, 16))[:, ::2]
dust = fbm(BW * 2, BH, 37, base=(2, 8))[:, ::2]
yy, xx = np.mgrid[0:BH, 0:BW]
deep = hexc("#0b0524") * (1 - yy[..., None] / BH) + hexc("#1c0a3a") * (yy[..., None] / BH)
pink, violet, blue = hexc("#ff3fb8"), hexc("#9a45ff"), hexc("#4a6cff")
band = np.sin((yy / BH * 2.2 + neb1 * 1.8) * np.pi * 2) * 0.5 + 0.5      # schräge Nebelbänder
nebula = smooth(0.42, 0.82, neb2) * (0.35 + 0.65 * band)
base_col = deep + violet * (nebula * 1.0)[..., None] + pink * (smooth(0.5, 0.82, neb2 * band) * 0.95)[..., None] \
    + blue * (smooth(0.48, 0.76, neb1) * (1 - band) * 0.65)[..., None]
base_col += hexc("#ffd0f0") * (smooth(0.72, 0.95, neb2 * band) * 0.35)[..., None]   # helle Nebelkerne
base_col *= (1 - 0.5 * smooth(0.55, 0.75, dust))[..., None]                # dunkle Staubbahnen
pos = 1 - (yy + 0.5) / BH                                                  # 0 Ansatz … 1 Spitze
n_stars = 230
sx, sy = rng.integers(0, BW, n_stars), rng.integers(0, BH, n_stars)
sb = rng.random(n_stars) ** 2.0
sph = rng.random(n_stars)
scol = np.where((rng.random(n_stars) < 0.35)[:, None], hexc("#ffb3ea"), hexc("#ffffff"))
frames = []
for f in range(BF):
    c = base_col.copy()
    shimmer = 0.9 + 0.1 * np.sin(2 * np.pi * (f / BF + neb1 * 2))
    w = wave(pos, f)
    c = c * (shimmer * (1 + 0.9 * w))[..., None] + hexc("#ff5fcf") * (0.3 * w)[..., None]
    tw = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * (f / BF + sph)))
    for i in range(n_stars):
        v = np.clip(0.4 + 0.9 * sb[i], 0, 1.2) * tw[i]
        c[sy[i], sx[i]] = np.maximum(c[sy[i], sx[i]], scol[i] * v)
        if sb[i] > 0.7:   # helle Sterne mit kleinem Kreuz
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                X, Y = sx[i] + dx, sy[i] + dy
                if 0 <= X < BW and 0 <= Y < BH:
                    c[Y, X] = np.maximum(c[Y, X], scol[i] * v * 0.45)
    frames.append(c)
save("blade", np.concatenate(frames, 0), frame_h=BH, frametime=2)

# Schneide 8 x 128 über die ganze Länge, animiert: Lavendel-Weiß, die Energie-Welle lässt sie pink-weiß aufleuchten
W, H = 8, 128
u = (np.arange(W) + 0.5) / W
grad = hexc("#a08cff")[None, :] * (1 - u[:, None]) + hexc("#f6eeff")[None, :] * u[:, None]
sparkle = rng.random((H, W)) < 0.05
epos = 1 - (np.arange(H) + 0.5) / H
frames = []
for f in range(WF):
    w = wave(epos, f)[:, None, None]
    c = np.broadcast_to(grad[None], (H, W, 3)).copy()
    c = c * (1 - 0.6 * np.minimum(w, 1)) + hexc("#ffd2f4") * 0.6 * np.minimum(w, 1) + hexc("#ff7ad8") * 0.25 * np.minimum(w, 1)
    c[sparkle] = hexc("#ffffff")
    frames.append(c)
save("edge", np.concatenate(frames, 0), frame_h=H, frametime=2)

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
cuts = np.linspace(0, YOKOTE, SEG_MAIN + 1) * L_BLADE
for i in range(SEG_MAIN):
    s0, s1 = cuts[i], cuts[i + 1]
    sm = (s0 + s1) / 2
    x0c, y0c, _ = arc(s0)
    _, ym, ang = arc(sm)
    seg_len = (s1 - s0) * 1.004                     # kaum Überlappung (sonst flackern übereinanderliegende Flächen)
    dz = 0.006 * (i % 2)                            # Nachbarn minimal versetzt: keine exakt gleichen Flächen
    b_, e_ = back_at(ym), edge_at(ym)
    split = e_ - EDGE_STRIP
    vt, vb = r(16 - 16 * s1 / L_BLADE), r(16 - 16 * s0 / L_BLADE)   # oben/unten auf der Textur (Spitze = 0)
    uw = r(16 * (split - b_) / (EDGE0 - BACK))
    faces = {"north": {"uv": [uw, vt, 0, vb], "texture": "#blade"}, "south": {"uv": [0, vt, uw, vb], "texture": "#blade"},
             "west": {"uv": [0, vt, 3, vb], "texture": "#blade"}}            # Rücken: Galaxie läuft drumherum
    ef = {"north": {"uv": [16, vt, 0, vb], "texture": "#edge"}, "south": {"uv": [0, vt, 16, vb], "texture": "#edge"},
          "east": {"uv": [12, vt, 16, vb], "texture": "#edge"}}
    if i == 0:
        faces["down"] = {"uv": [0, 14, 3, 16], "texture": "#blade"}
        ef["down"] = {"uv": [12, 14, 16, 16], "texture": "#edge"}
    elements.append(rbox(8 + x0c, y0c, b_, split, seg_len, 7.72 - dz, 8.28 + dz, ang, faces, light=6))
    elements.append(rbox(8 + x0c, y0c, split, e_, seg_len, 7.85 - dz, 8.15 + dz, ang, ef, light=8))

# Spitze (Kissaki): Galaxie in feinen Stufen, darüber eine durchgehende helle Schneide entlang der Schräge,
# die die Stufen-Ecken verdeckt -> saubere, gerade Spitze
kcuts = np.linspace(YOKOTE, 1.0, SEG_TIP + 1) * L_BLADE
for i in range(SEG_TIP):
    s0, s1 = kcuts[i], kcuts[i + 1]
    x0c, y0c, _ = arc(s0)
    _, ym, ang = arc((s0 + s1) / 2)
    _, y1w, _ = arc(s1)
    b_ = back_at(ym)
    e1 = edge_at(y1w)                                # Schräge am oberen Ende: Stufe bleibt innerhalb der Linie
    top = max(b_ + 0.06, e1 - 0.04)
    vt, vb = r(16 - 16 * s1 / L_BLADE), r(16 - 16 * s0 / L_BLADE)
    uw = r(16 * (top - b_) / (EDGE0 - BACK))
    dz = 0.006 * ((i + SEG_MAIN) % 2)
    faces = {"north": {"uv": [uw, vt, 0, vb], "texture": "#blade"}, "south": {"uv": [0, vt, uw, vb], "texture": "#blade"},
             "west": {"uv": [0, vt, 3, vb], "texture": "#blade"}}
    if i == SEG_TIP - 1:
        faces["up"] = {"uv": [0, 0, 3, 1], "texture": "#blade"}
    elements.append(rbox(8 + x0c, y0c, b_, top, (s1 - s0) * 1.004, 7.74 - dz, 8.26 + dz, ang, faces, light=6))
# helle Schneide entlang der runden Spitze: kurze Stücke, jedes von Punkt zu Punkt der Kurve
kp = np.linspace(YOKOTE, 1.0, 6) * L_BLADE
for j in range(len(kp) - 1):
    vt, vb = r(16 - 16 * kp[j + 1] / L_BLADE), r(16 - 16 * kp[j] / L_BLADE)
    KF = {"north": {"uv": [16, vt, 0, vb], "texture": "#edge"}, "south": {"uv": [0, vt, 16, vb], "texture": "#edge"},
          "east": {"uv": [12, vt, 16, vb], "texture": "#edge"}, "up": {"uv": [12, vt, 16, vb], "texture": "#edge"},
          "down": {"uv": [12, vt, 16, vb], "texture": "#edge"}}
    xa, ya, anga = arc(kp[j])
    xb2, yb2, angb = arc(kp[j + 1])
    ea, eb = edge_at(ya), edge_at(yb2)
    pa = np.array([xa + ea * np.cos(np.radians(anga)), ya + ea * np.sin(np.radians(anga))])
    pb = np.array([xb2 + eb * np.cos(np.radians(angb)), yb2 + eb * np.sin(np.radians(angb))])
    d = pb - pa
    seg = float(np.hypot(*d)) * 1.03
    ang_cut = float(np.degrees(np.arctan2(-d[0], d[1])))          # gegen den Uhrzeigersinn = zum Rücken hin
    w = EDGE_STRIP * (0.95 - 0.12 * j)
    elements.append(rbox(8 + pa[0], pa[1], -w, 0.0, seg, 7.7 - 0.004 * (j % 2), 8.3 + 0.004 * (j % 2), ang_cut, KF, light=8))

# ------------------------------------------------------------------ Habaki, Tsuba, Griff
CF = {k: {"uv": [0, 0, 16, 16], "texture": "#collar"} for k in ("north", "south", "east", "west", "up", "down")}
elements.append(box(8 + BACK - 0.05, 8 + EDGE0 + 0.05, 3.5, BASE_Y, 7.62, 8.38, CF, light=3))
MF = {k: {"uv": [0, 0, 16, 16], "texture": "#metal"} for k in ("north", "south", "east", "west", "up", "down")}
MS = {k: {"uv": [2, 2, 14, 14], "texture": "#metal"} for k in ("east", "west", "north", "south")}
elements.append(box(5.4, 10.6, 2.85, 3.6, 6.5, 9.5, MF))
elements.append(box(6.1, 9.9, 2.88, 3.57, 5.9, 10.1, MF))   # etwas flacher: Ober-/Unterseiten nicht deckungsgleich (Flackern)
elements.append(box(6.95, 9.05, 2.45, 2.85, 7.25, 8.75, CF, light=2))          # Fuchi
GF = {k: {"uv": [0, 0, 16, 16], "texture": "#grip"} for k in ("north", "south", "east", "west")}
elements.append(box(7.1, 8.9, -2.5, 2.45, 7.35, 8.65, GF))                     # Tsuka
elements.append(box(6.98, 9.02, -3.0, -2.5, 7.25, 8.75, dict(MF, **{"down": {"uv": [4, 4, 12, 12], "texture": "#metal"}})))  # Kashira

# ------------------------------------------------------------------ Partikel-Flächen (animiert)
# Fläche A in der Klingenebene: leuchtender Schein rund um die Klinge (pulsiert mit der Energie-Welle) und
# Partikel, die sich vom Rücken lösen. Fläche B quer dazu. Jeder Pixel weiß, wo er im Modell liegt –
# was aus dem Slot ragen würde, wird weggelassen.
PX0, PX1, PY0, PY1 = 8 - 6.0, 8 + 1.9, BASE_Y - 0.3, 20.2
PW, PHt, PF = 72, 150, 2 * WF            # 32 Bilder à 1 Tick = 1,6 s, gleicher Takt wie die Energie-Welle

# Schein: Abstand jedes Pixels zur Klinge
_gx = PX0 + (np.arange(PW) + 0.5) / PW * (PX1 - PX0)
_gy = PY1 - (np.arange(PHt) + 0.5) / PHt * (PY1 - PY0)
GX, GY = np.meshgrid(_gx, _gy)
_tipx, _tipy, _ = arc(L_BLADE)
_cxy = 8 + curve(GY)
_lo = _cxy + np.vectorize(back_at)(GY)
_hi = _cxy + np.vectorize(edge_at)(GY)
DIST = np.maximum(0, np.maximum(_lo - GX, GX - _hi))
DIST = np.where(GY > _tipy, np.hypot(GX - (8 + _tipx + back_at(_tipy)), GY - _tipy), DIST)
INSIDE = (DIST <= 0) & (GY >= BASE_Y) & (GY <= _tipy)
GPOS = np.clip((GY - BASE_Y) / (_tipy - BASE_Y), 0, 1)
_sx, _sy = np.vectorize(to_screen)(GX, GY)
IN_SLOT = (_sx > 0.4) & (_sx < 15.6) & (_sy > 0.4) & (_sy < 15.6)
HALO = 0.62 * np.exp(-DIST / 0.42) * smooth(BASE_Y, BASE_Y + 1.2, GY) * ~INSIDE * IN_SLOT
HALO_COL = hexc("#ff5fd6")[None, None, :] * np.exp(-DIST / 0.5)[..., None] + \
    hexc("#9b5cff")[None, None, :] * (1 - np.exp(-DIST / 0.5))[..., None]


def particle_frames(seed, side):
    rr = np.random.default_rng(seed)
    n = 75
    t0 = 0.3 + 0.7 * rr.random(n) ** 0.6                 # wo am Rücken (0 Wurzel … 1 Spitze)
    ph = rr.random(n)
    spd = 0.35 + 0.9 * rr.random(n) ** 1.5               # viele bleiben nah am Rücken
    size = rr.choice([1, 1, 2], n) * (1 if side else 2)   # Fläche A hat feinere Pixel
    hue = rr.random(n)
    frames, alphas = [], []
    for f in range(PF):
        c = np.zeros((PHt, PW, 3))
        a = np.zeros((PHt, PW))
        if not side:
            surge = 0.7 + 0.65 * np.minimum(wave(GPOS, f / PF * WF), 1.2)
            a = np.clip(HALO * surge, 0, 0.8)
            c = HALO_COL * (0.85 + 0.3 * np.minimum(wave(GPOS, f / PF * WF), 1))[..., None]
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
            sz = max(1, size[i] - int(age * 3 * (1 if side else 2)))   # zerfallen beim Wegtreiben
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
save("dust", pa, aa, frame_h=PHt, frametime=1, interpolate=False)
pb, ab = particle_frames(9, True)
save("dust_side", pb, ab, frame_h=PHt, frametime=1, interpolate=False)
# Rückseite mit gespiegelten u-Werten, damit Schein und Partikel von beiden Seiten an derselben Stelle liegen
elements.append(box(PX0, PX1, PY0, PY1, 8.0, 8.0, {"north": {"uv": [16, 0, 0, 16], "texture": "#dust"},
                                                    "south": {"uv": [0, 0, 16, 16], "texture": "#dust"}},
                    light=15, shade=False))
xm = 8 + curve(14)                               # Mitte der Klinge: liegt in keiner Außenfläche
DS = {"uv": [0, 0, 16, 16], "texture": "#dust_side"}
elements.append(box(xm, xm, PY0, PY1, 8 - 2.35, 8 + 2.35, {"east": dict(DS), "west": dict(DS)}, light=14, shade=False))



def mirror(e):
    """Element an der Klingen-Achse (Diagonale durch 8,8) spiegeln: dreht die Klinge um ihre Längsachse.
    Ohne das zeigen Schneide und Wölbung in der Hand zum Spieler statt nach vorne."""
    rot = e["rotation"]
    ox, oy, oz = rot["origin"]
    nox, noy = 8 + (oy - 8), 8 + (ox - 8)
    (fx, fy, fz), (tx, ty, tz) = e["from"], e["to"]
    e["from"] = [r(nox - (tx - ox)), r(noy + (fy - oy)), fz]
    e["to"] = [r(nox - (fx - ox)), r(noy + (ty - oy)), tz]
    e["rotation"] = {"origin": [r(nox), r(noy), oz], "axis": "z", "angle": r(-90 - rot["angle"])}
    faces = {}
    for k, f in e["faces"].items():
        f = dict(f)
        nk = {"east": "west", "west": "east"}.get(k, k)
        if nk in ("north", "south", "up", "down"):
            u1, v1, u2, v2 = f["uv"]
            f["uv"] = [u2, v1, u1, v2]
        faces[nk] = f
    e["faces"] = faces
    return e


elements = [mirror(e) for e in elements]

model = {
    "gui_light": "front",
    "textures": {"particle": T + "blade", "blade": T + "blade", "edge": T + "edge",
                 "grip": T + "grip", "metal": T + "metal", "collar": T + "collar",
                 "dust": T + "dust", "dust_side": T + "dust_side"},
    "elements": elements,
    "display": {
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 3.5, 0.5], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 3.5, 0.5], "scale": [0.75, 0.75, 0.75]},
        # z-Drehung 50 statt 25: Klinge steht aufrechter und zeigt leicht nach vorne (weg von dir)
        # deutlich höher (y 12), damit Griff und Stichblatt im Bild sind; Klinge ragt dafür weiter nach oben
        "firstperson_righthand": {"rotation": [0, -90, 50], "translation": [1.13, 12.0, 1.13], "scale": [0.78, 0.78, 0.78]},
        "firstperson_lefthand": {"rotation": [0, 90, -50], "translation": [1.13, 12.0, 1.13], "scale": [0.78, 0.78, 0.78]},
    },
}
with open(MODEL, "w") as f:
    json.dump(model, f, indent=1)
os.makedirs(os.path.dirname(ITEM_DEF), exist_ok=True)
with open(ITEM_DEF, "w") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "nuit_chains:item/" + NAME}}, f, indent=2)
print("Modell:", MODEL, "Elemente:", len(elements))
