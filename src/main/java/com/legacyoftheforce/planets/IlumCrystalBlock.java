package com.legacyoftheforce.planets;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** The loot table creates an identified crystal; silk touch never creates another source block. */
public final class IlumCrystalBlock extends Block {
    public static final MapCodec<IlumCrystalBlock> CODEC = simpleCodec(IlumCrystalBlock::new);
    public IlumCrystalBlock(Properties properties) { super(properties); }
    @Override public MapCodec<IlumCrystalBlock> codec() { return CODEC; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return box(3, 0, 3, 13, 15, 13); }
}
