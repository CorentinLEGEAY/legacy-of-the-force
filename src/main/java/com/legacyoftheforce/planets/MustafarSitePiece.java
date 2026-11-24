package com.legacyoftheforce.planets;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Crystal vault, refinery, ruins and two arenas; all are native one-time structure pieces. */
public final class MustafarSitePiece extends StructurePiece {
    private final int kind;
    private final CompoundTag retained;
    public MustafarSitePiece(BlockPos origin, int kind) {
        super(PlanetContent.VOLCANIC_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 12, origin.getY() + 16, origin.getZ() + 12));
        if (kind < 0 || kind > 4) throw new IllegalArgumentException("Invalid volcanic site");
        this.kind = kind; retained = new CompoundTag(); setOrientation(Direction.SOUTH);
    }
    public MustafarSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PlanetContent.VOLCANIC_PIECE.get(), tag); retained = tag.copy();
        kind = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == 1 && tag.contains("siteKind", Tag.TAG_INT)
                && tag.getInt("siteKind") >= 0 && tag.getInt("siteKind") <= 4 && getBoundingBox().getXSpan() == 13
                && getBoundingBox().getZSpan() == 13 && getBoundingBox().getYSpan() == 17 ? tag.getInt("siteKind") : -1;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        if (kind >= 0) { tag.putInt("schemaVersion", 1); tag.putInt("siteKind", kind); }
        for (String key : retained.getAllKeys()) if (!tag.contains(key)) tag.put(key, retained.get(key).copy());
    }
    private void put(WorldGenLevel world, BoundingBox clip, Block block, int x, int y, int z) { placeBlock(world, block.defaultBlockState(), x, y, z, clip); }
    @Override public void postProcess(WorldGenLevel world, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (kind < 0) return;
        if (kind >= 3) { arena(world, clip); return; }
        if (kind == 0) vault(world, clip); else if (kind == 1) refinery(world, clip); else ruins(world, clip);
        var loot = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, PlanetContent.id("chests/mustafar_expedition"));
        createChest(world, clip, random, 9, 1, 9, loot);
    }
    private void vault(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 7; y++) {
            boolean shell = x == 1 || x == 11 || z == 1 || z == 11 || y == 0 || y == 7;
            put(world, clip, shell ? y == 0 ? Blocks.OBSIDIAN : Blocks.BLACKSTONE : Blocks.CAVE_AIR, x, y, z);
        }
        // A lined shaft keeps the surrounding worldgen lava out of the descent.
        for (int x = 1; x <= 6; x++) for (int z = 1; z <= 6; z++) for (int y = 1; y <= 16; y++)
            put(world, clip, x == 1 || x == 6 || z == 1 || z == 6 ? Blocks.POLISHED_BASALT : Blocks.CAVE_AIR, x, y, z);
        int[][] path = {{2,2},{3,2},{4,2},{5,2},{5,3},{5,4},{5,5},{4,5},{3,5},{2,5},{2,4},{2,3}};
        for (int n = 0; n < 12; n++) put(world, clip, Blocks.POLISHED_BLACKSTONE, path[n][0], 13 - n, path[n][1]);
        for (int y = 13; y <= 16; y++) { put(world, clip, Blocks.CAVE_AIR, 2, y, 1); put(world, clip, Blocks.CAVE_AIR, 3, y, 1); }
        for (int y = 1; y <= 2; y++) put(world, clip, Blocks.CAVE_AIR, 4, y, 6);
        for (int x = 8; x <= 10; x++) put(world, clip, PlanetContent.SLAG_ORE.get(), x, 1, 10);
        put(world, clip, PlanetContent.MUSTAFAR_CRYSTAL.get(), 8, 1, 8); put(world, clip, Blocks.SHROOMLIGHT, 10, 6, 10);
    }
    private void refinery(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 7; y++) {
            boolean wall = x == 1 || x == 11 || z == 1 || z == 11;
            Block block = y == 0 || y == 6 || wall && y < 6 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR;
            if (z == 1 && x >= 5 && x <= 7 && y > 0 && y <= 3) block = Blocks.AIR;
            if (wall && y == 3 && (x + z) % 3 == 0) block = Blocks.IRON_BARS;
            put(world, clip, block, x, y, z);
        }
        for (int z : new int[]{4, 7}) { put(world, clip, Blocks.BLAST_FURNACE, 2, 1, z); put(world, clip, PlanetContent.SLAG_ORE.get(), 3, 1, z); }
        for (int y = 7; y <= 12; y++) { put(world, clip, Blocks.BASALT, 3, y, 4); put(world, clip, Blocks.BASALT, 3, y, 7); }
        put(world, clip, Blocks.SHROOMLIGHT, 8, 5, 8);
    }
    private void ruins(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 8; y++) {
            Block block = y == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.AIR;
            if (y > 0 && y <= 4 && (x == 1 || x == 11 || z == 11) && (x + y + z) % 4 != 0) block = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{3, 9}) for (int y = 1; y <= 6; y++) put(world, clip, Blocks.POLISHED_BASALT, x, y, 6);
        for (int x = 3; x <= 9; x++) put(world, clip, Blocks.CHISELED_POLISHED_BLACKSTONE, x, 6, 6);
        put(world, clip, PlanetContent.SLAG_ORE.get(), 2, 1, 9);
    }
    private void arena(WorldGenLevel world, BoundingBox clip) {
        boolean dark = kind == 4;
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 10; y++) {
            Block block = y == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.AIR;
            if ((x == 0 || x == 12 || z == 12) && y > 0 && y <= 3) block = Blocks.BLACKSTONE;
            if ((z == 0 || z == 12) && x >= 5 && x <= 7 && y > 0) block = Blocks.AIR;
            if (dark && y == 0 && (x == 0 || x == 12) && z >= 2 && z <= 10) block = Blocks.LAVA;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{3, 9}) for (int z : new int[]{3, 8}) for (int y = 1; y <= 3; y++) put(world, clip, Blocks.POLISHED_BASALT, x, y, z);
        for (int x : new int[]{4, 8}) for (int z : new int[]{4, 8}) put(world, clip, Blocks.CHISELED_POLISHED_BLACKSTONE, x, 1, z);
        put(world, clip, com.legacyoftheforce.core.api.BossArenaSites.altar(PlanetId.MUSTAFAR.id(), dark), 6, 1, 9);
        put(world, clip, Blocks.SHROOMLIGHT, 1, 1, 6); put(world, clip, Blocks.SHROOMLIGHT, 11, 1, 6);
    }
}
