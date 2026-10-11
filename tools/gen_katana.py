"""Galaxy-Katana als Totem der Unsterblichkeit (Paket "Pinkes HUD").

Grundlage ist das Katana-Modell aus "Simple Katana Swords" von Dark33 (MIT-Lizenz, siehe
tools/vorlagen/simple_katana_swords/LICENSE): Form und Halte-Positionen bleiben wie vom Autor
eingestellt. Neu sind:
  - eigene, animierte Textur (128x128): Klinge als Galaxie mit funkelnden Sternen und einer
    Energie-Welle, die zur Spitze läuft, helle Schneide, schwarzer Griff mit pinken Rauten,
    dunkles Stichblatt mit pinkem Rand
  - leuchtende Teile (light_emission)
  - eine Fläche mit pinkem Schein um die Klinge und Pixel-Partikeln, die sich vom Klingenrücken
    lösen, plus eine Querfläche mit Partikeln für die Seitenansicht

Aufruf: gen_katana.py PACK_ASSETS_DIR
  (schreibt minecraft/items/totem_of_undying.json, nuit_chains/models/item/galaxy_katana.json
   und nuit_chains/textures/item/galaxy_katana/*)
"""
import copy
import json
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BASE_MODEL = os.path.join(HERE, "vorlagen", "simple_katana_swords", "diamond_katana.json")
OUT = sys.argv[1]
NAME = "galaxy_katana"
TEX_DIR = os.path.join(OUT, "nuit_chains", "textures", "item", NAME)
MODEL = os.path.join(OUT, "nuit_chains", "models", "item", NAME + ".json")
ITEM_DEF = os.path.join(OUT, "minecraft", "items", "totem_of_undying.json")
T = "nuit_chains:item/" + NAME + "/"
os.makedirs(TEX_DIR, exist_ok=True)
os.makedirs(os.path.dirname(MODEL), exist_ok=True)
for f in os.listdir(TEX_DIR):                      # alte Texturen der früheren Version weg
    os.remove(os.path.join(TEX_DIR, f))


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
    if frame_h:
        # nicht quadratische Bilder -> Breite/Höhe angeben, sonst schneidet Minecraft falsch
        anim = {"frametime": frametime, "interpolate": interpolate, "width": rgb.shape[1], "height": frame_h}
        with open(os.path.join(TEX_DIR, name + ".png.mcmeta"), "w") as f:
            json.dump({"animation": anim}, f, indent=2)


