package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Adopted mineral assays with recovered aqueous carriers and paid metal reduction. */
public final class MineralChemicalProcesses {
    private MineralChemicalProcesses() { }
    private static long mass(String name, int count) { return ChemistryProcesses.mass(name, count); }
    private static long ratio(long value, String a, String b) {
        return BigDecimal.valueOf(value).multiply(new BigDecimal(a)).divide(new BigDecimal(b), 0, RoundingMode.DOWN).longValueExact();
    }
    private static int feed(String name, long mass) { return Math.toIntExact(PetroleumProcesses.ceilDiv(mass, mass(name, 1))); }
    public static ProcessRecipe osmiumOxidation() {
        long ore = mass("OSIR_CONCENTRATE_ITEM", 1), osmium = ratio(ore, ".30", "1"), oxygen = ratio(osmium, "63.9976", "190.23");
        int q = feed("OXYGEN_GAS", oxygen); long solution = (osmium + oxygen) * 10;
        return new ProcessRecipe("mineral:osmium_oxidation", new String[] {"OXYGEN_GAS", "FRESH_WATER"}, new int[] {q, 4}, new int[] {0, 0}, "OSIR_CONCENTRATE_ITEM", 1,
                new String[] {"OSMIRIDIUM_SOLUTION", "IRIDIUM_RESIDUE_ITEM", "FRESH_WATER", "OXYGEN_GAS"},
                new long[] {solution, ore - osmium, mass("FRESH_WATER", 4) + osmium + oxygen - solution, mass("OXYGEN_GAS", q) - oxygen}, 2000, 100000, 1000000, 850, false);
    }
    public static ProcessRecipe osmiumReduction() {
        long solution = mass("OSMIRIDIUM_SOLUTION", 1), oxide = solution / 10, metal = ratio(oxide, "190.23", "254.2276");
        long hydrogen = ratio(oxide, "8.06352", "254.2276"); int q = feed("HYDROGEN_GAS", hydrogen);
        return new ProcessRecipe("mineral:osmium_reduction", new String[] {"OSMIRIDIUM_SOLUTION", "HYDROGEN_GAS"}, new int[] {1, q}, new int[] {0, 0}, null, 0,
                new String[] {"OSMIUM_TINY_ITEM", "FRESH_WATER", "HYDROGEN_GAS"},
                new long[] {metal, solution + hydrogen - metal, mass("HYDROGEN_GAS", q) - hydrogen}, 800, 40000, 300000, 150, false);
    }
    public static ProcessRecipe rhodiumExtraction() {
        long ore = mass("RH_IR_CONCENTRATE_ITEM", 1), metal = ratio(ore, ".4", "1"), chlorine = ratio(metal, "106.359", "102.9055");
        int q = feed("CHLORINE", chlorine); long solution = ratio(metal + chlorine, "1", ".40");
        return new ProcessRecipe("mineral:rhodium_extraction", new String[] {"CHLORINE", "FRESH_WATER"}, new int[] {q, 2}, new int[] {0, 0}, "RH_IR_CONCENTRATE_ITEM", 1,
                new String[] {"RHODIUM_SOLUTION", "IRIDIUM_RESIDUE_ITEM", "FRESH_WATER", "CHLORINE"},
                new long[] {solution, ore - metal, mass("FRESH_WATER", 2) + metal + chlorine - solution, mass("CHLORINE", q) - chlorine}, 2000, 100000, 1000000, 600, false);
    }
    public static ProcessRecipe iridiumOxidation() {
        long metal = mass("IRIDIUM_RESIDUE_ITEM", 1), oxygen = ratio(metal, "31.9988", "192.217"); int q = feed("OXYGEN_GAS", oxygen);
        return new ProcessRecipe("mineral:iridium_oxidation", new String[] {"OXYGEN_GAS"}, new int[] {q}, new int[] {0}, "IRIDIUM_RESIDUE_ITEM", 1,
                new String[] {"IRIDIUM_OXIDE_ITEM", "OXYGEN_GAS"}, new long[] {metal + oxygen, mass("OXYGEN_GAS", q) - oxygen}, 2000, 100000, 1000000, 800, false);
    }
    public static ProcessRecipe iridiumChlorination() {
        // Carbochlorination, then absorption. CO removes oxide oxygen; no unsupported Ru coproduct.
        long oxide = mass("IRIDIUM_OXIDE_ITEM", 1), metal = ratio(oxide, "192.217", "224.2158");
        long chlorine = ratio(oxide, "106.359", "224.2158"), co = ratio(oxide, "56.0202", "224.2158");
        int qc = feed("CHLORINE", chlorine), qo = feed("CO", co); long solution = ratio(metal + chlorine, "1", ".40");
        return new ProcessRecipe("mineral:iridium_chlorination", new String[] {"CHLORINE", "CO", "FRESH_WATER"}, new int[] {qc, qo, 3}, new int[] {0, 0, 0}, "IRIDIUM_OXIDE_ITEM", 1,
                new String[] {"IRIDIUM_SOLUTION", "CARBONDIOXIDE", "FRESH_WATER", "CHLORINE", "VENT_CO"},
                new long[] {solution, oxide - metal + co, mass("FRESH_WATER", 3) + metal + chlorine - solution, mass("CHLORINE", qc) - chlorine, mass("CO", qo) - co},
                2000, 100000, 1000000, 850, false);
    }
    public static ProcessRecipe preciousMetalElectrolysis(boolean rhodium) {
        String source = rhodium ? "RHODIUM_SOLUTION" : "IRIDIUM_SOLUTION";
        long feed = mass(source, 1), chloride = ratio(feed, ".40", "1"), metal = ratio(chloride, rhodium ? "102.9055" : "192.217", rhodium ? "209.2645" : "298.576");
        return new ProcessRecipe("electrolysis:" + source.toLowerCase(java.util.Locale.ROOT), new String[] {source}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {rhodium ? "RHODIUM_ITEM" : "IRIDIUM_ITEM", "CHLORINE", "FRESH_WATER"}, new long[] {metal, chloride - metal, feed - chloride},
                1000, 1000000, 0, 20, false);
    }
    private static long proportion(long value, long part, long total) {
        return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(total), 0, RoundingMode.DOWN).longValueExact();
    }
    private static long[] polluciteAssay() {
        long ore = mass("POLLUCITE_ITEM", 1);
        long cs = ratio(ore, ".15", "1"), rb = ratio(ore, ".03", "1"), k = ore / 10, na = ore / 10;
        long acid = ratio(cs, "72.92188", "281.8103") + ratio(rb, "72.92188", "186.935") + ratio(k, "72.92188", "94.1966") + ratio(na, "72.92188", "61.97894");
        return new long[] {ratio(cs, "336.7169", "281.8103"), ratio(rb, "241.8416", "186.935"), ratio(k, "149.1026", "94.1966"), ratio(na, "116.88554", "61.97894"), acid};
    }
    public static ProcessRecipe polluciteLeaching() {
        // Activated aluminosilicate oxide-equivalent assay; acid exchange supplies actual chloride.
        long ore = mass("POLLUCITE_ITEM", 1), gangue = ratio(ore, ".62", "1"), used = ratio(polluciteAssay()[4], "1", ".30");
        return new ProcessRecipe("mineral:pollucite_leaching", new String[] {"HCL", "FRESH_WATER"}, new int[] {1, 2}, new int[] {0, 0}, "POLLUCITE_ITEM", 1,
                new String[] {"POLLUCITE_SOLUTION", "GANGUE_ITEM", "HCL"},
                new long[] {ore - gangue + used + mass("FRESH_WATER", 2), gangue, mass("HCL", 1) - used}, 2000, 100000, 2000000, 300, false);
    }
    public static ProcessRecipe polluciteSeparation() {
        long[] assay = polluciteAssay();
        long liquor = mass("POLLUCITE_ITEM", 1) - ratio(mass("POLLUCITE_ITEM", 1), ".62", "1") + ratio(assay[4], "1", ".30") + mass("FRESH_WATER", 2);
        long feed = mass("POLLUCITE_SOLUTION", 1), heavy = proportion(feed, (assay[0] + assay[1]) * 4, liquor), light = proportion(feed, (assay[2] + assay[3]) * 4, liquor);
        return new ProcessRecipe("mineral:pollucite_separation", new String[] {"POLLUCITE_SOLUTION"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"POLLUCITE_SOLUTION_HEAVY", "POLLUCITE_SOLUTION_LIGHT", "FRESH_WATER"}, new long[] {heavy, light, feed - heavy - light}, 1000, 50000, 500000, 80, true);
    }
    public static ProcessRecipe polluciteHeavyRecovery() {
        long[] assay = polluciteAssay(); long feed = mass("POLLUCITE_SOLUTION_HEAVY", 1), salts = feed / 4, cs = proportion(salts, assay[0], assay[0] + assay[1]);
        return new ProcessRecipe("mineral:pollucite_heavy_recovery", new String[] {"POLLUCITE_SOLUTION_HEAVY"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"CESIUM_SALT_ITEM", "RUBIDIUM_SALT_ITEM", "STEAM"}, new long[] {cs, salts - cs, feed - salts}, 1000, 30000, 2600000, 120, true);
    }
    public static ProcessRecipe polluciteLightRecovery() {
        long[] assay = polluciteAssay(); long feed = mass("POLLUCITE_SOLUTION_LIGHT", 1), salts = feed / 4, potassium = proportion(salts, assay[2], assay[2] + assay[3]);
        long liquor = potassium * 5;
        return new ProcessRecipe("mineral:pollucite_light_recovery", new String[] {"POLLUCITE_SOLUTION_LIGHT"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"POTASSIUM_CHLORIDE", "SALT_ITEM", "FRESH_WATER"}, new long[] {liquor, salts - potassium, feed - liquor - salts + potassium}, 1000, 30000, 1500000, 150, true);
    }
    public static ProcessRecipe heavyAlkaliReduction(boolean cesium) {
        String salt = cesium ? "CESIUM_SALT_ITEM" : "RUBIDIUM_SALT_ITEM"; long chloride = mass(salt, 1);
        long calcium = ratio(chloride, "20.039", cesium ? "168.35845" : "120.9208"), metal = ratio(chloride, cesium ? "132.90545" : "85.4678", cesium ? "168.35845" : "120.9208");
        return new ProcessRecipe("mineral:heavy_alkali_reduction_" + cesium, new String[] {"ARGON"}, new int[] {10}, new int[] {0},
                new String[] {salt, "CALCIUM_ITEM"}, new int[] {1, 1},
                new String[] {cesium ? "CESIUM_ITEM" : "RUBIDIUM_ITEM", "MOLTEN_CALCIUM_CHLORIDE", "CALCIUM_ITEM", "VENT_ARGON"},
                new long[] {metal, chloride + calcium - metal, mass("CALCIUM_ITEM", 1) - calcium, mass("ARGON", 10)}, 2000, 100000, 5000000, 1000, false);
    }
    public static ProcessRecipe lepidolite() {
        // Activated ore proxy: 3.5 wt% Li and 10 wt% K as their oxides; silicate gangue is rejected.
        long ore = mass("LEPIDOLITE_ITEM", 1), lithium = ratio(ore, ".035", "1"), potassium = ratio(ore, ".10", "1");
        long liOxide = ratio(lithium, "29.8814", "13.882"), kOxide = ratio(potassium, "94.196", "78.1966");
        long acid = ratio(lithium, "98.07848", "13.882") + ratio(potassium, "98.07848", "78.1966");
        long used = ratio(acid, "1", ".98"), liSalt = ratio(lithium, "109.94588", "13.882"), kSalt = ratio(potassium, "174.26048", "78.1966");
        return new ProcessRecipe("mineral:lepidolite", new String[] {"SULFURIC_ACID"}, new int[] {1}, new int[] {0}, "LEPIDOLITE_ITEM", 1,
                new String[] {"LITHIUM_SULFATE_ITEM", "POTASSIUM_SULFATE_ITEM", "GANGUE_ITEM", "FRESH_WATER", "SULFURIC_ACID"},
                new long[] {liSalt, kSalt, ore - liOxide - kOxide, liOxide + kOxide + used - liSalt - kSalt, mass("SULFURIC_ACID", 1) - used}, 2000, 100000, 2000000, 900, false);
    }
    public static ProcessRecipe lithiumCarbonate() {
        long sulfate = mass("LITHIUM_SULFATE_ITEM", 1), carbonate = ratio(sulfate, "105.98844", "109.94588");
        long used = ratio(carbonate, "1", ".20"), liCarbonate = ratio(sulfate, "73.8909", "109.94588"), sodiumSulfate = ratio(sulfate, "142.04342", "109.94588");
        return new ProcessRecipe("mineral:lithium_carbonate", new String[] {"SODIUM_CARBONATE"}, new int[] {5}, new int[] {0}, "LITHIUM_SULFATE_ITEM", 1,
                new String[] {"LITHIUM_CARBONATE_ITEM", "SODIUM_SULFATE_ITEM", "SODIUM_CARBONATE", "FRESH_WATER"},
                new long[] {liCarbonate, sodiumSulfate, mass("SODIUM_CARBONATE", 5) - used, sulfate + used - liCarbonate - sodiumSulfate}, 4000, 100000, 12000000, 150, false);
    }
    public static ProcessRecipe lithiumChloride() {
        long carbonate = mass("LITHIUM_CARBONATE_ITEM", 1), acid = ratio(carbonate, "72.92188", "73.8909"), used = ratio(acid, "1", ".30");
        long salt = ratio(carbonate, "84.788", "73.8909"), co2 = ratio(carbonate, "44.0095", "73.8909");
        return new ProcessRecipe("mineral:lithium_chloride_drying", new String[] {"HCL"}, new int[] {3}, new int[] {0}, "LITHIUM_CARBONATE_ITEM", 1,
                new String[] {"MOLTEN_LITHIUM_CHLORIDE", "CARBONDIOXIDE", "STEAM", "HCL"},
                new long[] {salt, co2, carbonate + used - salt - co2, mass("HCL", 3) - used}, 4000, 100000, 8000000, 700, false);
    }
    public static ProcessRecipe lithiumElectrolysis() {
        long salt = mass("MOLTEN_LITHIUM_CHLORIDE", 144), lithium = ratio(salt, "6.941", "42.394");
        return new ProcessRecipe("electrolysis:lithium_chloride", new String[] {"MOLTEN_LITHIUM_CHLORIDE"}, new int[] {144}, new int[] {0}, null, 0,
                new String[] {"LITHIUM_ITEM", "CHLORINE"}, new long[] {lithium, salt - lithium}, 10000, 10000000, 0, 700, false);
    }
}
