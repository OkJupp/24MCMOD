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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:lightning": ab und zu verzweigte Blitze am Himmel,
 * mit Flackern, Leuchten drumherum und kurzem Aufhellen des ganzen Himmels.
 */
public class LightningSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier DEFAULT_TEXTURE = NuitChainsClient.id("textures/sky/lightning.png");
    private static final RGBA DEFAULT_COLOR = new RGBA(1.0F, 0.82F, 0.96F, 1.0F);
    private static final Blend ADD = new Blend("add");

    public static final Codec<LightningSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(LightningSkybox::getTexture),
            RGBA.CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(LightningSkybox::getColor),
            LightningSettings.CODEC.optionalFieldOf("lightning", LightningSettings.DEFAULT).forGetter(LightningSkybox::getSettings)
    ).apply(instance, LightningSkybox::new));

    /** Aktuelle Blitz-Helligkeit (0..1) – kann von anderen Effekten gelesen werden. */
    public static volatile float currentFlash;

    private final Identifier texture;
    private final RGBA color;
    private final LightningSettings settings;
    private final LightningGeometry geometry;

    public LightningSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA color, LightningSettings settings) {
        super(properties, conditions);
        this.texture = texture;
        this.color = color;
        this.settings = settings;
        this.geometry = new LightningGeometry(settings);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        currentFlash = 0F;
        if (this.alpha <= 0.0F) {
            return;
        }
        Entity entity = context.camera().entity();
        if (entity == null) {
            return;
        }
        double timeSec = (entity.level().getGameTime() + (double) context.tickDelta()) / 20.0;
        List<LightningGeometry.Strike> strikes = this.geometry.active(timeSec);
        if (strikes.isEmpty()) {
            return;
        }
        context.applyFog();
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        RenderPipeline pipeline = NuitRenderPipelines.texturedSkybox(ADD.getBlendFunction());

        float flash = 0F;
        for (LightningGeometry.Strike strike : strikes) {
            LightningGeometry.Shape shape = this.geometry.shape(strike.hash());
            List<List<double[]>> bolts = shape.bolts();
            double grow = LightningGeometry.growth(strike.age());
            float glow = (float) strike.glowIntensity();
            float bolt = (float) strike.boltIntensity();
            flash = Math.max(flash, glow);

            // Himmel kurz aufhellen
            if (this.settings.flashStrength() > 0) {
                draw(pipeline, modelView, 6, this.geometry::flash, glow * this.settings.flashStrength());
            }
            // Leuchten um den Blitz
            if (this.settings.glowSize() > 0) {
                draw(pipeline, modelView, 1, sink -> this.geometry.glow(bolts.get(0), sink), glow * 0.9F);
            }
            // Verzweigungen, dann Hauptblitz
            if (bolt > 0.02F) {
                if (bolts.size() > 1) {
                    int quads = 0;
                    for (int i = 1; i < bolts.size(); i++) quads += bolts.get(i).size();
                    draw(pipeline, modelView, quads, sink -> {
                        for (int i = 1; i < bolts.size(); i++) {
                            // Verzweigung wächst los, sobald der Hauptblitz dort angekommen ist
                            double sf = shape.startFrac()[i];
                            double g = grow >= 1 ? 1 : Math.max(0, (grow - sf) / Math.max(0.05, 1 - sf));
                            this.geometry.ribbon(bolts.get(i), 0.55, 0.12, g, sink);
                        }
                    }, bolt * 0.7F);
                }
                draw(pipeline, modelView, bolts.get(0).size(), sink -> this.geometry.ribbon(bolts.get(0), 1.0, 0.45, grow, sink), bolt);
            }
        }
        currentFlash = flash;
    }

    private interface Emitter {
        void emit(LightningGeometry.QuadSink sink);
    }

    private void draw(RenderPipeline pipeline, Matrix4f modelView, int maxQuads, Emitter emitter, float intensity) {
        if (intensity <= 0.003F) {
            return;
        }
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, Math.max(4, maxQuads * 4))) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            emitter.emit((xyz, uv) -> {
                for (int v = 0; v < 4; v++) {
                    builder.addVertex(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2]).setUv(uv[v * 2], uv[v * 2 + 1]);
                }
            });
            MeshData mesh = builder.build();
            if (mesh == null) {
                return;
            }
            Vector4f mod = new Vector4f(this.color.getRed(), this.color.getGreen(), this.color.getBlue(),
                    Math.min(1F, this.color.getAlpha() * this.alpha * intensity));
            GpuBufferSlice transforms = NuitRenderBackend.createDynamicTransforms(modelView, mod);
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

    public RGBA getColor() {
        return this.color;
    }

    public LightningSettings getSettings() {
        return this.settings;
    }
}
