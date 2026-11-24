package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Journals keep their internals private; actors cannot move an entity with an unsettled before-image. */
public final class EntityMotionHooks {
    private static final List<BiPredicate<ServerPlayer,Entity>> POLICIES=new ArrayList<>();
    public static void register(BiPredicate<ServerPlayer,Entity> policy) {
        if(POLICIES.size()>=32)throw new IllegalStateException("Entity motion policy budget exhausted");POLICIES.add(Objects.requireNonNull(policy));
    }
    public static boolean ready(ServerPlayer actor,Entity target) {
        if(actor==null||target==null||!actor.server.isSameThread()||actor.level()!=target.level()||target.isRemoved())return false;
        for(var policy:POLICIES)try{if(!policy.test(actor,target))return false;}catch(RuntimeException unavailable){return false;}
        return true;
    }
    private EntityMotionHooks(){}
}
