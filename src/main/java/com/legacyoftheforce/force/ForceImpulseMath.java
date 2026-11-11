package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.ForceAction;
import net.minecraft.world.phys.Vec3;

public final class ForceImpulseMath {
    private ForceImpulseMath() {}
    public static Vec3 velocity(Vec3 current, Vec3 away, ForceAction action, double mass,
            double resistance, double speed, double maxSpeed, double lift) {
        if (current == null || away == null || (action != ForceAction.PUSH && action != ForceAction.PULL) || !Double.isFinite(current.lengthSqr())
                || !Double.isFinite(away.lengthSqr()) || !Double.isFinite(mass) || mass < 0.0001 || mass > 1_000_000
                || !Double.isFinite(resistance) || resistance < 0 || resistance > 1
                || !Double.isFinite(speed) || speed <= 0 || speed > 16 || !Double.isFinite(maxSpeed) || maxSpeed <= 0 || maxSpeed > 16
                || !Double.isFinite(lift) || lift < 0 || lift > 4) throw new IllegalArgumentException("Invalid impulse inputs");
        var horizontal = new Vec3(away.x, 0, away.z);
        if (horizontal.lengthSqr() < 0.000001) return current;
        double scale = speed * (1 - resistance) / Math.sqrt(mass);
        var impulse = horizontal.normalize().scale(action == ForceAction.PUSH ? scale : -scale)
                .add(0, lift * (1 - resistance) / Math.sqrt(mass), 0);
        var result = current.add(impulse);
        double length = result.length();
        return length > maxSpeed ? result.scale(maxSpeed / length) : result;
    }
}
