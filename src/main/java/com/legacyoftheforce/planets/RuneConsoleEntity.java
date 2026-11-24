package com.legacyoftheforce.planets;

import com.legacyoftheforce.LegacyOfTheForce;
import com.legacyoftheforce.core.operation.VerifiedChunkSaveBarrier;
import com.legacyoftheforce.core.security.VanillaProtectedWorldService;
import java.io.IOException;
import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One-chunk, item-free unlock. Uncertain writes stay unmineable and are retried, never rolled back blindly. */
public final class RuneConsoleEntity extends BlockEntity {
    private static final int[][] PATTERNS = {{2, 0, 1}, {1, 2, 0}, {0, 1, 2}};
    private int pattern, progress;
    private boolean solved, durable, failureReported;
    private long readyAt;
    private CompoundTag unsupported;
    public RuneConsoleEntity(BlockPos pos, BlockState state) { super(PlanetContent.RUNE_ENTITY.get(), pos, state); pattern = Math.floorMod(pos.getX() * 31 ^ pos.getZ() * 13 ^ pos.getY(), PATTERNS.length); }
    public boolean supported() { return unsupported == null; }
    public boolean ready() { return supported() && solved && durable; }
    public BlockPos crystalPosition() { return worldPosition.south(3); }
    public void interact(ServerPlayer actor, int glyph, BlockPos clicked) {
        if (!(level instanceof ServerLevel world) || actor.serverLevel() != world || !world.getServer().isSameThread() || !actor.isAlive() || actor.isFakePlayer() || actor.isSpectator()
                || !actor.canInteractWithBlock(clicked, 0) || glyph < -1 || glyph > 2 || !supported() || !world.hasChunkAt(crystalPosition())
                || world.getBlockEntity(worldPosition) != this || !world.getBlockState(worldPosition).is(PlanetContent.RUNE_CONSOLE.get())
                || !new ChunkPos(worldPosition).equals(new ChunkPos(clicked)) || !new ChunkPos(worldPosition).equals(new ChunkPos(crystalPosition())) || !world.dimension().equals(PlanetId.JEDHA.dimension())) return;
        if (glyph == -1 ? !clicked.equals(worldPosition) : !clicked.equals(worldPosition.offset((glyph - 1) * 3, 0, -3))) return;
        if (glyph >= 0 && (!(world.getBlockState(clicked).getBlock() instanceof RuneGlyphBlock source) || source.glyph() != glyph)) return;
        var protection = new VanillaProtectedWorldService();
        if (!protection.mayUseContainer(actor, world, clicked)) return;
        long now = world.getServer().overworld().getGameTime(); if (now < readyAt) return; readyAt = now + TravelConfig.RUNE_INTERVAL.get();
        if (glyph == -1 && !solved) { message(actor, "rune_clue_" + pattern); return; }
        // Only the clicked glyph needs interaction reach; linked, fixed positions still require build/claim permission.
        if (!protection.mayModify(actor, world, worldPosition, com.legacyoftheforce.core.api.ModificationKind.MOVE_THROUGH)
                || !protection.mayModify(actor, world, crystalPosition(), com.legacyoftheforce.core.api.ModificationKind.MOVE_THROUGH)) return;
        if (solved) { if (!durable) commit(actor, world); else message(actor, "rune_open"); return; }
        if (glyph != PATTERNS[pattern][progress]) { progress = 0; readyAt = now + TravelConfig.RUNE_FAILURE.get(); changed(); message(actor, "rune_wrong"); return; }
        progress++;
        world.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS, .6f, .8f + progress * .2f);
        if (progress < 3) { changed(); actor.displayClientMessage(Component.translatable("travel.legacyoftheforce.rune_progress", progress), true); return; }
        var crystal = world.getBlockState(crystalPosition());
        if (!crystal.is(PlanetContent.JEDHA_CRYSTAL.get()) || crystal.getValue(JedhaCrystalBlock.ATTUNED)) { progress = 0; changed(); message(actor, "rune_invalid"); return; }
        solved = true; durable = false; world.setBlock(crystalPosition(), crystal.setValue(JedhaCrystalBlock.ATTUNED, true), 3); changed(); commit(actor, world);
    }
    private void commit(ServerPlayer actor, ServerLevel world) {
        var state = world.getBlockState(crystalPosition());
        if (!state.is(PlanetContent.JEDHA_CRYSTAL.get()) || !state.getValue(JedhaCrystalBlock.ATTUNED)) { message(actor, "rune_invalid"); return; }
        try {
            durable = false; setChanged(); new VerifiedChunkSaveBarrier().persist(world, Set.of(worldPosition, crystalPosition()));
            durable = true; failureReported = false; changed(); message(actor, "rune_open");
        } catch (IOException | RuntimeException failure) {
            durable = false; changed(); if (!failureReported) LegacyOfTheForce.LOGGER.error("Rune unlock retained unmineable at {}: {}", worldPosition, failure.getMessage());
            failureReported = true; message(actor, "rune_pending");
        }
    }
    private static void message(ServerPlayer actor, String key) { actor.displayClientMessage(Component.translatable("travel.legacyoftheforce." + key), true); }
    private void changed() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider); if (unsupported != null) { tag.merge(unsupported.copy()); return; }
        tag.putInt("schemaVersion", 1); tag.putInt("pattern", pattern); tag.putInt("progress", progress); tag.putBoolean("solved", solved); tag.putBoolean("durable", durable); tag.putLong("readyAt", readyAt);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.sizeInBytes() > 8192 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1 || !tag.contains("pattern", Tag.TAG_INT) || tag.getInt("pattern") < 0 || tag.getInt("pattern") >= PATTERNS.length
                || !tag.contains("progress", Tag.TAG_INT) || tag.getInt("progress") < 0 || tag.getInt("progress") > 3 || !tag.contains("solved", Tag.TAG_BYTE) || !tag.contains("durable", Tag.TAG_BYTE)
                || tag.getByte("solved") < 0 || tag.getByte("solved") > 1 || tag.getByte("durable") < 0 || tag.getByte("durable") > 1
                || tag.getBoolean("solved") != (tag.getInt("progress") == 3) || tag.getBoolean("durable") && !tag.getBoolean("solved")
                || !tag.contains("readyAt", Tag.TAG_LONG) || tag.getLong("readyAt") < 0) { unsupported = tag.copy(); return; }
        pattern = tag.getInt("pattern"); progress = tag.getInt("progress"); solved = tag.getBoolean("solved"); durable = tag.getBoolean("durable"); readyAt = tag.getLong("readyAt");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        if (unsupported != null) { var tag = new CompoundTag(); tag.putInt("schemaVersion", -1); return tag; }
        return saveWithoutMetadata(provider);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
