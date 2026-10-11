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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:atmosphere": der Tag-Himmel. Eine Kuppel, deren Farben jedes Bild aus dem
 * Sonnenstand berechnet werden (siehe {@link AtmosphereModel}) – tagsüber hell und dunstig, morgens und
 * abends ein Glühen zur Sonne hin, nachts durchsichtig, damit die Pink-Galaxie zu sehen ist.
 * <p>
 * Die Nebelfarbe aus "properties.fog" gilt nur tagsüber (Nuit mischt sie nach {@link #getAlpha()} ein).
 */
public class AtmosphereSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier WHITE_TEXTURE = NuitChainsClient.id("textures/sky/white.png");
    private static final float RADIUS = 90F;
    private static final double[] RINGS = {-90, -35, -15, -6, -2, 0, 2, 5, 9, 14, 20, 28, 38, 50, 64, 78, 90};
    private static final int SEGMENTS = 64;

    public static final Codec<AtmosphereSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            AtmosphereSettings.CODEC.optionalFieldOf("atmosphere", AtmosphereSettings.DEFAULT).forGetter(AtmosphereSkybox::getSettings)
    ).apply(instance, AtmosphereSkybox::new));

    private final AtmosphereSettings settings;
    private final float[] px, py, pz, cr, cg, cb, ca;
    private final float[] rgba = new float[4];

    public AtmosphereSkybox(Properties properties, Conditions conditions, AtmosphereSettings settings) {
        super(properties, conditions);
        this.settings = settings;
        int n = RINGS.length * (SEGMENTS + 1);
        this.px = new float[n]; this.py = new float[n]; this.pz = new float[n];
        this.cr = new float[n]; this.cg = new float[n]; this.cb = new float[n]; this.ca = new float[n];
        for (int r = 0; r < RINGS.length; r++) {
            double el = Math.toRadians(RINGS[r]);
            for (int s = 0; s <= SEGMENTS; s++) {
                double az = s * Math.PI * 2 / SEGMENTS;
                int i = r * (SEGMENTS + 1) + s;
                this.px[i] = (float) (Math.cos(el) * Math.cos(az));
                this.py[i] = (float) Math.sin(el);
                this.pz[i] = (float) (Math.cos(el) * Math.sin(az));
            }
        }
    }

    @Override
    public void tick(ClientLevel level) {
        super.tick(level);
        try {
            DayCycle.update(Minecraft.getInstance().gameRenderer.mainCamera(), 1F);
        } catch (RuntimeException ignored) {
        }
    }

    /** Für Nuits Nebelfarbe: tagsüber zählt die Tag-Nebelfarbe, nachts die der Galaxie. */
    @Override
    public float getAlpha() {
        if (this.alpha <= 0F) {
            return 0F;
        }
        double w = Math.min(1, DayCycle.day() + 0.35 * DayCycle.twilight());
        return (float) Math.max(0.001, this.alpha * w);
    }

    @Override
    public void render(SkyboxRenderContext context) {
        DayCycle.update(context.camera(), context.tickDelta());
        if (this.alpha <= 0.0F) {
            return;
        }
        double sx = DayCycle.sunX(), sy = DayCycle.sunY(), sz = DayCycle.sunZ();
        float maxA = 0F;
        for (int i = 0; i < this.px.length; i++) {
            AtmosphereModel.color(this.px[i], this.py[i], this.pz[i], sx, sy, sz, this.settings, this.rgba);
            this.cr[i] = this.rgba[0]; this.cg[i] = this.rgba[1]; this.cb[i] = this.rgba[2]; this.ca[i] = this.rgba[3];
            maxA = Math.max(maxA, this.rgba[3]);
        }
        if (maxA < 0.002F) {
            return;   // tiefe Nacht: nichts zu zeichnen
        }
        RenderPipeline pipeline = ChainRenderTypes.skyChains();
        int quads = (RINGS.length - 1) * SEGMENTS;
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, quads * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            for (int r = 0; r < RINGS.length - 1; r++) {
                for (int s = 0; s < SEGMENTS; s++) {
                    int a = r * (SEGMENTS + 1) + s, b = a + 1, c = a + SEGMENTS + 2, d = a + SEGMENTS + 1;
                    if (this.ca[a] + this.ca[b] + this.ca[c] + this.ca[d] < 0.002F) continue;
                    vertex(builder, a);
                    vertex(builder, b);
                    vertex(builder, c);
                    vertex(builder, d);
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null) {
                return;
            }
            Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
            GpuBufferSlice transforms = NuitRenderBackend.createDynamicTransforms(modelView, new Vector4f(1F, 1F, 1F, this.alpha));
            NuitRenderBackend.drawTextured(pipeline, mesh, transforms, NuitRenderBackend.SAMPLER0_NAME, WHITE_TEXTURE);
        }
    }

    private void vertex(BufferBuilder builder, int i) {
        builder.addVertex(this.px[i] * RADIUS, this.py[i] * RADIUS, this.pz[i] * RADIUS)
                .setUv(0.5F, 0.5F)
                .setColor(this.cr[i], this.cg[i], this.cb[i], this.ca[i]);
    }

    @Override
    public Collection<Identifier> getTexturesToRegister() {
        return List.of(WHITE_TEXTURE);
    }

    public AtmosphereSettings getSettings() {
        return this.settings;
    }
}
