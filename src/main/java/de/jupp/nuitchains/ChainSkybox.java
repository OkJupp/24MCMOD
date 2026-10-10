package de.jupp.nuitchains;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxRenderContext;
import me.flashyreese.mods.nuit.api.skyboxes.SkyboxTextureProvider;
import me.flashyreese.mods.nuit.components.Blend;
import me.flashyreese.mods.nuit.components.Conditions;
import me.flashyreese.mods.nuit.components.Properties;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.render.NuitRenderPipelines;
import me.flashyreese.mods.nuit.skybox.AbstractSkybox;
import net.minecraft.client.Camera;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:chains": echte 3D-Ketten, die im Weltraster hoch am Himmel hängen
 * und leicht pendeln. Man kann unter ihnen durchlaufen und sieht sie aus jedem Winkel.
 * <p>
 * Gezeichnet wird im Himmel-Durchgang von Nuit (vor dem Gelände), deshalb verdeckt das Gelände
 * die Ketten korrekt und der Nebel der Sichtweite blendet sie nicht aus.
 */
public class ChainSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier DEFAULT_TEXTURE = NuitChainsClient.id("textures/chain/metal.png");
    private static final RGBA WHITE = new RGBA(1F, 1F, 1F, 1F);

    public static final Codec<ChainSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(ChainSkybox::getTexture),
            RGBA.CODEC.optionalFieldOf("tint", WHITE).forGetter(ChainSkybox::getTint),
            ChainSettings.CODEC.optionalFieldOf("chains", ChainSettings.DEFAULT).forGetter(ChainSkybox::getSettings)
    ).apply(instance, ChainSkybox::new));

    private final Identifier texture;
    private final RGBA tint;
    private final ChainSettings settings;
    private final ChainGeometry geometry;

    public ChainSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA tint, ChainSettings settings) {
        super(properties, conditions);
        this.texture = texture;
        this.tint = tint;
        this.settings = settings;
        this.geometry = new ChainGeometry(settings);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        if (this.alpha <= 0.0F) {
            return;
        }
        Camera camera = context.camera();
        Entity entity = camera.entity();
        if (entity == null) {
            return;
        }
        float tickDelta = context.tickDelta();
        Vec3 eye = entity.getEyePosition(tickDelta);
        Level level = entity.level();
        double timeSec = (level.getGameTime() + (double) tickDelta) / 20.0;

        List<ChainGeometry.Chain> chains = this.geometry.collect(eye.x, eye.y, eye.z);
        if (chains.isEmpty()) {
            return;
        }

        context.applyFog();
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        RenderPipeline pipeline = NuitRenderPipelines.texturedSkybox(Blend.normal().getBlendFunction());

        for (ChainGeometry.Chain chain : chains) {
            // grobe Obergrenze: Glieder * Vierecke pro Glied * 4 Ecken (nur ~die Hälfte ist sichtbar)
            int maxVertices = chain.links() * 22 * 8 * 4;
            try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, maxVertices)) {
                BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
                int quads = this.geometry.build(chain, eye.x, eye.y, eye.z, timeSec, (xyz, uv) -> {
                    for (int v = 0; v < 4; v++) {
                        builder.addVertex(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]).setUv(uv[v * 2], uv[v * 2 + 1]);
                    }
                });
                if (quads == 0) {
                    continue;
                }
                MeshData mesh = builder.build();
                if (mesh == null) {
                    continue;
                }
                Vector4f color = new Vector4f(
                        this.tint.getRed(), this.tint.getGreen(), this.tint.getBlue(),
                        this.tint.getAlpha() * this.alpha * (float) chain.fade());
                GpuBufferSlice transforms = NuitRenderBackend.createDynamicTransforms(modelView, color);
                NuitRenderBackend.drawTextured(pipeline, mesh, transforms, NuitRenderBackend.SAMPLER0_NAME, this.texture);
            }
        }
    }

    @Override
    public Collection<Identifier> getTexturesToRegister() {
        return List.of(this.texture);
    }

    public Identifier getTexture() {
        return this.texture;
    }

    public RGBA getTint() {
        return this.tint;
    }

    public ChainSettings getSettings() {
        return this.settings;
    }
}