def value_noise(w, h, cx, cy, seed):
    g = np.random.default_rng(seed).random((cy + 1, cx + 1)).astype(np.float32)
    im = Image.fromarray((g * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)
    return np.asarray(im).astype(np.float64) / 255


def fbm(w, h, seed, base=(3, 12), octaves=4):
    out = np.zeros((h, w))
    a, tot = 1.0, 0.0
    for o in range(octaves):
        out += value_noise(w, h, base[0] * 2 ** o, base[1] * 2 ** o, seed + o * 31) * a
        tot += a
        a *= 0.55
    return out / tot


base = json.load(open(BASE_MODEL))
HANDLE, GUARD, TIP, BLADE = 0, 1, 2, 3          # Reihenfolge der Elemente im Vorlagen-Modell
# Lage im Modell (nach der Drehung der Vorlage): Klinge x 7..9, z 7..8, y 8..31; Spitze x 7..8 (Rücken), y 31..32
BL_X0, BL_X1, BL_Z0, BL_Z1, BL_Y0, BL_Y1 = 7.0, 9.0, 7.0, 8.0, 8.0, 31.0

# ------------------------------------------------------------------ Energie-Welle (gemeinsamer Takt)
WF = 16                                            # Bilder pro Durchlauf (je 2 Ticks -> 1,6 s)


def wave(pos, f):
    """Helligkeit der Welle bei Position pos (0 Klingenansatz … 1 Spitze) im Bild f (0..WF)."""
    p = f / WF * 1.35 - 0.15
    return np.exp(-((pos - p) / 0.07) ** 2) + 0.45 * np.exp(-((pos - p + 0.55) / 0.09) ** 2)


# ------------------------------------------------------------------ Textur 128x128, 16 Bilder
S = 128
PXU = S / 16                                       # Pixel pro UV-Einheit
deep, deep2 = hexc("#0b0524"), hexc("#1c0a3a")
pink, violet, blue = hexc("#ff3fb8"), hexc("#9a45ff"), hexc("#4a6cff")
edge_lo, edge_hi = hexc("#a08cff"), hexc("#f6eeff")


def rect_of(uv):
    u1, v1, u2, v2 = uv
    x0, x1 = int(round(min(u1, u2) * PXU)), int(round(max(u1, u2) * PXU))
    y0, y1 = int(round(min(v1, v2) * PXU)), int(round(max(v1, v2) * PXU))
    return x0, max(x1, x0 + 1), y0, max(y1, y0 + 1)


def galaxy(w, h, seed, edge_side):
    """Galaxie-Streifen (h hoch = Klingenlänge, oben = Spitze), Schneide an edge_side ('l', 'r' oder None)."""
    r = np.random.default_rng(seed)
    n1 = fbm(w * 3, h, seed, base=(2, 8))[:, ::3]
    n2 = fbm(w * 3, h, seed + 7, base=(3, 13))[:, ::3]
    du = fbm(w * 3, h, seed + 13, base=(2, 6))[:, ::3]
    yy, xx = np.mgrid[0:h, 0:w]
    bg = deep * (1 - yy[..., None] / h) + deep2 * (yy[..., None] / h)
    band = np.sin((yy / h * 2.0 + n1 * 1.8) * np.pi * 2) * 0.5 + 0.5
    neb = smooth(0.42, 0.82, n2) * (0.35 + 0.65 * band)
    col = bg + violet * (neb * 1.0)[..., None] + pink * (smooth(0.5, 0.82, n2 * band) * 0.95)[..., None] \
        + blue * (smooth(0.48, 0.76, n1) * (1 - band) * 0.6)[..., None]
    col += hexc("#ffd0f0") * (smooth(0.72, 0.95, n2 * band) * 0.35)[..., None]
    col *= (1 - 0.5 * smooth(0.55, 0.75, du))[..., None]
    pos = 1 - (yy + 0.5) / h
    ns = max(4, int(w * h / 14))
    sx, sy = r.integers(0, w, ns), r.integers(0, h, ns)
    sb, sph = r.random(ns) ** 2.0, r.random(ns)
    scol = np.where((r.random(ns) < 0.35)[:, None], hexc("#ffb3ea"), hexc("#ffffff"))
    frames = []
    for f in range(WF):
        wv = wave(pos, f)
        c = col * (1 + 0.9 * wv)[..., None] + hexc("#ff5fcf") * (0.3 * wv)[..., None]
        tw = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * (f / WF + sph)))
        for i in range(ns):
            v = np.clip(0.4 + 0.9 * sb[i], 0, 1.2) * tw[i]
            c[sy[i], sx[i]] = np.maximum(c[sy[i], sx[i]], scol[i] * v)
        if edge_side:
            e = w - 1 if edge_side == "r" else 0
            e2 = w - 2 if edge_side == "r" else 1
            g = np.minimum(wv, 1)[:, 0][:, None]
            c[:, e] = edge_hi * (1 - 0.4 * g) + hexc("#ffd2f4") * 0.4 * g
            c[:, e2] = c[:, e2] * 0.35 + edge_lo * 0.65
        frames.append(c)
    return frames


def wrap(w, h):
    """Griff: schwarze Wicklung mit pinken Rauten."""
    yy, xx = np.mgrid[0:h, 0:w]
    c = np.zeros((h, w, 3)) + hexc("#0e0b14")
    cell = 8
    dxr = np.abs(((xx + 0.5) % cell) - cell / 2)
    dyr = np.abs(((yy + 0.5) % cell) - cell / 2)
    c[np.abs(((xx + yy) % cell) - cell / 2) < 0.8] = hexc("#2a2236")
    c[dxr + dyr < cell * 0.34] = hexc("#5c2d8f")
    c[dxr + dyr < cell * 0.18] = hexc("#e04aae")
    return c


def metal(w, h, rim=True):
    yy, xx = np.mgrid[0:h, 0:w]
    c = np.zeros((h, w, 3)) + hexc("#241e2c")
    c[(xx + yy) % 4 == 0] = hexc("#352c42")
    if rim:
        border = (xx == 0) | (yy == 0) | (xx == w - 1) | (yy == h - 1)
        c[border] = hexc("#c13c9e")
    return c


tex = np.zeros((WF, S, S, 3))
alpha = np.zeros((S, S))


def paint(rect, frames_or_img):
    x0, x1, y0, y1 = rect
    alpha[y0:y1, x0:x1] = 1
    if isinstance(frames_or_img, list):
        for f in range(WF):
            tex[f, y0:y1, x0:x1] = frames_or_img[f]
    else:
        tex[:, y0:y1, x0:x1] = frames_or_img[None]


