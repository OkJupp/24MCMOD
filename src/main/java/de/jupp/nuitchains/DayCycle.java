package de.jupp.nuitchains;

import net.minecraft.client.Camera;
import net.minecraft.world.attribute.EnvironmentAttributes;

/**
 * Wo steht die Sonne gerade? Wird jedes Bild aus Minecrafts Sonnenwinkel berechnet und von allen
 * Himmels-Effekten benutzt (Tag-Himmel, Sterne, Ketten-Leuchten, Nebelfarbe).
 */
public final class DayCycle {
    private DayCycle() {
    }

    private static volatile double sunX = 0, sunY = 1, sunZ = 0;
    private static volatile double day = 1, twilight = 0;
    private static final double[] TMP = new double[3];

    /** Aus der Kamera aktualisieren (Sonnenwinkel inkl. Zwischenbild). */
    public static void update(Camera camera, float partialTick) {
        if (camera == null) {
            return;
        }
        try {
            Float angle = camera.attributeProbe().getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
            if (angle != null) {
                setAngle(angle);
            }
        } catch (RuntimeException ignored) {
            // Kamera noch nicht bereit – alten Stand behalten
        }
    }

    public static synchronized void setAngle(double angleDeg) {
        AtmosphereModel.sunDirection(angleDeg, TMP);
        sunX = TMP[0];
        sunY = TMP[1];
        sunZ = TMP[2];
        day = AtmosphereModel.day(sunY);
        twilight = AtmosphereModel.twilight(sunY);
    }

    public static double sunX() {
        return sunX;
    }

    public static double sunY() {
        return sunY;
    }

    public static double sunZ() {
        return sunZ;
    }

    /** 1 = heller Tag, 0 = Nacht. */
    public static double day() {
        return day;
    }

    /** 1 = Sonne genau am Horizont (Morgen-/Abendrot). */
    public static double twilight() {
        return twilight;
    }

    /** Sichtbarkeit der Sterne (tagsüber weg, in der Dämmerung erscheinen die ersten). */
    public static float starVisibility() {
        return (float) Math.max(0, Math.min(1, 1 - day * 1.1 - twilight * 0.25));
    }

    /** Leuchten der Ketten: nachts voll, tagsüber deutlich schwächer (Ketten stehen als Silhouetten im Dunst). */
    public static float chainGlow() {
        return (float) (0.4 + 0.6 * (1 - day));
    }
}
