package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Learned forms and selection persist; no active counter or combo is restored. */
public final class SaberDiscipline implements INBTSerializable<CompoundTag> {
    private final Set<SaberForm> learned = EnumSet.noneOf(SaberForm.class);
    private SaberForm selected = SaberForm.BASIC;
    private long nextChange;
    private CompoundTag retained = new CompoundTag();
    private boolean unavailable;
    boolean knows(SaberForm form) { return !unavailable && (form == SaberForm.BASIC || learned.contains(form)); }
    boolean learn(SaberForm form) { return !unavailable && form != SaberForm.BASIC && learned.add(form); }
    SaberForm selected() { return unavailable ? SaberForm.BASIC : selected; }
    boolean select(SaberForm form, long now) {
        if (!knows(form) || now < 0 || now < nextChange) return false;
        selected = form; nextChange = now > Long.MAX_VALUE-SaberConfig.FORM_INTERVAL.get() ? Long.MAX_VALUE : now+SaberConfig.FORM_INTERVAL.get(); return true;
    }
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        if (unavailable) return retained.copy();
        var tag=retained.copy();tag.putInt("schemaVersion",1);tag.putString("selected",selected.key());tag.putLong("nextChange",nextChange);
        var list=new ListTag();learned.stream().sorted().forEach(form->list.add(StringTag.valueOf(form.key())));tag.put("learned",list);return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag) {
        retained=tag.copy();unavailable=true;learned.clear();selected=SaberForm.BASIC;nextChange=0;
        if(tag.sizeInBytes()>4096||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("selected",Tag.TAG_STRING)
                ||!tag.contains("nextChange",Tag.TAG_LONG)||tag.getLong("nextChange")<0||!tag.contains("learned",Tag.TAG_LIST))return;
        var entries=tag.getList("learned",Tag.TAG_STRING);if(!entries.equals(tag.get("learned"))||entries.size()>4)return;
        for(var entry:entries){var form=SaberForm.parse(entry.getAsString());if(form.isEmpty()||form.get()==SaberForm.BASIC||!learned.add(form.get())){learned.clear();return;}}
        var choice=SaberForm.parse(tag.getString("selected"));if(choice.isEmpty()||choice.get()!=SaberForm.BASIC&&!learned.contains(choice.get()))return;
        selected=choice.get();nextChange=tag.getLong("nextChange");unavailable=false;
    }
}
