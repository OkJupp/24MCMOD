package de.jupp.nuitchains;

/**
 * Reine Mathe ohne Minecraft-Abhängigkeiten: wo im Himmel gerade Nebel ist.
 * <p>
 * Die Nebelbänke treiben mit dem Wind und ändern langsam ihre Form. Dieselbe Funktion bestimmt,
 * wo Nebelschwaden gezeichnet werden ({@link FogGeometry}) und wie stark Kettenteile darin
 * verschwinden ({@link ChainGeometry}) – deshalb tauchen die Ketten genau dort in den Nebel ein,
 * wo man ihn sieht.
 */
public final class FogField {
    private final FogSettings s;
    private final long seed;
    private final double windX, windZ;
    private final double threshold;

    private static volatile FogField active;
    private static volatile float activeStrength;
    private static volatile long activeNanos;

    public FogField(FogSettings settings) {
        this.s = settings;
        this.seed = ChainGeometry.mix(settings.seed() * 0x9E3779B97F4A7C15L + 0x0F06L);
        double dir = ChainGeometry.rnd(this.seed, 1) * Math.PI * 2;
        this.windX = Math.cos(dir) * settings.drift();
        this.windZ = Math.sin(dir) * settings.drift();
        // Rauschwerte liegen meist um 0.5 – Schwelle so wählen, dass ungefähr "coverage" bedeckt ist
        this.threshold = 0.5 + (0.5 - settings.coverage()) * 0.42;
    }

    public FogSettings settings() {
        return this.s;
    }

    public double windX() {
        return this.windX;
    }

    public double windZ() {
        return this.windZ;
    }

    // ------------------------------------------------------------------ gemeinsamer Zustand

    /** Die Nebel-Skybox meldet sich jedes Bild hier an (mit ihrer aktuellen Deckkraft). */
    public static void publish(FogField field, float strength) {
        active = field;
        activeStrength = strength;
        activeNanos = System.nanoTime();
    }

    /** Aktiver Nebel oder null, wenn seit 2 Sekunden keine Nebel-Skybox mehr gezeichnet hat. */
    public static FogField current() {
        FogField f = active;
        if (f == null || System.nanoTime() - activeNanos > 2_000_000_000L) {
            return null;
        }
        return f;
    }

    public static float currentStrength() {
        return activeStrength;
    }

    // ------------------------------------------------------------------ Nebelfeld

    private static double smooth(double a, double b, double x) {
        double t = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Höhe der Nebel-Oberkante an dieser Stelle. */
    public double top(double x, double z, double t) {
        double u = (x - this.windX * t) / this.s.scale(), v = (z - this.windZ * t) / this.s.scale();
        double n = ChainGeometry.noise(u * 1.6 + 5.3, t * 0.004 * this.s.morph(), v * 1.6 - 2.1, this.seed ^ 0x70FL);
        return this.s.height() + this.s.heightVariation() * (2 * n - 1);
    }

    /** Wie dicht die Nebelbank an dieser Stelle ist (0 = Lücke, 1 = dicht). */
    public double cover(double x, double z, double t) {
        if (this.s.coverage() <= 0) return 0;
        if (this.s.coverage() >= 1) return 1;
        double u = (x - this.windX * t) / this.s.scale(), v = (z - this.windZ * t) / this.s.scale();
        double n = ChainGeometry.fbm(u, t * 0.006 * this.s.morph(), v, this.seed);
        return smooth(this.threshold - 0.09, this.threshold + 0.09, n);
    }

    /** Nebeldichte an einem Punkt (0..1): unterhalb der Oberkante wird es schnell dicht. */
    public double density(double x, double y, double z, double t) {
        double c = cover(x, z, t);
        if (c <= 0) return 0;
        double top = top(x, z, t);
        return c * smooth(top, top - this.s.thickness(), y);
    }

    /**
     * Wie gut ein Kettenteil an diesem Punkt noch zu sehen ist (1 = klar, 0 = im Nebel verschwunden),
     * inklusive Dunst in der Ferne. (ex, ez) = Kamera.
     */
    public double visibility(double x, double y, double z, double t, double ex, double ez, double strength) {
        double vis = 1 - this.s.opacity() * strength * density(x, y, z, t);
        if (this.s.haze() > 0) {
            double dx = x - ex, dz = z - ez;
            double d = Math.sqrt(dx * dx + dz * dz);
            vis *= 1 - this.s.haze() * strength * smooth(this.s.range() * 0.3, this.s.range() * 0.95, d);
        }
        return Math.max(0, vis);
    }
}
