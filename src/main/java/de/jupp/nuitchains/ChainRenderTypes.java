package de.jupp.nuitchains;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.jupp.nuitchains.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Render-Pipelines der Ketten. Alle nutzen Minecrafts "position_tex_color"-Shader:
 * Helligkeit kommt aus der Textur, die Deckkraft pro Ecke aus der Vertex-Farbe
 * (dadurch weiches Ausblenden und glasig-durchscheinende Glieder).
 */
public final class ChainRenderTypes {
    private static final BlendFunction ADDITIVE = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE);

    private static RenderPipeline worldPipeline;
    private static RenderPipeline skyPipeline;
    private static RenderPipeline skyGlowPipeline;
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    private ChainRenderTypes() {
    }

    private static RenderPipeline.Builder base(String location, BlendFunction blend) {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withLocation(NuitChainsClient.id(location))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(blend))
                .withCull(false);
    }

    /** Nahe Ketten in der echten Welt: durchscheinend, mit Tiefe (Gelände verdeckt richtig), ohne Nebel. */
    private static RenderPipeline worldPipeline() {
        if (worldPipeline == null) {
            worldPipeline = base("pipeline/chains", BlendFunction.TRANSLUCENT)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .build();
        }
        return worldPipeline;
    }

    /** Ferne Ketten im Himmel-Durchgang (wie Minecrafts End-Himmel: ohne Tiefe). */
    public static RenderPipeline skyChains() {
        if (skyPipeline == null) {
            skyPipeline = base("pipeline/sky_chains", BlendFunction.TRANSLUCENT).build();
        }
        return skyPipeline;
    }

    /** Leuchten um die Ketten: additiv, also nur aufhellend. */
    public static RenderPipeline skyGlow() {
        if (skyGlowPipeline == null) {
            skyGlowPipeline = base("pipeline/sky_chain_glow", ADDITIVE).build();
        }
        return skyGlowPipeline;
    }

    public static RenderType chains(Identifier texture) {
        return TYPES.computeIfAbsent(texture, tex -> RenderTypeAccessor.nuitchains$create(
                "nuit_chains_chains",
                RenderSetup.builder(worldPipeline()).withTexture("Sampler0", tex).createRenderSetup()));
    }
}
