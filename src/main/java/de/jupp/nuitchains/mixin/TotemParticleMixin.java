package de.jupp.nuitchains.mixin;

import de.jupp.nuitchains.PinkHud;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Totem-Partikel in Pink statt Grün/Gelb, solange das Paket "Pinkes HUD (Nuit Chains)" aktiv ist.
 * Vanilla setzt die Farbe zweimal (selten gelb, meist grün) – beide Aufrufe werden umgefärbt,
 * die Zufallswerte von Vanilla bleiben als Streuung erhalten.
 */
@Mixin(TotemParticle.class)
public abstract class TotemParticleMixin {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/TotemParticle;setColor(FFF)V"),
            require = 0)
    private void nuitchains$pinkTotem(TotemParticle self, float r, float g, float b) {
        if (!PinkHud.isActive()) {
            self.setColor(r, g, b);
            return;
        }
        if (r > 0.5F) {
            // seltener Vanilla-Gelbton -> fast weißes, helles Pink
            float t = (g - 0.6F) / 0.3F;
            self.setColor(1.0F, 0.78F + 0.14F * t, 0.93F + 0.07F * t);
        } else {
            // Vanilla-Grün -> Neon-Pink bis Violett
            float t = Math.max(0F, Math.min(1F, (g - 0.4F) / 0.3F));
            float lift = b * 1.5F;   // 0..0.3
            self.setColor(Math.min(1F, 1.0F - 0.38F * t + 0.1F * lift), 0.16F + 0.14F * t + 0.5F * lift,
                    Math.min(1F, 0.68F + 0.32F * t + 0.2F * lift));
        }
    }
}
