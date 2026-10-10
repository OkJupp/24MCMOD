package de.jupp.nuitchains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: berechnet, wo die Ketten hängen, wie sie pendeln,
 * wo sie zerfallen, und erzeugt Kettenglieder (glatte Rohre mit Dornen) und Kristall-Splitter
 * als Vierecke.
 * <p>
 * Alle Positionen werden relativ zur Kamera ausgegeben (echte Abstände), pro Kette von hinten
 * nach vorne sortiert.
 * <p>
 * Textur: v = Helligkeit (0 hell, 1 dunkel), u = Glühen (0 Kristall/Metall, 1 glühende Bruchstelle).
 * Beides wird pro Ecke berechnet und von der Grafikkarte weich überblendet.
 */
public final class ChainGeometry {

    /** Empfängt ein fertiges Viereck: 4 Punkte (x,y,z relativ zur Kamera), je u,v und je eine Deckkraft (0..1). */
    public interface QuadSink {
        void quad(float[] xyz, float[] uv, float[] alpha);
    }

    /** Eine Kette, die in diesem Frame gezeichnet wird. */
    public record Chain(long hash, double ax, double ay, double az, double length, int links,
                        double phase1, double phase2, double swingAxis, double twist,
                        double leanX, double leanZ, boolean broken, int brokenLinks,
                        double scale, double fade, double distance) {
    }

    private final ChainSettings s;
    private final DamageSettings dmg;
    private final VariationSettings var;
    private final MotionSettings mot;
    private final LinkMesh meshNear;
    private final LinkMesh meshMid;
    private final LinkMesh meshFar;
    private final LinkMesh meshDamaged;

    private final List<Part> parts = new ArrayList<>();
    private long[] partOrder = new long[64];

    private final float glowStrength;

    public ChainGeometry(ChainSettings settings, DamageSettings damage, VariationSettings variation, MotionSettings motion) {
        this(settings, damage, variation, motion, 0.3F);
    }

    public ChainGeometry(ChainSettings settings, DamageSettings damage, VariationSettings variation, MotionSettings motion,
                         float glowStrength) {
        this.glowStrength = glowStrength;
        this.s = settings;
        this.dmg = damage;
        this.var = variation;
        this.mot = motion;
        this.meshNear = new LinkMesh(settings, 13, 12, 8);
        this.meshMid = new LinkMesh(settings, 8, 8, 5);
        this.meshFar = new LinkMesh(settings, 5, 6, 4);
        this.meshDamaged = new LinkMesh(settings, 30, 22, 10);
    }

