package com.legacyoftheforce.rpg;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All eligibility states and costs shown by the tree are projected from the server. */
public final class SkillMenu extends AbstractContainerMenu {
    public static final List<List<String>> BRANCHES=List.of(List.of("sense","leap","speed","reflexes","freeze","levitate","throw","wave","dominate"),List.of("protect","heal","support"),List.of("fear","choke","drain","lightning","chain_lightning"));
    public static final List<String> IDS=BRANCHES.stream().flatMap(Collection::stream).toList();
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,LegacyOfTheForce.MOD_ID);
    public static final Supplier<MenuType<SkillMenu>> TYPE=MENUS.register("skills",()->new MenuType<>(SkillMenu::new,FeatureFlags.DEFAULT_FLAGS));
    private final Inventory inventory;private final DataSlot level=DataSlot.standalone(),points=DataSlot.standalone(),alignment=DataSlot.standalone(),status=DataSlot.standalone();private final List<DataSlot> states=new ArrayList<>(),levels=new ArrayList<>(),costs=new ArrayList<>(),parents=new ArrayList<>();private final List<DataSlot> ranks=new ArrayList<>(),uses=new ArrayList<>(),thresholds=new ArrayList<>(),energyPercent=new ArrayList<>();private long nextRequest;
    public static void register(IEventBus bus) {MENUS.register(bus);}
    public static void open(ServerPlayer actor) {if(actor.server.isSameThread()&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator())actor.openMenu(new SimpleMenuProvider((id,inventory,player)->new SkillMenu(id,inventory),Component.translatable("power.legacyoftheforce.tree")));}
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {event.getDispatcher().register(net.minecraft.commands.Commands.literal("lotf").then(net.minecraft.commands.Commands.literal("skill").then(net.minecraft.commands.Commands.literal("menu").executes(ctx->{open(ctx.getSource().getPlayerOrException());return 1;}))));}
    public SkillMenu(int id,Inventory inventory) {super(TYPE.get(),id);this.inventory=inventory;addDataSlot(level);addDataSlot(points);addDataSlot(alignment);addDataSlot(status);for(int index=0;index<IDS.size();index++) {var state=DataSlot.standalone();var required=DataSlot.standalone();var cost=DataSlot.standalone();var parent=DataSlot.standalone();parent.set(-1);states.add(state);levels.add(required);costs.add(cost);parents.add(parent);addDataSlot(state);addDataSlot(required);addDataSlot(cost);addDataSlot(parent);for(var list:List.of(ranks,uses,thresholds,energyPercent)){var value=DataSlot.standalone();list.add(value);addDataSlot(value);}}}
    public int rank(int index){return ranks.get(index).get();}public int uses(int index){return uses.get(index).get();}public int threshold(int index){return thresholds.get(index).get();}public int energyPercent(int index){return energyPercent.get(index).get();}
    public int level() {return level.get();}public int points() {return points.get();}public int alignment() {return alignment.get();}public int state(int index) {return states.get(index).get();}public int required(int index) {return levels.get(index).get();}public int cost(int index) {return costs.get(index).get();}public int parent(int index) {return parents.get(index).get();}public int status() {return status.get();}
    @Override public void broadcastChanges() {
        if(inventory.player instanceof ServerPlayer actor) {var progress=actor.getData(ProgressAttachments.PLAYER_PROGRESS).snapshot();var learned=actor.getData(ProgressAttachments.LEARNED_POWERS);level.set(progress.level());points.set(Math.min(32767,progress.skillPoints()));alignment.set(progress.alignmentTenths());
            for(int index=0;index<IDS.size();index++) {String id="legacyoftheforce:"+IDS.get(index);var definition=PowerCatalog.get(id);int state=0;
                if(definition.isPresent()) {var rule=definition.get();levels.get(index).set(rule.requirement().requiredLevel());costs.get(index).set(rule.points());parents.get(index).set(rule.prerequisite().isEmpty()?-1:IDS.indexOf(rule.prerequisite().substring(rule.prerequisite().indexOf(':')+1)));boolean eligible=rule.requirement().isEligible(progress);if(learned.knows(id))state=eligible?3:2;else if(learned.canLearn(id)&&eligible&&progress.skillPoints()>=rule.points()&&(rule.prerequisite().isEmpty()||learned.knows(rule.prerequisite())))state=1;}
                else {levels.get(index).set(101);costs.get(index).set(0);parents.get(index).set(-1);}states.get(index).set(state);var mastery=actor.getData(ProgressAttachments.POWER_MASTERY).snapshot(id);ranks.get(index).set(mastery.rank());uses.get(index).set(mastery.significantUses());thresholds.get(index).set(mastery.nextThreshold());energyPercent.get(index).set((int)Math.round(mastery.energyMultiplier()*100));
            }
        }super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int button) {if(!(player instanceof ServerPlayer actor)||actor.isFakePlayer()||!actor.server.isSameThread()||actor.containerMenu!=this||!stillValid(actor)||button<0||button>=IDS.size())return false;long now=actor.server.overworld().getGameTime();if(now<nextRequest)return false;nextRequest=now+10;boolean learned=new ProgressService().learnPower(actor.getUUID(),"legacyoftheforce:"+IDS.get(button));status.set(learned?1:2);broadcastChanges();return learned;}
    @Override public boolean stillValid(Player player) {return player==inventory.player&&player.isAlive()&&!player.isSpectator();}
    @Override public ItemStack quickMoveStack(Player player,int index) {return ItemStack.EMPTY;}
}
