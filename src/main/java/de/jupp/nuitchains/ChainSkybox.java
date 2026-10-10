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
    private static final int FLOATS_PER_QUAD = 24; // 12 Position + 8 UV + 4 Deckkraft
    public static final Identifier GLOW_TEXTURE = NuitChainsClient.id("textures/chain/glow.png");

    public static final Codec<ChainSkybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Properties.CODEC.optionalFieldOf("properties", Properties.of()).forGetter(AbstractSkybox::getProperties),
            Conditions.CODEC.optionalFieldOf("conditions", Conditions.of()).forGetter(AbstractSkybox::getConditions),
            Identifier.CODEC.optionalFieldOf("texture", DEFAULT_TEXTURE).forGetter(ChainSkybox::getTexture),
            RGBA.CODEC.optionalFieldOf("tint", WHITE).forGetter(ChainSkybox::getTint),
            ChainSettings.CODEC.optionalFieldOf("chains", ChainSettings.DEFAULT).forGetter(ChainSkybox::getSettings),
            DamageSettings.CODEC.optionalFieldOf("damage", DamageSettings.DEFAULT).forGetter(ChainSkybox::getDamage),
            VariationSettings.CODEC.optionalFieldOf("variation", VariationSettings.DEFAULT).forGetter(ChainSkybox::getVariation),
            MotionSettings.CODEC.optionalFieldOf("motion", MotionSettings.DEFAULT).forGetter(ChainSkybox::getMotion),
            Codec.floatRange(0F, 3F).optionalFieldOf("glow", 0.32F).forGetter(ChainSkybox::getGlow),
            Codec.floatRange(0F, 1000F).optionalFieldOf("nearDistance", 220F).forGetter(ChainSkybox::getNearDistance),
            Codec.BOOL.optionalFieldOf("hideClouds", true).forGetter(ChainSkybox::isHideClouds)
    ).apply(instance, ChainSkybox::new));

    private final Identifier texture;
    private final RGBA tint;
    private final ChainSettings settings;
    private final DamageSettings damage;
    private final VariationSettings variation;
    private final MotionSettings motion;
    private final float glow;
    private final float nearDistance;
    private final boolean hideClouds;
    private final ChainGeometry geometry;

    /** Ferne Vierecke und Leuchten dieses Bildes, je Kette – vom Welt-Durchgang vorbereitet. */
    private final List<FarChain> farChains = new ArrayList<>();
    private boolean farPrepared;

    /** Puffer für Vierecke (Position, UV, Deckkraft pro Ecke). */
    static final class QuadBuffer {
        float[] data = new float[FLOATS_PER_QUAD * 256];
        int quads;

        void add(float[] xyz, float[] uv, float[] alpha) {
            int o = this.quads * FLOATS_PER_QUAD;
            if (o + FLOATS_PER_QUAD > this.data.length) this.data = Arrays.copyOf(this.data, this.data.length * 2);
            System.arraycopy(xyz, 0, this.data, o, 12);
            System.arraycopy(uv, 0, this.data, o + 12, 8);
            System.arraycopy(alpha, 0, this.data, o + 20, 4);
            this.quads++;
        }
    }

    private static final class FarChain {
        final QuadBuffer body = new QuadBuffer();
        final QuadBuffer glow = new QuadBuffer();
        float fade;
    }

    public ChainSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA tint,
                       ChainSettings settings, DamageSettings damage, VariationSettings variation,
                       MotionSettings motion, float glow, float nearDistance, boolean hideClouds) {
        super(properties, conditions);
        this.texture = texture;
        this.tint = tint;
        this.settings = settings;
        this.damage = damage;
        this.variation = variation;
        this.motion = motion;
        this.glow = glow;
        this.nearDistance = nearDistance;
        this.hideClouds = hideClouds;
        this.geometry = new ChainGeometry(settings, damage, variation, motion, glow);
    }

    private FarChain farChain(int index, float fade) {
        while (this.farChains.size() <= index) this.farChains.add(new FarChain());
        FarChain far = this.farChains.get(index);
        far.body.quads = 0;
        far.glow.quads = 0;
        far.fade = fade;
        return far;
    }

    private void clearFrom(int index) {
        for (int i = index; i < this.farChains.size(); i++) {
            this.farChains.get(i).body.quads = 0;
            this.farChains.get(i).glow.quads = 0;
        }
    }

    // ------------------------------------------------------------------ Welt-Durchgang (läuft vor dem Himmel)

    /**
     * Wird pro Bild beim Sammeln der Welt-Geometrie aufgerufen: nahe Teile direkt einreichen,
     * ferne Teile und das Leuchten für den Himmel-Durchgang merken.
     */
    public void prepareFrame(SubmitNodeCollector collector, PoseStack poseStack, Vec3 eye, double timeSec, float depthFar) {
        this.farPrepared = false;
        if (this.alpha <= 0.0F) {
            return;
        }
        double split = Math.min(this.nearDistance, depthFar * 0.8);
        List<ChainGeometry.Chain> chains = this.geometry.collect(eye.x, eye.y, eye.z);
        QuadBuffer near = this.nearBuffer;
        near.quads = 0;
        int used = 0;
        for (ChainGeometry.Chain chain : chains) {
            FarChain far = farChain(used++, (float) chain.fade());
            this.geometry.build(chain, eye.x, eye.y, eye.z, timeSec, far.body::add, near::add, far.glow::add, split);
        }
        clearFrom(used);
        this.farPrepared = true;

        if (near.quads > 0) {
            final float[] data = near.data;
            final int count = near.quads;
            final float r = this.tint.getRed(), g = this.tint.getGreen(), b = this.tint.getBlue();
            final float a = this.tint.getAlpha() * this.alpha;
            RenderType type = ChainRenderTypes.chains(this.texture);
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
                for (int q = 0; q < count; q++) {
                    int o = q * FLOATS_PER_QUAD;
                    for (int v = 0; v < 4; v++) {
                        buffer.addVertex(pose, data[o + v * 3], data[o + v * 3 + 1], data[o + v * 3 + 2])
                                .setUv(data[o + 12 + v * 2], data[o + 13 + v * 2])
                                .setColor(r, g, b, a * data[o + 20 + v]);
                    }
                }
            });
        }
    }

    private final QuadBuffer nearBuffer = new QuadBuffer();

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
                FarChain far = farChain(used++, (float) chain.fade());
                this.geometry.build(chain, eye.x, eye.y, eye.z, timeSec, far.body::add, far.body::add, far.glow::add, 0);
            }
            clearFrom(used);
        }
        this.farPrepared = false;

        context.applyFog();
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        // Ketten leuchten kurz mit auf, wenn es blitzt
        float flash = LightningSkybox.currentFlash;
        float glowMul = 1F + 1.8F * flash;

        for (FarChain chain : this.farChains) {
            if (chain.glow.quads > 0) {
                draw(ChainRenderTypes.skyGlow(), GLOW_TEXTURE, modelView, chain.glow, Math.min(1F, chain.fade * glowMul));
            }
            if (chain.body.quads > 0) {
                draw(ChainRenderTypes.skyChains(), this.texture, modelView, chain.body, chain.fade);
            }
        }
    }

    private void draw(RenderPipeline pipeline, Identifier tex, Matrix4f modelView, QuadBuffer buf, float alpha) {
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, buf.quads * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            float[] d = buf.data;
            for (int q = 0; q < buf.quads; q++) {
                int o = q * FLOATS_PER_QUAD;
                for (int v = 0; v < 4; v++) {
                    double x = d[o + v * 3], y = d[o + v * 3 + 1], z = d[o + v * 3 + 2];
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    double sc = dist > SKY_RADIUS ? SKY_RADIUS / dist : 1.0; // Richtung bleibt gleich
                    builder.addVertex((float) (x * sc), (float) (y * sc), (float) (z * sc))
                            .setUv(d[o + 12 + v * 2], d[o + 13 + v * 2])
                            .setColor(1F, 1F, 1F, d[o + 20 + v]);
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
            NuitRenderBackend.drawTextured(pipeline, mesh, transforms, NuitRenderBackend.SAMPLER0_NAME, tex);
        }
    }

    @Override
    public Collection<Identifier> getTexturesToRegister() {
        return List.of(this.texture, GLOW_TEXTURE);
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

    public float getGlow() {
        return this.glow;
    }

    public MotionSettings getMotion() {
        return this.motion;
    }

    public float getNearDistance() {
        return this.nearDistance;
    }

    public boolean isHideClouds() {
        return this.hideClouds;
    }
}
