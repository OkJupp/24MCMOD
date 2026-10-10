package de.jupp.nuitchains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: berechnet, wo die Ketten hängen,
 * wie sie pendeln und erzeugt die 3D-Kettenglieder als Vierecke.
 * <p>
 * Ausgabe erfolgt über {@link QuadSink}; Positionen sind relativ zur Kamera,
 * bereits weit entfernte Punkte werden auf eine Kugel um die Kamera
 * "heran gezogen" (gleiche Richtung = gleiches Bild, aber innerhalb der Skybox).
 */
public final class ChainGeometry {

    /** Empfängt ein fertiges Viereck: 4 Punkte (x,y,z relativ zur Kamera) und je u,v. */
    public interface QuadSink {
        void quad(float[] xyz, float[] uv);
    }

    /** Eine Kette, die in diesem Frame gezeichnet wird. */
    public record Chain(double ax, double ay, double az, double length, int links,
                        double phase1, double phase2, double swingAxis, double twist,
                        double fade, double distance) {
    }

    /** Radius der "Heranzieh"-Kugel, muss kleiner als die Nuit-Skybox (100) sein. */
    public static final double IMPOSTOR_RADIUS = 92.0;

    private final ChainSettings s;
    private final LinkMesh hiDetail;
    private final LinkMesh loDetail;

    public ChainGeometry(ChainSettings settings) {
        this.s = settings;
        this.hiDetail = new LinkMesh(settings, 10, 8);
        this.loDetail = new LinkMesh(settings, 5, 4);
    }

