package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conserved carrier and isotope parcels for RTM's existing abstract cascade and fuel chemistry. */
public final class NuclearChemicalProcesses {
    private NuclearChemicalProcesses() { }
    private static long mass(String name, int count) { return ChemistryProcesses.mass(name, count); }
    private static long ratio(long amount, String numerator, String denominator) {
        return BigDecimal.valueOf(amount).multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator), 0, RoundingMode.DOWN).longValueExact();
    }
    public static ProcessRecipe yellowcake() {
        long ore = mass("URANIUM_ORE_ITEM", 1), concentrate = ratio(ore, ".2", "1"), rareEarths = ratio(ore, ".001", "1");
        return new ProcessRecipe("nuclear:yellowcake", new String[] {"SULFURIC_ACID", "FRESH_WATER"}, new int[] {1, 10}, new int[] {0, 0}, "URANIUM_ORE_ITEM", 1,
                new String[] {"YELLOWCAKE_ITEM", "ITEM:radium_residue", "RAFFINATE", "SULFURIC_ACID", "FRESH_WATER"},
                new long[] {concentrate, ore - concentrate - rareEarths, rareEarths + mass("FRESH_WATER", 2), mass("SULFURIC_ACID", 1), mass("FRESH_WATER", 8)},
                5000, 1000000, 20000000, 650, true);
    }
    public static ProcessRecipe uraniumFluorination() {
        long feed = mass("YELLOWCAKE_ITEM", 4), uranium = (PhysicalState.MASS_UNITS_PER_KG / 9) * 27;
        long oxideOxygen = feed - uranium, product = mass("UF6", 1200), fluoride = product - uranium;
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(fluoride, mass("FLUORINE", 1)));
        return new ProcessRecipe("nuclear:uranium_fluorination", new String[] {"FLUORINE"}, new int[] {q}, new int[] {0}, "YELLOWCAKE_ITEM", 4,
                new String[] {"UF6", "OXYGEN_GAS", "FLUORINE"}, new long[] {product, oxideOxygen, mass("FLUORINE", q) - fluoride},
                4000, 200000, 1000000, 400, false);
    }
    public static ProcessRecipe plutoniumFluorination() {
        long metal = mass("PLUTONIUM_ITEM", 1), fluoride = ratio(metal, "113.9904192", "239.05216");
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(fluoride, mass("FLUORINE", 1)));
        return new ProcessRecipe("nuclear:plutonium_fluorination", new String[] {"FLUORINE"}, new int[] {q}, new int[] {0}, "PLUTONIUM_ITEM", 1,
                new String[] {"PUF6", "FLUORINE"}, new long[] {metal + fluoride, mass("FLUORINE", q) - fluoride}, 4000, 200000, 1000000, 400, false);
    }
    public static ProcessRecipe rareEarthRecovery() {
        long feed = mass("RAFFINATE", 10), concentrate = ratio(feed, ".01", "2.01");
        return new ProcessRecipe("nuclear:raffinate_recovery", new String[] {"RAFFINATE"}, new int[] {10}, new int[] {0}, null, 0,
                new String[] {"REE_SLUDGE_ITEM", "FRESH_WATER"}, new long[] {concentrate, feed - concentrate}, 3000, 50000, 3000000, 80, true);
    }
    public static ProcessRecipe rareEarthExtraction(boolean scandium) {
        long feed = mass("REE_SLUDGE_ITEM", 1), recovered = ratio(feed, scandium ? ".10" : ".20", "1");
        return new ProcessRecipe("nuclear:rare_earth_" + (scandium ? "scandium" : "yttrium"), new String[] {"HCL"}, new int[] {1}, new int[] {0}, "REE_SLUDGE_ITEM", 1,
                new String[] {scandium ? "SCANDIUM_OXIDE_ITEM" : "YTTRIUM_OXIDE_ITEM", "REE_RESIDUE_ITEM", "HCL"},
                new long[] {recovered, feed - recovered, mass("HCL", 1)}, 2000, 100000, 1000000, 80, true);
    }
    public static ProcessRecipe oxideChlorination(boolean scandium) {
        String oxide = scandium ? "SCANDIUM_OXIDE_ITEM" : "YTTRIUM_OXIDE_ITEM";
        long feed = mass(oxide, 1), acid = ratio(feed, "218.76564", scandium ? "137.910024" : "225.8099");
        long solution = ratio(acid, "1", ".30"), salt = ratio(feed, scandium ? "302.629824" : "390.5297", scandium ? "137.910024" : "225.8099");
        int q = scandium ? 6 : 3;
        return new ProcessRecipe("nuclear:oxide_chlorination_" + scandium, new String[] {"HCL"}, new int[] {q}, new int[] {0}, oxide, 1,
                new String[] {scandium ? "SCANDIUM_CHLORIDE_ITEM" : "YTTRIUM_CHLORIDE_ITEM", "FRESH_WATER", "HCL"},
                new long[] {salt, feed + solution - salt, mass("HCL", q) - solution}, 4000, 50000, scandium ? 12000000 : 8000000, 300, false);
    }
    public static ProcessRecipe chlorideReduction(boolean scandium) {
        String chloride = scandium ? "SCANDIUM_CHLORIDE_ITEM" : "YTTRIUM_CHLORIDE_ITEM";
        long feed = mass(chloride, 1), calcium = ratio(feed, "60.117", scandium ? "151.314912" : "195.26485");
        long metal = ratio(feed, scandium ? "44.955912" : "88.90585", scandium ? "151.314912" : "195.26485");
        return new ProcessRecipe("nuclear:chloride_reduction_" + scandium, new String[0], new int[0], new int[0],
                new String[] {chloride, "CALCIUM_ITEM"}, new int[] {1, 1},
                new String[] {scandium ? "SCANDIUM_NUGGET_ITEM" : "YTTRIUM_TINY_ITEM", "MOLTEN_CALCIUM_CHLORIDE", "CALCIUM_ITEM"},
                new long[] {metal, feed + calcium - metal, mass("CALCIUM_ITEM", 1) - calcium}, 2000, 100000, 5000000, 1200, false);
    }
    public static ProcessRecipe titaniumTraceRecovery(boolean scandium) {
        long feed = mass("TITANIUM_TRACE_ITEM", 1), recovered = ratio(feed, scandium ? ".10" : ".20", "1");
        return new ProcessRecipe("nuclear:titanium_trace_" + scandium, new String[] {"HCL"}, new int[] {1}, new int[] {0}, "TITANIUM_TRACE_ITEM", 1,
                new String[] {scandium ? "SCANDIUM_OXIDE_ITEM" : "YTTRIUM_OXIDE_ITEM", "REE_RESIDUE_ITEM", "HCL"},
                new long[] {recovered, feed - recovered, mass("HCL", 1)}, 2000, 100000, 1000000, 80, true);
    }
    public static ProcessRecipe wasteNeutralization(boolean sodium) {
        // Compatibility proxy: old mixed acid waste is 5 wt% HCl-equivalent aqueous liquor.
        long feed = mass("ACIDWASTE", 10), acid = ratio(feed, ".05", "1");
        long base = ratio(acid, sodium ? "39.99711" : "56.0774", sodium ? "36.46094" : "72.92188");
        long salt = ratio(acid, sodium ? "58.44277" : "110.984", sodium ? "36.46094" : "72.92188");
        long product = sodium ? salt : ratio(salt, "1", ".40");
        return new ProcessRecipe("nuclear:waste_neutralization_" + sodium,
                sodium ? new String[] {"ACIDWASTE", "SODIUM_HYDROXIDE"} : new String[] {"ACIDWASTE"},
                sodium ? new int[] {10, 1} : new int[] {10}, sodium ? new int[] {0, 0} : new int[] {0}, sodium ? null : "QUICKLIME_ITEM", sodium ? 0 : 1,
                new String[] {"SALT_WATER", sodium ? "SALT_ITEM" : "CALCIUM_CHLORIDE", sodium ? "SODIUM_HYDROXIDE" : "QUICKLIME_ITEM"},
                new long[] {feed + base - product, product, mass(sodium ? "SODIUM_HYDROXIDE" : "QUICKLIME_ITEM", 1) - base},
                2400, 50000, 0, 20, false);
    }
    private static final Map<String, ProcessRecipe> CASCADE = createCascade();
    private static Map<String, ProcessRecipe> createCascade() {
        Map<String, ProcessRecipe> recipes = new LinkedHashMap<>();
        String[] stages = {"NUF6", "LEUF6", "MEUF6", "HEUF6", "PF6"};
        int[] quantities = {400, 200, 100, 50, 300}, tails = {4, 2, 1, 0, 0};
        for(int i = 0; i < stages.length; i++) for(boolean fast : new boolean[] {false, true}) {
            String feed = "CASCADE_" + stages[i]; long total = mass(feed, quantities[i]);
            String[] products; long[] amounts;
            long processedMetal = mass("U238_NUGGET_ITEM", i == 0 ? 9 : i == 1 ? 5 : i == 2 ? 3 : i == 3 ? 2 : 3);
            if(i < 3) {
                long next = mass("CASCADE_" + stages[i + 1], quantities[i + 1]), tail = mass("U238_NUGGET_ITEM", tails[i]);
                products = new String[] {"CASCADE_" + stages[i + 1], "U238_NUGGET_ITEM", "FLUORINE"};
                amounts = new long[] {next, tail, total - next - tail};
            } else if(i == 3) {
                // All natural-feed U-235 survives in the game's retained stream; terminal conversion cannot manufacture it.
                long u235 = ratio(mass("U238_NUGGET_ITEM", 9), ".007", "1");
                products = new String[] {"U235_NUGGET_ITEM", "U238_NUGGET_ITEM", "FLUORINE"};
                amounts = new long[] {u235, processedMetal - u235, total - processedMetal};
            } else {
                // A mixed plutonium feed stays mixed. Pu-238 remains supplied by the Np-237 breeding route.
                products = new String[] {"PU_MIX_NUGGET_ITEM", "FLUORINE"}; amounts = new long[] {processedMetal, total - processedMetal};
            }
            // Adopted game module work, not real facility design: paid separation plus carrier conversion/recovery.
            long work = PetroleumProcesses.joules(processedMetal, i < 3 ? 60000000 : 10000000);
            int ticks = Math.toIntExact(PetroleumProcesses.ceilDiv(work * 20, fast ? 100000 : 20000));
            ProcessRecipe recipe = new ProcessRecipe("cascade:" + stages[i] + (fast ? ":fast" : ":base"), new String[] {feed}, new int[] {quantities[i]}, new int[] {0}, null, 0,
                    products, amounts, ticks, work, 0, 80, false);
            recipes.put(stages[i] + fast, recipe);
        }
        return recipes;
    }
    public static ProcessRecipe cascade(String stage, boolean fast) { return CASCADE.get(stage + fast); }
}
