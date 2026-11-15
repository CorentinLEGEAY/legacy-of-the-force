package com.legacyoftheforce.planets;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class GalacticTerminalBlock extends Block {
    public static final MapCodec<GalacticTerminalBlock> CODEC = simpleCodec(GalacticTerminalBlock::new);
    public GalacticTerminalBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) player.openMenu(new SimpleMenuProvider((id, inventory, actor) -> new GalacticTerminalMenu(id, inventory, ContainerLevelAccess.create(level, pos)), Component.translatable("block.legacyoftheforce.galactic_terminal")));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
