package de.jupp.nuitchains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;

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

    private static final Identifier PULSE = NuitChainsClient.id("hud_fx/pulse");
    private static final Identifier SPARK = NuitChainsClient.id("hud_fx/spark");
    private static final Identifier SLOT_GLOW = NuitChainsClient.id("hud_fx/slot_glow");
    private static final Identifier SELECTION_GLOW = NuitChainsClient.id("hud_fx/selection_glow");
    private static final long START = System.nanoTime();
    /** Hotbar als eigenständiges Bild (nicht im GUI-Atlas). */
    private static final Identifier HOTBAR_TEXTURE = NuitChainsClient.id("textures/gui/pink_hotbar.png");

    private PinkHud() {
    }

    private static double seconds() {
        return (System.nanoTime() - START) / 1.0e9;
    }

    /** Ersetzt das Zeichnen des Hotbar-Hintergrunds; ohne aktives Paket bleibt alles wie in Vanilla. */
    public static void drawHotbar(GuiGraphicsExtractor g, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
                                  Identifier sprite, int x, int y, int width, int height) {
        if (!enabled()) {
            g.blitSprite(pipeline, sprite, x, y, width, height);
            return;
        }
        g.blit(pipeline, HOTBAR_TEXTURE, x, y, 0F, 0F, width, height, width, height);
        drawHotbarFx(g);
    }

    /**
     * Bewegung auf der Hotbar (gezeichnet direkt nach dem Hotbar-Hintergrund, vor Auswahl-Rahmen und Items):
     * Energie läuft um jeden Slot, die Rahmen flackern leicht, ab und zu knistert ein Funke,
     * der ausgewählte Slot pulsiert.
     */
    public static void drawHotbarFx(GuiGraphicsExtractor g) {
        if (!enabled()) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        int selected = player != null ? player.getInventory().getSelectedSlot() : -1;
        double t = seconds();
        float hx = g.guiWidth() / 2F - 91F;
        float hy = g.guiHeight() - 22F;
        Matrix3x2fStack pose = g.pose();

        for (int i = 0; i < 9; i++) {
            float x0 = hx + 1.8F + 20 * i, y0 = hy + 1.8F, size = 18.4F;
            // Rahmen flackert leise
            double flick = 0.5 + 0.5 * ChainGeometry.noise1(t * 2.2 + i * 7.3, 0x5107L + i);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_GLOW, (int) hx + 20 * i, (int) hy, 22, 22,
                    argb((float) (0.10 + 0.22 * flick), 0xFFC8F0));
            // zwei Energie-Stöße, die um den Rahmen laufen (Nachbar-Slots gegenläufig)
            double dir = (i & 1) == 0 ? 1 : -1;
            for (int k = 0; k < 2; k++) {
                double u = ((dir * t / 2.4 + i * 0.37 + k * 0.5) % 1.0 + 1.0) % 1.0;
                float[] p = onRect(x0, y0, size, size, u);
                drawCentered(g, pose, PULSE, p[0], p[1], 4.5F, argb(0.85F, 0xFFE0F6));
            }
        }
        // Funken: wechseln ~14x pro Sekunde die Stelle
        long frame = (long) Math.floor(t / 0.07);
        for (int k = 0; k < 3; k++) {
            long h = ChainGeometry.mix(frame * 0x9E3779B97F4A7C15L + k * 0xD1B54A32D192ED03L);
            if (ChainGeometry.rnd(h, 0) < 0.45) continue;
            int slot = (int) (ChainGeometry.rnd(h, 1) * 9);
            float[] p = onRect(hx + 1.8F + 20 * slot, hy + 1.8F, 18.4F, 18.4F, ChainGeometry.rnd(h, 2));
            drawCentered(g, pose, SPARK, p[0], p[1], 3F + 2F * (float) ChainGeometry.rnd(h, 3), argb(0.9F, 0xFFFFFF));
        }
        // ausgewählter Slot: pulsierender Schein und schnelle Stöße
        if (selected >= 0 && selected < 9) {
            double beat = 0.5 + 0.5 * Math.sin(t * 2 * Math.PI / 1.2);
            int sx = (int) hx - 1 + 20 * selected, sy = (int) hy - 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_GLOW, sx, sy, 24, 23, argb((float) (0.25 + 0.45 * beat), 0xFFFFFF));
            for (int k = 0; k < 3; k++) {
                float[] p = onRect(sx + 1.6F, sy + 1.6F, 20.8F, 20.8F, (t / 1.2 + k / 3.0) % 1.0);
                drawCentered(g, pose, PULSE, p[0], p[1], 5.5F, argb(0.95F, 0xFFFFFF));
            }
        }
    }

    /** Punkt auf dem Umfang eines Rechtecks, u = 0..1 einmal herum (im Uhrzeigersinn ab oben links). */
    private static float[] onRect(float x, float y, float w, float h, double u) {
        double per = 2 * (w + h);
        double d = u * per;
        if (d < w) return new float[]{(float) (x + d), y};
        d -= w;
        if (d < h) return new float[]{x + w, (float) (y + d)};
        d -= h;
        if (d < w) return new float[]{(float) (x + w - d), y + h};
        d -= w;
        return new float[]{x, (float) (y + h - d)};
    }

    /** Sprite mittig um (cx, cy) zeichnen, auch zwischen ganzen GUI-Pixeln (weiche Bewegung). */
    private static void drawCentered(GuiGraphicsExtractor g, Matrix3x2fStack pose, Identifier sprite, float cx, float cy, float size, int color) {
        pose.pushMatrix();
        pose.translate(cx - size / 2F, cy - size / 2F);
        pose.scale(size / 8F, size / 8F);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 8, 8, color);
        pose.popMatrix();
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
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
