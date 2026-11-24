package com.legacyoftheforce.lightsaber;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Typed identities prevent a loose crystal from being hidden by a saber's primary UUID. */
final class RareItemIdentity {
    enum Kind { SABER, CRYSTAL }
    record Key(Kind kind,UUID id){Key{Objects.requireNonNull(kind);if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("Nil rare identity");}}
    record Identity(Key primary,Set<Key> keys){Identity{keys=Set.copyOf(keys);if(!keys.contains(primary))throw new IllegalArgumentException("Primary identity missing");}}
    static boolean relevant(ItemStack stack){return stack.is(SaberContent.SABER.get())||stack.is(SaberContent.KYBER.get())||stack.has(SaberContent.ASSEMBLY.get())||stack.has(SaberContent.KYBER_DATA.get());}
    static Optional<Identity> inspect(ItemStack stack){
        if(!relevant(stack)||stack.getCount()!=1)return Optional.empty();
        try{
            if(stack.is(SaberContent.SABER.get())&&!stack.has(SaberContent.KYBER_DATA.get())){
                var assembly=stack.get(SaberContent.ASSEMBLY.get());if(assembly==null||!assembly.supported())return Optional.empty();
                var primary=new Key(Kind.SABER,assembly.tag().getUUID("saberId"));var keys=new HashSet<Key>();keys.add(primary);
                assembly.crystal().ifPresent(crystal->keys.add(new Key(Kind.CRYSTAL,crystal.state().orElseThrow().id())));return Optional.of(new Identity(primary,keys));
            }
            if(stack.is(SaberContent.KYBER.get())&&!stack.has(SaberContent.ASSEMBLY.get())){
                var crystal=stack.get(SaberContent.KYBER_DATA.get());if(crystal==null||crystal.state().isEmpty())return Optional.empty();var primary=new Key(Kind.CRYSTAL,crystal.state().orElseThrow().id());return Optional.of(new Identity(primary,Set.of(primary)));
            }
        }catch(IllegalArgumentException invalid){return Optional.empty();}
        return Optional.empty();
    }
    static boolean encodedRelevant(CompoundTag stack){
        String id=stack.getString("id");var components=stack.getCompound("components");
        return id.equals("legacyoftheforce:lightsaber")||id.equals("legacyoftheforce:ilum_crystal")||components.contains("legacyoftheforce:saber_assembly")||components.contains("legacyoftheforce:kyber");
    }
    static Set<Key> keys(CompoundTag encoded,HolderLookup.Provider registry){
        if(!encodedRelevant(encoded))return Set.of();var stack=ItemStack.parse(registry,encoded).orElseThrow(()->new IllegalArgumentException("Unreadable rare item"));
        return inspect(stack).orElseThrow(()->new IllegalArgumentException("Unsupported or ambiguous rare identity; original retained")).keys();
    }
    static Map<Key,Integer> counts(Iterable<Tag> items,HolderLookup.Provider registry){
        var result=new HashMap<Key,Integer>();for(var raw:items){if(!(raw instanceof CompoundTag stack))throw new IllegalArgumentException("Invalid native item list");for(var key:keys(stack,registry))result.merge(key,stack.getInt("count"),Math::addExact);}return Map.copyOf(result);
    }
    private RareItemIdentity(){}
}
