package com.legacyoftheforce.lightsaber;

import java.util.*;

public enum KyberKind {
    ILUM("blue","defense"),TATOOINE("yellow","endurance"),ENDOR("green","restoration"),MUSTAFAR("orange","offense"),DAGOBAH("cyan","telekinesis"),JEDHA("purple","technique");
    private final String color,resonance;
    KyberKind(String color,String resonance){this.color=color;this.resonance=resonance;}
    public String color(){return color;}public String resonance(){return resonance;}
    public String planet(){return "legacyoftheforce:"+name().toLowerCase(Locale.ROOT);}
    static Optional<KyberKind> fromPlanet(String planet){return Arrays.stream(values()).filter(kind->kind.planet().equals(planet)).findFirst();}
}
