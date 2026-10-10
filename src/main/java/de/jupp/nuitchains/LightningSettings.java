package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Einstellungen der Blitze, im JSON unter "lightning": { ... }.
 *
 * @param averageInterval Durchschnittlicher Abstand zwischen zwei Blitzen in Sekunden
 * @param doubleChance    Wahrscheinlichkeit, dass kurz danach ein zweiter Blitz folgt (0–1)
 * @param minElevation    Niedrigster Startpunkt am Himmel (Grad über dem Horizont)
 * @param maxElevation    Höchster Startpunkt am Himmel (Grad)
 * @param length          Länge eines Blitzes (Grad am Himmel)
 * @param branches        Maximale Anzahl Verzweigungen
 * @param width           Dicke des Blitzes (1 = normal)
 * @param glowSize        Größe des Leuchtens um den Blitz (1 = normal)
 * @param flashStrength   Wie stark der ganze Himmel beim Blitz aufhellt (0–1)
 * @param seed            Zufalls-Startwert
 */
public record LightningSettings(float averageInterval, float doubleChance, float minElevation, float maxElevation,
                                float length, int branches, float width, float glowSize, float flashStrength, int seed) {

    public static final LightningSettings DEFAULT = new LightningSettings(14F, 0.35F, 12F, 62F, 38F, 4, 1.0F, 1.0F, 0.22F, 77);

    public static final Codec<LightningSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(1F, 3600F).optionalFieldOf("averageInterval", DEFAULT.averageInterval()).forGetter(LightningSettings::averageInterval),
            Codec.floatRange(0F, 1F).optionalFieldOf("doubleChance", DEFAULT.doubleChance()).forGetter(LightningSettings::doubleChance),
            Codec.floatRange(-30F, 90F).optionalFieldOf("minElevation", DEFAULT.minElevation()).forGetter(LightningSettings::minElevation),
            Codec.floatRange(-30F, 90F).optionalFieldOf("maxElevation", DEFAULT.maxElevation()).forGetter(LightningSettings::maxElevation),
            Codec.floatRange(2F, 120F).optionalFieldOf("length", DEFAULT.length()).forGetter(LightningSettings::length),
            Codec.intRange(0, 12).optionalFieldOf("branches", DEFAULT.branches()).forGetter(LightningSettings::branches),
            Codec.floatRange(0.1F, 10F).optionalFieldOf("width", DEFAULT.width()).forGetter(LightningSettings::width),
            Codec.floatRange(0F, 10F).optionalFieldOf("glowSize", DEFAULT.glowSize()).forGetter(LightningSettings::glowSize),
            Codec.floatRange(0F, 1F).optionalFieldOf("flashStrength", DEFAULT.flashStrength()).forGetter(LightningSettings::flashStrength),
            Codec.INT.optionalFieldOf("seed", DEFAULT.seed()).forGetter(LightningSettings::seed)
    ).apply(i, LightningSettings::new));
}
