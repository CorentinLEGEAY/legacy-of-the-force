package com.legacyoftheforce.speeder;

import java.util.OptionalDouble;

/** One deterministic server tick. The entity must resolve the proposed displacement through world collision. */
public final class SpeederMotion {
    public record Input(double throttle, double steering, boolean occupied) {
        public Input {
            range(throttle, -1, 1); range(steering, -1, 1);
            if (!occupied && (throttle != 0 || steering != 0)) throw new IllegalArgumentException("Unoccupied input");
        }
        public static Input parked() { return new Input(0, 0, false); }
    }

    /** Speed in blocks/tick, yaw follows Minecraft (zero south, positive west). */
    public record State(double speed, double verticalSpeed, double yaw, double energy) {
        public State {
            range(speed, -1, 1); range(verticalSpeed, -1, 1); range(yaw, -180, 180);
            range(energy, 0, 12_800_000);
        }
    }

    public record Settings(double forwardSpeed, double reverseSpeed, double accelerationScale, double maxAcceleration,
            double turnDegrees, double drag, double brakingDrag, double hoverHeight, double hoverGain,
            double verticalDamping, double verticalSpeed, double gravity, double idleEnergy,
            double thrustEnergy, double steeringEnergy, double massEnergy) {
        public Settings {
            range(forwardSpeed, .01, 1); range(reverseSpeed, .01, forwardSpeed);
            range(accelerationScale, .00001, .1); range(maxAcceleration, .001, .2);
            range(turnDegrees, .1, 15); range(drag, .5, .999); range(brakingDrag, .1, drag);
            range(hoverHeight, 1.5, 3); range(hoverGain, .01, .5); range(verticalDamping, .1, .99);
            range(verticalSpeed, .01, .5); range(gravity, .001, .1);
            range(idleEnergy, .001, 10); range(thrustEnergy, .0001, 1);
            range(steeringEnergy, 0, 10); range(massEnergy, 0, 1);
        }
        public static Settings defaults() {
            return new Settings(.45, .15, .01, .035, 4, .94, .7, 2, .14, .55, .2, .035, .08, .012, .02, .002);
        }
    }

    public record Step(State state, double dx, double dy, double dz, double spentEnergy) {}

    /**
     * groundClearance is a SERVER measurement from the vehicle base to a supporting solid surface,
     * at most six blocks below. Empty means no support, including an unloaded query: never force-load terrain.
     * A partial battery funds a proportional tick; no positive charge can buy a free full-powered tick.
     */
    public static Step tick(State previous, Input input, BlueprintValidator.Stats stats,
            OptionalDouble groundClearance, Settings settings) {
        return tick(previous, input, stats, groundClearance, settings, 1);
    }
    public static Step tick(State previous, Input input, BlueprintValidator.Stats stats,
            OptionalDouble groundClearance, Settings settings, double engineFactor) {
        range(engineFactor, 0, 1);
        range(stats.mass(), .1, 12_800); range(stats.thrust(), 0, 128_000);
        range(stats.lift(), 0, 128_000); range(stats.capacity(), 0, 12_800_000); range(stats.stability(), 0, 1);
        if (previous.energy() > stats.capacity()) throw new IllegalArgumentException("Charge exceeds capacity");
        if (groundClearance.isPresent()) range(groundClearance.getAsDouble(), 0, 6);

        double demand = settings.idleEnergy() + stats.mass() * settings.massEnergy()
                + Math.abs(input.throttle()) * stats.thrust() * settings.thrustEnergy()
                + Math.abs(input.steering()) * settings.steeringEnergy();
        // An empty seat parks the vehicle without paying for a levitation engine left running.
        double spent = input.occupied() ? Math.min(previous.energy(), demand) : 0;
        double power = spent / demand;
        double acceleration = Math.min(settings.maxAcceleration(), stats.thrust() / stats.mass() * settings.accelerationScale());
        double speed = previous.speed();
        boolean braking = !input.occupied() || power == 0 || speed * input.throttle() < 0;
        speed *= braking ? settings.brakingDrag() : settings.drag();
        speed += acceleration * input.throttle() * power;
        // Lost propulsion brakes toward its reduced limit without erasing momentum in one tick.
        double residual = Math.abs(previous.speed()) * settings.brakingDrag();
        speed = Math.clamp(speed, -Math.min(settings.reverseSpeed(), Math.max(settings.reverseSpeed() * engineFactor, residual)),
                Math.min(settings.forwardSpeed(), Math.max(settings.forwardSpeed() * engineFactor, residual)));
        double yaw = wrap(previous.yaw() - input.steering() * settings.turnDegrees() * stats.stability() * power);

        double vertical = previous.verticalSpeed() - settings.gravity();
        if (power > 0 && groundClearance.isPresent()) {
            double supported = Math.min(1, stats.lift() / stats.mass()) * power;
            double target = settings.hoverHeight() * supported;
            vertical = previous.verticalSpeed() * settings.verticalDamping()
                    + (target - groundClearance.getAsDouble()) * settings.hoverGain();
        }
        vertical = Math.clamp(vertical, -settings.verticalSpeed(), settings.verticalSpeed());
        double radians = Math.toRadians(yaw);
        var next = new State(speed, vertical, yaw, Math.max(0, previous.energy() - spent));
        return new Step(next, -Math.sin(radians) * speed, vertical, Math.cos(radians) * speed, spent);
    }

    private static double wrap(double yaw) { return ((yaw + 180) % 360 + 360) % 360 - 180; }
    private static void range(double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) throw new IllegalArgumentException("Motion value out of range");
    }
    private SpeederMotion() {}
}