    // ------------------------------------------------------------------ Platzierung

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double rnd(long h, int n) {
        return (mix(h + n * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /** Alle Ketten im Raster um die Kamera, von weit nach nah sortiert. */
    public List<Chain> collect(double ex, double ey, double ez) {
        List<Chain> out = new ArrayList<>();
        int sp = s.spacing();
        int r = s.cellRadius();
        long cx0 = (long) Math.floor(ex / sp);
        long cz0 = (long) Math.floor(ez / sp);
        double fadeEnd = (r + 0.5) * sp;
        double fadeStart = fadeEnd * 0.7;
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
                double dx = ax - ex, dz = az - ez;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double fade = 1.0 - smooth(fadeStart, fadeEnd, horiz);
                if (fade <= 0.001) continue;
                double midY = s.topY() - len * 0.5;
                double dist = Math.sqrt(horiz * horiz + (midY - ey) * (midY - ey));
                out.add(new Chain(ax, s.topY(), az, len, Math.max(1, (int) Math.round(len / pitch)),
                        rnd(h, 4) * Math.PI * 2, rnd(h, 5) * Math.PI * 2,
                        rnd(h, 6) * Math.PI, rnd(h, 7) * Math.PI, fade, dist));
            }
        }
        out.sort((a, b) -> Double.compare(b.distance(), a.distance()));
        return out;
    }

    private double pitch() {
        return Math.max(0.2, s.linkLength() - 2.2 * s.thickness());
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ Pendeln + Glieder

    /**
     * Erzeugt alle sichtbaren Vierecke einer Kette.
     *
     * @param timeSec Zeit in Sekunden (Spielzeit inkl. Teil-Tick)
     * @return Anzahl ausgegebener Vierecke
     */
    public int build(Chain c, double ex, double ey, double ez, double timeSec, QuadSink sink) {
        LinkMesh mesh = c.distance() > 180 ? loDetail : hiDetail;
        double pitch = pitch();
        double swing = Math.toRadians(s.swingDegrees());
        // Pendelperiode: längere Ketten schwingen langsamer
        double period = (6.0 + c.length() / 30.0) / Math.max(0.05, s.swingSpeed());
        double w1 = 2 * Math.PI / period;
        double w2 = w1 * 0.73;
        double ca = Math.cos(c.swingAxis()), sa = Math.sin(c.swingAxis());
        double waveDelay = s.wave() * period * 0.25 / Math.max(1, c.links());

        double px = c.ax(), py = c.ay(), pz = c.az();
        int count = 0;
        int nLinks = c.links();
        double[] frames = new double[nLinks * 12];
        long[] linkOrder = new long[nLinks];
        for (int i = 0; i < nLinks; i++) {
            double t = timeSec - i * waveDelay;
            double th1 = swing * Math.sin(w1 * t + c.phase1());
            double th2 = 0.35 * swing * Math.sin(w2 * t + c.phase2());
            // Richtung nach unten, gekippt um zwei waagerechte Achsen
            double ux = Math.sin(th1) * ca + Math.sin(th2) * -sa;
            double uz = Math.sin(th1) * sa + Math.sin(th2) * ca;
            double uy = -Math.sqrt(Math.max(0.0001, 1 - ux * ux - uz * uz));

            double cxw = px + ux * pitch * 0.5, cyw = py + uy * pitch * 0.5, czw = pz + uz * pitch * 0.5;
            px += ux * pitch; py += uy * pitch; pz += uz * pitch;

            // Ebene des Glieds: abwechselnd um 90° gedreht
            double tw = c.twist() + (i & 1) * Math.PI * 0.5;
            double rx = Math.cos(tw), rz = Math.sin(tw);
            double d = rx * ux + rz * uz;
            double wx = rx - ux * d, wy = -uy * d, wz = rz - uz * d;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
            wx /= wl; wy /= wl; wz /= wl;
            // n = U x W
            double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;

            double[] f = {cxw - ex, cyw - ey, czw - ez, ux, uy, uz, wx, wy, wz, nx, ny, nz};
            System.arraycopy(f, 0, frames, i * 12, 12);
            float d2 = (float) (f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
            linkOrder[i] = ((long) Float.floatToIntBits(d2) << 32) | i;
        }
        // Glieder von hinten nach vorne
        Arrays.sort(linkOrder);
        for (int q = nLinks - 1; q >= 0; q--) {
            int o = (int) (linkOrder[q] & 0xFFFFFFFFL) * 12;
            count += mesh.emit(frames[o], frames[o + 1], frames[o + 2], frames[o + 3], frames[o + 4], frames[o + 5],
                    frames[o + 6], frames[o + 7], frames[o + 8], frames[o + 9], frames[o + 10], frames[o + 11], sink);
        }
        return count;
    }

    /**
     * Vorberechnetes Kettenglied (Stadion-förmiger Ring als Rohr) in lokalen Koordinaten.
     */
    static final class LinkMesh {
        final int m;  // Punkte entlang des Rings
        final int k;  // Punkte um das Rohr
        final double[] pu, pw, pn; // Position
        final double[] nu, nw, nn; // Normale
        // Puffer für Sortierung / Ausgabe
        private final float[] wx, wy, wz, uvV;
        private final long[] order;
        private final float[] qxyz = new float[12];
        private final float[] quv = new float[8];
        private final int[] quadIdx = new int[4];

        LinkMesh(ChainSettings s, int arcSegments, int tubeSegments) {
            double r = s.thickness();
            double halfLen = s.linkLength() * 0.5 - r;           // Mittellinie bis zum Ende
            double b = Math.max(r * 1.15, s.linkWidth() * 0.5 - r); // Radius der Rundungen
            double straight = Math.max(0, halfLen - b);            // halbe gerade Strecke

            // Mittellinie: rechte Rundung, oben gerade, linke Rundung, unten gerade (gegen Uhrzeigersinn)
            List<double[]> path = new ArrayList<>(); // {u, w, Nu, Nw}
            for (int i = 0; i < arcSegments; i++) {
                double a = -Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            if (straight > 1e-4) path.add(new double[]{straight, b, 0, 1});
            for (int i = 0; i < arcSegments; i++) {
                double a = Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{-straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            if (straight > 1e-4) path.add(new double[]{-straight, -b, 0, -1});

            this.m = path.size();
            this.k = tubeSegments;
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
            wx = new float[n]; wy = new float[n]; wz = new float[n]; uvV = new float[n];
            order = new long[m * k];
        }

        int emit(double cx, double cy, double cz,
                 double ux, double uy, double uz, double wxv, double wyv, double wzv,
                 double nxv, double nyv, double nzv, QuadSink sink) {
            int n = m * k;
            // Licht: von oben (Glühen am Himmel) + leichter Glanz in Blickrichtung
            for (int idx = 0; idx < n; idx++) {
                double x = cx + pu[idx] * ux + pw[idx] * wxv + pn[idx] * nxv;
                double y = cy + pu[idx] * uy + pw[idx] * wyv + pn[idx] * nyv;
                double z = cz + pu[idx] * uz + pw[idx] * wzv + pn[idx] * nzv;
                double gx = nu[idx] * ux + nw[idx] * wxv + nn[idx] * nxv;
                double gy = nu[idx] * uy + nw[idx] * wyv + nn[idx] * nyv;
                double gz = nu[idx] * uz + nw[idx] * wzv + nn[idx] * nzv;
                double dist = Math.sqrt(x * x + y * y + z * z);
                double vx = -x / dist, vy = -y / dist, vz = -z / dist;    // zur Kamera
                double ndv = gx * vx + gy * vy + gz * vz;
                double up = 0.35 * gy + 0.65 * Math.max(0, gy);
                double f = Math.max(0, ndv), rim = Math.max(0, 1 - Math.abs(ndv));
                rim *= rim;
                double light = 0.22 + 0.45 * Math.max(0, up) + 0.33 * f * f * f + 0.35 * rim * rim; // + Rand-Schimmer
                uvV[idx] = (float) Math.min(0.995, Math.max(0.005, 1.0 - light));
                // Weit entfernte Punkte in die Skybox ziehen (Richtung bleibt gleich)
                double sc = dist > IMPOSTOR_RADIUS ? IMPOSTOR_RADIUS / dist : 1.0;
                wx[idx] = (float) (x * sc); wy[idx] = (float) (y * sc); wz[idx] = (float) (z * sc);
            }

            int visible = 0;
            for (int i = 0; i < m; i++) {
                int i1 = (i + 1) % m;
                for (int j = 0; j < k; j++) {
                    int j1 = (j + 1) % k;
                    int a = i * k + j, b = i * k + j1, c = i1 * k + j1, d = i1 * k + j;
                    // Mittelpunkt und Normale des Vierecks (in echten Weltkoordinaten)
                    double mu = (pu[a] + pu[b] + pu[c] + pu[d]) * 0.25;
                    double mw = (pw[a] + pw[b] + pw[c] + pw[d]) * 0.25;
                    double mn = (pn[a] + pn[b] + pn[c] + pn[d]) * 0.25;
                    double qu = nu[a] + nu[b] + nu[c] + nu[d];
                    double qw = nw[a] + nw[b] + nw[c] + nw[d];
                    double qn = nn[a] + nn[b] + nn[c] + nn[d];
                    double x = cx + mu * ux + mw * wxv + mn * nxv;
                    double y = cy + mu * uy + mw * wyv + mn * nyv;
                    double z = cz + mu * uz + mw * wzv + mn * nzv;
                    double gx = qu * ux + qw * wxv + qn * nxv;
                    double gy = qu * uy + qw * wyv + qn * nyv;
                    double gz = qu * uz + qw * wzv + qn * nzv;
                    if (gx * x + gy * y + gz * z >= 0) continue; // Rückseite
                    float dist2 = (float) (x * x + y * y + z * z);
                    order[visible++] = ((long) Float.floatToIntBits(dist2) << 32) | (a & 0xFFFFFFFFL);
                }
            }
            // von hinten nach vorne zeichnen
            Arrays.sort(order, 0, visible);
            for (int q = visible - 1; q >= 0; q--) {
                int a = (int) (order[q] & 0xFFFFFFFFL);
                int i = a / k, j = a % k;
                int i1 = (i + 1) % m, j1 = (j + 1) % k;
                quadIdx[0] = a; quadIdx[1] = i * k + j1; quadIdx[2] = i1 * k + j1; quadIdx[3] = i1 * k + j;
                for (int t = 0; t < 4; t++) {
                    int vi = quadIdx[t];
                    qxyz[t * 3] = wx[vi]; qxyz[t * 3 + 1] = wy[vi]; qxyz[t * 3 + 2] = wz[vi];
                    quv[t * 2] = 0.5F; quv[t * 2 + 1] = uvV[vi];
                }
                sink.quad(qxyz, quv);
            }
            return visible;
        }
    }
}
