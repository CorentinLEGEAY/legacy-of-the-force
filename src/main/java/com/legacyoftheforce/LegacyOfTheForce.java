package com.legacyoftheforce;

import com.mojang.logging.LogUtils;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.network.ModNetwork;
import com.legacyoftheforce.rpg.ProgressAttachments;
import com.legacyoftheforce.rpg.ProgressEvents;
import com.legacyoftheforce.rpg.ProgressCommands;
import com.legacyoftheforce.rpg.CombatRewards;
import com.legacyoftheforce.rpg.ProgressService;
import com.legacyoftheforce.content.TransactionContent;
import com.legacyoftheforce.content.WorldTransactionAnchors;
import com.legacyoftheforce.core.operation.VerifiedChunkSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import com.legacyoftheforce.force.ForceConfig;
import com.legacyoftheforce.force.ForceModule;
import com.legacyoftheforce.force.ForceAttachments;
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
        container.registerConfig(ModConfig.Type.SERVER, ForceConfig.SPEC, "legacyoftheforce-force.toml");
        TransactionContent.register(modBus);
        NeoForge.EVENT_BUS.register(new TransactionContent());
        NeoForge.EVENT_BUS.register(new ForceModule(new ProgressService(), new VanillaProtectedWorldService(),
                new WorldTransactionAnchors(), new VerifiedChunkSaveBarrier()));
        ProgressAttachments.TYPES.register(modBus);
        ForceAttachments.TYPES.register(modBus);
        var progressEvents = new ProgressEvents();
        NeoForge.EVENT_BUS.addListener(new ProgressCommands()::register);
        NeoForge.EVENT_BUS.register(new CombatRewards());
        NeoForge.EVENT_BUS.addListener(progressEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(progressEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(progressEvents::onDimension);
        NeoForge.EVENT_BUS.addListener(progressEvents::onTick);
        var network = new ModNetwork();
        modBus.addListener(network::register);
        NeoForge.EVENT_BUS.addListener(network::onLogout);
        NeoForge.EVENT_BUS.addListener(network::onStop);
        LOGGER.info("Legacy of the Force: infrastructure loaded (Minecraft 1.21.1)");
    }
}
