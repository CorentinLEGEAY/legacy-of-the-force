package com.legacyoftheforce.content;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Shared test itinerary only; no server object or mutable gameplay state. */
public final class AdventureRoute {
    public static final List<String> NAMES=List.of("AdventureOne","AdventureTwo");
    public static BlockPos camp(int index) { return new BlockPos(200+16*index,100,200); }
    public static BlockPos table(int index) { return camp(index).south(2); }
    public static BlockPos workshop(int index) { return camp(index).west(2); }
    public static BlockPos garage(int index) { return camp(index).east(2); }
    public static BlockPos terminal(int index) { return camp(index).north(2); }
    public static final List<ResourceLocation> CRAFTS;
    static {
        var recipes=new ArrayList<ResourceLocation>();
        for(int i=0;i<3;i++)recipes.add(ResourceLocation.parse("minecraft:crafting_table"));
        for(String name:List.of("stick","iron_nugget","stone_button","compass","iron_pickaxe","iron_sword","iron_helmet","iron_chestplate","iron_leggings","iron_boots"))recipes.add(ResourceLocation.parse("minecraft:"+name));
        for(String name:List.of("emitter_basic","neck_basic","grip_basic","control_basic","circuit_basic","pommel_basic","saber_workshop","speeder_garage","chassis_light","engine_standard","battery_small","seat_pilot","speeder_blueprint","ilum_coordinates","galactic_terminal"))recipes.add(ResourceLocation.parse("legacyoftheforce:"+name));
        for(int i=0;i<4;i++)recipes.add(ResourceLocation.parse("legacyoftheforce:speeder_energy_cell"));CRAFTS=List.copyOf(recipes);
    }
    private AdventureRoute() {}
}
