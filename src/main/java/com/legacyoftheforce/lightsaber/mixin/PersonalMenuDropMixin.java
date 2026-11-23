package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberGuiDrops;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerMenu.class)
public abstract class PersonalMenuDropMixin {
    @WrapMethod(method="clicked") private void legacyoftheforce$completePersonalDrop(int slot,int button,ClickType type,Player actor,Operation<Void> original) {
        SaberGuiDrops.clicked((AbstractContainerMenu)(Object)this,slot,button,type,actor,original);
    }
}
