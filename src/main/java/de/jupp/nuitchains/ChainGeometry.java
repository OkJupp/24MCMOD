package de.jupp.nuitchains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: berechnet, wo die Ketten hängen, wie sie pendeln,
 * wo sie kaputt sind, und erzeugt Kettenglieder und Bruchstücke als Vierecke.
 * <p>
 * Alle Positionen werden relativ zur Kamera ausgegeben (echte Abstände). Die Vierecke kommen
 * pro Kette von hinten nach vorne sortiert heraus.
 * <p>
 * Textur-Atlas (u-Bereiche): 0.00–0.50 Metall (v = Helligkeit), 0.50–0.75 glühende Bruchkante,
 * 0.75–1.00 glühender Kern (Innenseite aufgebrochener Glieder).
 */
public final class ChainGeometry {

    /** Empfängt ein fertiges Viereck: 4 Punkte (x,y,z relativ zur Kamera) und je u,v. */
    public interface QuadSink {
        void quad(float[] xyz, float[] uv);
    }

    /** Eine Kette, die in diesem Frame gezeichnet wird. */
    public record Chain(long hash, double ax, double ay, double az, double length, int links,
                        double phase1, double phase2, double swingAxis, double twist,
                        double leanX, double leanZ, boolean broken, int brokenLinks,
                        double fade, double distance) {
    }

    private static final double U_EDGE = 0.625;
    private static final double U_CORE = 0.875;

    private final ChainSettings s;
    private final DamageSettings dmg;
    private final LinkMesh hiDetail;
    private final LinkMesh loDetail;
    private final LinkMesh damageDetail;

    // wiederverwendete Puffer
    private final List<Part> parts = new ArrayList<>();
    private long[] partOrder = new long[64];

    public ChainGeometry(ChainSettings settings, DamageSettings damage) {
        this.s = settings;
        this.dmg = damage;
        this.hiDetail = new LinkMesh(settings, 9, 10);
        this.loDetail = new LinkMesh(settings, 5, 6);
        this.damageDetail = new LinkMesh(settings, 14, 14);
    }

