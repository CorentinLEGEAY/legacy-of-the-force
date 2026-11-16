package com.legacyoftheforce.bosses.mixin;

import com.legacyoftheforce.bosses.BossLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers native buckets, fish buckets and the same placement path used by dispensers. */
@Mixin(BucketItem.class)
public abstract class ArenaBucketMixin {
    @Inject(method="emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$protectPlacement(Player actor,Level level,BlockPos pos,BlockHitResult hit,ItemStack stack,CallbackInfoReturnable<Boolean> callback) {
        if(BossLifecycle.protectedAt(level,pos)) { BossLifecycle.rejectFluid(actor,level,pos);callback.setReturnValue(false); }
    }
}
