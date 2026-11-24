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
        NeoForge.EVENT_BUS.addListener(new DevelopmentWindowPlacement()::tick);
        NeoForge.EVENT_BUS.register(new PlanetAtmosphere());
        var hud = new ProgressHud();
        ClientProjection.install(hud::receive);
        NeoForge.EVENT_BUS.addListener(hud::render);
        NeoForge.EVENT_BUS.addListener(hud::onLogout);
        NeoForge.EVENT_BUS.addListener(new DiagnosticCommands()::register);
        var forceKeys = new ForceKeys();
        NeoForge.EVENT_BUS.addListener(new SpeederControls()::tick);
        NeoForge.EVENT_BUS.addListener(new SpeederHud()::render);
        modBus.addListener(SpeederColors::register);
        modBus.addListener(CompositeSaberRenderer::register);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event)->event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(com.legacyoftheforce.lightsaber.SaberContent.KYBER.get(),com.legacyoftheforce.lightsaber.SaberContent.id("crystal_color"),(stack,level,entity,seed)->{
            var crystal=stack.get(com.legacyoftheforce.lightsaber.SaberContent.KYBER_DATA.get());if(crystal==null||crystal.state().isEmpty())return 0;
            return switch(crystal.visibleColor()){case "yellow"->1;case "green"->2;case "orange"->3;case "cyan"->4;case "purple"->5;case "red"->6;case "white"->7;default->0;};
        })));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.GUARDIAN.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.GUARDIAN));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.FROST_LORD.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.FROST_LORD));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.KNIGHT.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.WANDERING_KNIGHT));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.EXILE.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.DESERT_EXILE));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.ICE.get(), ArenaIceRenderer::new);
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.NATURE.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.NATURE_KEEPER));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.ASSASSIN.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.NIGHT_ASSASSIN));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.ILLUSION.get(), ArenaIllusionRenderer::new);
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.PROTECTOR.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.RUINS_PROTECTOR));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.FLAME.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.FLAME_LORD));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.TEMPLE.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.TEMPLE_GUARDIAN));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.RELIC_LORD.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.RELIC_LORD));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.HERMIT.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.FORCE_HERMIT));
            event.registerEntityRenderer(com.legacyoftheforce.bosses.BossContent.ILLUSION_MASTER.get(), context -> new IlumBossRenderer(context, com.legacyoftheforce.bosses.BossKind.ILLUSION_MASTER));
        });
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.legacyoftheforce.speeder.SpeederContent.VEHICLE.get(), SpeederRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(com.legacyoftheforce.speeder.SpeederContent.MENU.get(), SpeederGarageScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event)->event.register(com.legacyoftheforce.speeder.SpeederContent.CARGO_MENU.get(),SpeederCargoScreen::new));
        modBus.addListener(forceKeys::register);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event)->event.register(com.legacyoftheforce.rpg.SkillMenu.TYPE.get(),SkillScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> event.register(com.legacyoftheforce.quests.QuestContent.JOURNAL.get(), QuestJournalScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event)->event.register(com.legacyoftheforce.quests.QuestContent.CONVERSATION.get(),ConversationScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event)->com.legacyoftheforce.quests.QuestContent.people().values().forEach(type->event.registerEntityRenderer(type.get(),AdventurePersonRenderer::new)));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> event.register(com.legacyoftheforce.planets.PlanetContent.TERMINAL_MENU.get(), GalacticTerminalScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(com.legacyoftheforce.lightsaber.SaberContent.MENU.get(), SaberWorkshopScreen::new));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
                net.minecraft.client.renderer.item.ItemProperties.register(com.legacyoftheforce.lightsaber.SaberContent.SABER.get(),
                        com.legacyoftheforce.lightsaber.SaberContent.id("emitter"), (stack, level, entity, seed) -> {
                            var data = stack.get(com.legacyoftheforce.lightsaber.SaberContent.ASSEMBLY.get());
                            return data != null && data.parts().map(parts -> parts.get(com.legacyoftheforce.lightsaber.SaberSlot.EMITTER)
                                    .equals(com.legacyoftheforce.lightsaber.SaberContent.id("emitter_claw"))).orElse(false) ? 1 : 0;
                        })));
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
                net.minecraft.client.renderer.item.ItemProperties.register(com.legacyoftheforce.lightsaber.SaberContent.SABER.get(),
                        com.legacyoftheforce.lightsaber.SaberContent.id("lit"), (stack, level, entity, seed) -> {
                            var data = stack.get(com.legacyoftheforce.lightsaber.SaberContent.ASSEMBLY.get()); return data != null && data.active() ? 1 : 0;
                        })));
        NeoForge.EVENT_BUS.addListener(forceKeys::tick);
    }
}