    // ------------------------------------------------------------------ Zufall & Rauschen

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static double rnd(long h, int n) {
        return (mix(h + n * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /** Glattes 3D-Wertrauschen (0..1). */
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

    /** Zwei Oktaven Rauschen – organischere Bruchkanten. */
    static double fbm(double x, double y, double z, long seed) {
        return (noise(x, y, z, seed) * 0.65 + noise(x * 2.3 + 7.1, y * 2.3, z * 2.3, seed ^ 0x5DEECE66DL) * 0.35);
    }

    /** Glattes 1D-Rauschen (-1..1), zweimal stetig differenzierbar – für weiche Bewegungen ohne Ruckeln. */
    static double noise1(double x, long seed) {
        long xi = (long) Math.floor(x);
        double f = x - xi;
        double w = f * f * f * (f * (f * 6 - 15) + 10);
        double a = (mix(seed ^ (xi * 0x632BE59BD9B4E019L)) >>> 11) * 0x1.0p-53;
        double b = (mix(seed ^ ((xi + 1) * 0x632BE59BD9B4E019L)) >>> 11) * 0x1.0p-53;
        return (a + (b - a) * w) * 2 - 1;
    }

    /** Mehrere Oktaven 1D-Rauschen – unregelmäßige, aber weiche Böen. */
    static double gust(double x, long seed) {
        return noise1(x, seed) * 0.6 + noise1(x * 2.1 + 3.7, seed ^ 0x1234567L) * 0.28 + noise1(x * 4.3 + 9.1, seed ^ 0x7654321L) * 0.12;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /**
     * Helligkeit 0..1 für ein glasiges Kristall-Metall: weiches Licht von oben,
     * helle Ränder (Fresnel) und ein kleiner Glanzpunkt.
     */
    static double shade(double normalY, double nDotView) {
        double lam = 0.5 + 0.5 * normalY;
        double edge = 1 - Math.abs(nDotView);
        double fres = edge * edge * Math.sqrt(edge);
        double f = Math.max(0, nDotView);
        double f2 = f * f, f4 = f2 * f2, spec = f4 * f4 * f4;
        double l = 0.10 + 0.25 * lam + 0.72 * fres + 0.15 * spec;
        return Math.min(0.995, Math.max(0.005, l));
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
                double scale;
                if (rnd(h, 12) < var.giantChance()) {
                    scale = var.giantSize() * (0.85 + 0.3 * rnd(h, 13));
                    len *= 1.1;                                    // Riesen hängen etwas länger
                } else {
                    double t = rnd(h, 13);
                    scale = var.sizeMin() + (var.sizeMax() - var.sizeMin()) * t;
                }
                double topY = s.topY() + (rnd(h, 14) * 2 - 1) * var.topYVariation() + (scale - 1) * 60;
                // nie tiefer als minBottomY hängen
                len = Math.max(Math.min(40, len), Math.min(len, topY - var.minBottomY()));
                double leanA = rnd(h, 8) * Math.PI * 2;
                double leanM = Math.toRadians(s.lean()) * (0.25 + 0.75 * rnd(h, 9));
                double leanX = Math.sin(leanM) * Math.cos(leanA), leanZ = Math.sin(leanM) * Math.sin(leanA);
                double mx = ax + leanX * len * 0.5, mz = az + leanZ * len * 0.5, my = topY - len * 0.5;
                double dx = mx - ex, dz = mz - ez;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double fade = 1.0 - smooth(fadeStart, fadeEnd, horiz);
                if (fade <= 0.001) continue;
                double dist = Math.sqrt(horiz * horiz + (my - ey) * (my - ey));
                boolean broken = rnd(h, 10) < dmg.brokenChance();
                int bl = Math.max(1, (int) Math.round(dmg.brokenLinks() * (0.6 + 0.8 * rnd(h, 11))));
                out.add(new Chain(h, ax, topY, az, len, Math.max(2, (int) Math.round(len / (pitch * scale))),
                        rnd(h, 4) * Math.PI * 2, rnd(h, 5) * Math.PI * 2,
                        rnd(h, 6) * Math.PI, rnd(h, 7) * Math.PI, leanX, leanZ, broken, bl, scale, fade, dist));
            }
        }
        out.sort((a, b) -> Double.compare(b.distance(), a.distance()));
        return out;
    }

    private double pitch() {
        return Math.max(0.2, s.linkLength() - 2.3 * s.thickness());
    }

    // ------------------------------------------------------------------ Aufbau einer Kette

    /** Ein Teil der Kette: Glied oder Splitter. */
    private static final class Part {
        boolean shard;
        double cx, cy, cz;
        double ux, uy, uz, wx, wy, wz, nx, ny, nz;
        double damage;
        long seed;
        double sx, sy, sz;
        double glow;
        boolean hit;      // Einschlag statt Zerfall
        double scale = 1; // Größe des Glieds
        double pulse = 1; // Glühen der Bruchkanten (pulsiert)
        float alpha;      // grobe Deckkraft des ganzen Teils (zum Überspringen)
        double topRelY;   // Aufhängehöhe relativ zur Kamera (für weiches Ausblenden pro Ecke)
        double fadeLen;   // Länge des Ausblendens
        float shardAlpha = 1F;
    }

    /** Weiches Ausblenden nach oben: 0 an der Aufhängung, 1 nach {@code fadeLen} Blöcken – sehr sanfter Start. */
    static float topAlpha(double drop, double fadeLen) {
        double x = Math.min(1, Math.max(0, drop / fadeLen));
        double sm = x * x * x * (x * (x * 6 - 15) + 10);           // smootherstep
        return (float) Math.pow(sm, 1.35);
    }

    private Part part(int idx) {
        while (this.parts.size() <= idx) this.parts.add(new Part());
        return this.parts.get(idx);
    }

    public int build(Chain c, double ex, double ey, double ez, double timeSec, QuadSink sink) {
        return build(c, ex, ey, ez, timeSec, sink, sink, null, 0);
    }

    /**
     * Erzeugt alle sichtbaren Vierecke einer Kette, von hinten nach vorne.
     * Teile näher als {@code splitDistance} gehen an {@code nearSink} (echte 3D-Welt mit Tiefe),
     * alle anderen an {@code farSink} (Himmel).
     */
    public int build(Chain c, double ex, double ey, double ez, double timeSec,
                     QuadSink farSink, QuadSink nearSink, QuadSink glowSink, double splitDistance) {
        double split2 = splitDistance * splitDistance;
        // Ausblenden nie länger als 40 % der Kette – sonst wird die Kette nirgends richtig sichtbar
        double topFade = Math.max(0.01, Math.min(s.topFade() * Math.sqrt(c.scale()), c.length() * 0.4));
        double scale = c.scale();
        double pitch = pitch() * scale;
        double swing = Math.toRadians(s.swingDegrees());
        // schwere, lange Ketten schwingen langsamer
        double period = (9.0 + c.length() / 22.0) * Math.sqrt(scale) / Math.max(0.05, s.swingSpeed());
        double w1 = 2 * Math.PI / period;
        double w2 = w1 * 0.618;                                  // goldener Schnitt: wiederholt sich nie sichtbar
        double ca = Math.cos(c.swingAxis()), sa = Math.sin(c.swingAxis());
        double lagTotal = s.wave() * period * 0.35;              // so lange braucht eine Bewegung bis nach unten
        double windAmp = Math.toRadians(mot.windStrength());
        double windRate = 0.07 * mot.windSpeed();
        long windSeed = mix((long) s.seed() * 0x2545F4914F6CDD1DL);
        double response = 0.7 + 0.6 * rnd(c.hash(), 80);         // jede Kette fängt den Wind etwas anders
        double torsion = Math.toRadians(mot.torsionDegrees());
        double torsionT = timeSec * 0.045 * mot.torsionSpeed() / Math.sqrt(scale);
        double torsionNow = torsion * (0.65 * Math.sin(torsionT * 2 * Math.PI + c.phase2()) + 0.35 * noise1(torsionT * 1.7, c.hash()));
        double pulseBase = mot.emberPulse();
        // Welle, die langsam von oben nach unten durch die Kette läuft (unten etwas stärker – wie eine Peitsche)
        double waveAmp = Math.toRadians(mot.waveDegrees());
        double waveK = 2 * Math.PI / Math.max(2, mot.waveLength() / Math.sqrt(scale));
        double waveW = 2 * Math.PI / (mot.wavePeriod() * Math.sqrt(scale));
        double waveDir = c.swingAxis() + Math.PI * 0.5 + 0.7 * noise1(timeSec * 0.015, c.hash() ^ 0x77L);
        double wdx = Math.cos(waveDir), wdz = Math.sin(waveDir);
        double topRelY = c.ay() - ey;
        if (this.glowX.length < c.links() + 1) {
            this.glowX = new double[c.links() * 2 + 2]; this.glowY = new double[c.links() * 2 + 2];
            this.glowZ = new double[c.links() * 2 + 2]; this.glowA = new double[c.links() * 2 + 2];
        }
        this.glowX[0] = c.ax() - ex; this.glowY[0] = c.ay() - ey; this.glowZ[0] = c.az() - ez; this.glowA[0] = 0;

        int nLinks = c.links();
        int nParts = 0;
        double px = c.ax(), py = c.ay(), pz = c.az();
        double lastUx = 0, lastUy = -1, lastUz = 0;
        for (int i = 0; i < nLinks; i++) {
            double f = nLinks > 1 ? i / (double) (nLinks - 1) : 0;   // 0 oben … 1 unten
            double t = timeSec - f * lagTotal;
            // eigenes Pendeln (zwei sanfte Schwingungen)
            double th1 = swing * Math.sin(w1 * t + c.phase1());
            double th2 = 0.35 * swing * Math.sin(w2 * t + c.phase2());
            double sx = Math.sin(th1) * ca - Math.sin(th2) * sa;
            double sz = Math.sin(th1) * sa + Math.sin(th2) * ca;
            // gemeinsamer Wind: langsam drehende Richtung, weiche Böen
            double wt = t * windRate;
            double windDir = noise1(wt * 0.25, windSeed) * Math.PI;
            double windMag = windAmp * response * (0.55 + 0.45 * gust(wt, windSeed ^ 0x55L));
            sx += Math.sin(windMag) * Math.cos(windDir);
            sz += Math.sin(windMag) * Math.sin(windDir);
            // unten schwingt es etwas weiter aus als oben
            double reach = 0.6 + 0.4 * f;
            // Welle: wandert nach unten, oben kaum, unten etwas mehr Ausschlag
            double phase = waveK * i - waveW * timeSec + c.phase1();
            double wa = waveAmp * (0.2 + 0.8 * f) * (0.75 + 0.25 * Math.sin(timeSec * 0.13 + c.phase2()));
            double wv = Math.sin(wa * Math.sin(phase));
            double wv2 = Math.sin(0.35 * wa * Math.sin(phase * 0.71 + 1.3));
            sx += wv * wdx - wv2 * wdz;
            sz += wv * wdz + wv2 * wdx;
            double ux = c.leanX() + sx * reach;
            double uz = c.leanZ() + sz * reach;
            double uy = -Math.sqrt(Math.max(0.0001, 1 - ux * ux - uz * uz));
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            lastUx = ux; lastUy = uy; lastUz = uz;

            Part p = part(nParts++);
            p.shard = false;
            p.scale = scale;
            p.pulse = 1.0 - pulseBase * 0.5 + pulseBase * 0.5 * Math.sin(timeSec * (1.6 + rnd(c.hash(), 300 + i) * 1.2) + rnd(c.hash(), 400 + i) * 6.283);
            p.cx = px + ux * pitch * 0.5 - ex;
            p.cy = py + uy * pitch * 0.5 - ey;
            p.cz = pz + uz * pitch * 0.5 - ez;
            px += ux * pitch; py += uy * pitch; pz += uz * pitch;
            this.glowX[i + 1] = px - ex; this.glowY[i + 1] = py - ey; this.glowZ[i + 1] = pz - ez;

            double tw = c.twist() + (i & 1) * Math.PI * 0.5 + (rnd(c.hash(), 100 + i) - 0.5) * 0.12 + torsionNow * f;
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

            double damage = 0;
            boolean hit = false;
            if (c.broken()) {
                int fromEnd = nLinks - 1 - i;
                if (fromEnd < c.brokenLinks()) {
                    double fe = 1.0 - fromEnd / (double) c.brokenLinks();
                    damage = 0.22 + 0.6 * fe * fe;
                }
            }
            // Einschläge überall entlang der Kette (nicht im ausgeblendeten oberen Teil)
            if (damage == 0 && i * pitch > topFade * 0.5 && rnd(p.seed, 1) < dmg.wear()) {
                damage = dmg.wearStrength() * (0.6 + 0.4 * rnd(p.seed, 2));
                hit = true;
            }
            p.damage = damage;
            p.hit = hit;
            // oben langsam aus dem Nichts einblenden (pro Ecke, siehe LinkMesh.emit)
            p.topRelY = topRelY;
            p.fadeLen = topFade;
            p.shardAlpha = 1F;
            p.alpha = topAlpha(topRelY - (p.cy - pitch), topFade);   // Unterkante des Glieds
            // Leuchten: zum Ende hin weich auslaufen (bei zerfallenden Ketten schon vor den zerfallenen Gliedern),
            // beschädigte Glieder glühen etwas stärker
            double endFade = c.broken()
                    ? 1 - smooth(nLinks - c.brokenLinks() - 1.5, nLinks - c.brokenLinks() * 0.4, i + 1)
                    : 1 - smooth(nLinks - 2.5, nLinks, i + 1);
            this.glowA[i + 1] = topAlpha(topRelY - (py - ey), topFade) * endFade * (1 + 0.9 * Math.min(1, damage * 1.5));
        }

        // Kristall-Splitter unter einem zerfallenden Ende
        if (c.broken() && dmg.fragments() > 0) {
            double size = s.thickness() * 0.45 * scale;
            double life = 10.0;
            for (int k = 0; k < dmg.fragments(); k++) {
                long fh = mix(c.hash() ^ (k * 0xD1B54A32D192ED03L));
                double age = (timeSec / (life * (0.7 + 0.6 * rnd(fh, 1))) + rnd(fh, 2)) % 1.0;
                double life01 = Math.min(1, age * 8) * (1 - age * age) * (0.35 + 0.8 * rnd(fh, 3));
                if (life01 < 0.04) continue;
                double ang = rnd(fh, 4) * Math.PI * 2;
                double rad = s.linkWidth() * scale * (0.15 + 0.9 * rnd(fh, 5)) * (0.3 + age);
                // sanft beschleunigend fallen, leicht seitlich treiben
                double fall = dmg.fragmentFall() * scale * (0.35 * age + 0.65 * age * age) * (0.5 + rnd(fh, 6)) + s.linkLength() * scale * 0.2;
                ang += 0.6 * Math.sin(age * 3.0 + rnd(fh, 15) * 6.28) * age;
                Part p = part(nParts++);
                p.shard = true;
                p.cx = px + Math.cos(ang) * rad + lastUx * fall - ex;
                p.cy = py + lastUy * fall - ey;
                p.cz = pz + Math.sin(ang) * rad + lastUz * fall - ez;
                double spin = timeSec * (0.3 + 0.8 * rnd(fh, 7)) + rnd(fh, 8) * 6.3;
                double a1 = rnd(fh, 9) * Math.PI * 2, a2 = Math.acos(2 * rnd(fh, 10) - 1);
                setRotation(p, Math.sin(a2) * Math.cos(a1), Math.cos(a2), Math.sin(a2) * Math.sin(a1), spin);
                p.sx = size * life01 * (0.5 + 0.7 * rnd(fh, 11));
                p.sy = size * life01 * (1.2 + 1.6 * rnd(fh, 12)); // länglich wie Splitter
                p.sz = size * life01 * (0.4 + 0.5 * rnd(fh, 13));
                p.glow = 0.75 + 0.25 * rnd(fh, 14);               // glühende Tropfen
                p.seed = fh;
                p.damage = 0;
                p.hit = false;
                p.alpha = 1F;
                p.topRelY = topRelY;
                p.fadeLen = topFade;
                p.shardAlpha = (float) (smooth(0, 0.08, age) * smooth(0, 0.25, 1 - age));
            }
        }

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
            if (p.alpha <= 0.004F) continue;
            QuadSink sink = d2 < split2 ? nearSink : farSink;
            if (p.shard) {
                count += emitShard(p, sink);
            } else {
                double ld2 = d2 / (p.scale * p.scale);           // große Glieder behalten Details länger
                LinkMesh mesh = ld2 < 200 * 200 ? (p.damage > 0.001 ? this.meshDamaged : this.meshNear)
                        : ld2 < 420 * 420 ? this.meshMid : this.meshFar;
                count += mesh.emit(p, sink);
            }
        }
        if (glowSink != null && this.glowStrength > 0) {
            emitGlow(c, nLinks + 1, glowSink);
        }
        return count;
    }

    // ------------------------------------------------------------------ Leuchten um die Kette

    private double[] glowX = new double[64], glowY = new double[64], glowZ = new double[64], glowA = new double[64];
    private final float[] gxyz = new float[12];
    private final float[] guv = new float[8];
    private final float[] galpha = new float[4];

    /** Weiches Band entlang der Kettenmitte, immer zur Kamera gedreht – wirkt wie ein Leuchten (Bloom). */
    private void emitGlow(Chain c, int n, QuadSink sink) {
        double hw = s.linkWidth() * c.scale() * 1.35;
        double prevLx = 0, prevLy = 0, prevLz = 0, prevRx = 0, prevRy = 0, prevRz = 0;
        float prevA = 0;
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double tx = glowX[b] - glowX[a], ty = glowY[b] - glowY[a], tz = glowZ[b] - glowZ[a];
            double x = glowX[i], y = glowY[i], z = glowZ[i];
            // Seite = Blickrichtung × Kettenrichtung
            double sx = y * tz - z * ty, sy = z * tx - x * tz, sz = x * ty - y * tx;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-6) { sx = 1; sy = 0; sz = 0; sl = 1; }
            sx *= hw / sl; sy *= hw / sl; sz *= hw / sl;
            float al = (float) (Math.min(1.0, glowA[i]) * this.glowStrength);
            if (i > 0 && (al > 0.003F || prevA > 0.003F)) {
                put4(gxyz, 0, prevLx, prevLy, prevLz); put4(gxyz, 1, x - sx, y - sy, z - sz);
                put4(gxyz, 2, x + sx, y + sy, z + sz); put4(gxyz, 3, prevRx, prevRy, prevRz);
                guv[0] = 0; guv[1] = 0.5F; guv[2] = 0; guv[3] = 0.5F; guv[4] = 1; guv[5] = 0.5F; guv[6] = 1; guv[7] = 0.5F;
                galpha[0] = prevA; galpha[1] = al; galpha[2] = al; galpha[3] = prevA;
                sink.quad(gxyz, guv, galpha);
            }
            prevLx = x - sx; prevLy = y - sy; prevLz = z - sz;
            prevRx = x + sx; prevRy = y + sy; prevRz = z + sz;
            prevA = al;
        }
    }

    private static void put4(float[] arr, int k, double x, double y, double z) {
        arr[k * 3] = (float) x; arr[k * 3 + 1] = (float) y; arr[k * 3 + 2] = (float) z;
    }

    private static void setRotation(Part p, double kx, double ky, double kz, double a) {
        double c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        p.ux = t * kx * kx + c;      p.uy = t * kx * ky + s * kz; p.uz = t * kx * kz - s * ky;
        p.wx = t * kx * ky - s * kz; p.wy = t * ky * ky + c;      p.wz = t * ky * kz + s * kx;
        p.nx = t * kx * kz + s * ky; p.ny = t * ky * kz - s * kx; p.nz = t * kz * kz + c;
    }

    // ------------------------------------------------------------------ Kristall-Splitter (Oktaeder)

    private static final int[][] OCTA = {
            {0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}
    };
    private final float[] fxyz = new float[12];
    private final float[] fuv = new float[8];
    private final float[] falpha = new float[4];
    private final double[] corner = new double[18];

    private int emitShard(Part p, QuadSink sink) {
        double[][] local = {
                {p.sx * (0.7 + 0.6 * rnd(p.seed, 20)), 0, 0}, {-p.sx * (0.7 + 0.6 * rnd(p.seed, 21)), 0, 0},
                {0, p.sy * (0.7 + 0.6 * rnd(p.seed, 22)), 0}, {0, -p.sy * (0.7 + 0.6 * rnd(p.seed, 23)), 0},
                {0, 0, p.sz}, {0, 0, -p.sz}
        };
        for (int k = 0; k < 6; k++) {
            double lx = local[k][0], ly = local[k][1], lz = local[k][2];
            corner[k * 3] = p.cx + lx * p.ux + ly * p.wx + lz * p.nx;
            corner[k * 3 + 1] = p.cy + lx * p.uy + ly * p.wy + lz * p.ny;
            corner[k * 3 + 2] = p.cz + lx * p.uz + ly * p.wz + lz * p.nz;
        }
        int n = 0;
        for (int[] f : OCTA) {
            int a = f[0] * 3, b = f[1] * 3, c = f[2] * 3;
            double e1x = corner[b] - corner[a], e1y = corner[b + 1] - corner[a + 1], e1z = corner[b + 2] - corner[a + 2];
            double e2x = corner[c] - corner[a], e2y = corner[c + 1] - corner[a + 1], e2z = corner[c + 2] - corner[a + 2];
            double gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
            double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
            if (gl < 1e-9) continue;
            gx /= gl; gy /= gl; gz /= gl;
            double mx = (corner[a] + corner[b] + corner[c]) / 3, my = (corner[a + 1] + corner[b + 1] + corner[c + 1]) / 3,
                    mz = (corner[a + 2] + corner[b + 2] + corner[c + 2]) / 3;
            double md = Math.sqrt(mx * mx + my * my + mz * mz);
            double ndv = -(gx * mx + gy * my + gz * mz) / md;
            if (ndv <= 0) {
                // Seite zeigt weg; je nach Wicklung andersrum versuchen
                gx = -gx; gy = -gy; gz = -gz; ndv = -ndv;
                if (ndv <= 0) continue;
            }
            float v = (float) (1 - shade(gy, ndv));
            int[] order = {f[0], f[1], f[2], f[2]};
            for (int t = 0; t < 4; t++) {
                int ci = order[t] * 3;
                fxyz[t * 3] = (float) corner[ci]; fxyz[t * 3 + 1] = (float) corner[ci + 1]; fxyz[t * 3 + 2] = (float) corner[ci + 2];
                double g = order[t] >= 4 ? p.glow : p.glow * 0.5;  // Spitzen glühen mehr
                fuv[t * 2] = (float) (0.02 + 0.96 * g);
                fuv[t * 2 + 1] = v;
            }
            float sa = p.shardAlpha * topAlpha(p.topRelY - fxyz[1], p.fadeLen);
            falpha[0] = sa; falpha[1] = sa; falpha[2] = sa; falpha[3] = sa;
            sink.quad(fxyz, fuv, falpha);
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ Kettenglied mit Dornen

    /**
     * Vorberechnetes Kettenglied: Stadion-förmiger Ring als glattes Rohr, plus Dornen (Kegel)
     * an den Längsseiten. Lokale Achsen: U entlang der Kette (nach unten), W quer in der Ring-Ebene,
     * N senkrecht zur Ring-Ebene.
     */
    static final class LinkMesh {
        final int m, k;              // Rohr: Punkte entlang des Rings / um das Rohr
        final int nv;                // Anzahl Ecken gesamt
        final double[] pu, pw, pn;   // Position
        final double[] nu, nw, nn;   // Normale
        final int[] quads;           // je 4 Eckindizes
        final int nq;
        final double r;
        final double halfLen;
        final double straight, b;
        final double noiseScale;

        private final float[] vx, vy, vz, vV, vU, vA;
        private final float[] qa = new float[4];
        private final boolean[] removed;
        private long[] order;
        private final float[] qxyz = new float[12];
        private final float[] quv = new float[8];
        private final HashMap<Long, float[]> noiseCache = new HashMap<>();

        LinkMesh(ChainSettings s, int arcSegments, int tubeSegments, int spikeSegments) {
            this.r = s.thickness();
            this.halfLen = Math.max(0.1, s.linkLength() * 0.5 - r);
            double b = Math.max(r * 1.15, s.linkWidth() * 0.5 - r);
            double straight = Math.max(0, halfLen - b);
            this.b = b;
            this.straight = straight;
            int straightSeg = Math.max(1, arcSegments / 3);

            List<double[]> path = new ArrayList<>(); // {u, w, Nu, Nw}
            for (int i = 0; i < arcSegments; i++) {
                double a = -Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            for (int i = 0; i < straightSeg && straight > 1e-4; i++) {
                path.add(new double[]{straight - 2 * straight * i / straightSeg, b, 0, 1});
            }
            for (int i = 0; i < arcSegments; i++) {
                double a = Math.PI / 2 + Math.PI * i / arcSegments;
                path.add(new double[]{-straight + b * Math.cos(a), b * Math.sin(a), Math.cos(a), Math.sin(a)});
            }
            for (int i = 0; i < straightSeg && straight > 1e-4; i++) {
                path.add(new double[]{-straight + 2 * straight * i / straightSeg, -b, 0, -1});
            }
            this.m = path.size();
            this.k = tubeSegments;

            // Dornen: Kegel auf den Längsseiten, leicht nach oben (gegen U) gebogen
            int spikesPerSide = s.spikes();
            int ks = Math.max(3, spikeSegments);
            int spikeCount = spikesPerSide * 2;
            this.nv = m * k + spikeCount * (ks + 1);
            pu = new double[nv]; pw = new double[nv]; pn = new double[nv];
            nu = new double[nv]; nw = new double[nv]; nn = new double[nv];

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
            List<int[]> quadList = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                int i1 = (i + 1) % m;
                for (int j = 0; j < k; j++) {
                    int j1 = (j + 1) % k;
                    quadList.add(new int[]{i * k + j, i * k + j1, i1 * k + j1, i1 * k + j});
                }
            }

            int base = m * k;
            double spikeLen = r * 1.6, spikeRad = r * 0.42;
            for (int side = -1; side <= 1; side += 2) {
                for (int q = 0; q < spikesPerSide; q++) {
                    double t = spikesPerSide == 1 ? 0 : (2.0 * (q + 0.5) / spikesPerSide - 1) * 0.75;
                    double cu0 = straight * t, cw0 = side * (b + r * 0.55);
                    // Richtung: nach außen und etwas nach oben
                    double du = -0.55, dw = side, dl = Math.sqrt(du * du + dw * dw);
                    du /= dl; dw /= dl;
                    // senkrechte Basis-Achsen zum Kegel: e1 in Ring-Ebene, e2 = N
                    double e1u = -dw, e1w = du;
                    for (int j = 0; j < ks; j++) {
                        double phi = 2 * Math.PI * j / ks;
                        double cs = Math.cos(phi), sn = Math.sin(phi);
                        int idx = base + j;
                        pu[idx] = cu0 + spikeRad * cs * e1u;
                        pw[idx] = cw0 + spikeRad * cs * e1w;
                        pn[idx] = spikeRad * sn;
                        // Kegel-Normale: radial + etwas entlang der Spitze
                        double ru = cs * e1u, rw = cs * e1w, rn = sn;
                        double sl = spikeRad / spikeLen;
                        double gu = ru + sl * du, gw = rw + sl * dw, gn = rn;
                        double gl = Math.sqrt(gu * gu + gw * gw + gn * gn);
                        nu[idx] = gu / gl; nw[idx] = gw / gl; nn[idx] = gn / gl;
                    }
                    int tip = base + ks;
                    pu[tip] = cu0 + du * spikeLen; pw[tip] = cw0 + dw * spikeLen; pn[tip] = 0;
                    nu[tip] = du; nw[tip] = dw; nn[tip] = 0;
                    for (int j = 0; j < ks; j++) {
                        quadList.add(new int[]{base + j, base + (j + 1) % ks, tip, tip});
                    }
                    base += ks + 1;
                }
            }

            this.nq = quadList.size();
            this.quads = new int[nq * 4];
            for (int q = 0; q < nq; q++) System.arraycopy(quadList.get(q), 0, this.quads, q * 4, 4);
            this.noiseScale = 1.0 / Math.max(0.05, r * 2.2);
            vx = new float[nv]; vy = new float[nv]; vz = new float[nv]; vV = new float[nv]; vU = new float[nv]; vA = new float[nv];
            removed = new boolean[nq];
            order = new long[nq];
        }

        /** Rauschwert je Ecke (Zerfall) und Verformung – hängt nur vom Glied ab, wird zwischengespeichert. */
        private float[] noiseFor(long seed, boolean hit) {
            long key = hit ? seed ^ 0x48495448495448L : seed;
            float[] v = this.noiseCache.get(key);
            if (v != null) return v;
            if (this.noiseCache.size() > 4096) this.noiseCache.clear();
            v = new float[nv * 2];
            double off = (seed & 255) * 0.37;
            // Einschlagpunkt auf einer Längsseite (unabhängig von der Detailstufe)
            double side = rnd(seed, 30) < 0.5 ? -1 : 1;
            double phi = (rnd(seed, 31) - 0.5) * Math.PI * 1.2;
            double hu = (rnd(seed, 32) * 2 - 1) * this.straight * 0.8;
            double hw = side * (this.b + r * Math.cos(phi)), hn = r * Math.sin(phi);
            double hitR = r * (2.4 + 1.0 * rnd(seed, 33));
            for (int i = 0; i < nv; i++) {
                double f = fbm(pu[i] * noiseScale + off, pw[i] * noiseScale, pn[i] * noiseScale, seed);
                if (hit) {
                    double du = pu[i] - hu, dw = pw[i] - hw, dn = pn[i] - hn;
                    double dist = Math.sqrt(du * du + dw * dw + dn * dn) / hitR;
                    v[i] = (float) (0.6 * Math.min(1, dist) + 0.4 * f);     // Krater um den Einschlag
                } else {
                    // Neigung: das untere Ende eines Glieds zerfällt zuerst
                    v[i] = (float) (f - 0.22 * pu[i] / halfLen);
                }
                v[nv + i] = (float) noise(pu[i] * noiseScale * 1.7, pw[i] * noiseScale * 1.7 + off, pn[i] * noiseScale * 1.7, ~seed);
            }
            this.noiseCache.put(seed, v);
            return v;
        }

        int emit(Part p, QuadSink sink) {
            boolean damaged = p.damage > 0.001;
            float[] nz = damaged ? noiseFor(p.seed, p.hit) : null;
            double d = p.damage;
            double melt = damaged ? r * 0.45 * Math.min(1, d * 1.6) : 0;
            double sc = p.scale;

            for (int idx = 0; idx < nv; idx++) {
                double lu = pu[idx], lw = pw[idx], ln = pn[idx];
                if (damaged) {
                    // geschmolzene, unregelmäßige Oberfläche
                    double disp = (nz[nv + idx] - 0.5) * melt;
                    lu += nu[idx] * disp; lw += nw[idx] * disp; ln += nn[idx] * disp;
                }
                lu *= sc; lw *= sc; ln *= sc;
                double x = p.cx + lu * p.ux + lw * p.wx + ln * p.nx;
                double y = p.cy + lu * p.uy + lw * p.wy + ln * p.ny;
                double z = p.cz + lu * p.uz + lw * p.wz + ln * p.nz;
                double gx = nu[idx] * p.ux + nw[idx] * p.wx + nn[idx] * p.nx;
                double gy = nu[idx] * p.uy + nw[idx] * p.wy + nn[idx] * p.ny;
                double gz = nu[idx] * p.uz + nw[idx] * p.wz + nn[idx] * p.nz;
                double dist = Math.sqrt(x * x + y * y + z * z);
                double ndv = -(gx * x + gy * y + gz * z) / dist;
                vV[idx] = (float) (1.0 - shade(gy, ndv));
                double glow = 0;
                if (damaged) {
                    glow = Math.min(1, Math.max(0, (d + 0.11 - nz[idx]) / 0.11));
                    glow = glow * glow * (3 - 2 * glow);
                }
                vU[idx] = (float) (0.02 + 0.96 * glow * p.pulse);
                vx[idx] = (float) x; vy[idx] = (float) y; vz[idx] = (float) z;
                vA[idx] = topAlpha(p.topRelY - y, p.fadeLen);
            }

            int visible = 0;
            for (int q = 0; q < nq; q++) {
                int a = quads[q * 4], b = quads[q * 4 + 1], c = quads[q * 4 + 2], e = quads[q * 4 + 3];
                if (damaged && (nz[a] + nz[b] + nz[c] + nz[e]) * 0.25 < d) continue; // weggebrochen
                double x = (vx[a] + vx[b] + vx[c] + vx[e]) * 0.25;
                double y = (vy[a] + vy[b] + vy[c] + vy[e]) * 0.25;
                double z = (vz[a] + vz[b] + vz[c] + vz[e]) * 0.25;
                double qu = nu[a] + nu[b] + nu[c] + nu[e];
                double qw = nw[a] + nw[b] + nw[c] + nw[e];
                double qn = nn[a] + nn[b] + nn[c] + nn[e];
                double gx = qu * p.ux + qw * p.wx + qn * p.nx;
                double gy = qu * p.uy + qw * p.wy + qn * p.ny;
                double gz = qu * p.uz + qw * p.wz + qn * p.nz;
                boolean front = gx * x + gy * y + gz * z < 0;
                if (!front && !damaged) continue;
                removed[q] = !front; // hier: Innenseite merken
                float dist2 = (float) (x * x + y * y + z * z);
                order[visible++] = ((long) Float.floatToIntBits(dist2) << 32) | q;
            }
            Arrays.sort(order, 0, visible);
            for (int o = visible - 1; o >= 0; o--) {
                int q = (int) (order[o] & 0xFFFFFFFFL);
                boolean inside = removed[q];
                for (int t = 0; t < 4; t++) {
                    int vi = quads[q * 4 + t];
                    qxyz[t * 3] = vx[vi]; qxyz[t * 3 + 1] = vy[vi]; qxyz[t * 3 + 2] = vz[vi];
                    // Innenseite eines aufgebrochenen Glieds: glühender Kern
                    quv[t * 2] = inside ? (float) (0.9 * p.pulse) : vU[vi];
                    quv[t * 2 + 1] = inside ? 0.45F : vV[vi];
                    qa[t] = vA[vi];
                }
                sink.quad(qxyz, quv, qa);
            }
            return visible;
        }
    }
}
