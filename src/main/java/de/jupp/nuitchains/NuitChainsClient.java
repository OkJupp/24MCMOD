package de.jupp.nuitchains;

import me.flashyreese.mods.nuit.api.NuitApi;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt des Mods. Registriert eigene Nuit-Skybox-Typen,
 * die dann ganz normal per JSON im Ressourcenpaket benutzt werden.
 * <p>
 * Neue Effekte: Klasse wie {@link ChainSkybox} anlegen und hier registrieren.
 */
public class NuitChainsClient implements ClientModInitializer {
    public static final String MOD_ID = "nuit_chains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        // JSON: "type": "nuit_chains:chains"
        NuitApi.registerSkyboxType(id("chains"), 1, ChainSkybox.CODEC);
        LOGGER.info("Nuit Chains geladen – Skybox-Typ nuit_chains:chains registriert");
    }
}
