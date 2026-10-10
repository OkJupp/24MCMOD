package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Lebendige Energie in den Ketten, im JSON unter "energy": { ... }.
 * Alles läuft nur über die Zeit – Spieler in der Nähe verändern nichts.
 *
 * @param flow         Stärke der Energie-Stöße, die leuchtend von oben nach unten durch die Kette fließen (0 = aus)
 * @param flowSpeed    Tempo dieser Stöße (1 = normal, etwa 3 Glieder pro Sekunde)
 * @param flowInterval Durchschnittliche Sekunden zwischen zwei Stößen in einer Kette
 * @param shimmer      Wie lebendig die leuchtenden Adern wabern und über die Glieder kriechen (0 = stillstehend)
 * @param arcs         Stärke der elektrischen Bögen, die aus den Ketten zucken (0 = aus)
 * @param arcInterval  Durchschnittliche Sekunden zwischen zwei Bögen an derselben Kette
 * @param arcLength    Länge der Bögen (1 = normal)
 * @param sparks       Funken pro Bogen
 */
public record EnergySettings(float flow, float flowSpeed, float flowInterval, float shimmer,
                             float arcs, float arcInterval, float arcLength, int sparks) {

    public static final EnergySettings DEFAULT = new EnergySettings(1.0F, 1.0F, 6.0F, 1.0F, 1.0F, 9.0F, 1.0F, 7);

    public static final Codec<EnergySettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0F, 3F).optionalFieldOf("flow", DEFAULT.flow()).forGetter(EnergySettings::flow),
            Codec.floatRange(0.05F, 10F).optionalFieldOf("flowSpeed", DEFAULT.flowSpeed()).forGetter(EnergySettings::flowSpeed),
            Codec.floatRange(0.5F, 120F).optionalFieldOf("flowInterval", DEFAULT.flowInterval()).forGetter(EnergySettings::flowInterval),
            Codec.floatRange(0F, 5F).optionalFieldOf("shimmer", DEFAULT.shimmer()).forGetter(EnergySettings::shimmer),
            Codec.floatRange(0F, 3F).optionalFieldOf("arcs", DEFAULT.arcs()).forGetter(EnergySettings::arcs),
            Codec.floatRange(0.5F, 600F).optionalFieldOf("arcInterval", DEFAULT.arcInterval()).forGetter(EnergySettings::arcInterval),
            Codec.floatRange(0.1F, 5F).optionalFieldOf("arcLength", DEFAULT.arcLength()).forGetter(EnergySettings::arcLength),
            Codec.intRange(0, 40).optionalFieldOf("sparks", DEFAULT.sparks()).forGetter(EnergySettings::sparks)
    ).apply(i, EnergySettings::new));
}
