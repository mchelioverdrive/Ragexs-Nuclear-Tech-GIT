package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Physical preparation bills. Only the final furnace recovery yields are gameplay-scaled. */
public final class SteelmakingProcesses {
    private SteelmakingProcesses() { }
    public static long ratio(long mass, String numerator, String denominator) {
        return BigDecimal.valueOf(mass).multiply(new BigDecimal(numerator))
                .divide(new BigDecimal(denominator), 0, RoundingMode.DOWN).longValueExact();
    }
    public static long oxideOxygen() {
        return ratio(PhysicalState.MASS_UNITS_PER_KG, "47.9982", "111.69");
    }
    public static long fluxCO2() {
        // A divisible eight-pellet bill keeps every solid output integral.
        return ratio(PhysicalState.MASS_UNITS_PER_KG, "44.0095", "100.0869") / 8 * 8;
    }
    private static long mass(String name, int count) { return ChemistryProcesses.mass(name, count); }
    public static ProcessRecipe concentrate() {
        // RTM iron powder is metallic. Oxidative conditioning supplies, rather than invents, oxide oxygen.
        long oxygen = oxideOxygen() * 8;
        int gas = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1)));
        return new ProcessRecipe("steel:oxidative_concentration", new String[] {"OXYGEN_GAS", "WATER"}, new int[] {gas, 2}, new int[] {0, 0},
                "IRON_PREP_POWDER_ITEM", 8, new String[] {"IRON_FINES_ITEM", "FRESH_WATER", "OXYGEN_GAS"},
                new long[] {mass("IRON_FINES_ITEM", 8), mass("WATER", 2), mass("OXYGEN_GAS", gas) - oxygen}, 1200, 400000, 0, 20, false);
    }
    public static ProcessRecipe pelletize() {
        return new ProcessRecipe("steel:green_pellets", new String[] {"WATER"}, new int[] {2}, new int[] {0},
                new String[] {"IRON_FINES_ITEM", "PELLET_LIMESTONE_ITEM", "PELLET_CLAY_ITEM"}, new int[] {8, 1, 1},
                new String[] {"IRON_GREEN_PELLET_ITEM"}, new long[] {mass("IRON_GREEN_PELLET_ITEM", 8)}, 1200, 400000, 0, 20, true);
    }
    public static ProcessRecipe harden() {
        return new ProcessRecipe("steel:pellet_induration", new String[0], new int[0], new int[0], "IRON_GREEN_PELLET_ITEM", 8,
                new String[] {"IRON_HARD_PELLET_ITEM", "VENT_WATER", "CARBONDIOXIDE"},
                new long[] {mass("IRON_HARD_PELLET_ITEM", 8), mass("WATER", 2), fluxCO2()}, 12000, 800000, 24000000, 1200, false);
    }
    public static ProcessRecipe reduce() {
        // Fe2O3 + 3 H2 -> 2 Fe + 3 H2O. Return the unused whole-litre gas rounding margin.
        long oxygen = oxideOxygen() * 8;
        long hydrogen = ratio(oxygen, "6.04764", "47.9982");
        int gas = Math.toIntExact(PetroleumProcesses.ceilDiv(hydrogen, mass("HYDROGEN_GAS", 1)));
        return new ProcessRecipe("steel:hydrogen_direct_reduction", new String[] {"HYDROGEN_GAS"}, new int[] {gas}, new int[] {0},
                "IRON_HARD_PELLET_ITEM", 8, new String[] {"IRON_DRI_ITEM", "FRESH_WATER", "HYDROGEN_GAS"},
                new long[] {mass("IRON_DRI_ITEM", 8), oxygen + hydrogen, mass("HYDROGEN_GAS", gas) - hydrogen},
                12000, 800000, 32000000, 900, false);
    }
}