els = base["elements"]
for k, face in els[BLADE]["faces"].items():
    x0, x1, y0, y1 = rect_of(face["uv"])
    w, h = x1 - x0, y1 - y0
    if k == "east":                       # breite Seite (+z): Schneide rechts
        paint((x0, x1, y0, y1), galaxy(w, h, 11, "r"))
    elif k == "west":                     # breite Seite (-z): Schneide links
        paint((x0, x1, y0, y1), galaxy(w, h, 19, "l"))
    elif k == "north":                    # schmale Seite x=9: die Schneide
        fr = []
        pos = 1 - (np.arange(h) + 0.5) / h
        for f in range(WF):
            g = np.minimum(wave(pos, f), 1)[:, None, None]
            fr.append(np.broadcast_to(edge_hi * (1 - 0.4 * g) + hexc("#ffd2f4") * 0.4 * g, (h, w, 3)).copy())
        paint((x0, x1, y0, y1), fr)
    elif k == "south":                    # schmale Seite x=7: Rücken, Galaxie
        paint((x0, x1, y0, y1), galaxy(w, h, 23, None))
    else:
        paint((x0, x1, y0, y1), np.zeros((h, w, 3)) + hexc("#2a1a40"))
for k, face in els[TIP]["faces"].items():
    x0, x1, y0, y1 = rect_of(face["uv"])
    paint((x0, x1, y0, y1), np.zeros((y1 - y0, x1 - x0, 3)) + hexc("#f3e6ff"))
for k, face in els[HANDLE]["faces"].items():
    x0, x1, y0, y1 = rect_of(face["uv"])
    paint((x0, x1, y0, y1), wrap(x1 - x0, y1 - y0) if k not in ("up", "down") else metal(x1 - x0, y1 - y0))
for k, face in els[GUARD]["faces"].items():
    x0, x1, y0, y1 = rect_of(face["uv"])
    paint((x0, x1, y0, y1), metal(x1 - x0, y1 - y0))
save("katana", tex.reshape(WF * S, S, 3), np.tile(alpha, (WF, 1)), frame_h=S, frametime=2)

# ------------------------------------------------------------------ Modell
model = copy.deepcopy(base)
model.pop("groups", None)
model["credit"] = "Form: Simple Katana Swords von Dark33 (MIT) - Galaxy-Look: Nuit Chains"
model["texture_size"] = [S, S]
model["textures"] = {"0": T + "katana", "particle": T + "katana", "dust": T + "dust", "dust_side": T + "dust_side"}
for e in model["elements"]:
    for f in e["faces"].values():
        f["texture"] = "#0"
model["elements"][BLADE]["light_emission"] = 9
model["elements"][TIP]["light_emission"] = 12
GUI = model["display"]["gui"]


def gui_screen(x, y):
    """Modellpunkt (x, y) in der Bildebene -> Position im Inventar-Slot (0..16) mit der gui-Ansicht."""
    s = GUI.get("scale", [1, 1, 1])[0]
    a = np.radians(GUI.get("rotation", [0, 0, 0])[2])
    tx, ty = GUI.get("translation", [0, 0, 0])[:2]
    dx, dy = (x - 8) * s, (y - 8) * s
    return 8 + tx + dx * np.cos(a) - dy * np.sin(a), 8 + ty + dx * np.sin(a) + dy * np.cos(a)


# ------------------------------------------------------------------ Schein + Partikel (Fläche in der Klingenebene)
PX0, PX1, PY0, PY1 = 2.0, 10.8, 7.6, 33.6
PZ = (BL_Z0 + BL_Z1) / 2                          # Mitte der Klinge: liegt in keiner Außenfläche
DENS = 6                                          # Pixel pro Modell-Einheit
PW, PH = int(round((PX1 - PX0) * DENS)), int(round((PY1 - PY0) * DENS))
PF = 2 * WF                                       # 32 Bilder à 1 Tick = gleicher Takt wie die Welle
gx = PX0 + (np.arange(PW) + 0.5) / DENS
gy = PY1 - (np.arange(PH) + 0.5) / DENS
GX, GY = np.meshgrid(gx, gy)
dxb = np.maximum(0, np.maximum(BL_X0 - GX, GX - BL_X1))
dyb = np.maximum(0, np.maximum(BL_Y0 - GY, GY - BL_Y1))
DIST = np.hypot(dxb, dyb)
INSIDE = DIST <= 0
SX_PLANE, SZ0, SZ1 = 8.02, PZ - 2.5, PZ + 2.5     # knapp neben x=8, damit nichts mit der Spitze (x 7..8) zusammenfällt
GPOS = np.clip((GY - BL_Y0) / (BL_Y1 - BL_Y0), 0, 1)
SX, SY = np.vectorize(gui_screen)(GX, GY)
IN_SLOT = (SX > 0.3) & (SX < 15.7) & (SY > 0.3) & (SY < 15.7)
HALO = 0.6 * np.exp(-DIST / 0.5) * smooth(BL_Y0 - 0.2, BL_Y0 + 1.5, GY) * ~INSIDE * IN_SLOT
HALO_COL = hexc("#ff5fd6")[None, None, :] * np.exp(-DIST / 0.7)[..., None] + \
    hexc("#9b5cff")[None, None, :] * (1 - np.exp(-DIST / 0.7))[..., None]


