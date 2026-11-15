package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.planets.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native-client protocol test driver: uses real menu packets and read-only synchronized projections. */
@EventBusSubscriber(modid = "legacyoftheforce", value = Dist.CLIENT)
public final class TravelNetworkClientDriver {
    private static String stage = "START", lastScreen = ""; private static long ticks, after; private static boolean connected, backedUp; private static final long STARTED = System.nanoTime();
    private static final ListTag STEPS = new ListTag();
    private static long previousClientPid;
    private static java.util.UUID resumeOwner;
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (Boolean.getBoolean("lotf.validation.soloOwnerSave")) {
            var mc = Minecraft.getInstance();
            if (Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves/TravelValidation/validation/solo-owner-save.nbt"))) {
                if (System.getProperty("lotf.validation.soloOwnerSave.mode", "").equals("RecoverTravel")) {
                    if (mc.player == null || mc.level == null || !mc.level.dimension().equals(PlanetContent.ILUM)) return;
                    try {
                        var ack = new CompoundTag(); ack.putInt("schemaVersion", 1); ack.putLong("pid", ProcessHandle.current().pid());
                        ack.putUUID("owner", mc.player.getUUID()); ack.putString("dimension", mc.level.dimension().location().toString());
                        AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/solo-travel-client.nbt"), ack, 4096);
                        LegacyOfTheForce.LOGGER.info("SOLO_TRAVEL_CLIENT_ILUM_ACK");
                    } catch (Exception failure) { throw new IllegalStateException("Cannot persist native solo travel acknowledgment", failure); }
                }
                mc.stop();
            }
            return;
        }
        if (!Boolean.getBoolean("lotf.validation.travelNetwork")) return; var mc = Minecraft.getInstance(); ticks++;
        if (stage.equals("FAILED") || stage.equals("PASS")) { if (mc.getConnection() == null || Boolean.getBoolean("lotf.validation.travelSolo")) mc.stop(); return; }
        try {
            if (Boolean.getBoolean("lotf.validation.travelResume") && stage.equals("START")) {
                var resume = AtomicNbtFile.read(mc.gameDirectory.toPath().resolve("validation/travel-network-resume.nbt"), 65536);
                require(resume.getInt("schemaVersion") == 1 && resume.getString("name").equals("ForceTesterOne"), "Known disconnected client prefix required");
                STEPS.addAll(resume.getList("steps", Tag.TAG_COMPOUND)); require(STEPS.size() == 3, "Three verified prefix stages required");
                ticks = STEPS.getCompound(2).getLong("tick") + 1; previousClientPid = resume.getLong("clientPid"); stage = "ILUM";
                resumeOwner = resume.getUUID("owner");
            }
            require(System.nanoTime() - STARTED < 240_000_000_000L, "Native network client timed out in " + stage);
            var screen = mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
            if (!screen.equals(lastScreen)) { lastScreen = screen; LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_CLIENT_SCREEN stage={} screen={} title={}", stage, screen, mc.screen == null ? "" : mc.screen.getTitle().getString()); }
            if (!backedUp && Boolean.getBoolean("lotf.validation.travelSolo") && mc.screen instanceof BackupConfirmScreen confirm
                    && mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().startsWith("run-travel-client-solo-")
                    && (confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.experimental").getString())
                        || confirm.getTitle().getString().equals(Component.translatable("selectWorld.backupQuestion.customized").getString()))) {
                var label = Component.translatable("selectWorld.backupJoinConfirmButton").getString();
                var button = confirm.children().stream().filter(widget -> widget instanceof Button b && b.getMessage().getString().equals(label)).map(widget -> (Button)widget).findFirst().orElseThrow();
                backedUp = true; button.onPress(); LegacyOfTheForce.LOGGER.info("TRAVEL_SOLO_NATIVE_BACKUP_AND_LOAD"); return;
            }
            if (connected && mc.getConnection() == null) throw new IllegalStateException("Connection closed before client proof in " + stage);
            if (mc.player != null && mc.getConnection() != null) connected = true;
            if (mc.player != null && resumeOwner != null) require(mc.player.getUUID().equals(resumeOwner), "Reconnected native identity must match the saved prefix");
            if (mc.player == null || mc.level == null || mc.gameMode == null || !(mc.player.containerMenu instanceof GalacticTerminalMenu menu)) return;
            if (stage.equals("START")) {
                require(!menu.known() && menu.atHome(), "Fresh synchronized home menu expected"); inventory(mc, 1, 2, 1); capture(mc, menu, "initial");
                mc.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, 99));
                mc.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, -1));
                mc.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId + 40, 2));
                stage = "INVALID"; after = ticks + 40; return;
            }
            if (stage.equals("INVALID") && ticks >= after) {
                require(!menu.known(), "Invalid/stale menu packets must not discover or charge"); inventory(mc, 1, 2, 1); capture(mc, menu, "invalid_ignored");
                click(mc, menu, 2); click(mc, menu, 2); stage = "DISCOVER"; return;
            }
            if (stage.equals("DISCOVER") && menu.known() && count(mc, PlanetContent.COORDINATES.get()) == 0) {
                inventory(mc, 0, 2, 1); require(menu.cost() == 2, "Projected first departure must cost two cells"); capture(mc, menu, "discovery_ack");
                stage = "OUTBOUND_WAIT"; after = ticks + 100; return;
            }
            if (stage.equals("OUTBOUND_WAIT") && ticks >= after) {
                if (Boolean.getBoolean("lotf.validation.travelDrop") && mc.player.getGameProfile().getName().equals("ForceTesterOne")) {
                    var resume = new CompoundTag(); resume.putInt("schemaVersion", 1); resume.putString("name", "ForceTesterOne");
                    resume.putUUID("owner", mc.player.getUUID()); resume.putLong("clientPid", ProcessHandle.current().pid()); resume.put("steps", STEPS.copy());
                    AtomicNbtFile.write(mc.gameDirectory.toPath().resolve("validation/travel-network-resume.nbt"), resume, 65536);
                }
                click(mc, menu, 0); click(mc, menu, 0); stage = "ILUM"; return;
            }
            if (stage.equals("ILUM") && mc.level.dimension().equals(PlanetContent.ILUM) && menu.atIlum() && menu.credit()) {
                if (!Boolean.getBoolean("lotf.validation.travelSolo") && mc.level.players().size() < 2) return;
                inventory(mc, 0, 0, 0); capture(mc, menu, "arrival_ack"); stage = "RETURN_WAIT"; after = ticks + 40; return;
            }
            if (stage.equals("RETURN_WAIT") && ticks >= after) { click(mc, menu, 1); click(mc, menu, 1); stage = "HOME"; return; }
            if (stage.equals("HOME") && mc.level.dimension().equals(Level.OVERWORLD) && menu.atHome() && menu.known() && !menu.credit()) {
                if (!Boolean.getBoolean("lotf.validation.travelSolo") && mc.level.players().size() < 2) return;
                inventory(mc, 0, 0, 0); capture(mc, menu, "home_ack"); stage = "PASS"; write(mc, true, "");
                LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_CLIENT_PASS name={}", mc.player.getGameProfile().getName());
            }
        } catch (Exception failure) {
            stage = "FAILED"; LegacyOfTheForce.LOGGER.error("TRAVEL_NETWORK_CLIENT_FAILED", failure);
            try { write(mc, false, failure.toString()); } catch (Exception writeFailure) { LegacyOfTheForce.LOGGER.error("Could not write native network failure", writeFailure); }
        }
    }
    private static void click(Minecraft mc, GalacticTerminalMenu menu, int button) { mc.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    private static int count(Minecraft mc, Item item) { return mc.player.getInventory().countItem(item); }
    private static Item cells() { return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell")); }
    private static void inventory(Minecraft mc, int charts, int cells, int terminals) {
        require(count(mc, PlanetContent.COORDINATES.get()) == charts && count(mc, cells()) == cells
                && count(mc, PlanetContent.TERMINAL_ITEM.get()) == terminals && count(mc, Items.DIAMOND) == 7, "Synchronized inventory mismatch at " + stage);
    }
    private static void capture(Minecraft mc, GalacticTerminalMenu menu, String label) {
        var step = new CompoundTag(); step.putString("label", label); step.putLong("tick", ticks); step.putString("dimension", mc.level.dimension().location().toString());
        step.putBoolean("known", menu.known()); step.putBoolean("credit", menu.credit()); step.putInt("cost", menu.cost()); step.putInt("playersSeen", mc.level.players().size());
        step.putInt("charts", count(mc, PlanetContent.COORDINATES.get())); step.putInt("cells", count(mc, cells())); step.putInt("terminals", count(mc, PlanetContent.TERMINAL_ITEM.get())); step.putInt("diamonds", count(mc, Items.DIAMOND)); STEPS.add(step);
        LegacyOfTheForce.LOGGER.info("TRAVEL_NETWORK_CLIENT_STAGE name={} stage={}", mc.player.getGameProfile().getName(), label);
    }
    private static void write(Minecraft mc, boolean passed, String failure) throws Exception {
        var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize(); require(directory.getFileName().toString().startsWith("run-travel-client-"), "Isolated native client profile required");
        var proof = new CompoundTag(); proof.putInt("schemaVersion", 1); proof.putBoolean("passed", passed); proof.putString("stage", stage); proof.putString("failure", failure); proof.put("steps", STEPS.copy());
        proof.putLong("clientPid", ProcessHandle.current().pid());
        if (previousClientPid != 0) proof.putLong("previousClientPid", previousClientPid);
        if (mc.player != null) { proof.putUUID("owner", mc.player.getUUID()); proof.putString("name", mc.player.getGameProfile().getName()); }
        AtomicNbtFile.write(directory.resolve("validation/travel-network-client.nbt"), proof, 65536);
    }
}
