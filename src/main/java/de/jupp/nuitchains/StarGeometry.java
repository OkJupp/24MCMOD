package de.jupp.nuitchains;

/**
 * Reine Mathe für die funkelnden Sterne (ohne Minecraft): Verteilung, Funkeln, kurzes Aufblitzen,
 * Sternschnuppen und das langsame "Atmen" der zwei großen Sterne. Alles hängt nur von der Zeit ab.
 * <p>
 * Koordinaten: Kamera im Ursprung, Sterne auf einer Kugel mit Radius {@link #R}.
 * Textur-Atlas (4:1): links runder Stern, daneben Stern mit Strahlen, rechte Hälfte Schweif-Streifen.
 */
public final class StarGeometry {

    /** Empfängt ein Viereck: 4 Punkte, je u,v und je Farbe r,g,b,a (16 Werte). */
    public interface QuadSink {
        void quad(float[] xyz, float[] uv, float[] rgba);
    }

    public static final double R = 80.0;
    /** Die zwei großen, hellen Sterne der Himmels-Textur (gleiche Richtung wie dort eingebrannt). */
    private static final double[][] BIG = {norm(0.15, 0.55, -1.0), norm(0.9, 0.35, -0.55)};
    private static final double[] BAND_N = norm(0.35, 0.8, 0.45);
    private static final double[][] PALETTE = {
            {1.00, 0.90, 0.97}, {1.00, 0.70, 0.92}, {0.86, 0.80, 1.00}, {0.78, 0.88, 1.00}, {1.00, 0.84, 0.80}
    };

    private final StarSettings s;
    private final int n;
    private final float[] dir, t1, t2, col;
    private final float[] base, size, amp, freq, phase;
    private final long[] hash;

