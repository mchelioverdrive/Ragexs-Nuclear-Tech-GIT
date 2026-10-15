package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pressurized 20 -> 325 C coolant parcels. Inventory volume is referenced to the cold state. */
public final class CoolantLedger {
    private CoolantLedger() { }
    public static String cold(String hot) {
        for(String name:new String[]{"COOLANT","LIGHT_WATER","BORATED_WATER","HEAVYWATER"}) if((name+"_HOT").equals(hot)) return name;
        return null;
    }
    public static long sensibleJPerKg(String cold) { return (cold.equals("COOLANT")?2600L:4200L)*305L; }
    public static int heatingQuanta(String cold) {
        return ProcessRecipe.resolved(cold).massUnits <= 0 ? 0 : BigDecimal.valueOf(PhysicalRegistry.get(cold).massUnits)
                .multiply(BigDecimal.valueOf(sensibleJPerKg(cold)*2)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG),0,RoundingMode.CEILING).intValueExact();
    }
    /** Boiler heat is paid by the process source, never by a nominal HU token. */
    public static ProcessRecipe heating(String cold) { return HeatingBills.ALL.get(cold); }
    private static final class HeatingBills {
        static final java.util.Map<String,ProcessRecipe> ALL = create();
        private static java.util.Map<String,ProcessRecipe> create() {
            java.util.Map<String,ProcessRecipe> result=new java.util.HashMap<String,ProcessRecipe>();
            for(String name:new String[]{"COOLANT","LIGHT_WATER","BORATED_WATER","HEAVYWATER"}) result.put(name,createHeating(name));
            return result;
        }
    }
    private static ProcessRecipe createHeating(String cold) {
        long heat=SteamLedger.ceilDiv(heatingQuanta(cold),2);
        int ticks=Math.toIntExact(Math.max(20,SteamLedger.ceilDiv(heat*2,20000)));
        return new ProcessRecipe("coolant-heating:"+cold,new String[]{cold},new int[]{1},new int[]{0},
                null,0,new String[]{cold+"_HOT"},new long[]{PhysicalRegistry.get(cold).massUnits},
                ticks,1000,heat,355,true);
    }
    static void registerStates() {
        for(String name:new String[]{"COOLANT","LIGHT_WATER","BORATED_WATER","HEAVYWATER"}) {
            PhysicalState base=PhysicalRegistry.get(name);
            PhysicalRegistry.hotCoolant(name+"_HOT",new PhysicalState(base.substance,base.phase,base.basis,base.massUnits,base.lhvJoulesPerKg,
                    "325 C pressurized coolant; cold-reference inventory, no quantity expansion; adopted constant cp",base.mixture,BigDecimal.valueOf(sensibleJPerKg(name))));
        }
    }
}
