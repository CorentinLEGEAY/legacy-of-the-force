package com.legacyoftheforce.force.mixin;

import com.legacyoftheforce.force.MobSuspensionController;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MobSuspensionMovementMixin {
    @Inject(method="move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true)
    private void legacyoftheforce$ownedSuspension(MoverType type,Vec3 delta,CallbackInfo callback) {
        if(!MobSuspensionController.allowMovement((Entity)(Object)this,type,delta))callback.cancel();
    }
}
