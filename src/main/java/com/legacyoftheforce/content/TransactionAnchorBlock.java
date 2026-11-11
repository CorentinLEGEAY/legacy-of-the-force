package com.legacyoftheforce.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TransactionAnchorBlock extends BaseEntityBlock {
    public static final MapCodec<TransactionAnchorBlock> CODEC = simpleCodec(TransactionAnchorBlock::new);
    public TransactionAnchorBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new TransactionAnchorEntity(position, state);
    }
}
