package com.legacyoftheforce.content;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Physical resources shared by recipes; world acquisition belongs to planets/bosses. */
public final class PlanetaryMaterials {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(LegacyOfTheForce.MOD_ID);
    public static final List<String> NAMES=List.of("ilum_frost_alloy","tatooine_silica","endor_resin","mustafar_slag","dagobah_essence","jedha_relic_dust");
    private static final Map<String,Supplier<Item>> VALUES=new HashMap<>();
    static{for(String name:NAMES)VALUES.put(name,ITEMS.register(name,()->new Item(new Item.Properties())));}
    public static Item item(String id){return VALUES.get(id).get();}
    public static void register(net.neoforged.bus.api.IEventBus bus){ITEMS.register(bus);bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event)->{if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))NAMES.forEach(name->event.accept(item(name)));});}
    private PlanetaryMaterials(){}
}
