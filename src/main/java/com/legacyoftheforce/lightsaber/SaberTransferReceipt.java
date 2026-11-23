package com.legacyoftheforce.lightsaber;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.nbt.*;

/** The receipt and the changed inventory share one native player save. */
public record SaberTransferReceipt(CompoundTag tag) {
    public static final Codec<SaberTransferReceipt> CODEC=CompoundTag.CODEC.xmap(SaberTransferReceipt::new,SaberTransferReceipt::tag);
    public record Stamp(long revision,UUID operation) {}
    public SaberTransferReceipt {tag=tag.copy();}
    @Override public CompoundTag tag() {return tag.copy();}
    public static SaberTransferReceipt empty() {var tag=new CompoundTag();tag.putInt("schemaVersion",1);tag.putLong("revision",0);return new SaberTransferReceipt(tag);}
    public Optional<Stamp> stamp() {
        if(tag.sizeInBytes()>1024||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("revision",Tag.TAG_LONG)||tag.getLong("revision")<0)return Optional.empty();
        long revision=tag.getLong("revision");if(revision==0)return tag.contains("operation")?Optional.empty():Optional.of(new Stamp(0,null));
        return tag.hasUUID("operation")&&!tag.getUUID("operation").equals(new UUID(0,0))?Optional.of(new Stamp(revision,tag.getUUID("operation"))):Optional.empty();
    }
    public SaberTransferReceipt next(UUID operation) {
        var old=stamp().orElseThrow(()->new IllegalStateException("Unsupported saber transfer receipt"));
        if(old.revision()==Long.MAX_VALUE||operation==null||operation.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid transfer revision");
        var result=tag.copy();result.putLong("revision",old.revision()+1);result.putUUID("operation",operation);return new SaberTransferReceipt(result);
    }
}
