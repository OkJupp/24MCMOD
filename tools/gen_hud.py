"""Pinkes, animiertes HUD als GUI-Sprites (Minecraft 26.3).
Aufruf: gen_hud.py VANILLA_HUD_DIR OUT_HUD_DIR [FX_DIR] [HOTBAR_PNG]
Alle Maße in GUI-Pixeln; Texturen in S-facher Auflösung, animiert als senkrechte Bildstreifen + .mcmeta."""
import sys, os, json, math
import numpy as np
from PIL import Image

VAN, OUT = sys.argv[1], sys.argv[2]
S = 4                                    # Texel pro GUI-Pixel
os.makedirs(os.path.join(OUT, "heart"), exist_ok=True)

PINK = np.array([255, 92, 205]) / 255
PINK_HI = np.array([255, 196, 240]) / 255
VIOLET = np.array([150, 70, 255]) / 255
PLUM = np.array([22, 8, 28]) / 255
PLUM_D = np.array([12, 4, 16]) / 255
WHITE = np.ones(3)

# ------------------------------------------------------------------ Hilfen
rng = np.random.default_rng(24)
PERM = rng.permutation(256)
PERM = np.concatenate([PERM, PERM])
GV = rng.random(256)

def vnoise2(x, y):
    xi, yi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    xf, yf = x - xi, y - yi
    u, v = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
    xi &= 255; yi &= 255
    def h(dx, dy): return GV[PERM[PERM[xi + dx] + yi + dy]]
    a = h(0, 0) * (1 - u) + h(1, 0) * u
    b = h(0, 1) * (1 - u) + h(1, 1) * u
    return a * (1 - v) + b * v

def loop_noise(x, y, t, scale, radius=3.0, octaves=3):
    """Rauschen, das sich nach t=1 nahtlos wiederholt (Abtastfenster wandert im Kreis)."""
    s, n, amp, f = 0.0, 0.0, 1.0, 1.0
    for o in range(octaves):
        ox = radius * f * math.cos(2 * math.pi * t + o * 1.7)
        oy = radius * f * math.sin(2 * math.pi * t + o * 1.7)
        s += amp * vnoise2(x * scale * f + ox + o * 31.7, y * scale * f + oy + o * 17.3)
        n += amp; amp *= 0.5; f *= 2.03
    return s / n

def grid(w, h):
    """GUI-Koordinaten der Texel-Mitten."""
    xs = (np.arange(w * S) + 0.5) / S
    ys = (np.arange(h * S) + 0.5) / S
    return np.meshgrid(xs, ys)

def sd_rrect(X, Y, x0, y0, x1, y1, r):
    """Signierter Abstand zu einem abgerundeten Rechteck (negativ = innen)."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx, qy = np.abs(X - cx) - hx, np.abs(Y - cy) - hy
    out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
    return out + np.minimum(np.maximum(qx, qy), 0) - r

def perim_param(X, Y, x0, y0, x1, y1):
    """0..1 einmal rund um ein Rechteck (Winkel um die Mitte, auf Seitenverhältnis korrigiert)."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    a = np.arctan2((Y - cy) / (y1 - y0), (X - cx) / (x1 - x0))
    return (a / (2 * math.pi)) % 1.0

def wrap(d):
    return (d + 0.5) % 1.0 - 0.5

def over(dst, rgb, a):
    """Schicht (rgb, a) über dst (H,W,4, vormultipliziert nicht) legen."""
    a = np.clip(a, 0, 1)[..., None]
    da = dst[..., 3:4]
    oa = a + da * (1 - a)
    rgb = np.broadcast_to(rgb, dst[..., :3].shape) if np.ndim(rgb) == 1 else rgb
    col = (rgb * a + dst[..., :3] * da * (1 - a)) / np.maximum(oa, 1e-6)
    dst[..., :3] = col
    dst[..., 3:4] = oa

def add_light(dst, rgb, k):
    """Leuchten: hellt auf und macht sichtbar (wirkt wie additives Licht)."""
    k = np.clip(k, 0, 4)[..., None]
    rgb = np.broadcast_to(rgb, dst[..., :3].shape) if np.ndim(rgb) == 1 else rgb
    a0 = dst[..., 3:4]
    add = rgb * k
    # Licht auf durchsichtigem Grund: Deckkraft steigt mit der Helligkeit
    na = np.clip(a0 + (1 - a0) * np.clip(k, 0, 1) * 0.9, 0, 1)
    col = (dst[..., :3] * a0 + add) / np.maximum(na, 1e-6)
    dst[..., :3] = np.clip(col, 0, 1)
    dst[..., 3:4] = na

