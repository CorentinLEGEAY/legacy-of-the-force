package com.legacyoftheforce.lightsaber.mixin;

import com.legacyoftheforce.lightsaber.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMenuStateMixin implements SaberMenuStateCarrier {
    @Unique private Tag legacyoftheforce$menuImage;
    @Unique private boolean legacyoftheforce$pendingMenu;
    public Tag legacyoftheforce$loadedMenu() {return legacyoftheforce$menuImage;}
    public void legacyoftheforce$loadedMenu(Tag image) {
        legacyoftheforce$menuImage=image.copy();legacyoftheforce$pendingMenu=true;
        try {if(SaberMenuState.validate(image).isEmpty())legacyoftheforce$menuRestored();}catch(IllegalArgumentException unsupported) { /* Preserve opaque future/corrupt images for the login guard. */ }
    }
    public boolean legacyoftheforce$menuPending() {return legacyoftheforce$pendingMenu;}
    public void legacyoftheforce$menuRestored() {legacyoftheforce$pendingMenu=false;legacyoftheforce$menuImage=null;}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL")) private void legacyoftheforce$saveMenu(CompoundTag tag,CallbackInfo callback) {
        if((Object)this instanceof ServerPlayer actor)tag.put(SaberMenuState.KEY,SaberMenuState.forSave(actor));
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL")) private void legacyoftheforce$loadMenu(CompoundTag tag,CallbackInfo callback) {
        if((Object)this instanceof ServerPlayer&&tag.contains(SaberMenuState.KEY))legacyoftheforce$loadedMenu(tag.get(SaberMenuState.KEY));
    }
}
