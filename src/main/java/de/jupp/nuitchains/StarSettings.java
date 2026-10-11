package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Einstellungen der funkelnden Sterne, im JSON unter "stars": { ... }.
 *
 * @param count                Anzahl der Sterne (die meisten sind ganz schwach)
 * @param brightness           Helligkeit aller Sterne (1 = normal)
 * @param size                 Größe aller Sterne (1 = normal)
 * @param twinkle              Wie stark die Sterne funkeln (0 = gar nicht, 1 = kräftig)
 * @param twinkleSpeed         Tempo des Funkelns (1 = normal)
 * @param flares               Wie oft einzelne Sterne kurz hell aufblitzen (0 = nie, 1 = normal)
 * @param shootingStarInterval Durchschnittliche Sekunden zwischen zwei Sternschnuppen (0 = keine)
 * @param bigStarPulse         Wie stark die zwei großen hellen Sterne langsam "atmen" (0 = gar nicht)
 * @param seed                 Andere Zahl = andere Verteilung
 */
public record StarSettings(int count, float brightness, float size, float twinkle, float twinkleSpeed,
                           float flares, float shootingStarInterval, float bigStarPulse, int seed) {

    public static final StarSettings DEFAULT = new StarSettings(2200, 1.0F, 1.0F, 0.7F, 1.0F, 1.0F, 24F, 1.0F, 7);

    public static final Codec<StarSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 20000).optionalFieldOf("count", DEFAULT.count()).forGetter(StarSettings::count),
            Codec.floatRange(0F, 5F).optionalFieldOf("brightness", DEFAULT.brightness()).forGetter(StarSettings::brightness),
            Codec.floatRange(0.1F, 5F).optionalFieldOf("size", DEFAULT.size()).forGetter(StarSettings::size),
            Codec.floatRange(0F, 1F).optionalFieldOf("twinkle", DEFAULT.twinkle()).forGetter(StarSettings::twinkle),
            Codec.floatRange(0F, 10F).optionalFieldOf("twinkleSpeed", DEFAULT.twinkleSpeed()).forGetter(StarSettings::twinkleSpeed),
            Codec.floatRange(0F, 10F).optionalFieldOf("flares", DEFAULT.flares()).forGetter(StarSettings::flares),
            Codec.floatRange(0F, 3600F).optionalFieldOf("shootingStarInterval", DEFAULT.shootingStarInterval()).forGetter(StarSettings::shootingStarInterval),
            Codec.floatRange(0F, 3F).optionalFieldOf("bigStarPulse", DEFAULT.bigStarPulse()).forGetter(StarSettings::bigStarPulse),
            Codec.INT.optionalFieldOf("seed", DEFAULT.seed()).forGetter(StarSettings::seed)
    ).apply(i, StarSettings::new));
}
