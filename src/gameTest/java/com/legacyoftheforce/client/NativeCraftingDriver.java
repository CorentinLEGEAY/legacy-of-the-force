package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Native container clicks only. No recipe-book unlocks, direct inventory edits or calls to a server. */
public final class NativeCraftingDriver {
    private record Input(int gridSlot,Ingredient ingredient) {}
    private final ResourceLocation id;
    private final List<Input> inputs=new ArrayList<>();
    private ItemStack result;private CompoundTag before;private int cursor,source=-1,width,expectedCount,clicks;
    private long ready,started;private String stage="INIT";
    public NativeCraftingDriver(ResourceLocation id) { this.id=id; }
    private static void require(boolean valid,String message) { if(!valid)throw new IllegalStateException(message); }
    public boolean step(Minecraft mc,long tick) {
        var menu=mc.player.containerMenu;
        if(stage.equals("DONE"))return true;
        if(tick<ready)return false;
        if(stage.equals("INIT")) {
            require(menu instanceof CraftingMenu||menu instanceof InventoryMenu,"A native crafting menu is required for "+id);
            width=menu instanceof CraftingMenu?3:2;
            for(int i=1;i<=width*width;i++)require(menu.getSlot(i).getItem().isEmpty(),"Crafting grid must start empty");
            require(menu.getCarried().isEmpty(),"Cursor must start empty");
            var recipe=mc.getConnection().getRecipeManager().byKey(id).orElseThrow(()->new IllegalStateException("Server recipe not received: "+id)).value();
            require(recipe instanceof CraftingRecipe,"Crafting recipe required: "+id);
            var crafting=(CraftingRecipe)recipe;require(crafting.canCraftInDimensions(width,width),"Recipe does not fit: "+id);
            result=crafting.getResultItem(mc.level.registryAccess()).copy();require(!result.isEmpty(),"Known recipe output required");
            if(crafting instanceof ShapedRecipe shaped) {
                for(int row=0;row<shaped.getHeight();row++)for(int col=0;col<shaped.getWidth();col++) {
                    var ingredient=shaped.getIngredients().get(row*shaped.getWidth()+col);
                    if(!ingredient.isEmpty())inputs.add(new Input(1+row*width+col,ingredient));
                }
            } else {
                int slot=1;for(var ingredient:crafting.getIngredients())if(!ingredient.isEmpty())inputs.add(new Input(slot++,ingredient));
            }
            require(!inputs.isEmpty(),"Recipe must consume real materials");before=inventory(mc);expectedCount=mc.player.getInventory().countItem(result.getItem())+result.getCount();started=tick;stage="PICK";
        }
        require(tick-started<500,"Native crafting timed out: "+id+" stage="+stage);
        if(stage.equals("PICK")) {
            if(cursor==inputs.size()) { stage="RESULT";ready=tick+4;return false; }
            source=-1;var input=inputs.get(cursor);
            for(int i=0;i<menu.slots.size();i++)if(menu.slots.get(i).container==mc.player.getInventory()&&input.ingredient().test(menu.getSlot(i).getItem())) {source=i;break;}
            require(source>=0,"Missing native ingredient for "+id+" grid="+input.gridSlot());
            click(mc,source,0,ClickType.PICKUP);stage="DEPOSIT";
        } else if(stage.equals("DEPOSIT")) {
            require(!menu.getCarried().isEmpty(),"Native source pickup failed: "+id);
            click(mc,inputs.get(cursor).gridSlot(),1,ClickType.PICKUP);stage="RETURN";
        } else if(stage.equals("RETURN")) {
            if(!menu.getCarried().isEmpty())click(mc,source,0,ClickType.PICKUP);
            require(menu.getCarried().isEmpty(),"Ingredient remainder must return to its source slot");cursor++;stage="PICK";
        } else if(stage.equals("RESULT")) {
            var output=menu.getSlot(0).getItem();if(!ItemStack.isSameItemSameComponents(output,result)||output.getCount()!=result.getCount())return false;
            boolean room=mc.player.getInventory().items.stream().anyMatch(stack->stack.isEmpty()||ItemStack.isSameItemSameComponents(stack,result)&&stack.getCount()+result.getCount()<=stack.getMaxStackSize());
            require(room,"Native inventory has no room for output of "+id);
            click(mc,0,0,ClickType.QUICK_MOVE);stage="VERIFY";ready=tick+6;return false;
        } else if(stage.equals("VERIFY")) {
            if(mc.player.getInventory().countItem(result.getItem())<expectedCount)return false;
            for(int i=1;i<=width*width;i++)require(menu.getSlot(i).getItem().isEmpty(),"No recipe input or remainder may remain: "+id);
            require(menu.getCarried().isEmpty(),"Craft cannot leave an output on cursor");stage="DONE";
            LegacyOfTheForce.LOGGER.info("ADVENTURE_CRAFT_CLIENT recipe={} output={}",id,result);return true;
        }
        ready=tick+2;return false;
    }
    public CompoundTag proof(Minecraft mc) {
        require(stage.equals("DONE"),"Craft not completed");var tag=new CompoundTag();tag.putString("recipe",id.toString());tag.putInt("grid",width);
        tag.put("before",before);tag.put("after",inventory(mc));tag.put("result",result.save(mc.level.registryAccess()));tag.putInt("clicks",clicks);return tag;
    }
    private void click(Minecraft mc,int slot,int button,ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);clicks++; }
    private static CompoundTag inventory(Minecraft mc) { var tag=new CompoundTag();tag.put("Inventory",mc.player.getInventory().save(new ListTag()));return tag; }
}
