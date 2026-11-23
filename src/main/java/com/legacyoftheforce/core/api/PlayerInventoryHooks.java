package com.legacyoftheforce.core.api;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/** Composed at mod construction; modules keep their pending-transfer implementations private. */
public final class PlayerInventoryHooks {
    private static final List<Predicate<ServerPlayer>> POLICIES=new ArrayList<>();
    public static void register(Predicate<ServerPlayer> policy){if(POLICIES.size()>=32)throw new IllegalStateException("Inventory policy budget exhausted");POLICIES.add(Objects.requireNonNull(policy));}
    public static boolean ready(ServerPlayer actor){
        return actor!=null&&actor.server.isSameThread()&&actor.isAlive()&&!actor.isFakePlayer()&&!actor.isSpectator()&&!actor.hasDisconnected()
                &&actor.containerMenu.getCarried().isEmpty()&&actor.inventoryMenu.getCarried().isEmpty()&&POLICIES.stream().allMatch(p->p.test(actor));
    }
    private PlayerInventoryHooks(){}
}
