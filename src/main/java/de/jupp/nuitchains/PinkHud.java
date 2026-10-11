package de.jupp.nuitchains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Pinkes HUD: die Grafiken (Hotbar, Herzen, Rüstung, Essen, XP-Leiste) liegen als animierte Sprites im
 * eingebauten Ressourcenpaket resourcepacks/pink_hud/ (erzeugt mit tools/gen_hud.py). Hier nur, was Code braucht.
 */
public final class PinkHud {
    private static final int OUTLINE = 0xFF2A0830;
    private static final int PINK = 0xFFFF5CCD;
    private static final int PINK_LIGHT = 0xFFFFC8F0;

    /** Liegt nur im eingebauten Paket "Pinkes HUD" – ist es abgeschaltet, bleibt auch die Zahl grün. */
    private static final net.minecraft.resources.Identifier MARKER = NuitChainsClient.id("pink_hud.json");

    private PinkHud() {
    }

    private static boolean enabled() {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(MARKER).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Wie Vanilla (gleiche Position, dunkler Umriss), aber pink und langsam pulsierend. */
    public static void drawExperienceLevel(GuiGraphicsExtractor graphics, Font font, int level) {
        if (!enabled()) {
            ContextualBar.extractExperienceLevel(graphics, font, level);
            return;
        }
        Component text = Component.translatable("gui.experience.level", level);
        int x = (graphics.guiWidth() - font.width(text)) / 2;
        int y = graphics.guiHeight() - 24 - font.lineHeight - 2;
        graphics.text(font, text, x + 1, y, OUTLINE, false);
        graphics.text(font, text, x - 1, y, OUTLINE, false);
        graphics.text(font, text, x, y + 1, OUTLINE, false);
        graphics.text(font, text, x, y - 1, OUTLINE, false);
        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 2.4));
        graphics.text(font, text, x, y, lerpColor(PINK, PINK_LIGHT, pulse * pulse), false);
    }

    private static int lerpColor(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * f), g = Math.round(ag + (bg - ag) * f), bl = Math.round(ab + (bb - ab) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
