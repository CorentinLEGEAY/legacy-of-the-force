package com.legacyoftheforce;

import com.mojang.logging.LogUtils;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LegacyOfTheForce.MOD_ID)
public final class LegacyOfTheForce {
    public static final String MOD_ID = "legacyoftheforce";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LegacyOfTheForce(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        var network = new ModNetwork();
        modBus.addListener(network::register);
        NeoForge.EVENT_BUS.addListener(network::onLogout);
        NeoForge.EVENT_BUS.addListener(network::onStop);
        LOGGER.info("Legacy of the Force: infrastructure loaded (Minecraft 1.21.1)");
    }
}
