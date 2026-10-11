package de.jupp.nuitchains.mixin;

import de.jupp.nuitchains.PinkHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.DeltaTracker;

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

    /** Nach Hotbar-Hintergrund und Auswahl-Rahmen (zweites blitSprite), vor Nebenhand und Items. */
    @Inject(method = "extractItemHotbar",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
                    ordinal = 1, shift = At.Shift.AFTER),
            require = 0)
    private void nuitchains$hotbarFx(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        PinkHud.drawHotbarFx(graphics);
    }
}
