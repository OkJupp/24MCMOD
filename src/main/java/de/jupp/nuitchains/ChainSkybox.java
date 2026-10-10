package de.jupp.nuitchains;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Nuit-Skybox-Typ "nuit_chains:chains": echte 3D-Ketten, die im Weltraster hoch am Himmel hängen,
 * pendeln und am Ende zerfallen.
 * <p>
 * Zwei Durchgänge pro Bild:
 * <ul>
 *     <li>Nahe Teile (bis {@code nearDistance}) werden als echte Weltgeometrie mit Tiefe gezeichnet –
 *     Gelände, Spieler und Wolken liegen korrekt davor oder dahinter.</li>
 *     <li>Ferne Teile werden im Himmel-Durchgang von Nuit gezeichnet, in die Skybox "herangezogen"
 *     (gleiche Richtung = gleiches Bild). So verschluckt sie weder der Sichtweiten-Nebel noch die
 *     Sichtweite selbst.</li>
 * </ul>
 */
public class ChainSkybox extends AbstractSkybox implements SkyboxTextureProvider {
    public static final Identifier DEFAULT_TEXTURE = NuitChainsClient.id("textures/chain/metal.png");
    private static final RGBA WHITE = new RGBA(1F, 1F, 1F, 1F);
    /** Radius der "Heranzieh"-Kugel im Himmel, muss kleiner als die Nuit-Skybox (100) sein. */
    private static final double SKY_RADIUS = 92.0;
    private static final int FLOATS_PER_QUAD = 21; // 12 Position + 8 UV + 1 Deckkraft

