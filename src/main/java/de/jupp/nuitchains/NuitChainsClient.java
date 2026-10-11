package de.jupp.nuitchains;

import de.jupp.nuitchains.compat.DistantHorizonsCompat;
import me.flashyreese.mods.nuit.api.NuitApi;
import me.flashyreese.mods.nuit.api.skyboxes.Skybox;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt des Mods. Registriert eigene Nuit-Skybox-Typen,
 * die dann ganz normal per JSON (assets/nuit_chains/sky/*.json) benutzt werden.
 * <p>
 * Neue Effekte: Klasse wie {@link ChainSkybox} anlegen und hier registrieren.
 */
public class NuitChainsClient implements ClientModInitializer {
    public static final String MOD_ID = "nuit_chains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean renderErrorLogged;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        // JSON: "type": "nuit_chains:chains"
        NuitApi.registerSkyboxType(id("chains"), 1, ChainSkybox.CODEC);
        // JSON: "type": "nuit_chains:lightning"
        NuitApi.registerSkyboxType(id("lightning"), 1, LightningSkybox.CODEC);
        // JSON: "type": "nuit_chains:stars"
        NuitApi.registerSkyboxType(id("stars"), 1, StarsSkybox.CODEC);

        // Vanilla-Wolken ausblenden, solange eine Ketten-Skybox aktiv ist ("hideClouds")
        // (gilt auch für die eigenen LOD-Wolken von Distant Horizons, falls installiert)
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            boolean hide = false;
            for (Skybox skybox : NuitApi.getInstance().getActiveSkyboxes()) {
                if (skybox instanceof ChainSkybox chains && chains.isHideClouds()) {
                    hide = true;
                    break;
                }
            }
            if (hide) {
                context.levelState().cloudColor = 0;
            }
            DistantHorizonsCompat.setCloudsHidden(hide);
        });

        // Nahe Kettenteile als echte Weltgeometrie einreichen, ferne für den Himmel vorbereiten
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            try {
                LevelRenderState state = context.levelState();
                CameraRenderState camera = state.cameraRenderState;
                double timeSec = (state.gameTime + (double) state.worldPartialTicks) / 20.0;
                for (Skybox skybox : NuitApi.getInstance().getActiveSkyboxes()) {
                    if (skybox instanceof ChainSkybox chains) {
                        chains.prepareFrame(context.submitNodeCollector(), context.poseStack(), camera.pos, timeSec, camera.depthFar);
                    }
                }
            } catch (RuntimeException e) {
                if (!renderErrorLogged) {
                    renderErrorLogged = true;
                    LOGGER.error("Nuit Chains: Fehler beim Zeichnen der Ketten", e);
                }
            }
        });

        // Pinkes HUD als eingebautes Ressourcenpaket (standardmäßig an, unter Optionen → Ressourcenpakete abschaltbar)
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container ->
                ResourceLoader.registerBuiltinPack(id("pink_hud"), container,
                        Component.literal("Pinkes HUD (Nuit Chains)"), PackActivationType.DEFAULT_ENABLED));

        LOGGER.info("Nuit Chains geladen – Skybox-Typ nuit_chains:chains registriert");
    }
}
