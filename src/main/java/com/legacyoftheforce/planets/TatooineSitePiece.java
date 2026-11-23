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

/** Original desert geometry. Native chest loot seeds are assigned once by structure generation. */
public final class TatooineSitePiece extends StructurePiece {
    private final int kind;
    private final CompoundTag retained;
    public TatooineSitePiece(BlockPos origin, int kind) {
        super(PlanetContent.DESERT_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 12, origin.getY() + 16, origin.getZ() + 12));
        if (kind < 0 || kind > 2) throw new IllegalArgumentException("Invalid desert site");
        this.kind = kind; retained = new CompoundTag(); setOrientation(Direction.SOUTH);
    }
    public TatooineSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PlanetContent.DESERT_PIECE.get(), tag); retained = tag.copy();
        kind = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == 1
                && tag.contains("siteKind", Tag.TAG_INT) && tag.getInt("siteKind") >= 0 && tag.getInt("siteKind") <= 2
                && getBoundingBox().getXSpan() == 13 && getBoundingBox().getZSpan() == 13 && getBoundingBox().getYSpan() == 17 ? tag.getInt("siteKind") : -1;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        if (kind >= 0) { tag.putInt("schemaVersion", 1); tag.putInt("siteKind", kind); }
        for (String key : retained.getAllKeys()) if (!tag.contains(key)) tag.put(key, retained.get(key).copy());
    }
    private void put(WorldGenLevel world, BoundingBox clip, Block block, int x, int y, int z) { placeBlock(world, block.defaultBlockState(), x, y, z, clip); }
    @Override public void postProcess(WorldGenLevel world, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (kind < 0) return;
        if (kind == 0) mine(world, clip); else if (kind == 1) settlement(world, clip); else wreck(world, clip);
        var loot = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, PlanetContent.id("chests/tatooine_expedition"));
        createChest(world, clip, random, kind == 0 ? 10 : 6, 1, kind == 0 ? 10 : 9, loot);
    }
    private void mine(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 6; y++) {
            boolean shell = x == 1 || x == 11 || z == 1 || z == 11 || y == 0 || y == 6;
            put(world, clip, shell ? Blocks.SANDSTONE : Blocks.CAVE_AIR, x, y, z);
        }
        // A descending perimeter stair reaches the sealed chamber without forcing a vertical drop.
        int[][] path = {{2,2},{3,2},{4,2},{5,2},{5,3},{5,4},{5,5},{4,5},{3,5},{2,5},{2,4},{2,3}};
        for (int y = 1; y <= 16; y++) for (int x = 2; x <= 5; x++) for (int z = 2; z <= 5; z++) put(world, clip, Blocks.CAVE_AIR, x, y, z);
        for (int n = 0; n < 12; n++) {
            int x = path[n][0], z = path[n][1];
            put(world, clip, Blocks.CUT_SANDSTONE, x, 13 - n, z);
            put(world, clip, Blocks.CAVE_AIR, x, 14 - n, z); put(world, clip, Blocks.CAVE_AIR, x, 15 - n, z);
        }
        for (int y = 1; y <= 2; y++) put(world, clip, Blocks.CAVE_AIR, 5, y, 6);
        for (int y = 13; y <= 16; y++) { put(world, clip, Blocks.CAVE_AIR, 2, y, 1); put(world, clip, Blocks.CAVE_AIR, 3, y, 1); }
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) put(world, clip, PlanetContent.SILICA_ORE.get(), x, 0, z);
        put(world, clip, PlanetContent.TATOOINE_CRYSTAL.get(), 8, 1, 8);
        put(world, clip, Blocks.SHROOMLIGHT, 10, 5, 10);
    }
    private void settlement(WorldGenLevel world, BoundingBox clip) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) {
            put(world, clip, Blocks.CUT_SANDSTONE, x, 0, z);
            for (int y = 1; y <= 7; y++) put(world, clip, Blocks.AIR, x, y, z);
        }
        for (int x : new int[]{1, 9}) for (int z = 2; z <= 8; z++) for (int dx = 0; dx <= 2; dx++) for (int y = 1; y <= 4; y++) {
            boolean wall = dx == 0 || dx == 2 || z == 2 || z == 8 || y == 4;
            put(world, clip, wall ? Blocks.SMOOTH_SANDSTONE : Blocks.AIR, x + dx, y, z);
        }
        for (int x : new int[]{3, 9}) for (int z = 4; z <= 6; z++) for (int y = 1; y <= 2; y++) put(world, clip, Blocks.AIR, x, y, z);
        for (int z : new int[]{2, 8}) for (int y = 1; y <= 3; y++) put(world, clip, Blocks.CHISELED_SANDSTONE, 6, y, z);
        put(world, clip, Blocks.CAMPFIRE, 6, 1, 5); put(world, clip, Blocks.HAY_BLOCK, 5, 1, 8);
    }
    private void wreck(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 6; y++) {
            boolean rib = x % 3 == 1 && (z == 2 || z == 10 || y == 5);
            boolean skin = y == 0 || z == 2 || z == 10 || x == 1;
            Block block = rib ? Blocks.CUT_COPPER : skin && (x + y + z) % 5 != 0 ? Blocks.WAXED_WEATHERED_COPPER : Blocks.AIR;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{2, 10}) for (int y = 1; y <= 3; y++) put(world, clip, Blocks.POLISHED_BASALT, x, y, 6);
        put(world, clip, Blocks.REDSTONE_LAMP, 2, 2, 5);
    }
}
