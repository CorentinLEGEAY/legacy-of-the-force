package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberItemTransfers;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes piston movement, which does not pass through the held item's own tick. */
@Mixin(Entity.class)
public abstract class RareItemMovementMixin {
    @Inject(method="move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$holdJournalPosition(MoverType mover,Vec3 delta,CallbackInfo callback){
        if((Object)this instanceof ItemEntity item&&SaberItemTransfers.worldLocked(item))callback.cancel();
    }
}
