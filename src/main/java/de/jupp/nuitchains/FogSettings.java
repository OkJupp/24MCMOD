package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Einstellungen der Nebelbänke, im JSON unter "fog": { ... }.
 *
 * @param height          Mittlere Höhe (Y) der Nebel-Oberkante – hier tauchen die Ketten ein
 * @param thickness       Wie tief es von der Oberkante dauert, bis der Nebel ganz dicht ist (Blöcke)
 * @param heightVariation Wie stark die Oberkante auf und ab wogt (± Blöcke)
 * @param coverage        Anteil des Himmels mit Nebelbänken (0 = keiner, 1 = geschlossene Decke)
 * @param scale           Größe der Nebelbänke (Blöcke)
 * @param drift           Wie schnell der Nebel treibt (Blöcke pro Sekunde)
 * @param morph           Wie schnell die Bänke ihre Form ändern (0 = gar nicht, 1 = normal)
 * @param opacity         Wie stark der Nebel die Ketten verschluckt (0 = gar nicht, 1 = ganz)
 * @param density         Deckkraft der sichtbaren Nebelschwaden (1 = normal)
 * @param puffSize        Größe der einzelnen Schwaden (1 = normal)
 * @param range           Bis zu welcher Entfernung Nebel gezeichnet wird (Blöcke)
 * @param clearRadius     Bis zu dieser Entfernung werden keine bzw. nur zarte Schwaden gezeichnet – der Nebel liegt
 *                        als Band in der Ferne, der Himmel über dir bleibt klar (Blöcke)
 * @param haze            Dunst in der Ferne: weit entfernte Ketten verschwimmen (0 = aus, 1 = stark)
 * @param lightningGlow   Wie hell Blitze den Nebel aufleuchten lassen (0 = gar nicht)
 * @param seed            Andere Zahl = andere Nebelbänke
 */
public record FogSettings(float height, float thickness, float heightVariation, float coverage, float scale,
                          float drift, float morph, float opacity, float density, float puffSize, float range,
                          float clearRadius, float haze, float lightningGlow, int seed) {

    public static final FogSettings DEFAULT = new FogSettings(280F, 70F, 50F, 0.55F, 380F, 3.0F, 1.0F, 0.95F,
            0.8F, 1.0F, 1100F, 480F, 0.25F, 1.0F, 99);

    public static final Codec<FogSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(-64F, 2000F).optionalFieldOf("height", DEFAULT.height()).forGetter(FogSettings::height),
            Codec.floatRange(1F, 1000F).optionalFieldOf("thickness", DEFAULT.thickness()).forGetter(FogSettings::thickness),
            Codec.floatRange(0F, 1000F).optionalFieldOf("heightVariation", DEFAULT.heightVariation()).forGetter(FogSettings::heightVariation),
            Codec.floatRange(0F, 1F).optionalFieldOf("coverage", DEFAULT.coverage()).forGetter(FogSettings::coverage),
            Codec.floatRange(20F, 5000F).optionalFieldOf("scale", DEFAULT.scale()).forGetter(FogSettings::scale),
            Codec.floatRange(0F, 100F).optionalFieldOf("drift", DEFAULT.drift()).forGetter(FogSettings::drift),
            Codec.floatRange(0F, 20F).optionalFieldOf("morph", DEFAULT.morph()).forGetter(FogSettings::morph),
            Codec.floatRange(0F, 1F).optionalFieldOf("opacity", DEFAULT.opacity()).forGetter(FogSettings::opacity),
            Codec.floatRange(0F, 3F).optionalFieldOf("density", DEFAULT.density()).forGetter(FogSettings::density),
            Codec.floatRange(0.2F, 5F).optionalFieldOf("puffSize", DEFAULT.puffSize()).forGetter(FogSettings::puffSize),
            Codec.floatRange(100F, 4000F).optionalFieldOf("range", DEFAULT.range()).forGetter(FogSettings::range),
            Codec.floatRange(0F, 2000F).optionalFieldOf("clearRadius", DEFAULT.clearRadius()).forGetter(FogSettings::clearRadius),
            Codec.floatRange(0F, 1F).optionalFieldOf("haze", DEFAULT.haze()).forGetter(FogSettings::haze),
            Codec.floatRange(0F, 5F).optionalFieldOf("lightningGlow", DEFAULT.lightningGlow()).forGetter(FogSettings::lightningGlow),
            Codec.INT.optionalFieldOf("seed", DEFAULT.seed()).forGetter(FogSettings::seed)
    ).apply(i, FogSettings::new));
}
