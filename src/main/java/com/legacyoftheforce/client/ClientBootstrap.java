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
        NeoForge.EVENT_BUS.addListener(new SpeederControls()::tick);
        NeoForge.EVENT_BUS.addListener(new SpeederHud()::render);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(com.legacyoftheforce.speeder.SpeederContent.VEHICLE.get(), SpeederRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(com.legacyoftheforce.speeder.SpeederContent.MENU.get(), SpeederGarageScreen::new));
        modBus.addListener(forceKeys::register);
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
