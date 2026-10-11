package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Unterschiede zwischen den Ketten, im JSON unter "variation": { ... }.
 *
 * @param sizeMin       Kleinste Ketten-Größe (1 = Standardgröße aus "chains")
 * @param sizeMax       Größte Ketten-Größe
 * @param giantChance   Anteil riesiger Ketten (0–1), die noch größer als sizeMax sind
 * @param giantSize     Größe dieser Riesen-Ketten
 * @param topYVariation Wie viele Blöcke die Aufhängehöhe nach oben/unten abweichen darf
 * @param minBottomY    Tiefster Punkt, bis zu dem eine Kette herunterhängen darf (Y-Höhe)
 * @param fadeVariation Wie unterschiedlich lang das Einblenden oben ist (0 = alle gleich, 1 = von halb bis anderthalb so lang)
 * @param fadeOffset    Bis zu so vielen Blöcken bleibt eine Kette oben zusätzlich unsichtbar – jede Kette anders,
 *                      dadurch fangen sie unterschiedlich hoch an
 */
public record VariationSettings(float sizeMin, float sizeMax, float giantChance, float giantSize, float topYVariation, float minBottomY,
                                float fadeVariation, float fadeOffset) {

    public static final VariationSettings DEFAULT = new VariationSettings(0.9F, 1.5F, 0.1F, 2.2F, 90F, 240F, 0.8F, 70F);

    public static final Codec<VariationSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.1F, 10F).optionalFieldOf("sizeMin", DEFAULT.sizeMin()).forGetter(VariationSettings::sizeMin),
            Codec.floatRange(0.1F, 10F).optionalFieldOf("sizeMax", DEFAULT.sizeMax()).forGetter(VariationSettings::sizeMax),
            Codec.floatRange(0F, 1F).optionalFieldOf("giantChance", DEFAULT.giantChance()).forGetter(VariationSettings::giantChance),
            Codec.floatRange(0.1F, 20F).optionalFieldOf("giantSize", DEFAULT.giantSize()).forGetter(VariationSettings::giantSize),
            Codec.floatRange(0F, 2000F).optionalFieldOf("topYVariation", DEFAULT.topYVariation()).forGetter(VariationSettings::topYVariation),
            Codec.floatRange(-64F, 4000F).optionalFieldOf("minBottomY", DEFAULT.minBottomY()).forGetter(VariationSettings::minBottomY),
            Codec.floatRange(0F, 1F).optionalFieldOf("fadeVariation", DEFAULT.fadeVariation()).forGetter(VariationSettings::fadeVariation),
            Codec.floatRange(0F, 1000F).optionalFieldOf("fadeOffset", DEFAULT.fadeOffset()).forGetter(VariationSettings::fadeOffset)
    ).apply(i, VariationSettings::new));
}
