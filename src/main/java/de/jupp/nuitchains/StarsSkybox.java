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
import me.flashyreese.mods.nuit.components.Conditions;
import me.flashyreese.mods.nuit.components.Properties;
import me.flashyreese.mods.nuit.components.RGBA;
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.render.NuitRenderPipelines;
import me.flashyreese.mods.nuit.skybox.AbstractSkybox;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:stars": funkelnde Sterne, die ab und zu aufblitzen,
 * Sternschnuppen und zwei langsam "atmende" große Sterne. Wird additiv über die Himmels-Textur gezeichnet.
 */
public class StarsSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier DEFAULT_TEXTURE = NuitChainsClient.id("textures/sky/stars.png");
    private static final RGBA WHITE = new RGBA(1F, 1F, 1F, 1F);

    public static final Codec<StarsSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(StarsSkybox::getTexture),
            RGBA.CODEC.optionalFieldOf("tint", WHITE).forGetter(StarsSkybox::getTint),
            StarSettings.CODEC.optionalFieldOf("stars", StarSettings.DEFAULT).forGetter(StarsSkybox::getSettings)
    ).apply(instance, StarsSkybox::new));

    private final Identifier texture;
    private final RGBA tint;
    private final StarSettings settings;
    private final StarGeometry geometry;

    public StarsSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA tint, StarSettings settings) {
        super(properties, conditions);
        this.texture = texture;
        this.tint = tint;
        this.settings = settings;
        this.geometry = new StarGeometry(settings);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        DayCycle.update(context.camera(), context.tickDelta());
        float visible = DayCycle.starVisibility();
        if (this.alpha <= 0.0F || visible <= 0.002F) {
            return;
        }
        Entity entity = context.camera().entity();
        if (entity == null) {
            return;
        }
        double timeSec = (entity.level().getGameTime() + (double) context.tickDelta()) / 20.0;
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        RenderPipeline pipeline = ChainRenderTypes.skyGlow();
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, this.geometry.maxQuads() * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            this.geometry.build(timeSec, (xyz, uv, rgba) -> {
                for (int v = 0; v < 4; v++) {
                    builder.addVertex(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2])
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setColor(rgba[v * 4], rgba[v * 4 + 1], rgba[v * 4 + 2], rgba[v * 4 + 3]);
                }
            });
            MeshData mesh = builder.build();
            if (mesh == null) {
                return;
            }
            Vector4f color = new Vector4f(this.tint.getRed(), this.tint.getGreen(), this.tint.getBlue(),
                    this.tint.getAlpha() * this.alpha * visible);
            GpuBufferSlice transforms = NuitRenderBackend.createDynamicTransforms(modelView, color);
            NuitRenderBackend.drawTextured(pipeline, mesh, transforms, NuitRenderBackend.SAMPLER0_NAME, this.texture);
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

    public StarSettings getSettings() {
        return this.settings;
    }
}