def particle_frames(seed, side):
    rr = np.random.default_rng(seed)
    n = 70
    t0 = 0.25 + 0.75 * rr.random(n) ** 0.6          # wo am Rücken (0 Ansatz … 1 Spitze)
    ph = rr.random(n)
    spd = 0.35 + 0.9 * rr.random(n) ** 1.5
    size = rr.choice([1, 1, 2], n)
    hue = rr.random(n)
    if side:
        W, H, x0w = int((SZ1 - SZ0) * DENS), PH, SZ0  # Querfläche quer zur Klinge
    else:
        W, H, x0w = PW, PH, PX0
    frames, alphas = [], []
    for f in range(PF):
        if side:
            c, a = np.zeros((H, W, 3)), np.zeros((H, W))
        else:
            wv = np.minimum(wave(GPOS, f / PF * WF), 1.2)
            a = np.clip(HALO * (0.7 + 0.65 * wv), 0, 0.8)
            c = HALO_COL * (0.85 + 0.3 * np.minimum(wv, 1))[..., None]
        for i in range(n):
            age = (f / PF + ph[i]) % 1.0
            if age > 0.88:
                continue
            y = BL_Y0 + t0[i] * (BL_Y1 - BL_Y0 - 0.5) + 1.4 * age * spd[i]
            if side:
                x = PZ + (2.2 * age * spd[i]) * (1 if i % 2 else -1)
            else:
                x = BL_X0 - 0.1 - 3.6 * age * spd[i]           # vom Rücken (x=7) weg
                sx_, sy_ = gui_screen(x, y)
                if not (0.4 < sx_ < 15.6 and 0.4 < sy_ < 15.6):
                    continue
            px = int((x - x0w) * DENS)
            py = int((PY1 - y) * DENS)
            fade = 1 - smooth(0.45, 0.88, age)
            colr = (hexc("#ffffff") * (1 - hue[i]) + hexc("#ff6fd2") * hue[i]) if age < 0.25 else \
                (hexc("#c062ff") * (1 - hue[i]) + hexc("#ff3fb8") * hue[i])
            colr = colr * (0.35 + 0.65 * fade) + hexc("#2a1050") * (1 - fade) * 0.4
            sz = max(1, size[i] - int(age * 2))
            for dy in range(sz):
                for dx in range(sz):
                    X, Y = px + dx, py + dy
                    if 0 <= X < W and 0 <= Y < H:
                        c[Y, X] = colr
                        a[Y, X] = 1
        frames.append(c)
        alphas.append(a)
    return np.concatenate(frames, 0), np.concatenate(alphas, 0), H


pa, aa, fh = particle_frames(5, False)
save("dust", pa, aa, frame_h=fh, frametime=1, interpolate=False)
pb, ab, fhb = particle_frames(9, True)
save("dust_side", pb, ab, frame_h=fhb, frametime=1, interpolate=False)
# Vorder- und Rückseite mit gespiegeltem u, damit Schein und Partikel von beiden Seiten an derselben Stelle liegen
model["elements"].append({
    "from": [PX0, PY0, PZ], "to": [PX1, PY1, PZ], "shade": False, "light_emission": 15,
    "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#dust"}, "north": {"uv": [16, 0, 0, 16], "texture": "#dust"}}})
model["elements"].append({
    "from": [SX_PLANE, PY0, SZ0], "to": [SX_PLANE, PY1, SZ1], "shade": False, "light_emission": 15,
    "faces": {"east": {"uv": [0, 0, 16, 16], "texture": "#dust_side"}, "west": {"uv": [16, 0, 0, 16], "texture": "#dust_side"}}})
with open(MODEL, "w") as f:
    json.dump(model, f, indent=1)
os.makedirs(os.path.dirname(ITEM_DEF), exist_ok=True)
with open(ITEM_DEF, "w") as f:
    json.dump({"model": {"type": "minecraft:model", "model": "nuit_chains:item/" + NAME}}, f, indent=2)
print("Modell:", MODEL, "Elemente:", len(model["elements"]))
