package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberItemTransfers;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block and minecart hoppers share this native item-entity insertion boundary. */
@Mixin(HopperBlockEntity.class)
public abstract class HopperRarePickupMixin {
    @Inject(method="addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/entity/item/ItemEntity;)Z",at=@At("HEAD"),cancellable=true)
    private static void legacyoftheforce$denyPendingSource(Container destination,ItemEntity item,CallbackInfoReturnable<Boolean> callback){
        if(SaberItemTransfers.worldLocked(item))callback.setReturnValue(false);
    }
}
