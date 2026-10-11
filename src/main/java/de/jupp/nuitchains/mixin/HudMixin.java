package de.jupp.nuitchains.mixin;

import de.jupp.nuitchains.PinkHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;

/** Pinkes HUD: XP-Level-Zahl pink und pulsierend, Leucht-Animation auf der Hotbar. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Redirect(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"),
            require = 0)
    private void nuitchains$pinkLevel(GuiGraphicsExtractor graphics, Font font, int level) {
        PinkHud.drawExperienceLevel(graphics, font, level);
    }

    /**
     * Hotbar-Hintergrund: statt des Sprites aus dem GUI-Atlas (kam bei manchen Setups als lila-schwarze
     * Platzhalter-Textur an) ein eigenes Bild zeichnen, danach die Leucht-Animation.
     */
    @Redirect(method = "extractItemHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 0),
            require = 0)
    private void nuitchains$hotbar(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
        PinkHud.drawHotbar(graphics, pipeline, sprite, x, y, width, height);
    }
}
