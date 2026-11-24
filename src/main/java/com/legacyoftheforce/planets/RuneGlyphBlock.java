package com.legacyoftheforce.planets;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RuneGlyphBlock extends Block {
    private final int glyph;
    public RuneGlyphBlock(int glyph, Properties properties) { super(properties); if (glyph < 0 || glyph > 2) throw new IllegalArgumentException("Invalid glyph"); this.glyph = glyph; }
    public int glyph() { return glyph; }
    private BlockPos consolePosition(BlockPos pos) { return pos.offset((1 - glyph) * 3, 0, 3); }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(properties -> new RuneGlyphBlock(glyph, properties)); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var linked = consolePosition(pos);
        if (player instanceof ServerPlayer actor && level.hasChunkAt(linked) && level.getBlockEntity(linked) instanceof RuneConsoleEntity console) console.interact(actor, glyph, pos);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter world, BlockPos pos) {
        return world.getBlockEntity(consolePosition(pos)) instanceof RuneConsoleEntity console && console.supported() && console.ready() ? player.getDigSpeed(state, pos) / TravelConfig.RUNE_MINING_HARDNESS.get().floatValue() / 30 : 0;
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter world, BlockPos pos, Entity entity) { return false; }
}
