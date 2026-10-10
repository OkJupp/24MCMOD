package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Form und Bewegung der Ketten, im JSON unter "chains": { ... }.
 * Jeder Wert ist optional, fehlende Werte nehmen den Standard.
 *
 * @param spacing       Rastergröße in Blöcken – pro Rasterfeld höchstens eine Kette
 * @param chance        Wahrscheinlichkeit (0–1), dass in einem Feld eine Kette hängt
 * @param cellRadius    Wie viele Rasterfelder um dich herum gezeichnet werden
 * @param topY          Y-Höhe, an der die Ketten oben aufgehängt sind
 * @param minLength     Minimale Kettenlänge in Blöcken
 * @param maxLength     Maximale Kettenlänge in Blöcken
 * @param linkLength    Länge eines Kettenglieds in Blöcken
 * @param linkWidth     Breite eines Kettenglieds in Blöcken
 * @param thickness     Dicke des Metalls (Radius) in Blöcken
 * @param lean          Maximale Schräglage einer Kette in Grad (jede Kette zufällig)
 * @param spikes        Dornen pro Seite eines Glieds (0 = glatte Kette)
 * @param topFade       Über wie viele Blöcke die Kette oben aus dem Nichts eingeblendet wird
 * @param swingDegrees  Wie weit die Kette pendelt (Grad)
 * @param swingSpeed    Tempo-Faktor fürs Pendeln (1 = normal)
 * @param wave          Wie stark sich die Kette beim Pendeln biegt (0 = starr)
 * @param seed          Zufalls-Startwert – anderer Wert = andere Kettenverteilung
 */
public record ChainSettings(int spacing, float chance, int cellRadius, float topY,
                            float minLength, float maxLength, float linkLength, float linkWidth,
                            float thickness, float lean, int spikes, float topFade, float swingDegrees, float swingSpeed, float wave, int seed) {

    public static final ChainSettings DEFAULT = new ChainSettings(
            220, 0.55F, 2, 420F,
            90F, 190F, 11.0F, 7.0F,
            1.45F, 14F, 2, 90F, 3.0F, 1.0F, 0.6F, 2424);

    public static final Codec<ChainSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(16, 4096).optionalFieldOf("spacing", DEFAULT.spacing()).forGetter(ChainSettings::spacing),
            Codec.floatRange(0F, 1F).optionalFieldOf("chance", DEFAULT.chance()).forGetter(ChainSettings::chance),
            Codec.intRange(0, 6).optionalFieldOf("cellRadius", DEFAULT.cellRadius()).forGetter(ChainSettings::cellRadius),
            Codec.FLOAT.optionalFieldOf("topY", DEFAULT.topY()).forGetter(ChainSettings::topY),
            Codec.floatRange(4F, 4000F).optionalFieldOf("minLength", DEFAULT.minLength()).forGetter(ChainSettings::minLength),
            Codec.floatRange(4F, 4000F).optionalFieldOf("maxLength", DEFAULT.maxLength()).forGetter(ChainSettings::maxLength),
            Codec.floatRange(0.5F, 128F).optionalFieldOf("linkLength", DEFAULT.linkLength()).forGetter(ChainSettings::linkLength),
            Codec.floatRange(0.3F, 128F).optionalFieldOf("linkWidth", DEFAULT.linkWidth()).forGetter(ChainSettings::linkWidth),
            Codec.floatRange(0.02F, 32F).optionalFieldOf("thickness", DEFAULT.thickness()).forGetter(ChainSettings::thickness),
            Codec.floatRange(0F, 60F).optionalFieldOf("lean", DEFAULT.lean()).forGetter(ChainSettings::lean),
            Codec.intRange(0, 4).optionalFieldOf("spikes", DEFAULT.spikes()).forGetter(ChainSettings::spikes),
            Codec.floatRange(0F, 2000F).optionalFieldOf("topFade", DEFAULT.topFade()).forGetter(ChainSettings::topFade),
            Codec.floatRange(0F, 45F).optionalFieldOf("swingDegrees", DEFAULT.swingDegrees()).forGetter(ChainSettings::swingDegrees),
            Codec.floatRange(0F, 20F).optionalFieldOf("swingSpeed", DEFAULT.swingSpeed()).forGetter(ChainSettings::swingSpeed),
            Codec.floatRange(0F, 5F).optionalFieldOf("wave", DEFAULT.wave()).forGetter(ChainSettings::wave),
            Codec.INT.optionalFieldOf("seed", DEFAULT.seed()).forGetter(ChainSettings::seed)
    ).apply(i, ChainSettings::new));
}
