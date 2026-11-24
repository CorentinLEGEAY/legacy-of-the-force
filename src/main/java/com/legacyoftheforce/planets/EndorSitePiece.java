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

/** Root grotto, lookout, ruins and two arenas; all are native one-time structure pieces. */
public final class EndorSitePiece extends StructurePiece {
    private final int kind;
    private final CompoundTag retained;
    public EndorSitePiece(BlockPos origin, int kind) {
        super(PlanetContent.FOREST_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 12, origin.getY() + 16, origin.getZ() + 12));
        if (kind < 0 || kind > 4) throw new IllegalArgumentException("Invalid forest site");
        this.kind = kind; retained = new CompoundTag(); setOrientation(Direction.SOUTH);
    }
    public EndorSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PlanetContent.FOREST_PIECE.get(), tag); retained = tag.copy();
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
        if (kind == 0) grotto(world, clip); else if (kind == 1) lookout(world, clip); else ruins(world, clip);
        var loot = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, PlanetContent.id("chests/endor_expedition"));
        createChest(world, clip, random, 9, kind == 1 ? 11 : 1, 9, loot);
    }
    private void grotto(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 7; y++) {
            boolean boundary = x == 1 || x == 11 || z == 1 || z == 11 || y == 0 || y == 7;
            put(world, clip, boundary ? y == 0 ? Blocks.MOSS_BLOCK : (x + z) % 3 == 0 ? Blocks.SPRUCE_LOG : Blocks.CALCITE : Blocks.CAVE_AIR, x, y, z);
        }
        for (int y = 1; y <= 16; y++) for (int x = 2; x <= 5; x++) for (int z = 2; z <= 5; z++) put(world, clip, Blocks.CAVE_AIR, x, y, z);
        int[][] path = {{2,2},{3,2},{4,2},{5,2},{5,3},{5,4},{5,5},{4,5},{3,5},{2,5},{2,4},{2,3}};
        for (int n = 0; n < 12; n++) put(world, clip, Blocks.MOSSY_STONE_BRICKS, path[n][0], 13 - n, path[n][1]);
        for (int y = 13; y <= 16; y++) { put(world, clip, Blocks.CAVE_AIR, 2, y, 1); put(world, clip, Blocks.CAVE_AIR, 3, y, 1); }
        for (int x = 7; x <= 9; x++) put(world, clip, PlanetContent.RESIN_WOOD.get(), x, 1, 10);
        put(world, clip, PlanetContent.ENDOR_CRYSTAL.get(), 8, 1, 8); put(world, clip, Blocks.SHROOMLIGHT, 10, 6, 10);
    }
    private void lookout(WorldGenLevel world, BoundingBox clip) {
        for (int x = 2; x <= 10; x++) for (int z = 2; z <= 10; z++) for (int y = 0; y <= 15; y++) put(world, clip, y == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.AIR, x, y, z);
        for (int x = 5; x <= 7; x++) for (int z = 4; z <= 6; z++) for (int y = 1; y <= 14; y++) put(world, clip, Blocks.SPRUCE_LOG, x, y, z);
        for (int x = 2; x <= 10; x++) for (int z = 2; z <= 10; z++) {
            put(world, clip, Blocks.SPRUCE_PLANKS, x, 10, z);
            if (x == 2 || x == 10 || z == 2 || z == 10) put(world, clip, Blocks.SPRUCE_FENCE, x, 11, z);
        }
        for (int y = 1; y <= 12; y++) placeBlock(world, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 6, y, 3, clip);
        put(world, clip, PlanetContent.RESIN_WOOD.get(), 3, 11, 8); put(world, clip, Blocks.LANTERN, 8, 11, 3);
    }
    private void ruins(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 7; y++) {
            Block block = y == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.AIR;
            if (y > 0 && y <= 4 && (x == 1 || x == 11 || z == 11) && (x + z + y) % 4 != 0) block = Blocks.MOSSY_COBBLESTONE;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{3, 9}) for (int y = 1; y <= 5; y++) put(world, clip, Blocks.SPRUCE_LOG, x, y, 7);
        for (int x = 3; x <= 9; x++) put(world, clip, Blocks.MOSSY_STONE_BRICKS, x, 5, 7);
        put(world, clip, PlanetContent.RESIN_WOOD.get(), 2, 1, 10);
    }
    private void arena(WorldGenLevel world, BoundingBox clip) {
        boolean dark = kind == 4;
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 10; y++) {
            Block block = y == 0 ? dark ? Blocks.DEEPSLATE_BRICKS : Blocks.MOSSY_STONE_BRICKS : Blocks.AIR;
            if ((x == 0 || x == 12 || z == 12) && y > 0 && y <= 2) block = dark ? Blocks.DEEPSLATE_BRICKS : Blocks.MOSSY_COBBLESTONE;
            if (z == 12 && x >= 5 && x <= 7 && y > 0) block = Blocks.AIR;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{3, 9}) for (int z : new int[]{3, 8}) for (int y = 1; y <= 3; y++) put(world, clip, Blocks.SPRUCE_LOG, x, y, z);
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) if (x == 0 || x == 12 || z == 0 || z == 12) put(world, clip, Blocks.MOSS_BLOCK, x, 0, z);
        put(world, clip, com.legacyoftheforce.core.api.BossArenaSites.altar(PlanetId.ENDOR.id(), dark), 6, 1, 9);
        put(world, clip, Blocks.SHROOMLIGHT, 1, 1, 6); put(world, clip, Blocks.SHROOMLIGHT, 11, 1, 6);
    }
}
