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
    public Optional<State> state() {
        if (tag.sizeInBytes() > 8192 || !tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1
                || !tag.hasUUID("crystalId") || tag.getUUID("crystalId").equals(new UUID(0, 0))) return Optional.empty();
        for (var key : new String[]{"originPlanet", "baseKind", "state", "resonance"}) if (!tag.contains(key, Tag.TAG_STRING)) return Optional.empty();
        if (!tag.getString("originPlanet").equals("legacyoftheforce:ilum") || !tag.getString("baseKind").equals("blue")
                || !tag.getString("state").equals("natural") || !tag.getString("resonance").equals("defense")
                || !tag.contains("purity", Tag.TAG_DOUBLE) || !tag.contains("stability", Tag.TAG_DOUBLE)
                || !tag.contains("bondLevel", Tag.TAG_INT) || !tag.contains("bondProgress", Tag.TAG_LONG)) return Optional.empty();
        double purity = tag.getDouble("purity"), stability = tag.getDouble("stability");
        int bond = tag.getInt("bondLevel"); long progress = tag.getLong("bondProgress");
        if (!Double.isFinite(purity) || purity < 0 || purity > 1 || !Double.isFinite(stability) || stability < 0 || stability > 1
                || bond < 0 || bond > 4 || progress < 0 || progress > Integer.MAX_VALUE) return Optional.empty();
        return Optional.of(new State(tag.getUUID("crystalId"), purity, stability, bond, progress));
    }
}
