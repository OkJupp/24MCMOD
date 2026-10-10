package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Bewegung der Ketten, im JSON unter "motion": { ... }.
 * Zusätzlich zum Pendeln aus "chains" (swingDegrees / swingSpeed / wave).
 *
 * @param windStrength   Stärke der Windböen in Grad – alle Ketten reagieren gemeinsam darauf
 * @param windSpeed      Wie schnell sich die Böen ändern (1 = normal)
 * @param torsionDegrees Wie weit sich eine Kette langsam um sich selbst dreht (Grad, unten am stärksten)
 * @param torsionSpeed   Tempo dieser Drehung (1 = normal)
 * @param emberPulse     Wie stark die glühenden Bruchkanten pulsieren (0 = gar nicht)
 */
public record MotionSettings(float windStrength, float windSpeed, float torsionDegrees, float torsionSpeed, float emberPulse) {

    public static final MotionSettings DEFAULT = new MotionSettings(3.5F, 1.0F, 28F, 1.0F, 0.35F);

    public static final Codec<MotionSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0F, 45F).optionalFieldOf("windStrength", DEFAULT.windStrength()).forGetter(MotionSettings::windStrength),
            Codec.floatRange(0F, 20F).optionalFieldOf("windSpeed", DEFAULT.windSpeed()).forGetter(MotionSettings::windSpeed),
            Codec.floatRange(0F, 360F).optionalFieldOf("torsionDegrees", DEFAULT.torsionDegrees()).forGetter(MotionSettings::torsionDegrees),
            Codec.floatRange(0F, 20F).optionalFieldOf("torsionSpeed", DEFAULT.torsionSpeed()).forGetter(MotionSettings::torsionSpeed),
            Codec.floatRange(0F, 1F).optionalFieldOf("emberPulse", DEFAULT.emberPulse()).forGetter(MotionSettings::emberPulse)
    ).apply(i, MotionSettings::new));
}