    public StarGeometry(StarSettings settings) {
        this.s = settings;
        this.n = settings.count();
        dir = new float[n * 3]; t1 = new float[n * 3]; t2 = new float[n * 3]; col = new float[n * 3];
        base = new float[n]; size = new float[n]; amp = new float[n]; freq = new float[n]; phase = new float[n];
        hash = new long[n];
        double[] e1 = norm(cross(BAND_N, new double[]{0, 0, 1}));
        double[] e2 = cross(BAND_N, e1);
        for (int i = 0; i < n; i++) {
            long h = ChainGeometry.mix((long) settings.seed() * 0x9E3779B97F4A7C15L + i * 0xD1B54A32D192ED03L);
            hash[i] = h;
            double[] d;
            if (rnd(h, 0) < 0.35) {
                // dichter entlang des Galaxie-Bands
                double a = rnd(h, 1) * Math.PI * 2;
                double off = gauss(h, 2) * 0.13;
                d = norm(e1[0] * Math.cos(a) + e2[0] * Math.sin(a) + BAND_N[0] * off,
                        e1[1] * Math.cos(a) + e2[1] * Math.sin(a) + BAND_N[1] * off,
                        e1[2] * Math.cos(a) + e2[2] * Math.sin(a) + BAND_N[2] * off);
                if (d[1] < -0.1) d[1] = -d[1];
            } else {
                double y = -0.1 + 1.1 * rnd(h, 1);
                double a = rnd(h, 2) * Math.PI * 2, r = Math.sqrt(Math.max(0, 1 - y * y));
                d = new double[]{r * Math.cos(a), y, r * Math.sin(a)};
            }
            double[] up = Math.abs(d[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double[] a1 = norm(cross(d, up));
            double[] a2 = cross(d, a1);
            for (int k = 0; k < 3; k++) {
                dir[i * 3 + k] = (float) d[k]; t1[i * 3 + k] = (float) a1[k]; t2[i * 3 + k] = (float) a2[k];
            }
            // viele schwache, wenige helle Sterne
            double mag = Math.pow(rnd(h, 3), 3.2);
            // zum Horizont hin schwächer (dickere Luft)
            double ext = smooth(-0.08, 0.35, d[1]);
            base[i] = (float) ((0.10 + 1.75 * mag) * ext);
            size[i] = (float) ((0.7 + 0.8 * mag) * (0.85 + 0.3 * rnd(h, 4)));
            amp[i] = (float) (0.35 + 0.65 * rnd(h, 5));
            freq[i] = (float) (0.45 + 2.2 * rnd(h, 6));
            phase[i] = (float) (rnd(h, 7) * 500.0);
            double[] c = PALETTE[(int) Math.min(PALETTE.length - 1, Math.floor(Math.pow(rnd(h, 8), 1.4) * PALETTE.length))];
            col[i * 3] = (float) c[0]; col[i * 3 + 1] = (float) c[1]; col[i * 3 + 2] = (float) c[2];
        }
    }

    // ------------------------------------------------------------------ Ausgabe

    private final float[] qxyz = new float[12];
    private final float[] quv = new float[8];
    private final float[] qcol = new float[16];

    /** Wie viele Vierecke {@link #build} höchstens erzeugt. */
    public int maxQuads() {
        return n + 4 * 2 + 4 + 40;
    }

    public void build(double t, QuadSink sink) {
        double tw = s.twinkle();
        double speed = s.twinkleSpeed();
        double bright = s.brightness();
        double sz = 0.0047 * s.size();                     // halbe Kantenlänge eines normalen Sterns (Bogenmaß)
        for (int i = 0; i < n; i++) {
            double b0 = base[i] * bright;
            if (b0 < 0.004) continue;
            // Funkeln: zwei weiche Rauschkurven, die schnellere sorgt für das typische Flimmern
            double n1 = ChainGeometry.noise1(t * freq[i] * speed + phase[i], hash[i]);
            double n2 = ChainGeometry.noise1(t * freq[i] * speed * 3.1 + phase[i] * 1.7, hash[i] ^ 0x5A5AL);
            double sc = 1 + tw * amp[i] * (0.62 * n1 + 0.38 * n2);
            double b = b0 * Math.max(0.08, sc);
            // in hellen Momenten etwas weißer
            double wh = Math.max(0, n2) * 0.35 * tw;
            float r = (float) (col[i * 3] + (1 - col[i * 3]) * wh);
            float g = (float) (col[i * 3 + 1] + (1 - col[i * 3 + 1]) * wh);
            float bl = (float) (col[i * 3 + 2] + (1 - col[i * 3 + 2]) * wh);
            double half = sz * size[i] * (0.85 + 0.25 * Math.min(1.5, sc));
            emitSprite(i, half, 0, 0.0F, 0.25F, r, g, bl, (float) Math.min(1, b), sink);
        }
        flares(t, sink);
        bigStars(t, sink);
        shootingStars(t, sink);
    }

    /** Einzelne Sterne blitzen kurz hell auf, mit Strahlenkranz. */
    private void flares(double t, QuadSink sink) {
        if (s.flares() <= 0 || n == 0) return;
        double every = 1.3 / s.flares();
        long epoch = (long) Math.floor(t / every);
        for (long e = epoch - 1; e <= epoch; e++) {
            long h = ChainGeometry.mix(0xF1A5EL ^ (e * 0x9E3779B97F4A7C15L) ^ s.seed());
            double age = t - (e + rnd(h, 1) * 0.8) * every;
            if (age < 0 || age > 1.1) continue;
            // aus drei Kandidaten den hellsten (helle Sterne blitzen öfter)
            int best = -1;
            for (int k = 0; k < 3; k++) {
                int c = (int) (rnd(h, 2 + k) * n);
                if (best < 0 || base[c] > base[best]) best = c;
            }
            if (base[best] < 0.05) continue;
            double env = smooth(0, 0.06, age) * Math.exp(-age / 0.28);
            double half = 0.0042 * s.size() * size[best] * (2.2 + 1.5 * env);
            float a = (float) Math.min(1, env * (0.6 + base[best]) * s.brightness());
            emitSprite(best, half, age * 0.6, 0.25F, 0.5F, 1F, 0.86F, 0.97F, a, sink);
            emitSprite(best, half * 0.55, 0, 0.0F, 0.25F, 1F, 1F, 1F, a, sink);
        }
    }

    /** Die zwei großen Sterne der Himmels-Textur "atmen" langsam und glitzern dabei. */
    private void bigStars(double t, QuadSink sink) {
        if (s.bigStarPulse() <= 0) return;
        for (int k = 0; k < BIG.length; k++) {
            double[] d = BIG[k];
            double scale = k == 0 ? 1.0 : 0.6;
            double breathe = 0.5 + 0.5 * Math.sin(t * (2 * Math.PI / 6.5) + k * 2.1);
            double sparkle = Math.max(0, ChainGeometry.noise1(t * 1.3 + k * 17, 0xB16L + k));
            float glow = (float) Math.min(1, (0.10 + 0.16 * breathe) * s.bigStarPulse());
            float rays = (float) Math.min(1, (0.12 + 0.45 * sparkle * sparkle) * s.bigStarPulse());
            emitAt(d, 0.035 * scale, 0, 0.0F, 0.25F, 1F, 0.62F, 0.88F, glow, sink);
            // Strahlen um 45° gedreht zu den eingebrannten – wirkt wie Glitzern
            emitAt(d, 0.022 * scale * (0.9 + 0.2 * sparkle), Math.PI / 4 + t * 0.02, 0.25F, 0.5F, 1F, 0.9F, 0.98F, rays, sink);
        }
    }

    /** Ab und zu eine Sternschnuppe: schneller, pinker Schweif, der nach unten zieht. */
    private void shootingStars(double t, QuadSink sink) {
        if (s.shootingStarInterval() <= 0) return;
        double every = s.shootingStarInterval();
        long epoch = (long) Math.floor(t / every);
        for (long e = epoch - 1; e <= epoch; e++) {
            long h = ChainGeometry.mix(0x5E7EAL ^ (e * 0xD1B54A32D192ED03L) ^ ((long) s.seed() << 20));
            if (rnd(h, 0) < 0.25) continue;
            double dur = 0.7 + 0.6 * rnd(h, 1);
            double age = t - (e + rnd(h, 2) * 0.8) * every;
            if (age < 0 || age > dur) continue;
            double az = rnd(h, 3) * Math.PI * 2, el = Math.toRadians(30 + 40 * rnd(h, 4));
            double[] start = {Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az)};
            // Flugrichtung: schräg nach unten
            double[] east = norm(cross(new double[]{0, 1, 0}, start));
            double[] down = norm(cross(start, east));             // zeigt nach oben – umdrehen
            double side = (rnd(h, 5) - 0.5) * 2.2;
            double[] dirT = norm(-down[0] * 0.8 + east[0] * side, -down[1] * 0.8 + east[1] * side, -down[2] * 0.8 + east[2] * side);
            double[] axis = norm(cross(start, dirT));
            double arc = Math.toRadians(16 + 18 * rnd(h, 6));
            double p = age / dur;
            double headA = arc * (p * (0.6 + 0.4 * p));              // wird etwas schneller
            double tailA = Math.max(0, headA - arc * 0.45);
            double env = Math.pow(Math.sin(Math.PI * Math.min(1, p * 1.05)), 0.6);
            int seg = 10;
            double w = 0.0030 * s.size();
            double prevLx = 0, prevLy = 0, prevLz = 0, prevRx = 0, prevRy = 0, prevRz = 0;
            float prevA = 0;
            for (int k = 0; k <= seg; k++) {
                double f = k / (double) seg;                          // 0 Schweifende … 1 Kopf
                double ang = tailA + (headA - tailA) * f;
                double[] pt = rotate(start, axis, ang);
                double[] tan = cross(axis, pt);
                double[] sd = norm(cross(pt, tan));
                double hw = R * w * (0.4 + 0.9 * f);
                double x = pt[0] * R, y = pt[1] * R, z = pt[2] * R;
                float a = (float) (env * f * f);
                if (k > 0) {
                    put(0, prevLx, prevLy, prevLz, 0.5F + 0.5F * (float) ((k - 1) / (double) seg), 0F);
                    put(1, x - sd[0] * hw, y - sd[1] * hw, z - sd[2] * hw, 0.5F + 0.5F * (float) f, 0F);
                    put(2, x + sd[0] * hw, y + sd[1] * hw, z + sd[2] * hw, 0.5F + 0.5F * (float) f, 1F);
                    put(3, prevRx, prevRy, prevRz, 0.5F + 0.5F * (float) ((k - 1) / (double) seg), 1F);
                    color(0, 1F, 0.75F, 0.93F, prevA); color(1, 1F, 0.8F, 0.95F, a);
                    color(2, 1F, 0.8F, 0.95F, a); color(3, 1F, 0.75F, 0.93F, prevA);
                    sink.quad(qxyz, quv, qcol);
                }
                prevLx = x - sd[0] * hw; prevLy = y - sd[1] * hw; prevLz = z - sd[2] * hw;
                prevRx = x + sd[0] * hw; prevRy = y + sd[1] * hw; prevRz = z + sd[2] * hw;
                prevA = a;
            }
            double[] head = rotate(start, axis, headA);
            emitAt(head, 0.009 * s.size(), 0, 0.0F, 0.25F, 1F, 0.95F, 1F, (float) env, sink);
            emitAt(head, 0.026 * s.size(), 0, 0.0F, 0.25F, 1F, 0.55F, 0.85F, (float) (env * 0.5), sink);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private void emitSprite(int i, double half, double rot, float u0, float u1, float r, float g, float b, float a, QuadSink sink) {
        double c = Math.cos(rot), sn = Math.sin(rot);
        double ax = (t1[i * 3] * c + t2[i * 3] * sn) * half, ay = (t1[i * 3 + 1] * c + t2[i * 3 + 1] * sn) * half,
                az = (t1[i * 3 + 2] * c + t2[i * 3 + 2] * sn) * half;
        double bx = (t2[i * 3] * c - t1[i * 3] * sn) * half, by = (t2[i * 3 + 1] * c - t1[i * 3 + 1] * sn) * half,
                bz = (t2[i * 3 + 2] * c - t1[i * 3 + 2] * sn) * half;
        quadAt(dir[i * 3], dir[i * 3 + 1], dir[i * 3 + 2], ax, ay, az, bx, by, bz, u0, u1, r, g, b, a, sink);
    }

    private void emitAt(double[] d, double half, double rot, float u0, float u1, float r, float g, float b, float a, QuadSink sink) {
        if (a <= 0.002F) return;
        double[] up = Math.abs(d[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] a1 = norm(cross(d, up));
        double[] a2 = cross(d, a1);
        double c = Math.cos(rot), sn = Math.sin(rot);
        quadAt(d[0], d[1], d[2],
                (a1[0] * c + a2[0] * sn) * half, (a1[1] * c + a2[1] * sn) * half, (a1[2] * c + a2[2] * sn) * half,
                (a2[0] * c - a1[0] * sn) * half, (a2[1] * c - a1[1] * sn) * half, (a2[2] * c - a1[2] * sn) * half,
                u0, u1, r, g, b, a, sink);
    }

    private void quadAt(double dx, double dy, double dz, double ax, double ay, double az, double bx, double by, double bz,
                        float u0, float u1, float r, float g, float b, float a, QuadSink sink) {
        put(0, (dx - ax - bx) * R, (dy - ay - by) * R, (dz - az - bz) * R, u0, 0F);
        put(1, (dx - ax + bx) * R, (dy - ay + by) * R, (dz - az + bz) * R, u0, 1F);
        put(2, (dx + ax + bx) * R, (dy + ay + by) * R, (dz + az + bz) * R, u1, 1F);
        put(3, (dx + ax - bx) * R, (dy + ay - by) * R, (dz + az - bz) * R, u1, 0F);
        for (int k = 0; k < 4; k++) color(k, r, g, b, a);
        sink.quad(qxyz, quv, qcol);
    }

    private void put(int k, double x, double y, double z, float u, float v) {
        qxyz[k * 3] = (float) x; qxyz[k * 3 + 1] = (float) y; qxyz[k * 3 + 2] = (float) z;
        quv[k * 2] = u; quv[k * 2 + 1] = v;
    }

    private void color(int k, float r, float g, float b, float a) {
        qcol[k * 4] = r; qcol[k * 4 + 1] = g; qcol[k * 4 + 2] = b; qcol[k * 4 + 3] = a;
    }

    private static double rnd(long h, int k) {
        return ChainGeometry.rnd(h, k);
    }

    private static double gauss(long h, int k) {
        double u1 = Math.max(1e-9, rnd(h, k)), u2 = rnd(h, k + 100);
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private static double[] norm(double[] v) {
        return norm(v[0], v[1], v[2]);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /** Dreht v um die Achse k (Rodrigues). */
    private static double[] rotate(double[] v, double[] k, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double[] kxv = cross(k, v);
        double kd = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
        return new double[]{v[0] * c + kxv[0] * s + k[0] * kd * (1 - c),
                v[1] * c + kxv[1] * s + k[1] * kd * (1 - c),
                v[2] * c + kxv[2] * s + k[2] * kd * (1 - c)};
    }
}
