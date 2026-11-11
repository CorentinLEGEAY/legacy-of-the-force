package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.network.ClientProjection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;

@Mod(value = LegacyOfTheForce.MOD_ID, dist = Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        var hud = new ProgressHud();
        ClientProjection.install(hud::receive);
        NeoForge.EVENT_BUS.addListener(hud::render);
        NeoForge.EVENT_BUS.addListener(hud::onLogout);
        NeoForge.EVENT_BUS.addListener(new DiagnosticCommands()::register);
        var forceKeys = new ForceKeys();
        modBus.addListener(forceKeys::register);
        NeoForge.EVENT_BUS.addListener(forceKeys::tick);
    }
}
