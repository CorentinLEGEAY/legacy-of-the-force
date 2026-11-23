package com.legacyoftheforce.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class ForceKeys {
    private static final KeyMapping POWER_NEXT=new KeyMapping("key.legacyoftheforce.power_next",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.legacyoftheforce");
    private static final KeyMapping POWER_CAST=new KeyMapping("key.legacyoftheforce.power_cast",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_O,"key.categories.legacyoftheforce");
    private static final KeyMapping SKILLS=new KeyMapping("key.legacyoftheforce.skills",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_I,"key.categories.legacyoftheforce");
    private static final String[] POWERS={"sense","leap","speed","reflexes","freeze","levitate","throw","wave","dominate","protect","heal","support","fear","choke","drain","lightning","chain_lightning"};
    private int selectedPower;
    private static final KeyMapping GUARD = new KeyMapping("key.legacyoftheforce.saber_guard", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.legacyoftheforce");
    private static final KeyMapping TOGGLE = new KeyMapping("key.legacyoftheforce.block_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.legacyoftheforce");
    private static final KeyMapping CANCEL = new KeyMapping("key.legacyoftheforce.block_cancel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.legacyoftheforce");
    private static final KeyMapping PUSH = new KeyMapping("key.legacyoftheforce.force_push", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.legacyoftheforce");
    private static final KeyMapping PULL = new KeyMapping("key.legacyoftheforce.force_pull", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.legacyoftheforce");
    private static final KeyMapping STABILIZE = new KeyMapping("key.legacyoftheforce.force_stabilize", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.legacyoftheforce");
    private static final KeyMapping BRAKE = new KeyMapping("key.legacyoftheforce.force_brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.legacyoftheforce");
    public void register(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(CANCEL); event.register(PUSH); event.register(PULL); event.register(GUARD); event.register(STABILIZE); event.register(BRAKE); event.register(POWER_NEXT);event.register(POWER_CAST);event.register(SKILLS); }
    public void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while(SKILLS.consumeClick())if(minecraft.screen==null&&minecraft.player!=null&&minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("lotf skill menu");
        while(POWER_NEXT.consumeClick())if(minecraft.screen==null&&minecraft.player!=null) {selectedPower=(selectedPower+1)%POWERS.length;minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("power.legacyoftheforce.selected",net.minecraft.network.chat.Component.translatable("power.legacyoftheforce."+POWERS[selectedPower])),true);}
        while(POWER_CAST.consumeClick())if(minecraft.screen==null&&minecraft.player!=null&&minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("lotf force power "+POWERS[selectedPower]);
        while (STABILIZE.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force stabilize");
        }
        while (BRAKE.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force brake");
        }
        while (GUARD.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf saber guard");
        }
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
