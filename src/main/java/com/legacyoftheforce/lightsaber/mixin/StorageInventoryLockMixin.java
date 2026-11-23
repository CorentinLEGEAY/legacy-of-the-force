package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberStorageTransactions;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native hoppers/capabilities must not mutate a chest involved in an uncertain transaction. */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class StorageInventoryLockMixin {
    public boolean canPlaceItem(int slot,ItemStack stack) {return !SaberStorageTransactions.blocked((BlockEntity)(Object)this);}
    public boolean canTakeItem(Container destination,int slot,ItemStack stack) {return !SaberStorageTransactions.blocked((BlockEntity)(Object)this);}
    @Inject(method="getItem",at=@At("HEAD"),cancellable=true) private void legacyoftheforce$hideLocked(int slot,CallbackInfoReturnable<ItemStack> callback) {if(SaberStorageTransactions.blocked((BlockEntity)(Object)this))callback.setReturnValue(ItemStack.EMPTY);}
    @Inject(method="removeItem",at=@At("HEAD"),cancellable=true) private void legacyoftheforce$denyRemoval(int slot,int count,CallbackInfoReturnable<ItemStack> callback) {if(SaberStorageTransactions.blocked((BlockEntity)(Object)this))callback.setReturnValue(ItemStack.EMPTY);}
    @Inject(method="removeItemNoUpdate",at=@At("HEAD"),cancellable=true) private void legacyoftheforce$denyUncheckedRemoval(int slot,CallbackInfoReturnable<ItemStack> callback) {if(SaberStorageTransactions.blocked((BlockEntity)(Object)this))callback.setReturnValue(ItemStack.EMPTY);}
    @Inject(method="setItem",at=@At("HEAD"),cancellable=true) private void legacyoftheforce$denyWrite(int slot,ItemStack stack,CallbackInfo callback) {if(SaberStorageTransactions.blocked((BlockEntity)(Object)this))callback.cancel();}
}
