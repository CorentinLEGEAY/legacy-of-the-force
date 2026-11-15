package com.legacyoftheforce.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Reserve the space actually used by native boss bars each frame, including other mods' bars. */
@EventBusSubscriber(modid = "legacyoftheforce", value = Dist.CLIENT)
public final class HudLayout {
    private static int top = 6, progressBottom = 22;
    private HudLayout() {}
    @SubscribeEvent public static void begin(RenderGuiEvent.Pre event) { top = 6; progressBottom = 22; }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void boss(CustomizeGuiOverlayEvent.BossEventProgress event) {
        top = Math.max(top, event.getY() + Math.max(5, event.getIncrement()) + 6);
    }
    static int top() { return top; }
    static void progressBottom(int value) { progressBottom = value; }
    static int vehicleTop() { return Math.max(top + 24, progressBottom + 8); }
}
