package com.legacyoftheforce.bosses.mixin;

import com.legacyoftheforce.bosses.BossLifecycle;
import net.minecraft.core.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla has no cancellable event for ordinary fluid spreading in NeoForge 21.1.265. */
@Mixin(FlowingFluid.class)
public abstract class ArenaFluidFlowMixin {
    @Inject(method="spreadTo",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$protectDestination(LevelAccessor level,BlockPos pos,BlockState state,Direction direction,FluidState fluid,CallbackInfo callback) {
        if(BossLifecycle.protectedAt(level,pos)) callback.cancel();
    }
}
