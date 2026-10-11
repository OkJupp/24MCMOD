package de.jupp.nuitchains;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: Farbe des Tag-Himmels für eine Blickrichtung,
 * abhängig vom Sonnenstand.
 * <ul>
 *     <li>Tag: heller Rosé-Horizont, Flieder in der Mitte, Lavendel im Zenit – die Galaxie schimmert leicht durch.</li>
 *     <li>Dämmerung: Orange-Magenta-Glühen auf der Sonnenseite, violetter Gürtel gegenüber, oben tiefes Violett.</li>
 *     <li>Nacht: durchsichtig (die Pink-Galaxie ist zu sehen), nur ein leichtes pinkes Leuchten am Horizont.</li>
 * </ul>
 * Ausgabe ist eine normale Farbe mit Deckkraft, die über die Galaxie gelegt wird.
 */
public final class AtmosphereModel {
    private AtmosphereModel() {
    }

    private static final double[] HORIZON = {1.00, 0.86, 0.93};
    private static final double[] MID = {0.89, 0.72, 0.95};
    private static final double[] ZENITH = {0.63, 0.56, 0.94};
    private static final double[] BELOW = {0.90, 0.75, 0.87};
    private static final double[] ORANGE = {1.00, 0.58, 0.34};
    private static final double[] MAGENTA = {1.00, 0.32, 0.62};
    private static final double[] PURPLE = {0.44, 0.22, 0.64};
    private static final double[] DUSK_HIGH = {0.26, 0.14, 0.46};
    private static final double[] HALO = {1.00, 0.95, 0.92};
    private static final double[] NIGHT_GLOW = {1.00, 0.30, 0.74};

    static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** 1 bei Tag, 0 bei Nacht (nach der Höhe der Sonne, -1..1). */
    public static double day(double sunY) {
        return smooth(-0.10, 0.28, sunY);
    }

    /** Dämmerung: am stärksten, wenn die Sonne gerade am Horizont steht. */
    public static double twilight(double sunY) {
        return smooth(-0.30, -0.02, sunY) * (1 - smooth(0.06, 0.40, sunY));
    }

    /** Sonnenrichtung wie in Minecraft (Winkel in Grad, 0 = Mittag, 90 = Sonnenuntergang im Westen). */
    public static void sunDirection(double angleDeg, double[] out) {
        double a = Math.toRadians(angleDeg);
        out[0] = -Math.sin(a);
        out[1] = Math.cos(a);
        out[2] = 0;
    }

    private static double mix(double[] a, double[] b, double t, int c) {
        return a[c] + (b[c] - a[c]) * t;
    }

    /**
     * Farbe (rgba, 0..1, nicht vormultipliziert) für die Blickrichtung (dx,dy,dz) bei Sonne (sx,sy,sz).
     * Beide Richtungen müssen Länge 1 haben.
     */
    public static void color(double dx, double dy, double dz, double sx, double sy, double sz,
                             AtmosphereSettings s, float[] rgba) {
        double day = day(sy);
        double tw = twilight(sy) * s.twilight();
        double night = 1 - smooth(-0.25, 0.05, sy);
        double e = dy;
        double eh = Math.max(0, e);
        double pr, pg, pb, a;   // vormultipliziert

        // Tag: Verlauf vom Horizont zum Zenit
        double t = Math.sqrt(eh);
        double cr, cg, cb;
        if (e < 0) {
            double k = Math.min(1, -e * 4);
            cr = mix(HORIZON, BELOW, k, 0); cg = mix(HORIZON, BELOW, k, 1); cb = mix(HORIZON, BELOW, k, 2);
        } else if (t < 0.5) {
            cr = mix(HORIZON, MID, t * 2, 0); cg = mix(HORIZON, MID, t * 2, 1); cb = mix(HORIZON, MID, t * 2, 2);
        } else {
            cr = mix(MID, ZENITH, (t - 0.5) * 2, 0); cg = mix(MID, ZENITH, (t - 0.5) * 2, 1); cb = mix(MID, ZENITH, (t - 0.5) * 2, 2);
        }
        double dA = Math.min(1, day * s.dayOpacity() * (1.07 - 0.17 * t));
        pr = cr * dA; pg = cg * dA; pb = cb * dA; a = dA;

        // Dämmerung: oben tiefes Violett, am Horizont glühendes Band (zur Sonne hin orange)
        double hb = e >= 0 ? Math.exp(-e / 0.17) : Math.exp(e / 0.12);
        double hl = Math.sqrt(dx * dx + dz * dz), sl = Math.sqrt(sx * sx + sz * sz);
        double ch = hl > 1e-4 && sl > 1e-4 ? (dx * sx + dz * sz) / (hl * sl) : 0;
        double w = Math.pow((ch + 1) * 0.5, 2.5);
        double up = Math.min(1, tw * 0.32 * (1 - hb));
        pr = DUSK_HIGH[0] * up + pr * (1 - up); pg = DUSK_HIGH[1] * up + pg * (1 - up); pb = DUSK_HIGH[2] * up + pb * (1 - up);
        a = up + a * (1 - up);
        double warmT = Math.min(1, hb * w * 1.3);
        double side = smooth(0.05, 0.8, w);
        double wr = mix(MAGENTA, ORANGE, warmT, 0), wg = mix(MAGENTA, ORANGE, warmT, 1), wb = mix(MAGENTA, ORANGE, warmT, 2);
        double tr = PURPLE[0] + (wr - PURPLE[0]) * side, tg = PURPLE[1] + (wg - PURPLE[1]) * side, tb = PURPLE[2] + (wb - PURPLE[2]) * side;
        double twA = Math.min(1, tw * (0.12 + 0.8 * hb) * (0.4 + 0.6 * w));
        pr = tr * twA + pr * (1 - twA); pg = tg * twA + pg * (1 - twA); pb = tb * twA + pb * (1 - twA);
        a = twA + a * (1 - twA);

        // Lichthof um die Sonne
        double cs = dx * sx + dy * sy + dz * sz;
        if (cs > 0 && s.sunGlow() > 0) {
            double c2 = cs * cs, c4 = c2 * c2, c8 = c4 * c4, c16 = c8 * c8, c64 = c16 * c16 * c16 * c16;
            double halo = (c8 * 0.3 + c64 * 0.45) * s.sunGlow() * (day + 0.8 * tw) * (e > -0.05 ? 1 : 0.5);
            double ht = Math.min(1, tw * (1 - day) * 0.8);
            pr += mix(HALO, ORANGE, ht, 0) * halo; pg += mix(HALO, ORANGE, ht, 1) * halo; pb += mix(HALO, ORANGE, ht, 2) * halo;
            a = Math.min(1, a + halo * 0.7);
        }

        // Nacht: leichtes pinkes Leuchten am Horizont
        double ng = night * s.nightGlow() * (e >= 0 ? Math.exp(-e / 0.09) : Math.exp(e / 0.05)) * 0.45;
        if (ng > 0.001) {
            pr += NIGHT_GLOW[0] * ng; pg += NIGHT_GLOW[1] * ng; pb += NIGHT_GLOW[2] * ng;
            a = Math.min(1, a + ng * 0.6);
        }

        if (a < 1e-4) {
            rgba[0] = 0; rgba[1] = 0; rgba[2] = 0; rgba[3] = 0;
            return;
        }
        rgba[0] = (float) Math.min(1, pr / a);
        rgba[1] = (float) Math.min(1, pg / a);
        rgba[2] = (float) Math.min(1, pb / a);
        rgba[3] = (float) Math.min(1, a);
    }
}
