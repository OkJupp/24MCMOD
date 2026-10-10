package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Wie kaputt die Ketten sind, im JSON unter "damage": { ... }.
 *
 * @param brokenChance  Anteil der Ketten, deren Ende zerfällt (0–1)
 * @param brokenLinks   Über wie viele Glieder sich der Zerfall am Ende erstreckt
 * @param wear          Anteil der Glieder mit Löchern/Abplatzern mitten in der Kette (0–1)
 * @param wearStrength  Wie groß diese Löcher werden (0–1)
 * @param fragments     Anzahl Bruchstücke, die unter einer zerfallenden Kette schweben
 * @param fragmentFall  Wie weit die Bruchstücke nach unten treiben, bevor sie verschwinden (Blöcke)
 */
public record DamageSettings(float brokenChance, int brokenLinks, float wear, float wearStrength,
                             int fragments, float fragmentFall) {

    public static final DamageSettings DEFAULT = new DamageSettings(0.7F, 6, 0.18F, 0.4F, 14, 45F);

    public static final Codec<DamageSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0F, 1F).optionalFieldOf("brokenChance", DEFAULT.brokenChance()).forGetter(DamageSettings::brokenChance),
            Codec.intRange(1, 64).optionalFieldOf("brokenLinks", DEFAULT.brokenLinks()).forGetter(DamageSettings::brokenLinks),
            Codec.floatRange(0F, 1F).optionalFieldOf("wear", DEFAULT.wear()).forGetter(DamageSettings::wear),
            Codec.floatRange(0F, 1F).optionalFieldOf("wearStrength", DEFAULT.wearStrength()).forGetter(DamageSettings::wearStrength),
            Codec.intRange(0, 128).optionalFieldOf("fragments", DEFAULT.fragments()).forGetter(DamageSettings::fragments),
            Codec.floatRange(0F, 500F).optionalFieldOf("fragmentFall", DEFAULT.fragmentFall()).forGetter(DamageSettings::fragmentFall)
    ).apply(i, DamageSettings::new));
}
