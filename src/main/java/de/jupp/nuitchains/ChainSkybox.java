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
import java.util.HashMap;
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
            StyleSettings.CODEC.optionalFieldOf("style", StyleSettings.DEFAULT).forGetter(ChainSkybox::getStyle),
            EnergySettings.CODEC.optionalFieldOf("energy", EnergySettings.DEFAULT).forGetter(ChainSkybox::getEnergy),
            Codec.floatRange(0F, 3F).optionalFieldOf("glow", 0.45F).forGetter(ChainSkybox::getGlow),
            Codec.floatRange(0F, 1000F).optionalFieldOf("nearDistance", 220F).forGetter(ChainSkybox::getNearDistance),
            Codec.BOOL.optionalFieldOf("hideClouds", true).forGetter(ChainSkybox::isHideClouds)
    ).apply(instance, ChainSkybox::new));

    private final Identifier texture;
    private final RGBA tint;
    private final ChainSettings settings;
    private final DamageSettings damage;
    private final VariationSettings variation;
    private final MotionSettings motion;
    private final StyleSettings style;
    private final EnergySettings energy;
    private final float glow;
    private final float nearDistance;
    private final boolean hideClouds;
    private final ChainGeometry geometry;

    /** Ferne Vierecke und Leuchten je Kette (nach Kette zwischengespeichert), Reihenfolge dieses Bildes in {@link #drawList}. */
    private final HashMap<Long, FarChain> farCache = new HashMap<>();
    private final List<FarChain> drawList = new ArrayList<>();
    private boolean farPrepared;
    private long frameNo;
    private double eyeX, eyeY, eyeZ;

    /** Puffer für Vierecke (Position, UV, Deckkraft pro Ecke). */
    static final class QuadBuffer {
        float[] data = new float[FLOATS_PER_QUAD * 256];
        int quads;

        double maxD2;

        void add(float[] xyz, float[] uv, float[] alpha) {
            for (int v = 0; v < 4; v++) {
                double x = xyz[v * 3], y = xyz[v * 3 + 1], z = xyz[v * 3 + 2];
                double d2 = x * x + y * y + z * z;
                if (d2 > this.maxD2) this.maxD2 = d2;
            }
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
        double bx, by, bz;                 // Kameraposition, als die Kette zuletzt gebaut wurde
        long builtFrame = Long.MIN_VALUE;
        long seenFrame;

        void reset(double x, double y, double z, long frame) {
            this.body.quads = 0; this.body.maxD2 = 0;
            this.glow.quads = 0; this.glow.maxD2 = 0;
            this.bx = x; this.by = y; this.bz = z;
            this.builtFrame = frame;
        }
    }

    public ChainSkybox(Properties properties, Conditions conditions, Identifier texture, RGBA tint,
                       ChainSettings settings, DamageSettings damage, VariationSettings variation,
                       MotionSettings motion, StyleSettings style, EnergySettings energy, float glow, float nearDistance,
                       boolean hideClouds) {
        super(properties, conditions);
        this.texture = texture;
        this.tint = tint;
        this.settings = settings;
        this.damage = damage;
        this.variation = variation;
        this.motion = motion;
        this.style = style;
        this.energy = energy;
        this.glow = glow;
        this.nearDistance = nearDistance;
        this.hideClouds = hideClouds;
        this.geometry = new ChainGeometry(settings, damage, variation, motion, glow, style, energy);
    }

    /**
     * Baut alle Ketten dieses Bildes. Nahe Ketten werden jedes Bild neu gebaut; weit entfernte nur jedes
     * 2. bzw. 3. Bild (versetzt, damit die Arbeit sich verteilt) – Kamerabewegung wird beim Zeichnen
     * ausgeglichen, deshalb sieht man davon nichts.
     */
    private void buildFrame(List<ChainGeometry.Chain> chains, double ex, double ey, double ez, double timeSec,
                            double split, QuadBuffer near) {
        this.frameNo++;
        this.eyeX = ex; this.eyeY = ey; this.eyeZ = ez;
        this.drawList.clear();
        for (ChainGeometry.Chain chain : chains) {
            FarChain far = this.farCache.computeIfAbsent(chain.hash(), k -> new FarChain());
            far.seenFrame = this.frameNo;
            far.fade = (float) chain.fade();
            // nahe Ketten (oder solche, die in die Welt-Geometrie reichen) jedes Bild, ferne seltener
            boolean distant = chain.distance() - chain.length() * 0.75 - 80 > Math.max(split, this.nearDistance);
            boolean onlyFar = near == null || distant;
            int every = !distant ? 1 : chain.distance() > 650 ? 4 : chain.distance() > 420 ? 3 : 2;
            double mx = far.bx - ex, my = far.by - ey, mz = far.bz - ez;
            boolean moved = mx * mx + my * my + mz * mz > 0.0004 * chain.distance() * chain.distance();
            if (every == 1 || far.builtFrame == Long.MIN_VALUE || moved
                    || (this.frameNo + (chain.hash() & 0xFF)) % every == 0) {
                far.reset(ex, ey, ez, this.frameNo);
                if (onlyFar) {
                    this.geometry.build(chain, ex, ey, ez, timeSec, far.body::add, far.body::add, far.glow::add, 0);
                } else {
                    this.geometry.build(chain, ex, ey, ez, timeSec, far.body::add, near::add, far.glow::add, split);
                }
            }
            this.drawList.add(far);
        }
        this.farCache.values().removeIf(f -> f.seenFrame != this.frameNo);
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
        buildFrame(chains, eye.x, eye.y, eye.z, timeSec, split, near);
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
            buildFrame(chains, eye.x, eye.y, eye.z, timeSec, 0, null);
        }
        this.farPrepared = false;

        context.applyFog();
        Matrix4f modelView = new Matrix4f(context.skyModelViewStack());
        // Ketten leuchten kurz mit auf, wenn es blitzt
        float flash = LightningSkybox.currentFlash;
        float glowMul = 1F + 1.8F * flash;

        for (FarChain chain : this.drawList) {
            // Kamera hat sich seit dem Bauen bewegt: verschieben (zwischengespeicherte ferne Ketten)
            float ox = (float) (chain.bx - this.eyeX), oy = (float) (chain.by - this.eyeY), oz = (float) (chain.bz - this.eyeZ);
            if (chain.glow.quads > 0) {
                draw(ChainRenderTypes.skyGlow(), GLOW_TEXTURE, modelView, chain.glow, Math.min(1F, chain.fade * glowMul), ox, oy, oz);
            }
            if (chain.body.quads > 0) {
                draw(ChainRenderTypes.skyChains(), this.texture, modelView, chain.body, chain.fade, ox, oy, oz);
            }
        }
    }

    private void draw(RenderPipeline pipeline, Identifier tex, Matrix4f modelView, QuadBuffer buf, float alpha,
                      float ox, float oy, float oz) {
        try (ByteBufferBuilder byteBufferBuilder = NuitRenderPipelines.byteBufferBuilder(pipeline, buf.quads * 4)) {
            BufferBuilder builder = NuitRenderPipelines.bufferBuilder(byteBufferBuilder, pipeline);
            float[] d = buf.data;
            // Ganze Kette gleichmäßig in die Himmelskugel ziehen: Richtung jeder Ecke bleibt gleich
            double maxDist = Math.sqrt(buf.maxD2) + Math.sqrt(ox * ox + oy * oy + oz * oz);
            float sc = (float) (maxDist > SKY_RADIUS ? SKY_RADIUS / maxDist : 1.0);
            for (int q = 0; q < buf.quads; q++) {
                int o = q * FLOATS_PER_QUAD;
                for (int v = 0; v < 4; v++) {
                    float x = d[o + v * 3] + ox, y = d[o + v * 3 + 1] + oy, z = d[o + v * 3 + 2] + oz;
                    builder.addVertex(x * sc, y * sc, z * sc)
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

    public StyleSettings getStyle() {
        return this.style;
    }

    public EnergySettings getEnergy() {
        return this.energy;
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
