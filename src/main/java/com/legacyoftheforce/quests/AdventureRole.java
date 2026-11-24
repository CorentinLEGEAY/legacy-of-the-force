package com.legacyoftheforce.quests;

public enum AdventureRole {
    JEDI("jedi"), HERMIT("travelers"), SITH("sith"), EXPLORER("travelers"), MERCHANT("travelers"), PILOT("travelers"), HUNTER("travelers");
    private final String faction;
    AdventureRole(String faction){this.faction=faction;}
    public String key(){return name().toLowerCase(java.util.Locale.ROOT);}
    public String faction(){return faction;}
}