def to_img(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")

def save_anim(name, frames, frametime, w, h, interpolate=True):
    """Nur das erste Bild als feste Grafik: animierte GUI-Sprites werden in 26.3 nicht zuverlässig
    in den GUI-Atlas geschrieben (bleiben sonst lila-schwarz). Die Bewegung kommt per Code (PinkHud)."""
    path = os.path.join(OUT, name + ".png")
    to_img(frames[0]).save(path, optimize=True)
    if os.path.exists(path + ".mcmeta"):
        os.remove(path + ".mcmeta")
    print("saved", name, "(static)")

def save_static(name, arr):
    to_img(arr).save(os.path.join(OUT, name + ".png"), optimize=True)
    print("saved", name)

# ------------------------------------------------------------------ Bausteine

def cell(img, X, Y, t, x0, y0, phase, direction, bright=1.0):
    """Ein Slot: dunkler Kern, leuchtender Rahmen, darauf wandernde Energie und knisternde Adern.
    (x0, y0) = linke obere Ecke der 16x16-Fläche fürs Item."""
    rx0, ry0, rx1, ry1 = x0 - 1.2, y0 - 1.2, x0 + 17.2, y0 + 17.2
    d = sd_rrect(X, Y, rx0, ry0, rx1, ry1, 2.2)
    inside = d < 0
    # dunkler Kern mit weichem pinkem Innenschein
    over(img, PLUM_D, np.where(inside, 0.86, 0.0))
    inner = np.exp(-np.maximum(-d, 0) / 1.6) * inside
    add_light(img, PINK * 0.55, inner * 0.22 * bright)
    # Energie, die um den Rahmen läuft (zwei Stöße, gegenläufig je Slot)
    u = perim_param(X, Y, rx0, ry0, rx1, ry1)
    pulse = np.zeros_like(X)
    for k in range(2):
        c = (direction * t + phase + k * 0.5) % 1.0
        pulse += np.exp(-(wrap(u - c) / 0.07) ** 2)
    ring = np.exp(-(d / 0.42) ** 2)                      # scharfe Linie
    halo = np.exp(-np.abs(d) / 0.9)                      # weicher Schein
    # knisternde Adern entlang des Rahmens
    n = loop_noise(X, Y, t, 0.55)
    veins = np.exp(-((n - 0.5) / 0.035) ** 2) * np.exp(-(d / 1.3) ** 2)
    flick = 0.8 + 0.2 * np.sin(2 * math.pi * (t * 3 + phase))
    add_light(img, PINK, (halo * 0.32 + ring * 0.55) * bright * flick)
    add_light(img, PINK_HI, ring * (0.25 + 0.9 * pulse) * bright)
    add_light(img, WHITE, ring * pulse * 0.55 * bright)
    add_light(img, PINK_HI, veins * (0.35 + 0.6 * pulse) * bright)

# ------------------------------------------------------------------ Hotbar 182x22

def hotbar(N=24):
    W, H = 182, 22
    X, Y = grid(W, H)
    frames = []
    base_noise = vnoise2(X * 0.35, Y * 0.35)
    for k in range(N):
        t = k / N
        img = np.zeros(X.shape + (4,))
        # Grundplatte: dunkles Glas mit feinem Rahmen
        d = sd_rrect(X, Y, 0.6, 0.6, 181.4, 21.4, 2.6)
        over(img, PLUM * (0.85 + 0.3 * base_noise[..., None]), np.where(d < 0, 0.80, 0.0))
        edge = np.exp(-(d / 0.5) ** 2)
        u = perim_param(X, Y, 0.6, 0.6, 181.4, 21.4)
        run = np.exp(-(wrap(u - t) / 0.025) ** 2) + np.exp(-(wrap(u - t - 0.5) / 0.025) ** 2)
        add_light(img, VIOLET, edge * 0.45)
        add_light(img, PINK_HI, edge * run * 0.9)
        for i in range(9):
            cell(img, X, Y, t, 3 + 20 * i, 3, phase=i * 0.37, direction=1 if i % 2 == 0 else -1)
        frames.append(img)
    save_anim("hotbar", frames, 2, W, H)

def selection(N=24):
    W, H = 24, 23
    X, Y = grid(W, H)
    frames = []
    for k in range(N):
        t = k / N
        img = np.zeros(X.shape + (4,))
        x0, y0, x1, y1 = 1.6, 1.6, 22.4, 22.4
        d = sd_rrect(X, Y, x0, y0, x1, y1, 3.0)
        beat = 0.82 + 0.18 * math.sin(2 * math.pi * t * 2)
        # pinker Schimmer im ausgewählten Feld
        fill = np.where(d < 0, 0.10 + 0.30 * np.exp(d / 2.2), 0.0)
        over(img, PINK, fill * beat)
        u = perim_param(X, Y, x0, y0, x1, y1)
        pulse = np.zeros_like(X)
        for j in range(3):
            pulse += np.exp(-(wrap(u - (t * 2 + j / 3)) / 0.06) ** 2)
        ring = np.exp(-(d / 0.75) ** 2)
        halo = np.exp(-np.abs(d) / 1.3)
        n = loop_noise(X, Y, t, 0.8, radius=4.0)
        veins = np.exp(-((n - 0.5) / 0.04) ** 2) * np.exp(-(d / 1.6) ** 2)
        add_light(img, PINK, (halo * 0.7 + ring * 0.9) * beat)
        add_light(img, PINK_HI, ring * (0.6 + 0.8 * pulse))
        add_light(img, WHITE, ring * pulse * 0.8 + veins * 0.5)
        frames.append(img)
    save_anim("hotbar_selection", frames, 2, W, H)

def offhand(side, N=24):
    W, H = 29, 24
    X, Y = grid(W, H)
    ox = 0 if side == "left" else 7
    frames = []
    for k in range(N):
        t = k / N
        img = np.zeros(X.shape + (4,))
        d = sd_rrect(X, Y, ox + 0.6, 1.6, ox + 21.4, 22.4, 2.6)
        over(img, PLUM, np.where(d < 0, 0.80, 0.0))
        add_light(img, VIOLET, np.exp(-(d / 0.5) ** 2) * 0.45)
        cell(img, X, Y, t, ox + 3, 4, phase=0.21 if side == "left" else 0.71, direction=1)
        frames.append(img)
    save_anim("hotbar_offhand_" + side, frames, 2, W, H)

# ------------------------------------------------------------------ XP-Leiste 182x5

def xp_background():
    W, H = 182, 5
    X, Y = grid(W, H)
    img = np.zeros(X.shape + (4,))
    d = sd_rrect(X, Y, 0.5, 0.5, 181.5, 4.5, 1.6)
    over(img, PLUM_D, np.where(d < 0, 0.85, 0.0))
    seg = np.exp(-(((X % 10.0) - 0.0) / 0.35) ** 2) * (d < -0.4)
    add_light(img, VIOLET, seg * 0.25 + np.exp(-(d / 0.4) ** 2) * 0.5)
    save_static("experience_bar_background", img)

def xp_progress(N=16):
    W, H = 182, 5
    X, Y = grid(W, H)
    frames = []
    for k in range(N):
        t = k / N
        img = np.zeros(X.shape + (4,))
        d = sd_rrect(X, Y, 0.5, 0.5, 181.5, 4.5, 1.6)
        inside = d < 0
        vert = np.clip((Y - 0.5) / 4.0, 0, 1)[..., None]
        base = PINK_HI * (1 - vert) * 0.7 + PINK * (0.3 + 0.7 * vert) * 0.9
        stripes = 0.5 + 0.5 * np.sin(2 * math.pi * ((X + Y * 1.2) / 6.0 - t))
        col = base * (0.78 + 0.32 * stripes[..., None])
        over(img, col, np.where(inside, 0.97, 0.0))
        gloss = np.exp(-((Y - 1.2) / 0.55) ** 2) * inside
        add_light(img, WHITE, gloss * 0.35)
        sweep = np.exp(-((((X / 182.0) - t) + 0.5) % 1.0 - 0.5) ** 2 / 0.0006) * inside
        add_light(img, WHITE, sweep * 0.5)
        add_light(img, PLUM * 0 + VIOLET, np.exp(-(d / 0.35) ** 2) * 0.35)
        frames.append(img)
    save_anim("experience_bar_progress", frames, 2, W, H)

# ------------------------------------------------------------------ Symbole (9x9, Pixel-Art umgefärbt)

def load(name):
    return np.asarray(Image.open(os.path.join(VAN, name + ".png")).convert("RGBA")).astype(np.float64) / 255

def rgb2hsv(c):
    r, g, b = c[..., 0], c[..., 1], c[..., 2]
    mx, mn = c[..., :3].max(-1), c[..., :3].min(-1)
    d = mx - mn
    h = np.zeros_like(mx)
    m = d > 1e-6
    rr = m & (mx == r); gg = m & (mx == g) & ~rr; bb = m & ~rr & ~gg
    h[rr] = ((g - b)[rr] / d[rr]) % 6
    h[gg] = (b - r)[gg] / d[gg] + 2
    h[bb] = (r - g)[bb] / d[bb] + 4
    h = h / 6
    s = np.where(mx > 1e-6, d / np.maximum(mx, 1e-6), 0)
    return h, s, mx

def hsv2rgb(h, s, v):
    i = np.floor(h * 6).astype(int) % 6
    f = h * 6 - np.floor(h * 6)
    p, q, t = v * (1 - s), v * (1 - f * s), v * (1 - (1 - f) * s)
    out = np.zeros(h.shape + (3,))
    for k, (a, b, c) in enumerate([(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)]):
        m = i == k
        out[m, 0], out[m, 1], out[m, 2] = a[m], b[m], c[m]
    return out

def recolor(px, hue, sat_mul=1.0, val_mul=1.0, gray_to=None):
    """Farbige Pixel auf neuen Farbton; graue Pixel (Umriss) optional in Pflaume tauchen."""
    h, s, v = rgb2hsv(px)
    out = px.copy()
    colored = s > 0.18
    nh = np.full_like(h, hue)
    rgb = hsv2rgb(nh, np.clip(s * sat_mul, 0, 1), np.clip(v * val_mul, 0, 1))
    out[colored, :3] = rgb[colored]
    if gray_to is not None:
        g = ~colored
        lum = v[g][:, None]
        out[g, :3] = gray_to[0] * (1 - lum) + gray_to[1] * lum
    return out

def up(px):
    return np.repeat(np.repeat(px, S, 0), S, 1)

PINK_HUE = 322 / 360
GRAY_PLUM = (np.array([30, 6, 34]) / 255, np.array([255, 205, 242]) / 255)

def glint_frames(base, N=30, active=7):
    """Glanzlicht, das kurz schräg über das Symbol wischt; danach ruhig."""
    h, w = base.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w] / float(h)
    frames = []
    for k in range(N):
        img = base.copy()
        if k < active:
            pos = -0.4 + 1.8 * k / (active - 1)
            band = np.exp(-(((xx + yy) * 0.5 - pos) / 0.09) ** 2)
            vis = (img[..., 3] > 0.5) & (img[..., :3].mean(-1) > 0.25)
            img[..., :3] = np.where(vis[..., None], np.clip(img[..., :3] + band[..., None] * 0.55, 0, 1), img[..., :3])
        frames.append(img)
    return frames

