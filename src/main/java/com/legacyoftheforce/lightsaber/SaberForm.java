package com.legacyoftheforce.lightsaber;

import java.util.*;

/** Original disciplines; their bounded tuning is supplied by server configuration. */
public enum SaberForm {
    BASIC(1, 1, 1, 1, 0), PRECISION(25, .95, .9, 1, .03), WARD(30, .9, 1.05, .85, .05),
    MOMENTUM(40, 1.08, 1.18, 1.15, -.02), TWIN(35, 1.04, 1.2, 1.25, .01);
    final int level; final double damage, attackCost, guardCost, parry;
    SaberForm(int level, double damage, double attackCost, double guardCost, double parry) { this.level=level;this.damage=damage;this.attackCost=attackCost;this.guardCost=guardCost;this.parry=parry; }
    public String key() { return name().toLowerCase(Locale.ROOT); }
    public static Optional<SaberForm> parse(String key) { return Arrays.stream(values()).filter(form -> form.key().equals(key)).findFirst(); }
    boolean supports(SaberType type) {
        return switch (this) {
            case PRECISION -> type == SaberType.SINGLE || type == SaberType.SHORT || type == SaberType.CURVED;
            case WARD -> type == SaberType.SINGLE || type == SaberType.CROSSGUARD;
            case MOMENTUM -> type == SaberType.SINGLE || type == SaberType.DOUBLE || type == SaberType.CROSSGUARD;
            case TWIN -> type == SaberType.SINGLE;
            default -> true;
        };
    }
}
