package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberGuiDrops;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class ExternalMenuCloseMixin {
    @WrapMethod(method="removed") private void legacyoftheforce$retainExternalCursor(Player actor,Operation<Void> original) {
        SaberGuiDrops.externalRemoved((AbstractContainerMenu)(Object)this,actor,original);
    }
}
