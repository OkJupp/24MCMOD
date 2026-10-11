"""Entfernt die eingebrannten kleinen Sterne aus der Himmels-Textur (die großen hellen Sterne bleiben).
Aufruf: destar.py IN OUT"""
import sys, math
import numpy as np
from PIL import Image
import scipy.ndimage as nd

src = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(np.float32)
H, W = src.shape[:2]; F = H // 2
lum = src.mean(-1)
out = src.copy()
removed = 0
# je Würfelseite getrennt (keine Nähte verschmieren)
for cy in range(2):
    for cx in range(3):
        sl = (slice(cy * F, (cy + 1) * F), slice(cx * F, (cx + 1) * F))
        L = lum[sl]
        bg = nd.median_filter(L, size=9)
        diff = L - bg
        mask = diff > 9.0
        lab, n = nd.label(mask)
        sizes = nd.sum(mask, lab, range(1, n + 1))
        peak = nd.maximum(diff, lab, range(1, n + 1))
        keep = np.zeros(n + 1, bool)
        # nur kleine, punktförmige Sterne entfernen
        keep[1:] = (sizes <= 60)
        m = keep[lab]
        m = nd.binary_dilation(m, iterations=2)
        removed += int(keep.sum())
        face = src[sl]
        fill = np.stack([nd.median_filter(face[..., c], size=11) for c in range(3)], -1)
        # weich einblenden
        soft = nd.gaussian_filter(m.astype(np.float32), 0.8)[..., None]
        soft = np.clip(soft * 1.6, 0, 1)
        out[sl] = face * (1 - soft) + fill * soft
print("removed", removed)
Image.fromarray(np.clip(out + 0.5, 0, 255).astype(np.uint8)).save(sys.argv[2], optimize=True)
