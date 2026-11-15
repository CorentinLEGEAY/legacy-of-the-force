package com.legacyoftheforce.core;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Reopens the real owner's native save loader on a disposable copy, after a newer durable barrier. */
@EventBusSubscriber(modid = "legacyoftheforce")
public final class SoloOwnerSaveValidation {
    private static final Set<MinecraftServer> CHECKED = Collections.newSetFromMap(new IdentityHashMap<>());

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("lotf.validation.soloOwnerSave")) return;
        var server = event.getServer();
        if (CHECKED.contains(server)) return;
        var actor = server.getPlayerList().getPlayerByName("ForceTesterSolo");
        if (actor == null) return;
        var mode = System.getProperty("lotf.validation.soloOwnerSave.mode", "Load");
        if (mode.equals("KillTravel") && !com.legacyoftheforce.planets.SoloTravelSaveValidation.ready(actor)) return;
        CHECKED.add(server);
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if (!root.getFileName().toString().equals("TravelValidation")
                    || !root.getParent().getParent().getFileName().toString().startsWith("run-travel-client-solo-")
                    || server.isDedicatedServer() || !server.isSingleplayerOwner(actor.getGameProfile())
                    || actor.getInventory().countItem(Items.DIAMOND) != (mode.startsWith("Recover") ? 9 : 7)) throw new IllegalStateException("Stopped solo fixture copy required");
            var proof = new CompoundTag(); proof.putInt("schemaVersion", 1);
            proof.putString("mode", mode); proof.putLong("pid", ProcessHandle.current().pid());
            proof.put("before", actor.saveWithoutId(new CompoundTag()));
            proof.put("embedded", server.getWorldData().getLoadedPlayerTag().copy());
            proof.put("levelFile", AtomicNbtFile.read(server.getWorldPath(LevelResource.LEVEL_DATA_FILE), 16 * 1024 * 1024));
            if (mode.startsWith("Recover")) {
                if (mode.equals("RecoverTravel")) com.legacyoftheforce.planets.SoloTravelSaveValidation.recovered(actor, root, proof);
                proof.putBoolean("passed", actor.getInventory().countItem(Items.DIAMOND) == 9);
                AtomicNbtFile.write(root.resolve("validation/solo-owner-save.nbt"), proof, 1048576);
                LegacyOfTheForce.LOGGER.info("SOLO_OWNER_SAVE_RECOVER_PASS"); return;
            }
            actor.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 9));
            if (mode.equals("KillTravel")) { com.legacyoftheforce.planets.SoloTravelSaveValidation.pause(actor, root, proof); return; }
            proof.put("durable", new VerifiedPlayerSaveBarrier().persist(actor));
            if (mode.equals("Kill")) {
                AtomicNbtFile.write(root.resolve("validation/solo-owner-checkpoint.nbt"), proof, 1048576);
                LegacyOfTheForce.LOGGER.info("SOLO_OWNER_SAVE_PAUSED_AFTER_BARRIER");
                new java.util.concurrent.CountDownLatch(1).await(); return;
            }
            // Exact vanilla owner loader, not an approximation of its priority rule.
            var selected = server.getPlayerList().load(actor).orElseThrow();
            proof.put("selected", selected.copy()); proof.put("loaded", actor.saveWithoutId(new CompoundTag()));
            proof.putBoolean("passed", actor.getInventory().countItem(Items.DIAMOND) == 9);
            AtomicNbtFile.write(root.resolve("validation/solo-owner-save.nbt"), proof, 1048576);
            LegacyOfTheForce.LOGGER.info("SOLO_OWNER_SAVE_RESULT passed={} diamonds={}", proof.getBoolean("passed"), actor.getInventory().countItem(Items.DIAMOND));
        } catch (Exception failure) {
            LegacyOfTheForce.LOGGER.error("SOLO_OWNER_SAVE_FAILED", failure);
        }
    }
}
