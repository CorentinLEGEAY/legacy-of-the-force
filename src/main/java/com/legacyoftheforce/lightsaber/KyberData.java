package com.legacyoftheforce.lightsaber;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** An identified crystal instance, never a recipe or a color-only token. */
public record KyberData(CompoundTag tag) {
    public static final Codec<KyberData> CODEC = CompoundTag.CODEC.xmap(KyberData::new, KyberData::tag);
    public KyberData { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record State(UUID id, double purity, double stability, int bondLevel, long bondProgress) {}
    public static KyberData blue(UUID id, double purity, double stability) {
        var tag = new CompoundTag(); tag.putInt("schemaVersion", 1); tag.putUUID("crystalId", id);
        tag.putString("originPlanet", "legacyoftheforce:ilum"); tag.putString("baseKind", "blue");
        tag.putString("state", "natural"); tag.putString("resonance", "defense");
        tag.putDouble("purity", purity); tag.putDouble("stability", stability); tag.putInt("bondLevel", 0); tag.putLong("bondProgress", 0);
        var result = new KyberData(tag); if (result.state().isEmpty()) throw new IllegalArgumentException("Invalid blue crystal"); return result;
    }
    public static KyberData natural(KyberKind kind,UUID id,double purity,double stability){
        var tag=blue(id,purity,stability).tag();tag.putInt("schemaVersion",2);tag.putString("originPlanet",kind.planet());tag.putString("baseKind",kind.color());tag.putString("visibleColor",kind.color());tag.putString("resonance",kind.resonance());
        var result=new KyberData(tag);if(result.state().isEmpty())throw new IllegalArgumentException("Invalid planetary crystal");return result;
    }
    public Optional<KyberKind> kind(){return KyberKind.fromPlanet(tag.getString("originPlanet"));}
    public String visibleColor(){return tag.getString("state").equals("corrupted")?"red":tag.getString("state").equals("purified")?"white":kind().map(KyberKind::color).orElse("blue");}
    public Optional<State> state() {
        if (tag.sizeInBytes() > 8192 || !tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") != 1&&tag.getInt("schemaVersion")!=2)
                || !tag.hasUUID("crystalId") || tag.getUUID("crystalId").equals(new UUID(0, 0))) return Optional.empty();
        for (var key : new String[]{"originPlanet", "baseKind", "state", "resonance"}) if (!tag.contains(key, Tag.TAG_STRING)) return Optional.empty();
        var kind=kind();if(kind.isEmpty()||!tag.getString("baseKind").equals(kind.get().color())||!tag.getString("resonance").equals(kind.get().resonance())
                ||!java.util.Set.of("natural","corrupted","purified").contains(tag.getString("state"))
                || !tag.contains("purity", Tag.TAG_DOUBLE) || !tag.contains("stability", Tag.TAG_DOUBLE)
                || !tag.contains("bondLevel", Tag.TAG_INT) || !tag.contains("bondProgress", Tag.TAG_LONG)) return Optional.empty();
        if(tag.getInt("schemaVersion")==1&&(kind.get()!=KyberKind.ILUM||!tag.getString("state").equals("natural")))return Optional.empty();
        if(tag.getInt("schemaVersion")==1&&tag.contains("visibleColor")&&(!tag.contains("visibleColor",Tag.TAG_STRING)||!tag.getString("visibleColor").equals("blue")))return Optional.empty();
        if(tag.contains("ritualHistory")&&!historyValid())return Optional.empty();
        if(tag.getInt("schemaVersion")==2&&(!tag.contains("visibleColor",Tag.TAG_STRING)||!tag.getString("visibleColor").equals(visibleColor())||!historyValid()))return Optional.empty();
        double purity = tag.getDouble("purity"), stability = tag.getDouble("stability");
        int bond = tag.getInt("bondLevel"); long progress = tag.getLong("bondProgress");
        if (!Double.isFinite(purity) || purity < 0 || purity > 1 || !Double.isFinite(stability) || stability < 0 || stability > 1
                || bond < 0 || bond > 4 || progress < 0 || progress > Integer.MAX_VALUE || !KyberBond.valid(tag)) return Optional.empty();
        return Optional.of(new State(tag.getUUID("crystalId"), purity, stability, bond, progress));
    }
    private boolean historyValid(){
        if(!tag.contains("ritualHistory"))return tag.getString("state").equals("natural");
        if(!tag.contains("ritualHistory",Tag.TAG_COMPOUND))return false;var history=tag.getCompound("ritualHistory");
        if(!history.contains("schemaVersion",Tag.TAG_INT)||history.getInt("schemaVersion")!=1||!history.hasUUID("actor")||history.getUUID("actor").equals(new UUID(0,0))
                ||!history.contains("tick",Tag.TAG_LONG)||history.getLong("tick")<0||!history.contains("corruptions",Tag.TAG_INT)||!history.contains("purifications",Tag.TAG_INT))return false;
        int corrupted=history.getInt("corruptions"),purified=history.getInt("purifications");
        return corrupted>=1&&corrupted<=1000000&&purified>=0&&(tag.getString("state").equals("corrupted")?corrupted==purified+1:tag.getString("state").equals("purified")&&corrupted==purified);
    }
    public KyberData transform(boolean purify,UUID actor,long tick){
        if(state().isEmpty()||actor==null||actor.equals(new UUID(0,0))||tick<0||purify&&!tag.getString("state").equals("corrupted")||!purify&&tag.getString("state").equals("corrupted"))throw new IllegalArgumentException("Invalid kyber transition");
        var updated=tag.copy();var history=updated.contains("ritualHistory",Tag.TAG_COMPOUND)?updated.getCompound("ritualHistory").copy():new CompoundTag();
        if(tick<history.getLong("tick"))throw new IllegalArgumentException("Ritual clock moved backwards");
        history.putInt("schemaVersion",1);history.putUUID("actor",actor);history.putLong("tick",tick);String counter=purify?"purifications":"corruptions";history.putInt(counter,Math.addExact(history.getInt(counter),1));
        if(!history.contains("purifications"))history.putInt("purifications",0);
        updated.putInt("schemaVersion",2);updated.putString("state",purify?"purified":"corrupted");updated.putString("visibleColor",purify?"white":"red");updated.put("ritualHistory",history);
        var result=new KyberData(updated);if(result.state().isEmpty())throw new IllegalArgumentException("Unsupported kyber transition result");return result;
    }
}
