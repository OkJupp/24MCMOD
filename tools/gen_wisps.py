"""Halbtransparente Dunst-Schicht als Nuit square-textured Würfel (RGBA, 3:2 Layout).
Aufruf: gen_haze.py FACE OUT MODE   (MODE = low | high)"""
import sys, math
import numpy as np
from PIL import Image

FACE = int(sys.argv[1]); OUT = sys.argv[2]
rng = np.random.default_rng(29)

def rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1,0,0],[0,c,-s],[0,s,c]])
def ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c,0,s],[0,1,0],[-s,0,c]])
def rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c,-s,0],[s,c,0],[0,0,1]])
D = math.radians
MATS = [np.eye(3), rx(D(90)), rx(D(-90)) @ ry(D(180)), rx(D(180)), rz(D(90)) @ ry(D(-90)), rz(D(-90)) @ ry(D(90))]
CELLS = [(0,0),(1,1),(2,0),(1,0),(2,1),(0,1)]

PERM = rng.permutation(256).astype(np.int64); PERM = np.concatenate([PERM, PERM])
GRAD = rng.random(256).astype(np.float32)
def vnoise(p):
    pi = np.floor(p).astype(np.int64); pf = (p - pi).astype(np.float32)
    w = pf*pf*(3-2*pf)
    xi, yi, zi = pi[...,0] & 255, pi[...,1] & 255, pi[...,2] & 255
    def h(dx,dy,dz): return GRAD[PERM[PERM[PERM[xi+dx]+yi+dy]+zi+dz]]
    x00 = h(0,0,0)*(1-w[...,0]) + h(1,0,0)*w[...,0]
    x10 = h(0,1,0)*(1-w[...,0]) + h(1,1,0)*w[...,0]
    x01 = h(0,0,1)*(1-w[...,0]) + h(1,0,1)*w[...,0]
    x11 = h(0,1,1)*(1-w[...,0]) + h(1,1,1)*w[...,0]
    y0 = x00*(1-w[...,1]) + x10*w[...,1]; y1 = x01*(1-w[...,1]) + x11*w[...,1]
    return y0*(1-w[...,2]) + y1*w[...,2]
def fbm(p, oct=6):
    a, s, n = 0.5, 0.0, 0.0
    for i in range(oct):
        s += a*vnoise(p + i*17.31); n += a; p = p*2.03; a *= 0.5
    return s/n
def sstep(a, b, x):
    t = np.clip((x-a)/(b-a), 0, 1); return t*t*(3-2*t)


def sstep2(a,b,x): return sstep(a,b,x)
CORE = np.array([0.15, 0.55, -1.0]); CORE = CORE/np.linalg.norm(CORE)

def dens_at(d):
    x, y, z = d[...,0], d[...,1], d[...,2]
    el = np.arcsin(np.clip(y, -1, 1))
    q = d*2.0
    w = fbm(q*1.1 + 71.0, 4)
    # stark waagerecht gestreckt -> lange Fetzen
    p = np.stack([x*3.0, y*14.0, z*3.0], -1) + (w[...,None]-0.5)*1.4
    n = fbm(p + 13.0, 6)
    band = sstep(0.06, 0.2, el) * (1 - sstep(0.55, 0.85, el))
    fine = fbm(np.stack([x*9.0, y*30.0, z*9.0], -1) + 3.0, 4)
    dens = sstep(0.56, 0.92, n) * band * (0.55 + 0.45*fine)
    return dens, el

def shade(d):
    dens, el = dens_at(d)
    up = np.array([0, 1.0, 0])
    d2 = d + up*0.035; d2 = d2/np.linalg.norm(d2, axis=-1, keepdims=True)
    dens_up, _ = dens_at(d2)
    rim = np.clip((dens - dens_up)*3.5, 0, 1)
    toward = np.exp(-np.arccos(np.clip(d @ CORE, -1, 1))/0.9)
    rim = rim * (0.55 + 0.9*toward)
    base = np.array([24, 6, 32])/255
    rimc = np.array([225, 105, 200])/255; hot = np.array([255, 190, 240])/255
    r = np.clip(rim, 0, 1)[...,None]
    col = base*(1-r*0.8) + rimc*r*0.8*(0.6 + 0.4*toward[...,None])
    a = np.clip(dens*0.55 + rim*0.12, 0, 0.45)
    return np.concatenate([col, a[...,None]], -1)
img = np.zeros((FACE*2, FACE*3, 4), np.uint8)
uv = (np.arange(FACE) + 0.5)/FACE
U, V = np.meshgrid(uv, uv)
local = np.stack([2*U-1, -np.ones_like(U), 2*V-1], -1)
for f in range(6):
    dirs = local @ MATS[f].T
    dirs /= np.linalg.norm(dirs, axis=-1, keepdims=True)
    cx, cy = CELLS[f]
    img[cy*FACE:(cy+1)*FACE, cx*FACE:(cx+1)*FACE] = (np.clip(shade(dirs.astype(np.float32)), 0, 1)*255 + 0.5).astype(np.uint8)
Image.fromarray(img, "RGBA").save(OUT, optimize=True)
print("saved", OUT)
