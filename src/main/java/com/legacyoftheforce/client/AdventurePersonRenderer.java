package com.legacyoftheforce.client;
import com.legacyoftheforce.quests.AdventurePersonEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
/** Original locally authored costumes on Minecraft's standard articulated humanoid. */
public final class AdventurePersonRenderer extends HumanoidMobRenderer<AdventurePersonEntity,PlayerModel<AdventurePersonEntity>> {
    public AdventurePersonRenderer(EntityRendererProvider.Context context){super(context,new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER),false),.35f);}
    @Override public ResourceLocation getTextureLocation(AdventurePersonEntity person){return ResourceLocation.fromNamespaceAndPath("legacyoftheforce","textures/entity/people/"+person.role().key()+".png");}
}
