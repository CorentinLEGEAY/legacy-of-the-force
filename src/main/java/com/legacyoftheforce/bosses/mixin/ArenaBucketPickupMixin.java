package com.legacyoftheforce.bosses.mixin;

import com.legacyoftheforce.bosses.BossLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LiquidBlock.class,PowderSnowBlock.class})
public abstract class ArenaBucketPickupMixin {
    @Inject(method="pickupBlock",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$protectPickup(Player actor,LevelAccessor level,BlockPos pos,BlockState state,CallbackInfoReturnable<ItemStack> callback) {
        if(BossLifecycle.protectedAt(level,pos)) { BossLifecycle.rejectFluid(actor,level,pos);callback.setReturnValue(ItemStack.EMPTY); }
    }
}
