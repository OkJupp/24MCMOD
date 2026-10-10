package de.jupp.nuitchains;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
 * RenderType für Kettenteile in der echten 3D-Welt: undurchsichtig, mit Tiefe (Gelände und
 * Wolken verdecken richtig), ohne Nebel und ohne Beleuchtung (Helligkeit steckt in der Textur).
 */
public final class ChainRenderTypes {
    private static RenderPipeline pipeline;
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    private ChainRenderTypes() {
    }

    private static RenderPipeline pipeline() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withLocation(NuitChainsClient.id("pipeline/chains"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .withCull(false)
                    .build();
        }
        return pipeline;
    }

    public static RenderType chains(Identifier texture) {
        return TYPES.computeIfAbsent(texture, tex -> RenderTypeAccessor.nuitchains$create(
                "nuit_chains_chains",
                RenderSetup.builder(pipeline()).withTexture("Sampler0", tex).createRenderSetup()));
    }
}
