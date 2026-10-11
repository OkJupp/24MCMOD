package de.jupp.nuitchains;

import java.util.Arrays;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: weiche Nebelschwaden (zur Kamera gedrehte Vierecke)
 * entlang der Nebelbänke aus {@link FogField}. Zwei Schichten pro Rasterzelle – eine lockere oben an
 * der Kante, eine dichtere darunter. Ausgabe relativ zur Kamera, von hinten nach vorne sortiert.
 * <p>
 * Textur: 2×2 verschiedene Schwaden; Farbe pro Ecke (oben heller, unten dunkler, zur Sonne hin warm).
 */
public final class FogGeometry {

    /** Empfängt ein Viereck: 4 Punkte (x,y,z), je u,v, je r,g,b,a. */
    public interface QuadSink {
        void quad(float[] xyz, float[] uv, float[] rgba);
    }

    /** Beleuchtung der Schwaden (aus Tageszeit, Sonne und Blitzen). */
    public static final class Light {
        public final double[] top = {0.5, 0.3, 0.55};
        public final double[] bottom = {0.2, 0.1, 0.28};
        public final double[] sun = {1.0, 0.55, 0.35};
        public double sunWeight;
        public double sunX, sunZ;
        public final double[] flash = {0.55, 0.4, 0.7};
        public double flashAmount;

        /** Nebelfarben je nach Tageszeit (Nacht: violett-pink, Tag: weiß-rosa, Dämmerung: pfirsich). */
        public void set(double day, double twilight, double sunX, double sunZ, double flash, double flashGlow) {
            double[] nightTop = {0.64, 0.34, 0.70}, nightBot = {0.27, 0.12, 0.35};
            double[] dayTop = {1.00, 0.95, 0.98}, dayBot = {0.80, 0.69, 0.83};
            double[] twTop = {1.00, 0.70, 0.66}, twBot = {0.45, 0.24, 0.48};
            double tw = Math.min(1, twilight * 0.75);
            for (int c = 0; c < 3; c++) {
                double t = nightTop[c] + (dayTop[c] - nightTop[c]) * day;
                double b = nightBot[c] + (dayBot[c] - nightBot[c]) * day;
                this.top[c] = t + (twTop[c] - t) * tw;
                this.bottom[c] = b + (twBot[c] - b) * tw;
            }
            this.sunWeight = twilight * 0.6;
            this.sunX = sunX;
            this.sunZ = sunZ;
            this.flashAmount = flash * flashGlow;
        }
    }

    private static final double CELL = 80;
    private static final double BASE_SIZE = 150;

    private final FogField field;
    private final FogSettings s;
    private final long seed;

    private double[] pd = new double[8 * 512];   // je Schwade: x,y,z, Größe, Deckkraft, Winkel, Variante, Abstand²
    private long[] order = new long[512];
    private final float[] xyz = new float[12];
    private final float[] uv = new float[8];
    private final float[] rgba = new float[16];

    public FogGeometry(FogField field) {
        this.field = field;
        this.s = field.settings();
        this.seed = ChainGeometry.mix(this.s.seed() * 0x632BE59BD9B4E019L ^ 0xF0F0L);
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Größte Ausdehnung einer Schwade (für Puffergrößen). */
    public int maxQuads() {
        int n = (int) Math.ceil(this.s.range() / CELL) * 2 + 2;
        return n * n * 2;
    }

    public int build(double ex, double ey, double ez, double t, double strength, Light light, QuadSink sink) {
        if (strength <= 0.001 || this.s.density() <= 0) return 0;
        double range = this.s.range();
        double wx = this.field.windX(), wz = this.field.windZ();
        double qx = ex - wx * t, qz = ez - wz * t;   // Kamera im mitreisenden Raster
        long i0 = (long) Math.floor((qx - range) / CELL), i1 = (long) Math.floor((qx + range) / CELL);
        long k0 = (long) Math.floor((qz - range) / CELL), k1 = (long) Math.floor((qz + range) / CELL);
        double thick = this.s.thickness();
        double clear = Math.max(1, this.s.clearRadius());
        int n = 0;
        for (long i = i0; i <= i1; i++) {
            for (long k = k0; k <= k1; k++) {
                long h = ChainGeometry.mix(this.seed ^ (i * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL));
                for (int layer = 0; layer < 2; layer++) {
                    int o = layer * 8;
                    double x = (i + 0.1 + 0.8 * ChainGeometry.rnd(h, o + 1)) * CELL + wx * t;
                    double z = (k + 0.1 + 0.8 * ChainGeometry.rnd(h, o + 2)) * CELL + wz * t;
                    double dx = x - ex, dz = z - ez;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > range) continue;
                    double cover = this.field.cover(x, z, t);
                    if (cover < 0.03) continue;
                    double top = this.field.top(x, z, t);
                    double y = layer == 0 ? top - thick * (0.15 + 0.22 * ChainGeometry.rnd(h, o + 3))
                            : top - thick * (0.55 + 0.35 * ChainGeometry.rnd(h, o + 3));
                    double size = BASE_SIZE * this.s.puffSize() * (0.7 + 0.6 * ChainGeometry.rnd(h, o + 4))
                            * (0.6 + 0.4 * cover) * (layer == 1 ? 1.25 : 1.0);
                    double alpha = this.s.density() * strength * Math.pow(cover, 0.7) * (layer == 0 ? 0.5 : 0.72)
                            * (1 - smooth(range * 0.72, range, d))
                            * smooth(clear * 0.55, clear, d);
                    if (alpha < 0.01) continue;
                    double angle = ChainGeometry.rnd(h, o + 5) * Math.PI * 2 + t * 0.012 * (ChainGeometry.rnd(h, o + 6) - 0.5);
                    if ((n + 1) * 8 > this.pd.length) this.pd = Arrays.copyOf(this.pd, this.pd.length * 2);
                    int b = n * 8;
                    double cy = y - ey;
                    this.pd[b] = dx; this.pd[b + 1] = cy; this.pd[b + 2] = dz;
                    this.pd[b + 3] = size; this.pd[b + 4] = Math.min(1, alpha); this.pd[b + 5] = angle;
                    this.pd[b + 6] = Math.floor(ChainGeometry.rnd(h, o + 7) * 4);
                    this.pd[b + 7] = dx * dx + cy * cy + dz * dz;
                    n++;
                }
            }
        }
        if (this.order.length < n) this.order = new long[n * 2];
        for (int q = 0; q < n; q++) {
            this.order[q] = ((long) Float.floatToIntBits((float) this.pd[q * 8 + 7]) << 32) | q;
        }
        Arrays.sort(this.order, 0, n);
        double sunH = Math.sqrt(light.sunX * light.sunX + light.sunZ * light.sunZ);
        for (int q = n - 1; q >= 0; q--) {
            emit((int) (this.order[q] & 0xFFFFFFFFL), light, sunH, sink);
        }
        return n;
    }

