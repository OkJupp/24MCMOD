package de.jupp.nuitchains;

import java.util.ArrayList;
import java.util.List;

/**
 * Reine Mathe für die Blitze (ohne Minecraft): wann es blitzt, wie hell, und die Form
 * des Blitzes als Bänder am Himmel. Koordinaten: Kamera im Ursprung, Himmelskugel mit Radius {@link #R}.
 * <p>
 * Alles ist aus der Zeit abgeleitet (kein Zufall pro Bild), dadurch flackert nichts unkontrolliert
 * und Speichern/Laden ändert nichts.
 * <p>
 * Textur-Atlas: obere Hälfte = Querschnitt des Blitzes (u quer), unten links = rundes Leuchten.
 */
public final class LightningGeometry {

    public interface QuadSink {
        void quad(float[] xyz, float[] uv);
    }

    /** Ein Blitz, der gerade sichtbar ist. */
    public record Strike(long hash, double boltIntensity, double glowIntensity) {
    }

    public static final double R = 80.0;
    private static final double DURATION = 1.1;

    private final LightningSettings s;

    public LightningGeometry(LightningSettings settings) {
        this.s = settings;
    }

    // ------------------------------------------------------------------ Zeitablauf

    public List<Strike> active(double timeSec) {
        List<Strike> out = new ArrayList<>(2);
        double L = Math.max(1.0, s.averageInterval());
        long epoch = (long) Math.floor(timeSec / L);
        for (long e = epoch - 1; e <= epoch; e++) {
            long h = ChainGeometry.mix((long) s.seed() * 0x9E3779B97F4A7C15L ^ (e * 0xD1B54A32D192ED03L));
            if (ChainGeometry.rnd(h, 0) > 0.9) continue;           // manche Abschnitte ohne Blitz
            double t0 = e * L + ChainGeometry.rnd(h, 1) * L * 0.85;
            addIfActive(out, h, timeSec - t0);
            if (ChainGeometry.rnd(h, 2) < s.doubleChance()) {
                long h2 = ChainGeometry.mix(h + 1);
                double t1 = t0 + 0.6 + ChainGeometry.rnd(h, 3) * 1.8;
                addIfActive(out, h2, timeSec - t1);
            }
        }
        return out;
    }

    private static void addIfActive(List<Strike> out, long h, double lt) {
        if (lt < 0 || lt > DURATION) return;
        int pulses = 2 + (int) (ChainGeometry.rnd(h, 10) * 3);
        double tk = 0, bolt = 0, glow = 0;
        for (int k = 0; k < pulses; k++) {
            if (k > 0) tk += 0.05 + ChainGeometry.rnd(h, 11 + k) * 0.13;
            double amp = k == 0 ? 1.0 : 0.45 + 0.55 * ChainGeometry.rnd(h, 20 + k);
            if (lt >= tk) {
                double dt = lt - tk;
                bolt = Math.max(bolt, amp * Math.exp(-dt / 0.055));
                glow = Math.max(glow, amp * (0.75 * Math.exp(-dt / 0.07) + 0.25 * Math.exp(-dt / 0.3)));
            }
        }
        if (glow > 0.01) out.add(new Strike(h, bolt, glow));
    }

    // ------------------------------------------------------------------ Form

