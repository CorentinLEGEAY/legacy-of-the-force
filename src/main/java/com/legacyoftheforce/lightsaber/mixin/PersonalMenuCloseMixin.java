package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.SaberGuiDrops;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InventoryMenu.class)
public abstract class PersonalMenuCloseMixin {
    @WrapMethod(method="removed") private void legacyoftheforce$preserveOverflow(Player actor,Operation<Void> original) {
        SaberGuiDrops.removed((InventoryMenu)(Object)this,actor,original);
    }
}
