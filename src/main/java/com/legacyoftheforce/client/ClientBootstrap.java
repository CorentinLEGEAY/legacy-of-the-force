package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(value = LegacyOfTheForce.MOD_ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
