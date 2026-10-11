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
import me.flashyreese.mods.nuit.render.NuitRenderBackend;
import me.flashyreese.mods.nuit.render.NuitRenderPipelines;
import me.flashyreese.mods.nuit.skybox.AbstractSkybox;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:fog": treibende Nebelbänke hoch am Himmel, in die die Ketten eintauchen.
 * Muss nach den Ketten gezeichnet werden (höherer "layer"). Farbe folgt der Tageszeit, Blitze lassen
 * den Nebel aufleuchten.
 */
public class FogSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier DEFAULT_TEXTURE = NuitChainsClient.id("textures/sky/fog_puff.png");
    private static final double SKY_RADIUS = 92.0;

    public static final Codec<FogSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(FogSkybox::getTexture),
            FogSettings.CODEC.optionalFieldOf("fog", FogSettings.DEFAULT).forGetter(FogSkybox::getSettings)
    ).apply(instance, FogSkybox::new));

    private final Identifier texture;
    private final FogSettings settings;
    private final FogField field;
    private final FogGeometry geometry;
    private final FogGeometry.Light light = new FogGeometry.Light();
    private float[] data = new float[36 * 256];
    private int quads;
    private double maxD2;

    public FogSkybox(Properties properties, Conditions conditions, Identifier texture, FogSettings settings) {
        super(properties, conditions);
        this.texture = texture;
        this.settings = settings;
        this.field = new FogField(settings);
        this.geometry = new FogGeometry(this.field);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        DayCycle.update(context.camera(), context.tickDelta());
        FogField.publish(this.field, Math.max(0F, this.alpha));
        if (this.alpha <= 0.0F) {
            return;
        }
        Entity entity = context.camera().entity();
        if (entity == null) {
            return;
        }
        float tickDelta = context.tickDelta();
        Vec3 eye = entity.getEyePosition(tickDelta);
        double timeSec = (entity.level().getGameTime() + (double) tickDelta) / 20.0;
        this.light.set(DayCycle.day(), DayCycle.twilight(), DayCycle.sunX(), DayCycle.sunZ(),
                LightningSkybox.currentFlash, this.settings.lightningGlow());

        this.quads = 0;
        this.maxD2 = 0;
        this.geometry.build(eye.x, eye.y, eye.z, timeSec, this.alpha, this.light, this::add);
        if (this.quads == 0) {
            return;
        }
        RenderPipeline pipeline = ChainRenderTypes.skyChains();
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, this.quads * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            float sc = (float) (SKY_RADIUS / Math.max(SKY_RADIUS, Math.sqrt(this.maxD2)));
            for (int q = 0; q < this.quads; q++) {
                int o = q * 36;
                for (int v = 0; v < 4; v++) {
                    builder.addVertex(this.data[o + v * 3] * sc, this.data[o + v * 3 + 1] * sc, this.data[o + v * 3 + 2] * sc)
                            .setUv(this.data[o + 12 + v * 2], this.data[o + 13 + v * 2])
                            .setColor(this.data[o + 20 + v * 4], this.data[o + 21 + v * 4], this.data[o + 22 + v * 4], this.data[o + 23 + v * 4]);
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null) {
                return;
            }
            Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
            GpuBufferSlice transforms = NuitRenderBackend.createDynamicTransforms(modelView, new Vector4f(1F, 1F, 1F, 1F));
            NuitRenderBackend.drawTextured(pipeline, mesh, transforms, NuitRenderBackend.SAMPLER0_NAME, this.texture);
        }
    }

    private void add(float[] xyz, float[] uv, float[] rgba) {
        int o = this.quads * 36;
        if (o + 36 > this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);
        for (int v = 0; v < 4; v++) {
            double x = xyz[v * 3], y = xyz[v * 3 + 1], z = xyz[v * 3 + 2];
            this.maxD2 = Math.max(this.maxD2, x * x + y * y + z * z);
        }
        System.arraycopy(xyz, 0, this.data, o, 12);
        System.arraycopy(uv, 0, this.data, o + 12, 8);
        System.arraycopy(rgba, 0, this.data, o + 20, 16);
        this.quads++;
    }

    @Override
    public Collection<Identifier> getTexturesToRegister() {
        return List.of(this.texture);
    }

    public Identifier getTexture() {
        return this.texture;
    }

    public FogSettings getSettings() {
        return this.settings;
    }
}
