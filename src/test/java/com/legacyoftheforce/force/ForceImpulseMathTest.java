package com.legacyoftheforce.force;

import com.legacyoftheforce.core.api.ForceAction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForceImpulseMathTest {
    @Test void pushAndPullHaveOppositeDirectionsAndMassAndResistanceMatter() {
        var direction = new Vec3(1, 0, 0);
        var push = ForceImpulseMath.velocity(Vec3.ZERO, direction, ForceAction.PUSH, 1, 0, 0.9, 1.25, 0.15);
        var pull = ForceImpulseMath.velocity(Vec3.ZERO, direction, ForceAction.PULL, 1, 0, 0.6, 1.25, 0.15);
        assertTrue(push.x > 0); assertTrue(pull.x < 0);
        var heavy = ForceImpulseMath.velocity(Vec3.ZERO, direction, ForceAction.PUSH, 9, 0, 0.9, 1.25, 0.15);
        var resistant = ForceImpulseMath.velocity(Vec3.ZERO, direction, ForceAction.PUSH, 1, 0.8, 0.9, 1.25, 0.15);
        assertTrue(heavy.length() < push.length()); assertTrue(resistant.length() < push.length());
    }
    @Test void finalVelocityIsCappedIncludingExistingMomentum() {
        var velocity = ForceImpulseMath.velocity(new Vec3(10, -10, 10), new Vec3(1, 0, 1), ForceAction.PUSH, 1, 0, 0.9, 1.25, 0.15);
        assertTrue(Double.isFinite(velocity.lengthSqr()));
        assertTrue(velocity.length() <= 1.250000001);
    }
    @Test void nonFiniteAndExtremeInputsAreRejected() {
        for (var action : new ForceAction[]{ForceAction.STABILIZE, ForceAction.BRAKE})
            assertThrows(IllegalArgumentException.class, () -> ForceImpulseMath.velocity(Vec3.ZERO, new Vec3(1, 0, 0), action, 1, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ForceImpulseMath.velocity(Vec3.ZERO, new Vec3(Double.NaN, 0, 0), ForceAction.PUSH, 1, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ForceImpulseMath.velocity(Vec3.ZERO, new Vec3(1, 0, 0), ForceAction.PUSH, Double.MIN_VALUE, 0, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ForceImpulseMath.velocity(Vec3.ZERO, new Vec3(1, 0, 0), ForceAction.PUSH, 1, -1, 1, 1, 0));
    }
}
