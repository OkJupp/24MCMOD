package de.jupp.nuitchains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Aussehen der Glieder, im JSON unter "style": { ... }.
 *
 * @param spikeStyle "studded" = gerade Stacheln mit Kragen (wie Nieten-Dornen), "hooks" = gebogene Widerhaken
 * @param spikeSize  Größe der Stacheln (1 = normal)
 */
public record StyleSettings(String spikeStyle, float spikeSize) {

    public static final StyleSettings DEFAULT = new StyleSettings("studded", 1.0F);

    public static final Codec<StyleSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("spikeStyle", DEFAULT.spikeStyle()).forGetter(StyleSettings::spikeStyle),
            Codec.floatRange(0.1F, 5F).optionalFieldOf("spikeSize", DEFAULT.spikeSize()).forGetter(StyleSettings::spikeSize)
    ).apply(i, StyleSettings::new));

    public boolean hooks() {
        return "hooks".equalsIgnoreCase(this.spikeStyle);
    }
}
