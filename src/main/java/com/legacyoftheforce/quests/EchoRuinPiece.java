package com.legacyoftheforce.quests;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class EchoRuinPiece extends StructurePiece {
    private final CompoundTag retained;
    private final boolean supported;
    public EchoRuinPiece(BlockPos origin) {
        super(QuestContent.RUIN_PIECE.get(), 0, new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 6, origin.getY() + 4, origin.getZ() + 6));
        retained = new CompoundTag(); supported = true; setOrientation(Direction.SOUTH);
    }
    public EchoRuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(QuestContent.RUIN_PIECE.get(), tag); retained = tag.copy();
        supported = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == 1
                && getBoundingBox().getXSpan() == 7 && getBoundingBox().getYSpan() == 5 && getBoundingBox().getZSpan() == 7;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        if (supported) tag.putInt("schemaVersion", 1);
        for (var key : retained.getAllKeys()) if (!tag.contains(key)) tag.put(key, retained.get(key).copy());
    }
    @Override public void postProcess(WorldGenLevel world, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox clip, ChunkPos chunk, BlockPos pivot) {
        if (!supported) return;
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            placeBlock(world, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), x, 0, z, clip);
            for (int y = 1; y <= 4; y++) placeBlock(world, Blocks.AIR.defaultBlockState(), x, y, z, clip);
            if ((x == 0 || x == 6) && (z == 0 || z == 6)) for (int y = 1; y <= 3; y++) placeBlock(world, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), x, y, z, clip);
        }
        placeBlock(world, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3, 1, 4, clip);
        placeBlock(world, QuestContent.HOLOCRON.get().defaultBlockState(), 3, 2, 4, clip);
        com.legacyoftheforce.core.api.AdventurePeople.place(world,clip,getWorldPos(2,1,2),"explorer");
    }
}