    // ------------------------------------------------------------------ Zufall

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static double rnd(long h, int n) {
        return (mix(h + n * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /** Glatter 3D-Wertrauschen (0..1), für Löcher und Bruchkanten. */
    static double noise(double x, double y, double z, long seed) {
        long xi = (long) Math.floor(x), yi = (long) Math.floor(y), zi = (long) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
        double r = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            long h = mix(seed ^ ((xi + dx) * 0x632BE59BD9B4E019L) ^ ((yi + dy) * 0x85157AF5L) ^ ((zi + dz) * 0x9E3779B97F4A7C15L));
            double val = (h >>> 11) * 0x1.0p-53;
            r += val * (dx == 1 ? u : 1 - u) * (dy == 1 ? v : 1 - v) * (dz == 1 ? w : 1 - w);
        }
        return r;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ Platzierung

    /** Alle Ketten im Raster um die Kamera, von weit nach nah sortiert. */
    public List<Chain> collect(double ex, double ey, double ez) {
        List<Chain> out = new ArrayList<>();
        int sp = s.spacing();
        int r = s.cellRadius();
        long cx0 = (long) Math.floor(ex / sp);
        long cz0 = (long) Math.floor(ez / sp);
        double fadeEnd = (r + 0.5) * sp;
        double fadeStart = fadeEnd * 0.72;
        double minL = Math.min(s.minLength(), s.maxLength());
        double maxL = Math.max(s.minLength(), s.maxLength());
        double pitch = pitch();

        for (long cx = cx0 - r; cx <= cx0 + r; cx++) {
            for (long cz = cz0 - r; cz <= cz0 + r; cz++) {
                long h = mix(cx * 0x632BE59BD9B4E019L ^ cz * 0x85157AF5L ^ (long) s.seed() * 0x9E3779B97F4A7C15L);
                if (rnd(h, 0) >= s.chance()) continue;
                double ax = (cx + 0.15 + 0.7 * rnd(h, 1)) * sp;
                double az = (cz + 0.15 + 0.7 * rnd(h, 2)) * sp;
                double len = minL + (maxL - minL) * rnd(h, 3);
                double leanA = rnd(h, 8) * Math.PI * 2;
                double leanM = Math.toRadians(s.lean()) * (0.25 + 0.75 * rnd(h, 9));
                double leanX = Math.sin(leanM) * Math.cos(leanA), leanZ = Math.sin(leanM) * Math.sin(leanA);
                // Mittelpunkt der Kette für Abstand/Ausblenden
                double mx = ax + leanX * len * 0.5, mz = az + leanZ * len * 0.5, my = s.topY() - len * 0.5;
                double dx = mx - ex, dz = mz - ez;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double fade = 1.0 - smooth(fadeStart, fadeEnd, horiz);
                if (fade <= 0.001) continue;
                double dist = Math.sqrt(horiz * horiz + (my - ey) * (my - ey));
                boolean broken = rnd(h, 10) < dmg.brokenChance();
                int bl = Math.max(1, (int) Math.round(dmg.brokenLinks() * (0.6 + 0.8 * rnd(h, 11))));
                out.add(new Chain(h, ax, s.topY(), az, len, Math.max(2, (int) Math.round(len / pitch)),
                        rnd(h, 4) * Math.PI * 2, rnd(h, 5) * Math.PI * 2,
                        rnd(h, 6) * Math.PI, rnd(h, 7) * Math.PI, leanX, leanZ, broken, bl, fade, dist));
            }
        }
        out.sort((a, b) -> Double.compare(b.distance(), a.distance()));
        return out;
    }

    private double pitch() {
        return Math.max(0.2, s.linkLength() - 2.3 * s.thickness());
    }

    // ------------------------------------------------------------------ Aufbau einer Kette

    /** Ein Teil der Kette: Glied oder Bruchstück. */
    private static final class Part {
        boolean fragment;
        double cx, cy, cz;                 // Mittelpunkt relativ zur Kamera
        double ux, uy, uz, wx, wy, wz, nx, ny, nz; // Achsen
        double damage;                      // 0 = heil, 1 = fast weg
        long seed;
        double sx, sy, sz;                  // Bruchstück-Größe
    }

    private Part part(int idx) {
        while (this.parts.size() <= idx) this.parts.add(new Part());
        return this.parts.get(idx);
    }

    /**
     * Erzeugt alle sichtbaren Vierecke einer Kette, von hinten nach vorne.
     * Teile näher als {@code splitDistance} gehen an {@code nearSink} (echte 3D-Welt mit Tiefe),
     * alle anderen an {@code farSink} (Himmel).
     *
     * @param timeSec Zeit in Sekunden (Spielzeit inkl. Teil-Tick)
     * @return Anzahl ausgegebener Vierecke
     */
    public int build(Chain c, double ex, double ey, double ez, double timeSec, QuadSink sink) {
        return build(c, ex, ey, ez, timeSec, sink, sink, 0);
    }

    public int build(Chain c, double ex, double ey, double ez, double timeSec,
                     QuadSink farSink, QuadSink nearSink, double splitDistance) {
        double split2 = splitDistance * splitDistance;
        LinkMesh mesh = c.distance() > 320 ? loDetail : hiDetail;
        double pitch = pitch();
        double swing = Math.toRadians(s.swingDegrees());
        double period = (8.0 + c.length() / 25.0) / Math.max(0.05, s.swingSpeed());
        double w1 = 2 * Math.PI / period;
        double w2 = w1 * 0.73;
        double ca = Math.cos(c.swingAxis()), sa = Math.sin(c.swingAxis());
        double waveDelay = s.wave() * period * 0.25 / Math.max(1, c.links());

        int nLinks = c.links();
        int nParts = 0;
        double px = c.ax(), py = c.ay(), pz = c.az();
        double lastUx = 0, lastUy = -1, lastUz = 0;
        for (int i = 0; i < nLinks; i++) {
            double t = timeSec - i * waveDelay;
            double th1 = swing * Math.sin(w1 * t + c.phase1());
            double th2 = 0.35 * swing * Math.sin(w2 * t + c.phase2());
            double ux = c.leanX() + Math.sin(th1) * ca - Math.sin(th2) * sa;
            double uz = c.leanZ() + Math.sin(th1) * sa + Math.sin(th2) * ca;
            double uy = -Math.sqrt(Math.max(0.0001, 1 - ux * ux - uz * uz));
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            lastUx = ux; lastUy = uy; lastUz = uz;

            Part p = part(nParts++);
            p.fragment = false;
            p.cx = px + ux * pitch * 0.5 - ex;
            p.cy = py + uy * pitch * 0.5 - ey;
            p.cz = pz + uz * pitch * 0.5 - ez;
            px += ux * pitch; py += uy * pitch; pz += uz * pitch;

            double tw = c.twist() + (i & 1) * Math.PI * 0.5 + (rnd(c.hash(), 100 + i) - 0.5) * 0.25;
            double rx = Math.cos(tw), rz = Math.sin(tw);
            double d = rx * ux + rz * uz;
            double wx = rx - ux * d, wy = -uy * d, wz = rz - uz * d;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
            p.ux = ux; p.uy = uy; p.uz = uz;
            p.wx = wx / wl; p.wy = wy / wl; p.wz = wz / wl;
            p.nx = p.uy * p.wz - p.uz * p.wy;
            p.ny = p.uz * p.wx - p.ux * p.wz;
            p.nz = p.ux * p.wy - p.uy * p.wx;
            p.seed = mix(c.hash() + i * 0x9E3779B97F4A7C15L);

            // Schaden: Zerfall am Ende + zufällige Abplatzer
            double damage = 0;
            if (c.broken()) {
                int fromEnd = nLinks - 1 - i;
                if (fromEnd < c.brokenLinks()) {
                    double f = 1.0 - fromEnd / (double) c.brokenLinks(); // 0..1 zum Ende hin
                    damage = 0.12 + 0.7 * f * f;
                }
            }
            if (rnd(p.seed, 1) < dmg.wear()) {
                damage = Math.max(damage, dmg.wearStrength() * (0.4 + 0.6 * rnd(p.seed, 2)));
            }
            p.damage = damage;
        }

        // Bruchstücke unter einem zerfallenden Ende
        if (c.broken() && dmg.fragments() > 0) {
            double size = s.thickness() * 0.8;
            double life = 9.0;
            for (int k = 0; k < dmg.fragments(); k++) {
                long fh = mix(c.hash() ^ (k * 0xD1B54A32D192ED03L));
                double age = (timeSec / (life * (0.7 + 0.6 * rnd(fh, 1))) + rnd(fh, 2)) % 1.0;
                double scale = (1 - age * age) * (0.35 + 0.9 * rnd(fh, 3));
                if (scale < 0.05) continue;
                double ang = rnd(fh, 4) * Math.PI * 2;
                double rad = s.linkWidth() * (0.3 + 1.4 * rnd(fh, 5)) * (0.4 + age);
                double fall = dmg.fragmentFall() * age * (0.5 + rnd(fh, 6));
                Part p = part(nParts++);
                p.fragment = true;
                p.cx = px + Math.cos(ang) * rad + lastUx * (fall + s.linkLength() * 0.3) - ex;
                p.cy = py + lastUy * (fall + s.linkLength() * 0.3) - ey;
                p.cz = pz + Math.sin(ang) * rad + lastUz * (fall + s.linkLength() * 0.3) - ez;
                // zufällige, sich drehende Ausrichtung
                double spin = timeSec * (0.4 + rnd(fh, 7)) + rnd(fh, 8) * 6.3;
                double a1 = rnd(fh, 9) * Math.PI * 2, a2 = Math.acos(2 * rnd(fh, 10) - 1);
                double kx = Math.sin(a2) * Math.cos(a1), ky = Math.cos(a2), kz = Math.sin(a2) * Math.sin(a1);
                setRotation(p, kx, ky, kz, spin);
                p.sx = size * scale * (0.6 + 0.8 * rnd(fh, 11));
                p.sy = size * scale * (0.6 + 0.8 * rnd(fh, 12));
                p.sz = size * scale * (0.4 + 0.6 * rnd(fh, 13));
                p.seed = fh;
                p.damage = 0;
            }
        }

        // Teile von hinten nach vorne
        if (this.partOrder.length < nParts) this.partOrder = new long[nParts * 2];
        for (int i = 0; i < nParts; i++) {
            Part p = this.parts.get(i);
            float d2 = (float) (p.cx * p.cx + p.cy * p.cy + p.cz * p.cz);
            this.partOrder[i] = ((long) Float.floatToIntBits(d2) << 32) | i;
        }
        Arrays.sort(this.partOrder, 0, nParts);
        int count = 0;
        for (int q = nParts - 1; q >= 0; q--) {
            Part p = this.parts.get((int) (this.partOrder[q] & 0xFFFFFFFFL));
            double d2 = p.cx * p.cx + p.cy * p.cy + p.cz * p.cz;
            QuadSink sink = d2 < split2 ? nearSink : farSink;
            if (p.fragment) {
                count += emitFragment(p, sink);
            } else {
                boolean near = d2 < 180 * 180;
                count += (p.damage > 0.001 && near ? this.damageDetail : (near ? this.hiDetail : mesh)).emit(p, sink);
            }
        }
        return count;
    }

    /** Rotationsmatrix (Rodrigues) als Achsen U, W, N in den Part schreiben. */
    private static void setRotation(Part p, double kx, double ky, double kz, double a) {
        double c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        p.ux = t * kx * kx + c;      p.uy = t * kx * ky + s * kz; p.uz = t * kx * kz - s * ky;
        p.wx = t * kx * ky - s * kz; p.wy = t * ky * ky + c;      p.wz = t * ky * kz + s * kx;
        p.nx = t * kx * kz + s * ky; p.ny = t * ky * kz - s * kx; p.nz = t * kz * kz + c;
    }

    // ------------------------------------------------------------------ Bruchstück (verbeulter Würfel)

    private static final int[][] BOX_FACES = {
            {1, 3, 7, 5}, {0, 4, 6, 2}, {2, 6, 7, 3}, {0, 1, 5, 4}, {4, 5, 7, 6}, {0, 2, 3, 1}
    };
    private static final double[][] BOX_NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final float[] fxyz = new float[12];
    private final float[] fuv = new float[8];
    private final double[] corner = new double[24];

    private int emitFragment(Part p, QuadSink sink) {
        for (int k = 0; k < 8; k++) {
            double jx = 0.45 + 1.1 * rnd(p.seed, 20 + k), jy = 0.45 + 1.1 * rnd(p.seed, 30 + k), jz = 0.45 + 1.1 * rnd(p.seed, 40 + k);
            double lx = ((k & 1) == 1 ? 1 : -1) * p.sx * jx;
            double ly = ((k & 2) == 2 ? 1 : -1) * p.sy * jy;
            double lz = ((k & 4) == 4 ? 1 : -1) * p.sz * jz;
            corner[k * 3] = p.cx + lx * p.ux + ly * p.wx + lz * p.nx;
            corner[k * 3 + 1] = p.cy + lx * p.uy + ly * p.wy + lz * p.ny;
            corner[k * 3 + 2] = p.cz + lx * p.uz + ly * p.wz + lz * p.nz;
        }
        double dist = Math.sqrt(p.cx * p.cx + p.cy * p.cy + p.cz * p.cz);
        int glowFace = (int) (rnd(p.seed, 50) * 6);
        int n = 0;
        for (int f = 0; f < 6; f++) {
            double[] ln = BOX_NORMALS[f];
            double gx = ln[0] * p.ux + ln[1] * p.wx + ln[2] * p.nx;
            double gy = ln[0] * p.uy + ln[1] * p.wy + ln[2] * p.ny;
            double gz = ln[0] * p.uz + ln[1] * p.wz + ln[2] * p.nz;
            double ndv = -(gx * p.cx + gy * p.cy + gz * p.cz) / dist;
            if (ndv <= 0) continue;
            double light = shade(gy, ndv);
            double u = f == glowFace ? U_EDGE : 0.1 + 0.3 * rnd(p.seed, 60 + f);
            for (int t = 0; t < 4; t++) {
                int ci = BOX_FACES[f][t] * 3;
                fxyz[t * 3] = (float) corner[ci]; fxyz[t * 3 + 1] = (float) corner[ci + 1]; fxyz[t * 3 + 2] = (float) corner[ci + 2];
                fuv[t * 2] = (float) (u + (t == 1 || t == 2 ? 0.04 : 0)); fuv[t * 2 + 1] = (float) (1 - light);
            }
            sink.quad(fxyz, fuv);
            n++;
        }
        return n;
    }

    /** Helligkeit 0..1: Licht von oben + Glanz in Blickrichtung + Rand-Schimmer. */
    static double shade(double normalY, double nDotView) {
        double up = 0.35 * normalY + 0.65 * Math.max(0, normalY);
        double f = Math.max(0, nDotView), rim = Math.max(0, 1 - Math.abs(nDotView));
        rim *= rim;
        double l = 0.16 + 0.42 * Math.max(0, up) + 0.36 * f * f * f + 0.3 * rim * rim;
        return Math.min(0.995, Math.max(0.005, l));
    }

    // ------------------------------------------------------------------ Kettenglied

    /** Vorberechnetes Kettenglied (Stadion-förmiger Ring als dickes Rohr) in lokalen Koordinaten. */
    static final class LinkMesh {
        final int m;  // Punkte entlang des Rings
        final int k;  // Punkte um das Rohr
        final double[] pu, pw, pn; // Position
        final double[] nu, nw, nn; // Normale
        final double noiseScale;
        private final float[] vx, vy, vz, vl;
        private long[] order;
        private final double[] quadU;
        private final boolean[] removed;
        private final java.util.HashMap<Long, float[]> noiseCache = new java.util.HashMap<>();
        private final float[] qxyz = new float[12];
        private final float[] quv = new float[8];
        private final int[] quadIdx = new int[4];

        LinkMesh(ChainSettings s, int arcSegments, int tubeSegments) {
            double r = s.thickness();
            double halfLen = s.linkLength() * 0.5 - r;
            double b = Math.max(r * 1.15, s.linkWidth() * 0.5 - r);
            double straight = Math.max(0, halfLen - b);

            List<double[]> path = new ArrayList<>(); // {u, w, Nu, Nw}
            for (int i = 0; i < arcSegments; i++) {
                double a = -Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            if (straight > 1e-4) {
                path.add(new double[]{straight, b, 0, 1});
                path.add(new double[]{0, b, 0, 1});
            }
            for (int i = 0; i < arcSegments; i++) {
                double a = Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{-straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            if (straight > 1e-4) {
                path.add(new double[]{-straight, -b, 0, -1});
                path.add(new double[]{0, -b, 0, -1});
            }

            this.m = path.size();
            this.k = tubeSegments;
            this.noiseScale = 1.0 / Math.max(0.05, r * 2.6);
            int n = m * k;
            pu = new double[n]; pw = new double[n]; pn = new double[n];
            nu = new double[n]; nw = new double[n]; nn = new double[n];
            for (int i = 0; i < m; i++) {
                double[] p = path.get(i);
                for (int j = 0; j < k; j++) {
                    double phi = 2 * Math.PI * j / k;
                    double cu = Math.cos(phi) * p[2], cw = Math.cos(phi) * p[3], cn = -Math.sin(phi);
                    int idx = i * k + j;
                    pu[idx] = p[0] + r * cu; pw[idx] = p[1] + r * cw; pn[idx] = r * cn;
                    nu[idx] = cu; nw[idx] = cw; nn[idx] = cn;
                }
            }
            vx = new float[n]; vy = new float[n]; vz = new float[n]; vl = new float[n];
            order = new long[n];
            quadU = new double[n];
            removed = new boolean[n];
        }

        /** Rauschwert je Viereck – hängt nur vom Glied ab, wird deshalb zwischengespeichert. */
        private float[] noiseFor(long seed) {
            float[] v = this.noiseCache.get(seed);
            if (v != null) return v;
            if (this.noiseCache.size() > 2048) this.noiseCache.clear();
            int n = m * k;
            v = new float[n];
            for (int i = 0; i < m; i++) {
                int i1 = (i + 1) % m;
                for (int j = 0; j < k; j++) {
                    int j1 = (j + 1) % k;
                    int a = i * k + j, b = i * k + j1, c = i1 * k + j1, d = i1 * k + j;
                    double mu = (pu[a] + pu[b] + pu[c] + pu[d]) * 0.25;
                    double mw = (pw[a] + pw[b] + pw[c] + pw[d]) * 0.25;
                    double mn = (pn[a] + pn[b] + pn[c] + pn[d]) * 0.25;
                    v[a] = (float) noise(mu * noiseScale, mw * noiseScale, mn * noiseScale + (seed & 63), seed);
                }
            }
            this.noiseCache.put(seed, v);
            return v;
        }

        int emit(Part p, QuadSink sink) {
            int n = m * k;
            for (int idx = 0; idx < n; idx++) {
                double x = p.cx + pu[idx] * p.ux + pw[idx] * p.wx + pn[idx] * p.nx;
                double y = p.cy + pu[idx] * p.uy + pw[idx] * p.wy + pn[idx] * p.ny;
                double z = p.cz + pu[idx] * p.uz + pw[idx] * p.wz + pn[idx] * p.nz;
                double gy = nu[idx] * p.uy + nw[idx] * p.wy + nn[idx] * p.ny;
                double gx = nu[idx] * p.ux + nw[idx] * p.wx + nn[idx] * p.nx;
                double gz = nu[idx] * p.uz + nw[idx] * p.wz + nn[idx] * p.nz;
                double dist = Math.sqrt(x * x + y * y + z * z);
                double ndv = -(gx * x + gy * y + gz * z) / dist;
                vl[idx] = (float) (1.0 - shade(gy, ndv));
                vx[idx] = (float) x; vy[idx] = (float) y; vz[idx] = (float) z;
            }

            boolean damaged = p.damage > 0.001;
            boolean[] removed = this.removed;
            if (damaged) {
                float[] nv = noiseFor(p.seed);
                for (int q = 0; q < n; q++) removed[q] = nv[q] < p.damage;
            }
            int visible = 0;
            for (int i = 0; i < m; i++) {
                int i1 = (i + 1) % m;
                for (int j = 0; j < k; j++) {
                    int j1 = (j + 1) % k;
                    int a = i * k + j, b = i * k + j1, c = i1 * k + j1, d = i1 * k + j;
                    if (damaged && removed[a]) continue;                  // weggebrochen
                    double mu = (pu[a] + pu[b] + pu[c] + pu[d]) * 0.25;
                    double mw = (pw[a] + pw[b] + pw[c] + pw[d]) * 0.25;
                    double mn = (pn[a] + pn[b] + pn[c] + pn[d]) * 0.25;
                    double qu = nu[a] + nu[b] + nu[c] + nu[d];
                    double qw = nw[a] + nw[b] + nw[c] + nw[d];
                    double qn = nn[a] + nn[b] + nn[c] + nn[d];
                    double x = p.cx + mu * p.ux + mw * p.wx + mn * p.nx;
                    double y = p.cy + mu * p.uy + mw * p.wy + mn * p.ny;
                    double z = p.cz + mu * p.uz + mw * p.wz + mn * p.nz;
                    double gx = qu * p.ux + qw * p.wx + qn * p.nx;
                    double gy = qu * p.uy + qw * p.wy + qn * p.ny;
                    double gz = qu * p.uz + qw * p.wz + qn * p.nz;
            boolean front = gx * x + gy * y + gz * z < 0;

                    double u;
                    if (damaged) {
                        int im = (i + m - 1) % m, jm = (j + k - 1) % k;
                        boolean edge = removed[i1 * k + j] || removed[im * k + j] || removed[i * k + j1] || removed[i * k + jm];
                        if (!front) {
                            u = U_CORE;                                  // Innenseite glüht
                        } else if (edge) {
                            u = U_EDGE;                                  // glühende Bruchkante
                        } else {
                            u = -1;                                      // normales Metall
                        }
                    } else {
                        if (!front) continue;
                        u = -1;
                    }
                    quadU[visible] = u;
                    float dist2 = (float) (x * x + y * y + z * z);
                    order[visible++] = ((long) Float.floatToIntBits(dist2) << 32) | (a & 0xFFFFFFFFL);
                }
            }
            if (visible == 0) return 0;
            // Sortieren mit u zusammen: u in eigenem Array nach Index ablegen
            double[] uByIndex = UBUF.get();
            if (uByIndex.length < n) { uByIndex = new double[n]; UBUF.set(uByIndex); }
            for (int q = 0; q < visible; q++) uByIndex[(int) (order[q] & 0xFFFFFFFFL)] = quadU[q];
            Arrays.sort(order, 0, visible);
            for (int q = visible - 1; q >= 0; q--) {
                int a = (int) (order[q] & 0xFFFFFFFFL);
                int i = a / k, j = a % k;
                int i1 = (i + 1) % m, j1 = (j + 1) % k;
                quadIdx[0] = a; quadIdx[1] = i * k + j1; quadIdx[2] = i1 * k + j1; quadIdx[3] = i1 * k + j;
                double u = uByIndex[a];
                // Metall: u läuft entlang des Rings (Kratzer-Muster), ohne Sprung an der Naht
                double uMetal0 = 0.02 + 0.46 * (i / (double) m);
                double uMetal1 = 0.02 + 0.46 * ((i + 1) / (double) m);
                for (int t = 0; t < 4; t++) {
                    int vi = quadIdx[t];
                    qxyz[t * 3] = vx[vi]; qxyz[t * 3 + 1] = vy[vi]; qxyz[t * 3 + 2] = vz[vi];
                    double uu = u >= 0 ? u + (t >= 2 ? 0.05 : 0) : (t >= 2 ? uMetal1 : uMetal0);
                    quv[t * 2] = (float) uu;
                    quv[t * 2 + 1] = vl[vi];
                }
                sink.quad(qxyz, quv);
            }
            return visible;
        }

        private static final ThreadLocal<double[]> UBUF = ThreadLocal.withInitial(() -> new double[256]);
    }
}
