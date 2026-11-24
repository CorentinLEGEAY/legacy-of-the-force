package com.legacyoftheforce.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class ForceKeys {
    private static final KeyMapping SABER_FORM = new KeyMapping("key.legacyoftheforce.saber_form", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
    private static final KeyMapping SABER_OFFHAND = new KeyMapping("key.legacyoftheforce.saber_offhand", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
    private static final KeyMapping SABER_STYLE=new KeyMapping("key.legacyoftheforce.saber_style",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Y,"key.categories.legacyoftheforce");
    private static final KeyMapping SABER_COUNTER=new KeyMapping("key.legacyoftheforce.saber_counter",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.legacyoftheforce");
    private static final KeyMapping DODGE_BACK=new KeyMapping("key.legacyoftheforce.dodge_back",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_LEFT_ALT,"key.categories.legacyoftheforce");
    private static final KeyMapping DODGE_LEFT=new KeyMapping("key.legacyoftheforce.dodge_left",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.legacyoftheforce");
    private static final KeyMapping DODGE_RIGHT=new KeyMapping("key.legacyoftheforce.dodge_right",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.legacyoftheforce");
    private static final KeyMapping GROUP = new KeyMapping("key.legacyoftheforce.block_group", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
    private static final KeyMapping ROTATE = new KeyMapping("key.legacyoftheforce.block_rotate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
    private static final KeyMapping THROW_BLOCKS = new KeyMapping("key.legacyoftheforce.block_throw", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
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
    private static final KeyMapping VEHICLE_LIFT = new KeyMapping("key.legacyoftheforce.vehicle_lift", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.legacyoftheforce");
    private static final KeyMapping BRAKE = new KeyMapping("key.legacyoftheforce.force_brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.legacyoftheforce");
    public void register(RegisterKeyMappingsEvent event) { event.register(SABER_STYLE);event.register(SABER_FORM);event.register(SABER_OFFHAND);event.register(SABER_COUNTER);event.register(DODGE_BACK);event.register(DODGE_LEFT);event.register(DODGE_RIGHT);event.register(GROUP); event.register(ROTATE); event.register(THROW_BLOCKS); event.register(TOGGLE); event.register(CANCEL); event.register(PUSH); event.register(PULL); event.register(GUARD); event.register(STABILIZE); event.register(BRAKE); event.register(VEHICLE_LIFT); event.register(POWER_NEXT);event.register(POWER_CAST);event.register(SKILLS); }
    public void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        while(SABER_FORM.consumeClick())sendSaber(minecraft,"form");
        while(SABER_OFFHAND.consumeClick())sendSaber(minecraft,"offhand");
        while(SABER_STYLE.consumeClick())sendSaber(minecraft,"style");
        while(SABER_COUNTER.consumeClick())sendSaber(minecraft,"counter");
        while(DODGE_BACK.consumeClick())sendSaber(minecraft,"dodge back");
        while(DODGE_LEFT.consumeClick())sendSaber(minecraft,"dodge left");
        while(DODGE_RIGHT.consumeClick())sendSaber(minecraft,"dodge right");
        while (VEHICLE_LIFT.consumeClick()) send(minecraft, "lift_vehicle");
        while (GROUP.consumeClick()) send(minecraft, "group");
        while (ROTATE.consumeClick()) send(minecraft, "rotate");
        while (THROW_BLOCKS.consumeClick()) send(minecraft, "throw_blocks");
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
    private void send(Minecraft minecraft, String action) {
        if (minecraft.screen == null && minecraft.player != null && minecraft.getConnection() != null) minecraft.getConnection().sendCommand("lotf force " + action);
    }
    private void sendSaber(Minecraft minecraft,String action){if(minecraft.screen==null&&minecraft.player!=null&&minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("lotf saber "+action);}
}
