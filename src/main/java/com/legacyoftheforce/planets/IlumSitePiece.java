package com.legacyoftheforce.planets;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Original block geometry, serialized by Minecraft structures; generation never runs on login. */
public final class IlumSitePiece extends StructurePiece {
    private final int kind;
    private final CompoundTag retained;
    public IlumSitePiece(BlockPos origin, int kind) {
        super(PlanetContent.SITE_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 10, origin.getY() + 15, origin.getZ() + 10));
        if (kind < 0 || kind > 3) throw new IllegalArgumentException("Invalid Ilum site kind");
        this.kind = kind; retained = new CompoundTag(); setOrientation(Direction.SOUTH);
    }
    public IlumSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PlanetContent.SITE_PIECE.get(), tag);
        retained = tag.copy();
        boolean supported = tag.contains("schemaVersion", net.minecraft.nbt.Tag.TAG_INT) && tag.getInt("schemaVersion") == 1
                && tag.contains("siteKind", net.minecraft.nbt.Tag.TAG_INT) && tag.getInt("siteKind") >= 0 && tag.getInt("siteKind") <= 3
                && getBoundingBox().getXSpan() == 11 && getBoundingBox().getZSpan() == 11 && getBoundingBox().getYSpan() == 16;
        kind = supported ? tag.getInt("siteKind") : -1;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        if (kind >= 0) { tag.putInt("schemaVersion", 1); tag.putInt("siteKind", kind); }
        for (var key : retained.getAllKeys()) if (!tag.contains(key)) tag.put(key, retained.get(key).copy());
    }
    @Override public void postProcess(WorldGenLevel world, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (kind < 0) return;
        if (kind == 0) cavern(world, clip); else building(world, clip);
    }
    private void put(WorldGenLevel world, BoundingBox clip, Block block, int x, int y, int z) { put(world, clip, block.defaultBlockState(), x, y, z); }
    private void put(WorldGenLevel world, BoundingBox clip, BlockState state, int x, int y, int z) { placeBlock(world, state, x, y, z, clip); }
    private void cavern(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 9; x++) for (int z = 4; z <= 10; z++) for (int y = 0; y <= 6; y++)
            put(world, clip, y == 0 ? Blocks.CALCITE : x == 1 || x == 9 || z == 4 || z == 10 || y == 6 ? Blocks.PACKED_ICE : Blocks.CAVE_AIR, x, y, z);
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) for (int y = 0; y <= 15; y++)
            put(world, clip, y == 0 || x == 1 || x == 5 || z == 1 || z == 5 || x == 3 && z == 3 ? Blocks.STONE_BRICKS : Blocks.CAVE_AIR, x, y, z);
        int[][] ring = {{2,2},{3,2},{4,2},{4,3},{4,4},{3,4},{2,4},{2,3}};
        for (int step = 0; step <= 12; step++) put(world, clip, Blocks.STONE_BRICKS, ring[step % 8][0], 13 - step, ring[step % 8][1]);
        for (int y = 1; y <= 3; y++) { put(world, clip, Blocks.CAVE_AIR, 4, y, 5); if (y > 1) put(world, clip, Blocks.CAVE_AIR, 4, y, 4); }
        for (int y = 13; y <= 15; y++) for (int x = 2; x <= 3; x++) put(world, clip, Blocks.CAVE_AIR, x, y, 1);
        put(world, clip, Blocks.SEA_LANTERN, 8, 5, 9); put(world, clip, Blocks.SEA_LANTERN, 1, 14, 3);
        for (int x = 6; x <= 8; x++) for (int z = 7; z <= 9; z++) for (int y = 1; y <= 2; y++) put(world, clip, Blocks.BLUE_ICE, x, y, z);
        put(world, clip, PlanetContent.CRYSTAL.get(), 7, 1, 8);
    }
    private void building(WorldGenLevel world, BoundingBox clip) {
        var wall = kind == 2 ? Blocks.DEEPSLATE_BRICKS : Blocks.QUARTZ_BRICKS;
        for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
            for (int y = 0; y <= 8; y++) {
                Block block = y == 0 ? Blocks.POLISHED_ANDESITE : Blocks.AIR;
                if (y > 0 && y <= 5 && (x == 0 || x == 10 || z == 0 || z == 10)) block = wall;
                if (y == 6 && kind != 3) block = wall;
                if (kind == 3 && y > 2 && (x + z + y) % 3 != 0) block = Blocks.AIR;
                if ((z == 0 || z == 10) && x >= 4 && x <= 6 && y > 0 && y <= 3) block = Blocks.AIR;
                put(world, clip, block, x, y, z);
            }
        }
        for (int x : new int[]{2, 8}) for (int z : new int[]{2, 8}) {
            for (int y = 1; y <= 5; y++) put(world, clip, kind == 2 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.QUARTZ_PILLAR, x, y, z);
            put(world, clip, Blocks.SEA_LANTERN, x, 5, z);
        }
        put(world, clip, kind == 1 || kind == 2 ? com.legacyoftheforce.core.api.BossArenaSites.altar(kind == 2) : Blocks.CHISELED_QUARTZ_BLOCK, 5, 1, 8);
        if (kind == 3) put(world, clip, Blocks.LIGHT_BLUE_STAINED_GLASS, 5, 2, 8);
    }
}