    private static double[] dir(double az, double el) {
        return new double[]{Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Zickzack per Mittelpunkt-Verschiebung auf der Himmelskugel. */
    private static List<double[]> zigzag(double[] a, double[] b, int levels, double rough, long h, int salt) {
        List<double[]> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        int counter = salt;
        for (int lv = 0; lv < levels; lv++) {
            List<double[]> next = new ArrayList<>();
            for (int i = 0; i < pts.size() - 1; i++) {
                double[] p = pts.get(i), q = pts.get(i + 1);
                double[] m = norm(new double[]{p[0] + q[0], p[1] + q[1], p[2] + q[2]});
                double seg = Math.acos(Math.max(-1, Math.min(1, p[0] * q[0] + p[1] * q[1] + p[2] * q[2])));
                double[] perp = norm(cross(m, new double[]{q[0] - p[0], q[1] - p[1], q[2] - p[2]}));
                double off = (ChainGeometry.rnd(h, counter++) - 0.5) * 2 * seg * rough;
                m = norm(new double[]{m[0] + perp[0] * off, m[1] + perp[1] * off, m[2] + perp[2] * off});
                next.add(p);
                next.add(m);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
        }
        return pts;
    }

    /** Hauptblitz (Index 0) und Verzweigungen. */
    public List<List<double[]>> shape(long h) {
        double az = ChainGeometry.rnd(h, 40) * Math.PI * 2;
        double e0 = Math.toRadians(s.minElevation() + (s.maxElevation() - s.minElevation()) * ChainGeometry.rnd(h, 41));
        double len = Math.toRadians(s.length() * (0.7 + 0.6 * ChainGeometry.rnd(h, 42)));
        double[] a = dir(az, e0);
        double[] b = dir(az + (ChainGeometry.rnd(h, 43) - 0.5) * len * 0.8, e0 - len);
        List<List<double[]>> bolts = new ArrayList<>();
        List<double[]> main = zigzag(a, b, 6, 0.32, h, 100);
        bolts.add(main);
        int n = main.size();
        int nb = (int) Math.round(s.branches() * (0.3 + 0.7 * ChainGeometry.rnd(h, 44)));
        for (int k = 0; k < nb; k++) {
            int idx = (int) (n * (0.15 + 0.6 * ChainGeometry.rnd(h, 50 + k)));
            idx = Math.max(1, Math.min(n - 2, idx));
            double[] p = main.get(idx);
            double[] t = norm(new double[]{main.get(idx + 1)[0] - main.get(idx - 1)[0],
                    main.get(idx + 1)[1] - main.get(idx - 1)[1], main.get(idx + 1)[2] - main.get(idx - 1)[2]});
            double[] side = norm(cross(p, t));
            double sign = ChainGeometry.rnd(h, 60 + k) < 0.5 ? -1 : 1;
            double bl = len * (0.18 + 0.3 * ChainGeometry.rnd(h, 70 + k));
            double[] end = norm(new double[]{
                    p[0] + (t[0] * 0.75 + side[0] * 0.65 * sign) * bl,
                    p[1] + (t[1] * 0.75 + side[1] * 0.65 * sign) * bl,
                    p[2] + (t[2] * 0.75 + side[2] * 0.65 * sign) * bl});
            bolts.add(zigzag(p, end, 4, 0.38, h, 1000 + k * 100));
        }
        return bolts;
    }

    // ------------------------------------------------------------------ Ausgabe

    private final float[] qxyz = new float[12];
    private final float[] quv = new float[8];

    /** Blitz als Band, das immer zur Kamera zeigt. Breite nimmt zum Ende hin ab. */
    public void ribbon(List<double[]> pts, double startWidth, double endWidth, QuadSink sink) {
        int n = pts.size();
        double[][] left = new double[n][], right = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] p = pts.get(i);
            double[] a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(n - 1, i + 1));
            double[] t = new double[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            double[] side = norm(cross(p, t));
            double f = i / (double) (n - 1);
            double hw = (startWidth + (endWidth - startWidth) * f) * s.width();
            left[i] = new double[]{p[0] * R - side[0] * hw, p[1] * R - side[1] * hw, p[2] * R - side[2] * hw};
            right[i] = new double[]{p[0] * R + side[0] * hw, p[1] * R + side[1] * hw, p[2] * R + side[2] * hw};
        }
        for (int i = 0; i < n - 1; i++) {
            put(0, left[i], 0.0, 0.25);
            put(1, left[i + 1], 0.0, 0.25);
            put(2, right[i + 1], 1.0, 0.25);
            put(3, right[i], 1.0, 0.25);
            sink.quad(qxyz, quv);
        }
    }

    /** Rundes Leuchten um den Blitz. */
    public void glow(List<double[]> main, QuadSink sink) {
        double[] c = norm(main.get(main.size() / 3));
        double size = 24.0 * s.glowSize();
        double[] up = Math.abs(c[1]) > 0.98 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] r = norm(cross(c, up));
        double[] u = norm(cross(r, c));
        double[][] corners = {
                {-1, -1, 0.0, 1.0}, {1, -1, 0.5, 1.0}, {1, 1, 0.5, 0.5}, {-1, 1, 0.0, 0.5}
        };
        for (int k = 0; k < 4; k++) {
            double sx = corners[k][0] * size, sy = corners[k][1] * size;
            put(k, new double[]{c[0] * R + r[0] * sx + u[0] * sy, c[1] * R + r[1] * sx + u[1] * sy, c[2] * R + r[2] * sx + u[2] * sy},
                    corners[k][2], corners[k][3]);
        }
        sink.quad(qxyz, quv);
    }

    /** Ganzer Himmel kurz heller: Würfel um die Kamera mit dem hellsten Texel. */
    public void flash(QuadSink sink) {
        double e = 50;
        int[][] faces = {{1, 3, 7, 5}, {0, 4, 6, 2}, {2, 6, 7, 3}, {0, 1, 5, 4}, {4, 5, 7, 6}, {0, 2, 3, 1}};
        for (int[] f : faces) {
            for (int k = 0; k < 4; k++) {
                int c = f[k];
                put(k, new double[]{(c & 1) == 1 ? e : -e, (c & 2) == 2 ? e : -e, (c & 4) == 4 ? e : -e}, 0.25, 0.75);
            }
            sink.quad(qxyz, quv);
        }
    }

    private void put(int k, double[] p, double u, double v) {
        qxyz[k * 3] = (float) p[0];
        qxyz[k * 3 + 1] = (float) p[1];
        qxyz[k * 3 + 2] = (float) p[2];
        quv[k * 2] = (float) u;
        quv[k * 2 + 1] = (float) v;
    }
}
