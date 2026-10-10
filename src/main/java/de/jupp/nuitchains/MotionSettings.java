package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Bewegung der Ketten, im JSON unter "motion": { ... }.
 * Zusätzlich zum Pendeln aus "chains" (swingDegrees / swingSpeed / wave).
 * Alles läuft nur über die Zeit – Spieler in der Nähe verändern nichts.
 *
 * @param windStrength   Stärke der Windböen in Grad – alle Ketten reagieren gemeinsam darauf
 * @param windSpeed      Wie schnell sich die Böen ändern (1 = normal)
 * @param torsionDegrees Wie weit sich eine Kette langsam um sich selbst dreht (Grad, unten am stärksten)
 * @param torsionSpeed   Tempo dieser Drehung (1 = normal)
 * @param emberPulse     Wie stark die glühenden Bruchkanten pulsieren (0 = gar nicht)
 * @param waveDegrees    Stärke der Welle, die durch die Kette nach unten läuft (Grad, unten am stärksten)
 * @param waveLength     Länge einer Welle in Kettengliedern
 * @param wavePeriod     Sekunden, bis eine neue Welle startet
 */
public record MotionSettings(float windStrength, float windSpeed, float torsionDegrees, float torsionSpeed, float emberPulse,
                             float waveDegrees, float waveLength, float wavePeriod) {

    public static final MotionSettings DEFAULT = new MotionSettings(1.0F, 1.0F, 14F, 1.0F, 0.35F, 3.2F, 16F, 5.5F);

    public static final Codec<MotionSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0F, 45F).optionalFieldOf("windStrength", DEFAULT.windStrength()).forGetter(MotionSettings::windStrength),
            Codec.floatRange(0F, 20F).optionalFieldOf("windSpeed", DEFAULT.windSpeed()).forGetter(MotionSettings::windSpeed),
            Codec.floatRange(0F, 360F).optionalFieldOf("torsionDegrees", DEFAULT.torsionDegrees()).forGetter(MotionSettings::torsionDegrees),
            Codec.floatRange(0F, 20F).optionalFieldOf("torsionSpeed", DEFAULT.torsionSpeed()).forGetter(MotionSettings::torsionSpeed),
            Codec.floatRange(0F, 1F).optionalFieldOf("emberPulse", DEFAULT.emberPulse()).forGetter(MotionSettings::emberPulse),
            Codec.floatRange(0F, 30F).optionalFieldOf("waveDegrees", DEFAULT.waveDegrees()).forGetter(MotionSettings::waveDegrees),
            Codec.floatRange(2F, 500F).optionalFieldOf("waveLength", DEFAULT.waveLength()).forGetter(MotionSettings::waveLength),
            Codec.floatRange(0.5F, 120F).optionalFieldOf("wavePeriod", DEFAULT.wavePeriod()).forGetter(MotionSettings::wavePeriod)
    ).apply(i, MotionSettings::new));
}