def save_icon_anim(name, frames, frametime=2):
    path = os.path.join(OUT, name + ".png")
    to_img(frames[-1]).save(path, optimize=True)     # letztes Bild = ohne Glanzlicht
    if os.path.exists(path + ".mcmeta"):
        os.remove(path + ".mcmeta")
    print("saved", name, "(static)")

def icons():
    # Herzen: pink, normale Herzen mit Glanz
    for n in ["full", "half", "hardcore_full", "hardcore_half"]:
        px = recolor(load("heart/" + n), PINK_HUE, 0.95, 1.05, GRAY_PLUM)
        save_icon_anim("heart/" + n, glint_frames(up(px)))
    for n in ["full_blinking", "half_blinking", "hardcore_full_blinking", "hardcore_half_blinking"]:
        px = recolor(load("heart/" + n), PINK_HUE, 0.8, 1.1, GRAY_PLUM)
        save_static("heart/" + n, up(px))
    for n in ["container", "container_blinking", "container_hardcore", "container_hardcore_blinking"]:
        px = load("heart/" + n)
        h, s, v = rgb2hsv(px)
        lum = v[..., None]
        out = px.copy()
        out[..., :3] = np.array([40, 10, 46]) / 255 * (1 - lum) + np.array([255, 120, 220]) / 255 * lum
        save_static("heart/" + n, up(out))
    # Rüstung: grau -> pink-violetter Kristall
    for n in ["armor_full", "armor_half"]:
        px = load(n)
        h, s, v = rgb2hsv(px)
        lum = v[..., None]
        out = px.copy()
        out[..., :3] = np.clip(np.array([70, 20, 110]) / 255 * (1 - lum) + np.array([255, 150, 235]) / 255 * lum * 1.05, 0, 1)
        save_icon_anim(n, glint_frames(up(out), N=30, active=7))
    px = load("armor_empty")
    h, s, v = rgb2hsv(px)
    out = px.copy(); lum = v[..., None]
    out[..., :3] = np.array([30, 6, 34]) / 255 * (1 - lum) + np.array([200, 110, 200]) / 255 * lum
    save_static("armor_empty", up(out))
    # Essen: Fleisch rosé statt braun, Knochen bleibt hell
    for n in ["food_full", "food_half"]:
        px = recolor(load(n), 335 / 360, 0.85, 1.12, None)
        save_static(n, up(px))
    px = load("food_empty")
    h, s, v = rgb2hsv(px)
    out = px.copy(); lum = v[..., None]
    out[..., :3] = np.array([30, 6, 34]) / 255 * (1 - lum) + np.array([200, 110, 200]) / 255 * lum
    save_static("food_empty", up(out))

