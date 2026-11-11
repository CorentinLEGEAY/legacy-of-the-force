package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import static com.legacyoftheforce.speeder.SpeederBlueprint.*;
import static com.legacyoftheforce.speeder.SpeederModule.Family;

/** Ordered checks mirror SPEED-03; station/material/spawn transactions belong to the garage/vehicle layer. */
public final class BlueprintValidator {
    public enum Error { NONE, FORMAT, TOO_LARGE, UNKNOWN_MODULE, OUTSIDE, OVERLAP, REQUIRED, PILOTS, DETACHED, SEAT_BLOCKED, PROPULSION, TOO_HEAVY, LIFT, ENERGY, STABILITY }
    public record Limits(int width, int height, int depth, int modules, double maxMass, double liftRatio, double stability) {
        public Limits {
            if (width < 1 || width > 12 || height < 1 || height > 7 || depth < 1 || depth > 5 || modules < 4 || modules > 128
                    || !Double.isFinite(maxMass) || maxMass <= 0 || !Double.isFinite(liftRatio) || liftRatio <= 0
                    || !Double.isFinite(stability) || stability < 0 || stability > 1) throw new IllegalArgumentException("Invalid blueprint limits");
        }
    }
    public record Stats(double mass, double thrust, double lift, double capacity, double stability) {}
    public record Result(Error error, Stats stats, Map<ResourceLocation, Integer> bill) {
        public Result { bill = Map.copyOf(bill); }
        public boolean valid() { return error == Error.NONE; }
    }
    private static Result fail(Error error) { return new Result(error, new Stats(0, 0, 0, 0, 0), Map.of()); }
    public static Result inspect(SpeederBlueprint blueprint, Map<ResourceLocation, SpeederModule> catalog, Limits limits) {
        var parsed = blueprint.plan(); if (parsed.isEmpty()) return fail(Error.FORMAT); var plan = parsed.get();
        if (plan.width() > limits.width || plan.height() > limits.height || plan.depth() > limits.depth || plan.modules().size() > limits.modules) return fail(Error.TOO_LARGE);
        if (plan.modules().stream().anyMatch(part -> !catalog.containsKey(part.kind()))) return fail(Error.UNKNOWN_MODULE);
        var cells = new HashMap<Cell, PlacedModule>(); var families = EnumSet.noneOf(Family.class); PlacedModule root = null, pilot = null; int pilots = 0;
        var bill = new HashMap<ResourceLocation, Integer>(); double mass = 0, thrust = 0, lift = 0, capacity = 0, stability = 0;
        for (var part : plan.modules()) {
            var at = part.at();
            if (at.x() < 0 || at.y() < 0 || at.z() < 0 || at.x() >= plan.width() || at.y() >= plan.height() || at.z() >= plan.depth()) return fail(Error.OUTSIDE);
            if (cells.put(at, part) != null) return fail(Error.OVERLAP);
            var definition = catalog.get(part.kind()); families.add(definition.family());
            if (definition.family() == Family.CHASSIS && root == null) root = part;
            if (definition.family() == Family.PILOT) { pilot = part; pilots++; }
            bill.merge(part.kind(), 1, Integer::sum); mass += definition.mass(); lift += definition.lift(); capacity += definition.capacity(); stability += definition.stability();
            if (definition.family() == Family.ENGINE) thrust += switch (part.rotation()) { case 0 -> definition.thrust(); case 2 -> -definition.thrust(); default -> 0; };
        }
        if (families.size() != Family.values().length) return fail(Error.REQUIRED);
        if (pilots != 1) return fail(Error.PILOTS);
        var reached = new HashSet<Cell>(); var queue = new ArrayDeque<PlacedModule>(); queue.add(root); reached.add(root.at());
        while (!queue.isEmpty()) {
            var part = queue.removeFirst(); var definition = catalog.get(part.kind());
            for (var direction : Direction.values()) {
                var next = cells.get(offset(part.at(), direction));
                if (next != null && !reached.contains(next.at()) && definition.connects(direction, part.rotation())
                        && catalog.get(next.kind()).connects(direction.getOpposite(), next.rotation())) { reached.add(next.at()); queue.add(next); }
            }
        }
        if (reached.size() != cells.size()) return fail(Error.DETACHED);
        var front = Direction.NORTH; for (int i = 0; i < pilot.rotation(); i++) front = front.getClockWise();
        if (cells.containsKey(offset(pilot.at(), Direction.UP)) || cells.containsKey(offset(pilot.at(), front))) return fail(Error.SEAT_BLOCKED);
        if (thrust <= 0 || pilot.rotation() != 0) return fail(Error.PROPULSION);
        for (var part : plan.modules()) if (catalog.get(part.kind()).family() == Family.ENGINE && part.rotation() == 0 && cells.containsKey(offset(part.at(), Direction.NORTH))) return fail(Error.PROPULSION);
        var stats = new Stats(mass, thrust, lift, capacity, Math.clamp(stability, 0, 1));
        Error error = mass > limits.maxMass ? Error.TOO_HEAVY : lift < mass * limits.liftRatio ? Error.LIFT
                : capacity <= 0 ? Error.ENERGY : stats.stability < limits.stability ? Error.STABILITY : Error.NONE;
        return new Result(error, stats, bill);
    }
    private static Cell offset(Cell cell, Direction direction) { return new Cell(cell.x() + direction.getStepX(), cell.y() + direction.getStepY(), cell.z() + direction.getStepZ()); }
    private BlueprintValidator() {}
}
