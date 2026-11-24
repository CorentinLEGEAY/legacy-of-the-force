package com.legacyoftheforce;

import com.mojang.logging.LogUtils;
import com.legacyoftheforce.core.config.ServerConfig;
import com.legacyoftheforce.core.network.ModNetwork;
import com.legacyoftheforce.rpg.ProgressAttachments;
import com.legacyoftheforce.rpg.ProgressEvents;
import com.legacyoftheforce.rpg.ProgressCommands;
import com.legacyoftheforce.rpg.CombatRewards;
import com.legacyoftheforce.rpg.ProgressService;
import com.legacyoftheforce.rpg.RewardLifecycle;
import com.legacyoftheforce.lightsaber.SaberContent;
import com.legacyoftheforce.lightsaber.SaberCatalog;
import com.legacyoftheforce.lightsaber.SaberConfig;
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
        com.legacyoftheforce.core.operation.PlayerSaveCheckpoint.register(modBus);
        SaberContent.register(modBus);
        com.legacyoftheforce.bosses.BossContent.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, com.legacyoftheforce.bosses.BossConfig.SPEC, "legacyoftheforce-bosses.toml");
        var bosses = new com.legacyoftheforce.bosses.BossLifecycle(new ProgressService());
        NeoForge.EVENT_BUS.register(bosses);
        com.legacyoftheforce.core.api.WorldProtectionHooks.register(bosses);
        com.legacyoftheforce.planets.PlanetContent.register(modBus);
        com.legacyoftheforce.quests.QuestContent.register(modBus);
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.quests.QuestLifecycle());
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.quests.QuestCatalog::register);
        container.registerConfig(ModConfig.Type.SERVER, com.legacyoftheforce.planets.TravelConfig.SPEC, "legacyoftheforce-travel.toml");
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.planets.TravelLifecycle());
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.planets.PlanetEnvironment());
        com.legacyoftheforce.core.api.WorldProtectionHooks.register(new com.legacyoftheforce.planets.RuneProtectionPolicy());
        com.legacyoftheforce.speeder.SpeederContent.register(modBus);
        com.legacyoftheforce.content.PlanetaryMaterials.register(modBus);
        com.legacyoftheforce.core.api.PlayerProgressAccess.install(new ProgressService()::get);
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.speeder.SpeederAssemblyLifecycle());
        var speederNetwork = new com.legacyoftheforce.speeder.SpeederNetwork();
        modBus.addListener(speederNetwork::register);
        NeoForge.EVENT_BUS.addListener(speederNetwork::logout);
        NeoForge.EVENT_BUS.addListener(speederNetwork::stop);
        modBus.addListener(com.legacyoftheforce.speeder.SpeederContent::creative);
        container.registerConfig(ModConfig.Type.SERVER, com.legacyoftheforce.speeder.SpeederConfig.SPEC, "legacyoftheforce-speeder.toml");
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.speeder.SpeederCatalog::register);
        modBus.addListener(SaberContent::creative);
        container.registerConfig(ModConfig.Type.SERVER, SaberConfig.SPEC, "legacyoftheforce-saber.toml");
        NeoForge.EVENT_BUS.addListener(SaberCatalog::register);
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.lightsaber.SaberTypeCatalog::register);
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.lightsaber.KyberAffinities::register);
        com.legacyoftheforce.core.api.CrystalAffinityAccess.install(com.legacyoftheforce.lightsaber.KyberAffinities::read);
        com.legacyoftheforce.core.api.PlanetaryCrystals.install(com.legacyoftheforce.lightsaber.PlanetaryCrystalLoot::create);
        com.legacyoftheforce.core.api.AdventureHooks.installReader(actor->new com.legacyoftheforce.core.api.AdventureHooks.Evidence(SaberContent.knownHolocrons(actor),new ProgressService().rewardedBosses(actor.getUUID())));
        com.legacyoftheforce.core.api.AdventureHooks.installGuides(com.legacyoftheforce.quests.QuestLifecycle::readGuide);
        com.legacyoftheforce.core.api.CombatHolocrons.install(SaberContent::holocron);
        com.legacyoftheforce.core.api.SaberDesigns.install(type -> {
            var stack = new net.minecraft.world.item.ItemStack(SaberContent.BLUEPRINT.get());
            stack.set(SaberContent.BLUEPRINT_DATA.get(), com.legacyoftheforce.lightsaber.SaberBlueprintData.create(
                    SaberContent.defaultDesign(com.legacyoftheforce.lightsaber.SaberType.parse(type).orElseThrow())));
            return stack;
        });
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.rpg.PowerCatalog::register);
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.lightsaber.SaberCombat());
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.lightsaber.SaberItemTransfers());
        com.legacyoftheforce.core.api.EntityMotionHooks.register(com.legacyoftheforce.lightsaber.SaberItemTransfers::movementReady);
        com.legacyoftheforce.core.api.PlayerInventoryHooks.register(new com.legacyoftheforce.lightsaber.SaberInventoryPolicy());
        NeoForge.EVENT_BUS.register(com.legacyoftheforce.lightsaber.SaberMenuState.listener());
        var saberStorage=new com.legacyoftheforce.lightsaber.SaberStorageTransactions();
        NeoForge.EVENT_BUS.register(saberStorage);
        com.legacyoftheforce.core.api.WorldProtectionHooks.register(saberStorage);
        NeoForge.EVENT_BUS.register(new com.legacyoftheforce.lightsaber.KyberBondService());
        NeoForge.EVENT_BUS.register(new TransactionContent());
        NeoForge.EVENT_BUS.register(new ForceModule(new ProgressService(), new VanillaProtectedWorldService(),
                new WorldTransactionAnchors(), new VerifiedChunkSaveBarrier()));
        ProgressAttachments.TYPES.register(modBus);
        com.legacyoftheforce.rpg.SkillMenu.register(modBus);
        NeoForge.EVENT_BUS.addListener(com.legacyoftheforce.rpg.SkillMenu::commands);
        ForceAttachments.TYPES.register(modBus);
        var progressEvents = new ProgressEvents();
        NeoForge.EVENT_BUS.register(new RewardLifecycle());
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
