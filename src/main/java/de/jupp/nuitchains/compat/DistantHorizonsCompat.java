package de.jupp.nuitchains.compat;

import de.jupp.nuitchains.NuitChainsClient;

import java.lang.reflect.Method;

/**
 * Optionale Anbindung an Distant Horizons (ohne harte Abhängigkeit, per Reflection).
 * <p>
 * DH zeichnet eigene LOD-Wolken, die den Minecraft-Wolkenschalter ignorieren. Solange ein
 * Ketten-Himmel mit {@code hideClouds} aktiv ist, werden sie über die DH-API abgeschaltet.
 * Das ist nur ein API-Override: deine DH-Einstellungsdatei wird nicht verändert.
 */
public final class DistantHorizonsCompat {
    private static final String DELAYED = "com.seibel.distanthorizons.api.DhApi$Delayed";
    private static final String CONFIG = "com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig";
    private static final String GRAPHICS = "com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig";
    private static final String GENERIC = "com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGenericRenderingConfig";
    private static final String VALUE = "com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue";

    private static boolean unavailable;
    private static Boolean applied; // zuletzt gesetzter Zustand
    private static int retryCooldown;

    private DistantHorizonsCompat() {
    }

    /** Pro Bild aufrufen; setzt den DH-Override nur, wenn sich der Wunsch ändert. */
    public static void setCloudsHidden(boolean hide) {
        if (unavailable || (applied != null && applied == hide)) {
            return;
        }
        if (retryCooldown > 0) {
            retryCooldown--;
            return;
        }
        try {
            Class<?> delayed = Class.forName(DELAYED);
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) {
                retryCooldown = 60; // DH ist noch nicht fertig geladen
                return;
            }
            Object graphics = Class.forName(CONFIG).getMethod("graphics").invoke(configs);
            Object generic = Class.forName(GRAPHICS).getMethod("genericRendering").invoke(graphics);
            Object clouds = Class.forName(GENERIC).getMethod("cloudRenderingEnabled").invoke(generic);
            Class<?> valueClass = Class.forName(VALUE);
            if (hide) {
                Method set = valueClass.getMethod("setValue", Object.class);
                set.invoke(clouds, Boolean.FALSE);
            } else {
                valueClass.getMethod("clearValue").invoke(clouds);
            }
            applied = hide;
        } catch (ClassNotFoundException e) {
            unavailable = true; // DH nicht installiert
        } catch (Throwable t) {
            unavailable = true;
            NuitChainsClient.LOGGER.warn("Nuit Chains: Distant-Horizons-Wolken konnten nicht umgeschaltet werden", t);
        }
    }
}
