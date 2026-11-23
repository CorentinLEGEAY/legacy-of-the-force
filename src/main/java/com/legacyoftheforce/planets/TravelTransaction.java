package com.legacyoftheforce.planets;

import com.legacyoftheforce.core.operation.NativeInventoryImage;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;

record TravelTransaction(CompoundTag tag) {
    enum Kind { DISCOVER, OUTBOUND, RETURN }
    enum Phase { PREPARED, PAID, ARRIVED, DONE, ABORTED }
    record View(UUID owner, UUID operation, Kind kind, Phase phase, CompoundTag before, CompoundTag after,
                TravelPoint source, TravelPoint target, TravelPoint fallback, int cost, PlanetId planet) {}
    TravelTransaction { tag = tag.copy(); }
    @Override public CompoundTag tag() { return tag.copy(); }
    static void image(CompoundTag image) {
        if (image.sizeInBytes() > 81920 || !image.contains("Inventory", Tag.TAG_LIST) || !image.contains("atlas", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Unsupported travel image");
        var inventory = image.getList("Inventory", Tag.TAG_COMPOUND);
        if (!inventory.equals(image.get("Inventory"))) throw new IllegalArgumentException("Invalid inventory image");
        NativeInventoryImage.validate(inventory); new TravelState(image.getCompound("atlas")).view().orElseThrow();
    }
    static String material(Kind kind){return material(kind,PlanetId.ILUM);}
    static String material(Kind kind,PlanetId planet){return kind==Kind.DISCOVER?planet.chartId():"legacyoftheforce:speeder_energy_cell";}
    static TravelTransaction prepare(UUID owner, UUID operation, Kind kind, CompoundTag before, CompoundTag after, TravelPoint source, TravelPoint target, TravelPoint fallback, int cost) {
        return prepare(owner,operation,kind,before,after,source,target,fallback,cost,PlanetId.ILUM,false);
    }
    static TravelTransaction prepare(UUID owner,UUID operation,Kind kind,CompoundTag before,CompoundTag after,TravelPoint source,TravelPoint target,TravelPoint fallback,int cost,PlanetId planet){return prepare(owner,operation,kind,before,after,source,target,fallback,cost,planet,true);}
    private static TravelTransaction prepare(UUID owner,UUID operation,Kind kind,CompoundTag before,CompoundTag after,TravelPoint source,TravelPoint target,TravelPoint fallback,int cost,PlanetId planet,boolean extended){
        var raw = new CompoundTag(); raw.putInt("schemaVersion", extended?2:1);if(extended)raw.putString("planet",planet.id().toString()); raw.putUUID("owner", owner); raw.putUUID("operation", operation);
        raw.putString("kind", kind.name()); raw.putString("phase", Phase.PREPARED.name()); raw.put("before", before); raw.put("after", after);
        raw.put("source", source.tag()); raw.put("target", target.tag()); if (fallback != null) raw.put("fallback", fallback.tag()); raw.putInt("cost", cost);
        var record = new TravelTransaction(raw); record.view(); return record;
    }
    View view() {
        if (tag.sizeInBytes() > 262144 || !tag.contains("schemaVersion", Tag.TAG_INT) || (tag.getInt("schemaVersion") != 1&&tag.getInt("schemaVersion")!=2) || !tag.hasUUID("owner") || !tag.hasUUID("operation")
                || !tag.contains("kind", Tag.TAG_STRING) || !tag.contains("phase", Tag.TAG_STRING) || !tag.contains("before", Tag.TAG_COMPOUND) || !tag.contains("after", Tag.TAG_COMPOUND)
                || !tag.contains("source", Tag.TAG_COMPOUND) || !tag.contains("target", Tag.TAG_COMPOUND) || !tag.contains("cost", Tag.TAG_INT)) throw new IllegalArgumentException("Unsupported travel journal");
        var kind = Kind.valueOf(tag.getString("kind")); var phase = Phase.valueOf(tag.getString("phase")); var before = tag.getCompound("before"); var after = tag.getCompound("after"); image(before); image(after);
        var original = new TravelState(before.getCompound("atlas")); var prior = original.view().orElseThrow(); var paid = new TravelState(after.getCompound("atlas")).view().orElseThrow();
        var source = TravelPoint.read(tag.getCompound("source")); var target = TravelPoint.read(tag.getCompound("target"));
        var fallback = tag.contains("fallback", Tag.TAG_COMPOUND) ? TravelPoint.read(tag.getCompound("fallback")) : null;
        var operation = tag.getUUID("operation"); int cost = tag.getInt("cost");
        boolean extended=tag.getInt("schemaVersion")==2;var planet=extended?PlanetId.from(net.minecraft.resources.ResourceLocation.tryParse(tag.getString("planet"))).orElseThrow():PlanetId.ILUM;
        var expected=extended?original.prepare(operation,kind,source,paid.readyAt(),planet):original.prepare(operation,kind,source,paid.readyAt());
        if (tag.getUUID("owner").equals(new UUID(0, 0)) || operation.equals(new UUID(0, 0)) || prior.pending() != null || prior.revision() == Long.MAX_VALUE
                || paid.revision() != prior.revision() + 1 || !operation.equals(paid.pending()) || paid.readyAt() < prior.readyAt()
                || !expected.tag().equals(after.getCompound("atlas"))) throw new IllegalArgumentException("Travel receipt/state conflict");
        if (kind == Kind.DISCOVER && (prior.known(planet) || cost != 1 || !target.equals(source) || fallback != null)
                || kind == Kind.OUTBOUND && (!prior.known(planet) || !source.dimension().equals(Level.OVERWORLD.location()) || !target.dimension().equals(planet.id()) || fallback != null || cost < 1 || cost > 64)
                || kind == Kind.RETURN && (!prior.returnCredit() || !source.dimension().equals(planet.id())||!prior.known(planet) || !target.equals(prior.home()) || cost != 0 || fallback == null || !fallback.dimension().equals(Level.OVERWORLD.location()))) throw new IllegalArgumentException("Invalid journey entitlement");
        debit(before.getList("Inventory", Tag.TAG_COMPOUND), after.getList("Inventory", Tag.TAG_COMPOUND), material(kind,planet), cost);
        return new View(tag.getUUID("owner"), operation, kind, phase, before.copy(), after.copy(), source, target, fallback, cost,planet);
    }
    private static void debit(ListTag before, ListTag after, String material, int amount) {
        var remaining = new HashMap<Integer, CompoundTag>(); for (var entry : after) { var item = (CompoundTag)entry; remaining.put(item.getByte("Slot") & 255, item); }
        int paid = 0;
        for (var entry : before) {
            var item = (CompoundTag)entry; var next = remaining.remove(item.getByte("Slot") & 255); int delta = item.getInt("count") - (next == null ? 0 : next.getInt("count"));
            if (delta < 0 || delta > 0 && !item.getString("id").equals(material)) throw new IllegalArgumentException("Travel cannot credit or remove other materials");
            if (next != null) { var a = item.copy(); var b = next.copy(); a.remove("count"); b.remove("count"); if (!a.equals(b)) throw new IllegalArgumentException("Travel cannot replace item components"); }
            paid += delta;
        }
        if (!remaining.isEmpty() || paid != amount) throw new IllegalArgumentException("Travel debit mismatch");
    }
    CompoundTag activeImage() { var after = view().after(); after.put("atlas", new TravelState(after.getCompound("atlas")).activate().tag()); return after; }
    TravelTransaction phase(Phase phase) {
        var old = view().phase();
        if (!(old == Phase.PREPARED && (phase == Phase.PAID || phase == Phase.ABORTED) || old == Phase.PAID && phase == Phase.ARRIVED || old == Phase.ARRIVED && phase == Phase.DONE)) throw new IllegalArgumentException("Invalid travel transition");
        var raw = tag.copy(); raw.putString("phase", phase.name()); return new TravelTransaction(raw);
    }
}
