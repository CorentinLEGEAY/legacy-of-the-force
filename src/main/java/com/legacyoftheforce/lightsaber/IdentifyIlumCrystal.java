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
    public static final MapCodec<IdentifyIlumCrystal> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance).and(
            com.mojang.serialization.Codec.STRING.validate(value -> KyberKind.fromPlanet(value).isPresent()
                    ? com.mojang.serialization.DataResult.success(value) : com.mojang.serialization.DataResult.error(() -> "Unknown crystal origin"))
                    .optionalFieldOf("planet", "legacyoftheforce:ilum").forGetter(function -> function.planet)).apply(instance, IdentifyIlumCrystal::new));
    private final String planet;
    private IdentifyIlumCrystal(List<LootItemCondition> conditions, String planet) { super(conditions); this.planet = planet; }
    @Override public LootItemFunctionType<IdentifyIlumCrystal> getType() { return SaberContent.IDENTIFY_ILUM.get(); }
    @Override protected ItemStack run(ItemStack stack, LootContext context) {
        if (!context.getLevel().dimension().location().toString().equals(planet)) return ItemStack.EMPTY;
        if (!stack.is(SaberContent.KYBER.get()) || stack.getCount() != 1) return ItemStack.EMPTY;
        if (stack.has(SaberContent.KYBER_DATA.get())) {
            var data = stack.get(SaberContent.KYBER_DATA.get());
            return data.state().isPresent() && data.kind().map(kind -> kind.planet().equals(planet)).orElse(false) ? stack : ItemStack.EMPTY;
        }
        double purity = quality(context, SaberConfig.MINED_PURITY_MIN.get(), SaberConfig.MINED_PURITY_MAX.get());
        double stability = quality(context, SaberConfig.MINED_STABILITY_MIN.get(), SaberConfig.MINED_STABILITY_MAX.get());
        stack.set(SaberContent.KYBER_DATA.get(), planet.equals("legacyoftheforce:ilum") ? KyberData.blue(UUID.randomUUID(), purity, stability)
                : KyberData.natural(KyberKind.fromPlanet(planet).orElseThrow(), UUID.randomUUID(), purity, stability)); return stack;
    }
    private static double quality(LootContext context, double a, double b) { return Math.min(a, b) + context.getRandom().nextDouble() * Math.abs(b - a); }
}
