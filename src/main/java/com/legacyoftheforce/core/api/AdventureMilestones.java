package com.legacyoftheforce.core.api;

import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;

/** Only business owners stage a witness, in the same native save as their effective operation. */
public final class AdventureMilestones {
    public enum Flag { SABER_ASSEMBLED, CRYSTAL_MOUNTED, SPEEDER_ASSEMBLED;
        public ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath("legacyoftheforce",name().toLowerCase(Locale.ROOT));}
    }
    private static final String ID="legacyoftheforce:adventure_milestones";
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"legacyoftheforce");
    private static final Supplier<AttachmentType<State>> TYPE=TYPES.register("adventure_milestones",()->AttachmentType.builder(State::empty).serialize(State.CODEC).copyOnDeath().build());
    public record State(CompoundTag tag){
        public static final Codec<State> CODEC=CompoundTag.CODEC.xmap(State::new,State::tag);
        public State {tag=tag.copy();}
        @Override public CompoundTag tag(){return tag.copy();}
        public static State empty(){var t=new CompoundTag();t.putInt("schemaVersion",1);t.putInt("flags",0);return new State(t);}
        public OptionalInt flags(){if(tag.sizeInBytes()>1024||!tag.contains("schemaVersion",Tag.TAG_INT)||tag.getInt("schemaVersion")!=1||!tag.contains("flags",Tag.TAG_INT)||tag.getInt("flags")<0||tag.getInt("flags")>7)return OptionalInt.empty();return OptionalInt.of(tag.getInt("flags"));}
    }
    public static void register(IEventBus bus){TYPES.register(bus);}
    public static State state(ServerPlayer actor){return actor.getData(TYPE);}
    /** Unknown versions remain opaque; an optional guide never blocks the underlying craft. */
    public static State stage(ServerPlayer actor,Flag flag){
        if(!actor.server.isSameThread()||actor.isFakePlayer())throw new IllegalArgumentException("Invalid witness owner");
        var before=state(actor);var flags=before.flags();if(flags.isEmpty())return before;
        var tag=before.tag();tag.putInt("flags",flags.getAsInt()|1<<flag.ordinal());var next=new State(tag);actor.setData(TYPE,next);return next;
    }
    public static Set<ResourceLocation> inspect(ServerPlayer actor){
        if(!actor.server.isSameThread())return Set.of();var value=state(actor).flags();if(value.isEmpty())return Set.of();
        var result=new HashSet<ResourceLocation>();for(var flag:Flag.values())if((value.getAsInt()&1<<flag.ordinal())!=0)result.add(flag.id());return Set.copyOf(result);
    }
    public static void requireProof(ServerPlayer actor,CompoundTag saved,State expected)throws IOException{
        if(!saved.getCompound("neoforge:attachments").getCompound(ID).equals(expected.tag())||!state(actor).equals(expected))throw new IOException("Adventure witness differs from native aggregate");
    }
    public static void changed(ServerPlayer actor){try{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new AdventureProgressChanged(actor.getUUID()));}catch(RuntimeException failure){com.legacyoftheforce.LegacyOfTheForce.LOGGER.error("Adventure witness consumer failed; reconcile saved owner evidence",failure);}}
    private AdventureMilestones(){}
}
