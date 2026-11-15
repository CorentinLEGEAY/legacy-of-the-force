package com.legacyoftheforce.planets;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("legacyoftheforce")
@PrefixGameTestTemplate(false)
public final class IlumSiteGameTests {
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void sourceNeedsIronPickaxeAndCannotYieldAnIlumCrystalOutsideIlum(GameTestHelper helper) {
        var state = PlanetContent.CRYSTAL.get().defaultBlockState();
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE).isCorrectToolForDrops(state)
                && !new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "Iron tool tier must be enforced by real block tags");
        var drops = net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), null, null,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        helper.assertTrue(drops.isEmpty(), "Source moved outside Ilum must not create a falsely provenanced crystal"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 120)
    public static void crystalMineHasOneSealedNodeAndWalkableSpiral(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(1, 1, 1)); var piece = new IlumSitePiece(origin, 0);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1), piece.getBoundingBox(), new ChunkPos(origin), origin);
        int count = 0;
        for (var pos : BlockPos.betweenClosed(origin, origin.offset(10, 15, 10))) if (level.getBlockState(pos).is(PlanetContent.CRYSTAL.get())) count++;
        helper.assertTrue(count == 1 && level.getBlockState(origin.offset(7, 1, 7)).is(Blocks.BLUE_ICE), "One rare crystal must be sealed behind real mineable ice");
        int[][] ring = {{2,2},{3,2},{4,2},{4,3},{4,4},{3,4},{2,4},{2,3}};
        for (int step = 0; step <= 12; step++) {
            var tread = origin.offset(ring[step % 8][0], 13 - step, ring[step % 8][1]);
            helper.assertTrue(level.getBlockState(tread).is(Blocks.STONE_BRICKS) && level.isEmptyBlock(tread.above()) && level.isEmptyBlock(tread.above(2)), "Spiral step must have ground and two-block clearance: " + step);
        }
        helper.assertTrue(level.isEmptyBlock(origin.offset(2, 13, 1)) && level.isEmptyBlock(origin.offset(4, 1, 5)), "Mine needs a surface entrance and bottom exit"); helper.succeed();
    }
    @GameTest(template = "empty_large", timeoutTicks = 100)
    public static void siteSchemasRoundTripAndUnknownVersionsPreserveWithoutGeneration(GameTestHelper helper) {
        var level = helper.getLevel(); var origin = helper.absolutePos(new BlockPos(1, 1, 1)); var context = StructurePieceSerializationContext.fromLevel(level);
        for (int kind = 0; kind < 4; kind++) {
            var piece = new IlumSitePiece(origin, kind); var saved = piece.createTag(context); saved.putString("extension", "keep");
            helper.assertTrue(new IlumSitePiece(context, saved).createTag(context).equals(saved), "Known site must retain kind, bounds and opaque extensions");
            var future = saved.copy(); future.putInt("schemaVersion", 99); var unknown = new IlumSitePiece(context, future);
            helper.assertTrue(unknown.createTag(context).equals(future), "Future schema must be preserved, not downgraded or discarded");
            level.setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
            unknown.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1), unknown.getBoundingBox(), new ChunkPos(origin), origin);
            helper.assertTrue(level.getBlockState(origin).is(Blocks.DIAMOND_BLOCK), "Unknown site must not rewrite existing terrain");
        }
        helper.succeed();
    }
}
