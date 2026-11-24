package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberItemTransfers;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The completed Pre event, rather than an observer's priority, determines the native pickup permission. */
@Mixin(ItemEntity.class)
public abstract class RareItemPickupMixin {
    @WrapOperation(method="playerTouch",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/event/EventHooks;fireItemPickupPre(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/entity/player/Player;)Lnet/neoforged/neoforge/event/entity/player/ItemEntityPickupEvent$Pre;"))
    private ItemEntityPickupEvent.Pre legacyoftheforce$journalPickup(ItemEntity item,Player actor,Operation<ItemEntityPickupEvent.Pre> original){
        boolean before=SaberItemTransfers.relevant(item.getItem());var event=original.call(item,actor);
        if(before||SaberItemTransfers.relevant(item.getItem()))SaberItemTransfers.pickupAfterPre(event);
        return event;
    }
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$protectPendingItem(net.minecraft.world.damagesource.DamageSource source,float damage,CallbackInfoReturnable<Boolean> callback){
        if(SaberItemTransfers.worldLocked((ItemEntity)(Object)this))callback.setReturnValue(false);
    }

    @Inject(method="hasPickUpDelay",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$pauseMobCollection(CallbackInfoReturnable<Boolean> callback){
        if(SaberItemTransfers.worldLocked((ItemEntity)(Object)this))callback.setReturnValue(true);
    }

}
