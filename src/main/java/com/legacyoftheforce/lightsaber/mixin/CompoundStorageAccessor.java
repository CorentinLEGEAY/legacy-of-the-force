package com.legacyoftheforce.lightsaber.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompoundContainer.class)
public interface CompoundStorageAccessor {
    @Accessor("container1") Container legacyoftheforce$first();
    @Accessor("container2") Container legacyoftheforce$second();
}
