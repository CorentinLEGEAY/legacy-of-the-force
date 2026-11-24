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

/** Sealed chamber, archive puzzle and ruins; all are native one-time structure pieces. */
public final class JedhaSitePiece extends StructurePiece {
    private final int kind;
    private final CompoundTag retained;
    public JedhaSitePiece(BlockPos origin, int kind) {
        super(PlanetContent.SACRED_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 12, origin.getY() + 16, origin.getZ() + 12));
        if (kind < 0 || kind > 2) throw new IllegalArgumentException("Invalid sacred site");
        this.kind = kind; retained = new CompoundTag(); setOrientation(Direction.SOUTH);
    }
    public JedhaSitePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(PlanetContent.SACRED_PIECE.get(), tag); retained = tag.copy();
        kind = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == 1 && tag.contains("siteKind", Tag.TAG_INT)
                && tag.getInt("siteKind") >= 0 && tag.getInt("siteKind") <= 2 && getBoundingBox().getXSpan() == 13
                && getBoundingBox().getZSpan() == 13 && getBoundingBox().getYSpan() == 17 ? tag.getInt("siteKind") : -1;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        if (kind >= 0) { tag.putInt("schemaVersion", 1); tag.putInt("siteKind", kind); }
        for (String key : retained.getAllKeys()) if (!tag.contains(key)) tag.put(key, retained.get(key).copy());
    }
    private void put(WorldGenLevel world, BoundingBox clip, Block block, int x, int y, int z) { placeBlock(world, block.defaultBlockState(), x, y, z, clip); }
    @Override public void postProcess(WorldGenLevel world, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (kind < 0) return;
        if (kind == 0) chamber(world, clip); else if (kind == 1) archive(world, clip); else ruins(world, clip);
        var loot = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, PlanetContent.id("chests/jedha_expedition"));
        createChest(world, clip, random, 9, 1, 9, loot);
    }
    private void puzzle(WorldGenLevel world, BoundingBox clip) {
        put(world, clip, PlanetContent.RUNE_CONSOLE.get(), 6, 1, 6);
        for (int glyph = 0; glyph < 3; glyph++) put(world, clip, PlanetContent.GLYPHS.get(glyph).get(), 3 + glyph * 3, 1, 3);
        put(world, clip, PlanetContent.JEDHA_CRYSTAL.get(), 6, 1, 9);
        for (int x = 2; x <= 4; x++) put(world, clip, PlanetContent.RELIC_ORE.get(), x, 1, 10);
    }
    private void chamber(WorldGenLevel world, BoundingBox clip) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 7; y++) {
            boolean shell = x == 0 || x == 12 || z == 0 || z == 12 || y == 0 || y == 7;
            put(world, clip, shell ? y == 0 ? Blocks.CALCITE : Blocks.SANDSTONE : Blocks.CAVE_AIR, x, y, z);
        }
        // Separate access shaft leaves the puzzle room and its three symbols unobstructed.
        for (int x = 0; x <= 2; x++) for (int z = 1; z <= 5; z++) for (int y = 1; y <= 16; y++)
            put(world, clip, x == 0 || x == 2 || z == 1 || z == 5 ? Blocks.CUT_SANDSTONE : Blocks.CAVE_AIR, x, y, z);
        for (int y = 1; y <= 14; y++) placeBlock(world, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST), 1, y, 2, clip);
        for (int y = 13; y <= 16; y++) put(world, clip, Blocks.CAVE_AIR, 1, y, 1);
        for (int y = 1; y <= 2; y++) put(world, clip, Blocks.CAVE_AIR, 2, y, 4);
        puzzle(world, clip); put(world, clip, Blocks.SHROOMLIGHT, 10, 6, 10);
    }
    private void archive(WorldGenLevel world, BoundingBox clip) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 0; y <= 9; y++) {
            boolean wall = x == 0 || x == 12 || z == 12;
            Block block = y == 0 || y == 8 ? Blocks.SMOOTH_SANDSTONE : wall && y <= 7 ? Blocks.CUT_SANDSTONE : Blocks.AIR;
            if ((z == 0 || z == 12) && x >= 5 && x <= 7 && y > 0 && y <= 3) block = Blocks.AIR;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{1, 11}) for (int z : new int[]{2, 10}) for (int y = 1; y <= 7; y++) put(world, clip, Blocks.CHISELED_SANDSTONE, x, y, z);
        for (int z = 4; z <= 8; z++) { put(world, clip, Blocks.BOOKSHELF, 1, 1, z); put(world, clip, Blocks.BOOKSHELF, 11, 1, z); }
        puzzle(world, clip); put(world, clip, Blocks.SHROOMLIGHT, 10, 7, 10);
    }
    private void ruins(WorldGenLevel world, BoundingBox clip) {
        for (int x = 1; x <= 11; x++) for (int z = 1; z <= 11; z++) for (int y = 0; y <= 8; y++) {
            Block block = y == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.AIR;
            if (y > 0 && y <= 4 && (x == 1 || x == 11 || z == 11) && (x + y + z) % 4 != 0) block = Blocks.CUT_SANDSTONE;
            put(world, clip, block, x, y, z);
        }
        for (int x : new int[]{3, 9}) for (int y = 1; y <= 6; y++) put(world, clip, Blocks.CALCITE, x, y, 5);
        for (int x = 3; x <= 9; x++) put(world, clip, Blocks.CHISELED_SANDSTONE, x, 6, 5);
        for (int x = 2; x <= 4; x++) put(world, clip, PlanetContent.RELIC_ORE.get(), x, 1, 10);
    }
}
