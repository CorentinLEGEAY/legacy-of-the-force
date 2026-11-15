package com.legacyoftheforce.bosses;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;

/** Saved with the native player inventory, and retained across death. */
public record BossLootReceipt(CompoundTag tag) {
    public static final Codec<BossLootReceipt> CODEC = CompoundTag.CODEC.xmap(BossLootReceipt::new, BossLootReceipt::tag);
    public BossLootReceipt { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    public record Stamp(long revision, UUID operation) {}
    public static BossLootReceipt empty() { var tag = new CompoundTag(); tag.putInt("schemaVersion",1); tag.putLong("revision",0); return new BossLootReceipt(tag); }
    public Optional<Stamp> stamp() {
        if (tag.sizeInBytes()>1024 || !tag.contains("schemaVersion",Tag.TAG_INT) || tag.getInt("schemaVersion")!=1 || !tag.contains("revision",Tag.TAG_LONG) || tag.getLong("revision")<0) return Optional.empty();
        long revision=tag.getLong("revision");
        if (revision==0) return tag.contains("operation") ? Optional.empty() : Optional.of(new Stamp(0,null));
        return tag.hasUUID("operation") && !tag.getUUID("operation").equals(new UUID(0,0)) ? Optional.of(new Stamp(revision,tag.getUUID("operation"))) : Optional.empty();
    }
    public BossLootReceipt next(UUID operation) {
        var current=stamp().orElseThrow();
        if (current.revision()==Long.MAX_VALUE || operation==null || operation.equals(new UUID(0,0))) throw new IllegalArgumentException("Invalid loot receipt advance");
        var result=tag.copy();result.putLong("revision",current.revision()+1);result.putUUID("operation",operation);return new BossLootReceipt(result);
    }
}
