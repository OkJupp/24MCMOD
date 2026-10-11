package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Einstellungen des Tag-Himmels, im JSON unter "atmosphere": { ... }.
 *
 * @param dayOpacity Wie stark der helle Tag-Himmel die Galaxie überdeckt (1 = ganz, 0.9 = Galaxie schimmert leicht durch)
 * @param twilight   Stärke von Morgen- und Abendrot (0 = aus, 1 = normal)
 * @param sunGlow    Stärke des Lichthofs um die Sonne (0 = aus)
 * @param nightGlow  Pinkes Leuchten am Horizont in der Nacht (0 = aus)
 */
public record AtmosphereSettings(float dayOpacity, float twilight, float sunGlow, float nightGlow) {

    public static final AtmosphereSettings DEFAULT = new AtmosphereSettings(0.9F, 1.0F, 1.0F, 0.35F);

    public static final Codec<AtmosphereSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0F, 1F).optionalFieldOf("dayOpacity", DEFAULT.dayOpacity()).forGetter(AtmosphereSettings::dayOpacity),
            Codec.floatRange(0F, 3F).optionalFieldOf("twilight", DEFAULT.twilight()).forGetter(AtmosphereSettings::twilight),
            Codec.floatRange(0F, 3F).optionalFieldOf("sunGlow", DEFAULT.sunGlow()).forGetter(AtmosphereSettings::sunGlow),
            Codec.floatRange(0F, 3F).optionalFieldOf("nightGlow", DEFAULT.nightGlow()).forGetter(AtmosphereSettings::nightGlow)
    ).apply(i, AtmosphereSettings::new));
}
