package com.legacyoftheforce.bosses;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BossAltarBlock extends BaseEntityBlock {
    private final BossKind kind;
    public BossAltarBlock(BossKind kind, Properties properties) { super(properties); this.kind = kind; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(p -> new BossAltarBlock(kind, p)); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BossAltarEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer actor && level.getBlockEntity(pos) instanceof BossAltarEntity altar) {
            var service = BossArenaService.forServer(actor.server);
            if (service != null && altar.supported()) {
                if (!altar.isEmpty() && service.canOpen(actor, pos)) actor.openMenu(altar);
                else service.challenge(actor, pos, kind);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof BossAltarEntity altar && altar.supported()) Containers.dropContents(level, pos, altar);
        super.onRemove(state, level, pos, next, moving);
    }
}
