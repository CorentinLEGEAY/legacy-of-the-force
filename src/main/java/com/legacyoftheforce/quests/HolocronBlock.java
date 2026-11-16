package com.legacyoftheforce.quests;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class HolocronBlock extends Block {
    public static final MapCodec<HolocronBlock> CODEC = simpleCodec(HolocronBlock::new);
    public HolocronBlock(Properties properties) { super(properties); }
    @Override public MapCodec<HolocronBlock> codec() { return CODEC; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(4, 0, 4, 12, 8, 12); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer actor) QuestLifecycle.discover(actor, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
