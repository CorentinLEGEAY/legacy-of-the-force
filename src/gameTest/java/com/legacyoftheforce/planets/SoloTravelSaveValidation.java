package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.AtomicNbtFile;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Native solo crash fixture; package access to the real travel checkpoint, never shipped. */
public final class SoloTravelSaveValidation {
    private SoloTravelSaveValidation() {}
    public static boolean ready(ServerPlayer actor) {
        return actor.server.overworld().getGameTime() >= actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow().readyAt();
    }
    public static void pause(ServerPlayer actor, Path root, CompoundTag proof) throws Exception {
        actor.getInventory().setItem(1, new ItemStack(PlanetContent.TERMINAL_ITEM.get()));
        actor.getInventory().setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell")), 2));
        new VerifiedPlayerSaveBarrier().persist(actor);
        var journalDirectory = root.resolve("data/legacyoftheforce/travel");
        var service = new TravelService(actor.server, journalDirectory, new VanillaProtectedWorldService(), checkpoint -> {
            if (checkpoint != TravelService.Checkpoint.ACTIVE_DURABLE) return;
            try {
                proof.put("durable", new VerifiedPlayerSaveBarrier().read(actor));
                proof.put("memory", actor.saveWithoutId(new CompoundTag()));
                proof.put("journal", AtomicNbtFile.read(journalDirectory.resolve(actor.getUUID() + ".nbt"), 262144));
                AtomicNbtFile.write(root.resolve("validation/solo-owner-checkpoint.nbt"), proof, 1048576);
                LegacyOfTheForce.LOGGER.info("SOLO_TRAVEL_PAUSED_ACTIVE_DURABLE");
                new CountDownLatch(1).await();
            } catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        });
        var result = service.request(actor, new BlockPos(600, 240, 600), 0);
        throw new IllegalStateException("Travel must pause before returning, got " + result);
    }
    public static void recovered(ServerPlayer actor, Path root, CompoundTag proof) throws Exception {
        var atlas = actor.getData(PlanetContent.ATLAS.get()).view().orElseThrow();
        var journal = AtomicNbtFile.read(root.resolve("data/legacyoftheforce/travel/" + actor.getUUID() + ".nbt"), 262144);
        if (!actor.level().dimension().equals(PlanetContent.ILUM) || atlas.revision() != 4 || atlas.pending() != null || !atlas.returnCredit()
                || !journal.getString("phase").equals("DONE") || actor.getInventory().countItem(PlanetContent.TERMINAL_ITEM.get()) != 1
                || actor.getInventory().countItem(BuiltInRegistries.ITEM.get(PlanetContent.id("speeder_energy_cell"))) != 0) {
            throw new IllegalStateException("Actual native solo travel did not recover once");
        }
        proof.put("journal", journal);
    }
}
