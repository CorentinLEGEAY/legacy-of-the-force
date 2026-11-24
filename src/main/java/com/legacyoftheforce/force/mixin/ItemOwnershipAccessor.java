package com.legacyoftheforce.force.mixin;

import java.util.UUID;
import com.legacyoftheforce.core.api.DroppedItemOwnership;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemOwnershipAccessor extends DroppedItemOwnership {
    @Override @Accessor("thrower") UUID legacyoftheforce$thrower();
}
