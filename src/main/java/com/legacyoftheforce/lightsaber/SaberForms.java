package com.legacyoftheforce.lightsaber;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.legacyoftheforce.core.api.PlayerInventoryHooks;
import com.legacyoftheforce.core.api.PlayerProgressAccess;
import com.legacyoftheforce.core.operation.VerifiedPlayerSaveBarrier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Forms affect server combat only when the learned discipline and physical loadout agree. */
final class SaberForms {
    record Profile(SaberForm form,double damage,double attackCost,double guardCost,double parry) {}
    private static final Profile BASIC=new Profile(SaberForm.BASIC,1,1,1,0);
    static int required(SaberForm form){return form==SaberForm.BASIC?1:SaberConfig.FORMS.get(form).level().get();}
    static boolean twins(ServerPlayer actor) {
        var main=actor.getMainHandItem();var other=actor.getOffhandItem();
        if(SaberCombat.profile(main).isEmpty()||SaberCombat.profile(other).isEmpty())return false;
        var a=main.get(SaberContent.ASSEMBLY.get());var b=other.get(SaberContent.ASSEMBLY.get());
        return a.type()==SaberType.SINGLE&&b.type()==SaberType.SINGLE&&!a.tag().getUUID("saberId").equals(b.tag().getUUID("saberId"))
                &&!a.crystal().orElseThrow().state().orElseThrow().id().equals(b.crystal().orElseThrow().state().orElseThrow().id());
    }
    private static boolean eligible(ServerPlayer actor,SaberForm form) {
        if(form==SaberForm.BASIC)return true;
        if(!actor.getData(SaberContent.DISCIPLINE).knows(form)||PlayerProgressAccess.snapshot(actor).filter(progress->!progress.unavailable()&&progress.level()>=required(form)).isEmpty()
                ||SaberCombat.profile(actor.getMainHandItem()).isEmpty())return false;
        var assembly=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());return form.supports(assembly.type())&&(form!=SaberForm.TWIN||twins(actor));
    }
    static Profile active(ServerPlayer actor) {
        var form=actor.getData(SaberContent.DISCIPLINE).selected();if(form==SaberForm.BASIC||!eligible(actor,form))return BASIC;
        var values=SaberConfig.FORMS.get(form);return new Profile(form,values.damage().get(),values.attackCost().get(),values.guardCost().get(),values.parry().get());
    }
    static UUID comboKey(ServerPlayer actor) {
        var main=actor.getMainHandItem().get(SaberContent.ASSEMBLY.get());if(main==null||!main.supported())return new UUID(0,0);var first=main.tag().getUUID("saberId");
        if(active(actor).form()!=SaberForm.TWIN)return first;
        var second=actor.getOffhandItem().get(SaberContent.ASSEMBLY.get()).tag().getUUID("saberId");
        return UUID.nameUUIDFromBytes(("saber-pair/"+first+"/"+second).getBytes(StandardCharsets.UTF_8));
    }
    static boolean select(ServerPlayer actor,SaberForm form) {
        if(!actor.server.isSameThread()||!actor.isAlive()||actor.isFakePlayer()||actor.isSpectator()||actor.containerMenu!=actor.inventoryMenu
                ||!PlayerInventoryHooks.ready(actor)||!eligible(actor,form)||!actor.getData(SaberContent.DISCIPLINE).select(form,actor.server.overworld().getGameTime()))return false;
        actor.getData(SaberContent.ENDURANCE).clearCombat();
        try{new VerifiedPlayerSaveBarrier().persist(actor);}catch(java.io.IOException|RuntimeException failure){
            com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Combat form save uncertain; native aggregate retained",failure);
            actor.connection.disconnect(Component.translatable("saber.legacyoftheforce.menu_recovery"));return false;
        }
        SaberCombat.refreshAttributes(actor);actor.displayClientMessage(Component.translatable("saber.legacyoftheforce.form_selected",Component.translatable("saber.legacyoftheforce.form_"+form.key())),true);return true;
    }
    static boolean next(ServerPlayer actor) {
        int current=actor.getData(SaberContent.DISCIPLINE).selected().ordinal();
        for(int step=1;step<=SaberForm.values().length;step++){var next=SaberForm.values()[(current+step)%SaberForm.values().length];if(eligible(actor,next))return select(actor,next);}return false;
    }
    static void list(ServerPlayer actor) {
        for(var form:SaberForm.values())actor.sendSystemMessage(Component.translatable("saber.legacyoftheforce.form_list",Component.translatable("saber.legacyoftheforce.form_"+form.key()),required(form),Component.translatable(actor.getData(SaberContent.DISCIPLINE).knows(form)?"saber.legacyoftheforce.form_yes":"saber.legacyoftheforce.form_no"),Component.translatable(active(actor).form()==form?"saber.legacyoftheforce.form_yes":"saber.legacyoftheforce.form_no")));
    }
    private SaberForms(){}
}
