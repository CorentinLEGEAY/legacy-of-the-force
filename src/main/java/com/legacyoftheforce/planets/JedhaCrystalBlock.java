package com.legacyoftheforce.planets;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.*;

/** A sealed source, not an item dispenser. Mining also requires its durable native console. */
public final class JedhaCrystalBlock extends Block {
    public static final BooleanProperty ATTUNED = BooleanProperty.create("attuned");
    public JedhaCrystalBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(ATTUNED, false)); }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(JedhaCrystalBlock::new); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ATTUNED); }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        if (!state.getValue(ATTUNED) || !(world.getBlockEntity(pos.north(3)) instanceof RuneConsoleEntity console) || !console.ready()) return 0;
        return player.getDigSpeed(state, pos) / TravelConfig.RUNE_MINING_HARDNESS.get().floatValue()
                / (net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck(player, state, world, pos) ? 30 : 100);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return state.getValue(ATTUNED) ? box(3, 0, 3, 13, 15, 13) : Shapes.block(); }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter world, BlockPos pos, Entity entity) { return false; }
}