    public static final Codec<ChainSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(ChainSkybox::getTexture),
            RGBA.CODEC.optionalFieldOf("tint", WHITE).forGetter(ChainSkybox::getTint),
            ChainSettings.CODEC.optionalFieldOf("chains", ChainSettings.DEFAULT).forGetter(ChainSkybox::getSettings),
            DamageSettings.CODEC.optionalFieldOf("damage", DamageSettings.DEFAULT).forGetter(ChainSkybox::getDamage),
            VariationSettings.CODEC.optionalFieldOf("variation", VariationSettings.DEFAULT).forGetter(ChainSkybox::getVariation),
            Codec.floatRange(0F, 1000F).optionalFieldOf("nearDistance", 220F).forGetter(ChainSkybox::getNearDistance),
            Codec.BOOL.optionalFieldOf("hideClouds", true).forGetter(ChainSkybox::isHideClouds)
    ).apply(instance, ChainSkybox::new));

    private final Identifier texture;
    private final RGBA tint;
    private final ChainSettings settings;
    private final DamageSettings damage;
    private final VariationSettings variation;
    private final float nearDistance;
    private final boolean hideClouds;
    private final ChainGeometry geometry;

    /** Ferne Vierecke dieses Bildes, je Kette – vom Welt-Durchgang vorbereitet. */
    private final List<FarChain> farChains = new ArrayList<>();
    private boolean farPrepared;

    private static final class FarChain {
        float[] data = new float[FLOATS_PER_QUAD * 256];
        int quads;
        float fade;

        void add(float[] xyz, float[] uv, float alpha) {
            int o = this.quads * FLOATS_PER_QUAD;
            if (o + FLOATS_PER_QUAD > this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);
            System.arraycopy(xyz, 0, this.data, o, 12);
            System.arraycopy(uv, 0, this.data, o + 12, 8);
            this.data[o + 20] = alpha;
            this.quads++;
        }
    }

    public ChainSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA tint,
                       ChainSettings settings, DamageSettings damage, VariationSettings variation,
                       float nearDistance, boolean hideClouds) {
        super(properties, conditions);
        this.texture = texture;
        this.tint = tint;
        this.settings = settings;
        this.damage = damage;
        this.variation = variation;
        this.nearDistance = nearDistance;
        this.hideClouds = hideClouds;
        this.geometry = new ChainGeometry(settings, damage, variation);
    }

    // ------------------------------------------------------------------ Welt-Durchgang (läuft vor dem Himmel)

    /**
     * Wird pro Bild beim Sammeln der Welt-Geometrie aufgerufen: nahe Teile direkt einreichen,
     * ferne Teile für den Himmel-Durchgang merken.
     */
    public void prepareFrame(SubmitNodeCollector collector, PoseStack poseStack, Vec3 eye, double timeSec, float depthFar) {
        this.farPrepared = false;
        if (this.alpha <= 0.0F) {
            return;
        }
        double split = Math.min(this.nearDistance, depthFar * 0.8);
        List<ChainGeometry.Chain> chains = this.geometry.collect(eye.x, eye.y, eye.z);
        FloatList near = new FloatList();
        int used = 0;
        for (ChainGeometry.Chain chain : chains) {
            FarChain far = used < this.farChains.size() ? this.farChains.get(used) : null;
            if (far == null) {
                far = new FarChain();
                this.farChains.add(far);
            }
            far.quads = 0;
            far.fade = (float) chain.fade();
            used++;
            final FarChain target = far;
            this.geometry.build(chain, eye.x, eye.y, eye.z, timeSec, target::add, near::addQuad, split);
        }
        for (int i = used; i < this.farChains.size(); i++) {
            this.farChains.get(i).quads = 0;
        }
        this.farPrepared = true;

        if (near.size > 0) {
            final float[] data = near.data;
            final int count = near.size / FLOATS_PER_QUAD;
            final float r = this.tint.getRed(), g = this.tint.getGreen(), b = this.tint.getBlue();
            RenderType type = ChainRenderTypes.chains(this.texture);
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (int q = 0; q < count; q++) {
                    int o = q * FLOATS_PER_QUAD;
                    for (int v = 0; v < 4; v++) {
                        buffer.addVertex(pose, data[o + v * 3], data[o + v * 3 + 1], data[o + v * 3 + 2])
                                .setUv(data[o + 12 + v * 2], data[o + 13 + v * 2])
                                .setColor(r, g, b, 1.0F);
                    }
                }
            });
        }
    }

    private static final class FloatList {
        float[] data = new float[FLOATS_PER_QUAD * 512];
        int size;

        void addQuad(float[] xyz, float[] uv, float alpha) {
            if (this.size + FLOATS_PER_QUAD > this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);
            System.arraycopy(xyz, 0, this.data, this.size, 12);
            System.arraycopy(uv, 0, this.data, this.size + 12, 8);
            this.data[this.size + 20] = alpha;
            this.size += FLOATS_PER_QUAD;
        }
    }

    // ------------------------------------------------------------------ Himmel-Durchgang (Nuit)

    @Override
    public void render(SkyboxRenderContext context) {
        if (this.alpha <= 0.0F) {
            return;
        }
        if (!this.farPrepared) {
            // Fallback, falls der Welt-Durchgang nicht lief: alles im Himmel zeichnen
            Camera camera = context.camera();
            Entity entity = camera.entity();
            if (entity == null) {
                return;
            }
            float tickDelta = context.tickDelta();
            Vec3 eye = entity.getEyePosition(tickDelta);
            double timeSec = (entity.level().getGameTime() + (double) tickDelta) / 20.0;
            List<ChainGeometry.Chain> chains = this.geometry.collect(eye.x, eye.y, eye.z);
            int used = 0;
            for (ChainGeometry.Chain chain : chains) {
                FarChain far = used < this.farChains.size() ? this.farChains.get(used) : null;
                if (far == null) {
                    far = new FarChain();
                    this.farChains.add(far);
                }
                far.quads = 0;
                far.fade = (float) chain.fade();
                used++;
                this.geometry.build(chain, eye.x, eye.y, eye.z, timeSec, far::add);
            }
            for (int i = used; i < this.farChains.size(); i++) {
                this.farChains.get(i).quads = 0;
            }
        }
        this.farPrepared = false;

        context.applyFog();
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        RenderPipeline pipeline = NuitRenderPipelines.texturedSkybox(Blend.normal().getBlendFunction());

        for (FarChain chain : this.farChains) {
            // aufeinanderfolgende Vierecke mit gleicher Deckkraft-Stufe in einem Aufruf zeichnen
            int q = 0;
            while (q < chain.quads) {
                int level = alphaLevel(chain.data[q * FLOATS_PER_QUAD + 20]);
                int end = q + 1;
                while (end < chain.quads && alphaLevel(chain.data[end * FLOATS_PER_QUAD + 20]) == level) end++;
                float alphaMul = level / (float) ALPHA_LEVELS;
                if (alphaMul > 0) {
                    drawFar(pipeline, modelView, chain, q, end, chain.fade * alphaMul);
                }
                q = end;
            }
        }
    }

    private static final int ALPHA_LEVELS = 24;

    private static int alphaLevel(float alpha) {
        return Math.round(Math.max(0F, Math.min(1F, alpha)) * ALPHA_LEVELS);
    }

    private void drawFar(RenderPipeline pipeline, Matrix4f modelView, FarChain chain, int from, int to, float alpha) {
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, (to - from) * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            float[] d = chain.data;
            for (int q = from; q < to; q++) {
                int o = q * FLOATS_PER_QUAD;
                for (int v = 0; v < 4; v++) {
                    double x = d[o + v * 3], y = d[o + v * 3 + 1], z = d[o + v * 3 + 2];
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    double sc = dist > SKY_RADIUS ? SKY_RADIUS / dist : 1.0; // Richtung bleibt gleich
                    builder.addVertex((float) (x * sc), (float) (y * sc), (float) (z * sc))
                            .setUv(d[o + 12 + v * 2], d[o + 13 + v * 2]);
                }
            }
            MeshData mesh = builder.build();
            if (mesh == null) {
                return;
            }
            Vector4f color = new Vector4f(
                    this.tint.getRed(), this.tint.getGreen(), this.tint.getBlue(),
                    this.tint.getAlpha() * this.alpha * alpha);
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

    public ChainSettings getSettings() {
        return this.settings;
    }

    public DamageSettings getDamage() {
        return this.damage;
    }

    public VariationSettings getVariation() {
        return this.variation;
    }

    public float getNearDistance() {
        return this.nearDistance;
    }

    public boolean isHideClouds() {
        return this.hideClouds;
    }
}
