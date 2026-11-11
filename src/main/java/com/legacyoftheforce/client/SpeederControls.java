package com.legacyoftheforce.client;

import com.legacyoftheforce.speeder.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpeederControls {
    private ClientPacketListener connection;
    private long sequence;
    private int tick;
    private boolean forwardTap, backTap, leftTap, rightTap;
    private static boolean tapped(KeyMapping key) { boolean tapped = false; while (key.consumeClick()) tapped = true; return tapped; }
    public void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        boolean forward = tapped(minecraft.options.keyUp), back = tapped(minecraft.options.keyDown);
        boolean left = tapped(minecraft.options.keyLeft), right = tapped(minecraft.options.keyRight);
        if (connection != minecraft.getConnection()) { connection = minecraft.getConnection(); sequence = 0; tick = 0; }
        if (connection == null || minecraft.player == null || !(minecraft.player.getVehicle() instanceof SpeederEntity vehicle) || minecraft.screen != null) {
            forwardTap = false; backTap = false; leftTap = false; rightTap = false; tick = 0; return;
        }
        forwardTap |= forward; backTap |= back; leftTap |= left; rightTap |= right;
        if (++tick % 2 != 0 || sequence == Long.MAX_VALUE) return;
        float throttle = 0, steering = 0;
        if (minecraft.screen == null) {
            throttle = (minecraft.options.keyUp.isDown() || forwardTap ? 1 : 0) - (minecraft.options.keyDown.isDown() || backTap ? 1 : 0);
            steering = (minecraft.options.keyLeft.isDown() || leftTap ? 1 : 0) - (minecraft.options.keyRight.isDown() || rightTap ? 1 : 0);
        }
        PacketDistributor.sendToServer(new SpeederInput(vehicle.getId(), sequence++, throttle, steering));
        forwardTap = false; backTap = false; leftTap = false; rightTap = false;
    }
}
