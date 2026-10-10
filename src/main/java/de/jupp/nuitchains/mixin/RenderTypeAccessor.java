package de.jupp.nuitchains.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Macht {@code RenderType.create} (paketintern) für eigene RenderTypes nutzbar. */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Invoker("create")
    static RenderType nuitchains$create(String name, RenderSetup setup) {
        throw new AssertionError("Mixin nicht angewendet");
    }
}
