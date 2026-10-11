package de.jupp.nuitchains.mixin;

import de.jupp.nuitchains.PinkHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Die XP-Level-Zahl in Pink (sanft pulsierend) statt Grün zeichnen. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Redirect(method = "extractHotbarAndDecorations",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"),
            require = 0)
    private void nuitchains$pinkLevel(GuiGraphicsExtractor graphics, Font font, int level) {
        PinkHud.drawExperienceLevel(graphics, font, level);
    }
}
