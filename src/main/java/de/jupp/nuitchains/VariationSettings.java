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
 */
public record VariationSettings(float sizeMin, float sizeMax, float giantChance, float giantSize, float topYVariation) {

    public static final VariationSettings DEFAULT = new VariationSettings(0.55F, 1.3F, 0.12F, 2.4F, 90F);

    public static final Codec<VariationSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.1F, 10F).optionalFieldOf("sizeMin", DEFAULT.sizeMin()).forGetter(VariationSettings::sizeMin),
            Codec.floatRange(0.1F, 10F).optionalFieldOf("sizeMax", DEFAULT.sizeMax()).forGetter(VariationSettings::sizeMax),
            Codec.floatRange(0F, 1F).optionalFieldOf("giantChance", DEFAULT.giantChance()).forGetter(VariationSettings::giantChance),
            Codec.floatRange(0.1F, 20F).optionalFieldOf("giantSize", DEFAULT.giantSize()).forGetter(VariationSettings::giantSize),
            Codec.floatRange(0F, 2000F).optionalFieldOf("topYVariation", DEFAULT.topYVariation()).forGetter(VariationSettings::topYVariation)
    ).apply(i, VariationSettings::new));
}
