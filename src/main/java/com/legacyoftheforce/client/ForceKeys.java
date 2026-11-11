package com.legacyoftheforce.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class ForceKeys {
    private static final KeyMapping TOGGLE = new KeyMapping("key.legacyoftheforce.block_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.legacyoftheforce");
    private static final KeyMapping CANCEL = new KeyMapping("key.legacyoftheforce.block_cancel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.legacyoftheforce");
    private static final KeyMapping PUSH = new KeyMapping("key.legacyoftheforce.force_push", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.legacyoftheforce");
    private static final KeyMapping PULL = new KeyMapping("key.legacyoftheforce.force_pull", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.legacyoftheforce");
    public void register(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(CANCEL); event.register(PUSH); event.register(PULL); }
    public void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force toggle");
        }
        while (CANCEL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force cancel");
        }
        while (PUSH.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force push");
        }
        while (PULL.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force pull");
        }
    }
}
