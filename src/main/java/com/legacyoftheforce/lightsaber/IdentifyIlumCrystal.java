package com.legacyoftheforce.lightsaber;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Vanilla loot pipeline: one nonstackable identity, no fortune multiplication or source-block drop. */
public final class IdentifyIlumCrystal extends LootItemConditionalFunction {
    public static final MapCodec<IdentifyIlumCrystal> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance).apply(instance, IdentifyIlumCrystal::new));
    private IdentifyIlumCrystal(List<LootItemCondition> conditions) { super(conditions); }
    @Override public LootItemFunctionType<IdentifyIlumCrystal> getType() { return SaberContent.IDENTIFY_ILUM.get(); }
    @Override protected ItemStack run(ItemStack stack, LootContext context) {
        if (!context.getLevel().dimension().location().toString().equals("legacyoftheforce:ilum")) return ItemStack.EMPTY;
        if (!stack.is(SaberContent.KYBER.get()) || stack.getCount() != 1) return ItemStack.EMPTY;
        if (stack.has(SaberContent.KYBER_DATA.get())) return stack;
        double purity = quality(context, SaberConfig.MINED_PURITY_MIN.get(), SaberConfig.MINED_PURITY_MAX.get());
        double stability = quality(context, SaberConfig.MINED_STABILITY_MIN.get(), SaberConfig.MINED_STABILITY_MAX.get());
        stack.set(SaberContent.KYBER_DATA.get(), KyberData.blue(UUID.randomUUID(), purity, stability)); return stack;
    }
    private static double quality(LootContext context, double a, double b) { return Math.min(a, b) + context.getRandom().nextDouble() * Math.abs(b - a); }
}
