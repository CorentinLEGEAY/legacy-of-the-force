package com.legacyoftheforce.lightsaber;

import java.util.Locale;
import java.util.Optional;

public enum SaberType {
    SINGLE, SHORT, CURVED, DOUBLE, CROSSGUARD;
    public String key(){return name().toLowerCase(Locale.ROOT);}
    public static Optional<SaberType> parse(String id){if(id==null)return Optional.empty();try{return Optional.of(valueOf(id.toUpperCase(Locale.ROOT)));}catch(IllegalArgumentException unknown){return Optional.empty();}}
}