    private void emit(int q, Light light, double sunH, QuadSink sink) {
        int b = q * 8;
        double cx = this.pd[b], cy = this.pd[b + 1], cz = this.pd[b + 2];
        double hs = this.pd[b + 3] * 0.5, alpha = this.pd[b + 4], ang = this.pd[b + 5];
        int variant = (int) this.pd[b + 6];
        double dist = Math.sqrt(this.pd[b + 7]);
        double nx = cx / dist, ny = cy / dist, nz = cz / dist;
        // rechts = Blickrichtung × oben
        double rx = -nz, ry = 0, rz = nx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = ry * nz - rz * ny, uy = rz * nx - rx * nz, uz = rx * ny - ry * nx;
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double r2x = rx * ca + ux * sa, r2y = ry * ca + uy * sa, r2z = rz * ca + uz * sa;
        double u2x = ux * ca - rx * sa, u2y = uy * ca - ry * sa, u2z = uz * ca - rz * sa;
        // Sonnenseite (warm angestrahlt in der Dämmerung)
        double sunSide = 0;
        if (light.sunWeight > 0 && sunH > 1e-4) {
            double hl = Math.sqrt(cx * cx + cz * cz);
            if (hl > 1e-4) {
                double c = (cx * light.sunX + cz * light.sunZ) / (hl * sunH);
                sunSide = c > 0 ? c * c * light.sunWeight : 0;
            }
        }
        float u0 = (variant & 1) * 0.5F, v0 = (variant >> 1) * 0.5F;
        double[] sx = {-1, 1, 1, -1}, sy = {-1, -1, 1, 1};
        for (int v = 0; v < 4; v++) {
            double ox = (r2x * sx[v] + u2x * sy[v]) * hs, oy = (r2y * sx[v] + u2y * sy[v]) * hs, oz = (r2z * sx[v] + u2z * sy[v]) * hs;
            this.xyz[v * 3] = (float) (cx + ox);
            this.xyz[v * 3 + 1] = (float) (cy + oy);
            this.xyz[v * 3 + 2] = (float) (cz + oz);
            this.uv[v * 2] = u0 + (sx[v] > 0 ? 0.5F : 0F);
            this.uv[v * 2 + 1] = v0 + (sy[v] > 0 ? 0F : 0.5F);
            // oben hell, unten dunkel – nach der echten Höhe der Ecke, nicht nach der Drehung
            double k = Math.max(0, Math.min(1, (oy / (hs * 1.41) + 1) * 0.5));
            for (int c = 0; c < 3; c++) {
                double col = light.bottom[c] + (light.top[c] - light.bottom[c]) * k;
                col += light.sun[c] * sunSide * (0.45 + 0.55 * k);
                col += light.flash[c] * light.flashAmount;
                this.rgba[v * 4 + c] = (float) Math.min(1, col);
            }
            this.rgba[v * 4 + 3] = (float) alpha;
        }
        sink.quad(this.xyz, this.uv, this.rgba);
    }
}
