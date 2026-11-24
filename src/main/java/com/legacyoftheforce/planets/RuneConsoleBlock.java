package com.legacyoftheforce.planets;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RuneConsoleBlock extends BaseEntityBlock {
    public RuneConsoleBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(RuneConsoleBlock::new); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RuneConsoleEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer actor && level.getBlockEntity(pos) instanceof RuneConsoleEntity console) console.interact(actor, -1, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof RuneConsoleEntity console && console.supported() && !world.getBlockState(console.crystalPosition()).is(PlanetContent.JEDHA_CRYSTAL.get()) ? player.getDigSpeed(state, pos) / TravelConfig.RUNE_MINING_HARDNESS.get().floatValue() / 30 : 0;
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter world, BlockPos pos, Entity entity) { return false; }
}
