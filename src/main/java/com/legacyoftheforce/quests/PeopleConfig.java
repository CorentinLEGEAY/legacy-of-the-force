package com.legacyoftheforce.quests;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class PeopleConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ACTION_INTERVAL,TRADE_INTERVAL,HELP_REPUTATION,TRUSTED_REPUTATION,CAUTION_ALIGNMENT;
    static {var b=new ModConfigSpec.Builder();ACTION_INTERVAL=b.defineInRange("minimumActionTicks",10,10,100);TRADE_INTERVAL=b.defineInRange("tradeCooldownTicks",80,20,1200);HELP_REPUTATION=b.defineInRange("reputationPerUniqueHelp",5,1,20);TRUSTED_REPUTATION=b.defineInRange("trustedDialogueReputation",5,1,100);CAUTION_ALIGNMENT=b.defineInRange("cautiousAlignmentTenths",250,0,1000);SPEC=b.build();}
    private PeopleConfig(){}
}