def fx_sprites(fx_dir):
    """Kleine Leucht-Sprites, die PinkHud jedes Bild an wechselnden Stellen zeichnet."""
    os.makedirs(fx_dir, exist_ok=True)
    n = 32
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot((xx + 0.5) / n * 2 - 1, (yy + 0.5) / n * 2 - 1)
    a = np.clip(np.exp(-(r / 0.22) ** 2) * 1.0 + np.exp(-(r / 0.55) ** 2) * 0.45, 0, 1) * np.clip((1 - r) / 0.2, 0, 1)
    col = np.ones((n, n, 3)) * np.array([1.0, 0.86, 0.97])
    to_img(np.concatenate([col, a[..., None]], -1)).save(os.path.join(fx_dir, "pulse.png"))
    # Funke: kleines Kreuz
    m = 16
    yy, xx = np.mgrid[0:m, 0:m]
    cx, cy = (xx + 0.5) / m * 2 - 1, (yy + 0.5) / m * 2 - 1
    r = np.hypot(cx, cy)
    a = np.exp(-(r / 0.18) ** 2) + (np.exp(-np.abs(cx) / 0.07) * np.exp(-np.abs(cy) / 0.6) + np.exp(-np.abs(cy) / 0.07) * np.exp(-np.abs(cx) / 0.6)) * 0.7
    a = np.clip(a, 0, 1) * np.clip((1 - r) / 0.15, 0, 1)
    col = np.ones((m, m, 3))
    to_img(np.concatenate([col, a[..., None]], -1)).save(os.path.join(fx_dir, "spark.png"))
    # Schein um einen Slot-Rahmen (22x22) und um den Auswahl-Rahmen (24x23)
    for name, (W, H, x0, y0, x1, y1, rr) in {"slot_glow": (22, 22, 1.8, 1.8, 20.2, 20.2, 2.2),
                                             "selection_glow": (24, 23, 1.6, 1.6, 22.4, 22.4, 3.0)}.items():
        X, Y = grid(W, H)
        d = sd_rrect(X, Y, x0, y0, x1, y1, rr)
        a = np.clip(np.exp(-(d / 0.6) ** 2) * 0.9 + np.exp(-np.abs(d) / 1.4) * 0.35, 0, 1)
        col = np.ones(X.shape + (3,)) * PINK_HI
        to_img(np.concatenate([col, a[..., None]], -1)).save(os.path.join(fx_dir, name + ".png"))
    print("saved fx sprites")

if len(sys.argv) > 3:
    fx_sprites(sys.argv[3])
hotbar()
# Die Hotbar wird nicht aus dem GUI-Atlas gezeichnet, sondern als eigenes Bild (siehe PinkHud.drawHotbar)
if len(sys.argv) > 4:
    os.replace(os.path.join(OUT, "hotbar.png"), sys.argv[4])
selection()
offhand("left")
offhand("right")
xp_background()
xp_progress()
icons()
