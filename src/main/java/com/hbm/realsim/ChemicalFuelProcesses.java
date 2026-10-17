package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Reactor fuel chemistry; all bills include final liquid mass, purge and solid residues. */
public final class ChemicalFuelProcesses {
    private ChemicalFuelProcesses() { }
    private static long mass(String name, int amount) { return Math.multiplyExact(ProcessRecipe.resolved(name).massUnits, amount); }
    private static long ratio(long mass, String numerator, String denominator) {
        return BigDecimal.valueOf(mass).multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator), 0, RoundingMode.DOWN).longValueExact();
    }
    public static ProcessRecipe blend(String product, String a, int qa, String b, int qb) {
        long total = mass(a, qa) + mass(b, qb);
        return new ProcessRecipe("chem:" + product, new String[] {a, b}, new int[] {qa, qb}, new int[] {0, 0}, null, 0,
                new String[] {product}, new long[] {total}, (qa + qb) * 100, (qa + qb) * 5000L, 0, 20, true);
    }
    public static ProcessRecipe finishing(String feed, String product) {
        return new ProcessRecipe("chem:" + product, new String[] {feed}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {product}, new long[] {mass(feed, 1)}, 100, 5000, 0, 20, true);
    }
    public static ProcessRecipe leaded(String feed) {
        return new ProcessRecipe("chem:leaded_" + feed, new String[] {feed}, new int[] {100}, new int[] {0}, "FUEL_ADDITIVE_DOSE_ITEM", 1,
                new String[] {feed + "_LEADED"}, new long[] {mass(feed, 100) + mass("FUEL_ADDITIVE_DOSE_ITEM", 1)}, 10000, 500000, 0, 20, true);
    }
    public static ProcessRecipe methanol() {
        // 600 CO + 600 H2 from syngas; 95% reacts with 570 extra H2. 60 syngas units purge intact.
        long purge = mass("SYNGAS", 60), total = mass("SYNGAS", 1200) + mass("HYDROGEN_GAS", 570);
        return new ProcessRecipe("chem:methanol", new String[] {"SYNGAS", "HYDROGEN_GAS"}, new int[] {1200, 570}, new int[] {1, 1}, null, 0,
                new String[] {"METHANOL", "SYNGAS"}, new long[] {total - purge, purge}, 1500, 600000, 80000, 250, false);
    }
    public static ProcessRecipe syntheticGasoline() {
        // CO + 2H2 -> CH2 + H2O. FT/stabilization is deliberately compressed into one process family.
        long total = mass("SYNGAS", 1124) + mass("HYDROGEN_GAS", 562);
        long gasoline = ratio(total, "14.02658", "32.04186");
        return new ProcessRecipe("chem:synthetic_gasoline", new String[] {"SYNGAS", "HYDROGEN_GAS"}, new int[] {1124, 562}, new int[] {1, 1}, null, 0,
                new String[] {"GASOLINE", "WATER"}, new long[] {gasoline, total - gasoline}, 2400, 960000, 200000, 250, false);
    }
    public static ProcessRecipe coalLiquid(String product) {
        long coal = mass("COAL_ITEM", 8);
        long carbon = ratio(coal, "0.80", "1"), coalH = ratio(coal, "0.05", "1"), oxygen = coalH;
        long convertedCarbon = ratio(carbon, "0.75", "1");
        long liquid = ratio(convertedCarbon, "14.02658", "12.0107");
        long water = ratio(oxygen, "18.01528", "15.9994");
        long charMass = carbon - convertedCarbon, ash = ratio(coal, "0.10", "1");
        long hydrogenRequired = liquid + water + charMass + ash - coal;
        int hydrogen = Math.toIntExact(PetroleumProcesses.ceilDiv(hydrogenRequired, mass("HYDROGEN_GAS", 1)));
        long excess = mass("HYDROGEN_GAS", hydrogen) - hydrogenRequired;
        return new ProcessRecipe("chem:coal_" + product, new String[] {"HYDROGEN_GAS"}, new int[] {hydrogen}, new int[] {1}, "COAL_ITEM", 8,
                new String[] {product, "WATER", "CHAR_ITEM", "ASH_ITEM", "VENT_HYDROGEN_GAS"}, new long[] {liquid, water, charMass, ash, excess},
                80000, 32000000, 240000000, 450, false);
    }
    public static ProcessRecipe gasification() {
        // Whole coal assay plus 2 L water; 75% carbon gasifies to equimolar CO/H2.
        long coal = mass("COAL_ITEM", 1), carbon = ratio(coal, "0.8", "1"), converted = ratio(carbon, "0.75", "1");
        long syngas = ratio(converted, "30.02598", "12.0107");
        long charMass = carbon - converted, ash = ratio(coal, "0.1", "1");
        long consumedWater = ratio(ratio(converted, "15.9994", "12.0107") - ratio(coal, "0.05", "1"), "18.01528", "15.9994");
        long water = mass("WATER", 2) - consumedWater;
        long purge = coal + mass("WATER", 2) - syngas - charMass - ash - water;
        return new ProcessRecipe("chem:coal_syngas", new String[] {"FRESH_WATER"}, new int[] {2}, new int[] {0}, "COAL_ITEM", 1,
                new String[] {"SYNGAS", "WATER", "CHAR_ITEM", "ASH_ITEM", "VENT_HYDROGEN_GAS"}, new long[] {syngas, water, charMass, ash, purge}, 10000, 4000000, 12000000, 850, false).rebatch(3);
    }
    public static ProcessRecipe methaneReforming() {
        // CH4 + 2H2O -> CO2 + 4H2; excess liquid feed is vented after heating.
        long h2 = mass("HYDROGEN_GAS", 4000), co2 = mass("CARBONDIOXIDE", 1000);
        long excess = mass("GAS", 1000) + mass("WATER", 2) - h2 - co2;
        return new ProcessRecipe("chem:methane_reforming", new String[] {"GAS", "FRESH_WATER"}, new int[] {1000, 2}, new int[] {0, 0}, null, 0,
                new String[] {"HYDROGEN_GAS", "CARBONDIOXIDE", "FRESH_WATER"}, new long[] {h2, co2, excess}, 4000, 1600000, 14000000, 850, false);
    }
    public static ProcessRecipe gasRecovery() {
        long lpg = mass("LPG_VAPOR_ASSAY", 1000), methane = mass("GAS", 1000);
        return new ProcessRecipe("chem:lpg_recovery", new String[] {"PETROLEUM"}, new int[] {2000}, new int[] {0}, null, 0,
                new String[] {"LPG", "GAS"}, new long[] {lpg, methane}, 10000, 4000000, 0, 20, true);
    }
    public static ProcessRecipe sweetening() {
        return new ProcessRecipe("chem:sweetening", new String[] {"SOURGAS"}, new int[] {1000}, new int[] {0}, null, 0,
                new String[] {"GAS", "HYDROGEN_SULFIDE"}, new long[] {mass("GAS", 900), mass("HYDROGEN_SULFIDE", 100)}, 1000, 100000, 250000, 100, true);
    }
    public static ProcessRecipe sulfurRecovery() {
        long total = mass("HYDROGEN_SULFIDE", 1000) + mass("OXYGEN_GAS", 500);
        long sulfur = 32_065_000_000L * 1000; // 32.065 g/mol at 22.711 standard L/mol.
        return new ProcessRecipe("chem:sulfur_recovery", new String[] {"HYDROGEN_SULFIDE", "OXYGEN_GAS"}, new int[] {1000, 500}, new int[] {0, 0}, null, 0,
                new String[] {"WATER", "SULFUR_ITEM"}, new long[] {total - sulfur, sulfur}, 1000, 100000, 50000, 300, false);
    }
    public static ProcessRecipe digestion() {
        long total = mass("BIOMASS_ITEM", 16);
        return new ProcessRecipe("chem:biogas", new String[] {}, new int[] {}, new int[] {}, "BIOMASS_ITEM", 16,
                new String[] {"BIOGAS", "WATER"}, PetroleumProcesses.split(total, new double[] {.75, .25}), 10000, 2000000, 500000, 40, false).rebatch(4); // One stack of established biological feed.
    }
    public static ProcessRecipe fermentation() {
        long sugar = mass("SUGAR_ITEM", 16);
        long ethanol = ratio(sugar, "92.13688", "180.15588");
        return new ProcessRecipe("chem:ethanol", new String[] {}, new int[] {}, new int[] {}, "SUGAR_ITEM", 16,
                new String[] {"ETHANOL", "CARBONDIOXIDE"}, new long[] {ethanol, sugar - ethanol}, 16000, 1600000, 320000, 40, false).rebatch(4); // One stack; same conversion and paid energy per sugar.
    }
    public static ProcessRecipe oilExtraction(boolean fish) {
        long feed = mass(fish ? "FISH_FEED_ITEM" : "SEED_FEED_ITEM", 1);
        return new ProcessRecipe(fish ? "chem:fish_oil_extraction" : "chem:seed_oil_extraction", new String[] {}, new int[] {}, new int[] {}, fish ? "FISH_FEED_ITEM" : "SEED_FEED_ITEM", 1,
                new String[] {fish ? "FISHOIL" : "SUNFLOWEROIL", "BIOMASS_ITEM"}, PetroleumProcesses.split(feed, new double[] {fish ? .2 : .4, fish ? .8 : .6}),
                100, 5000, 0, 20, true);
    }
    public static ProcessRecipe woodPyrolysis() {
        long wood = mass("WOOD_LOG", 1);
        return new ProcessRecipe("chem:wood_pyrolysis", new String[] {}, new int[] {}, new int[] {}, "WOOD_LOG", 1,
                new String[] {"WOODOIL", "WATER", "CHARCOAL_ITEM"}, new long[] {mass("WOODOIL", 1), ratio(wood, ".45", "1"), mass("CHARCOAL_ITEM", 1)},
                2500, 1000000, 6000000, 500, false);
    }
    public static ProcessRecipe cokeOven(String feed) {
        boolean lignite = feed.startsWith("LIGNITE");
        long total = mass(feed, 1);
        long coke = ratio(total, lignite ? ".50" : ".70", "1");
        long tar = ratio(total, ".22", "1");
        return new ProcessRecipe("cokeoven:" + feed, new String[] {}, new int[] {}, new int[] {}, feed, 1,
                new String[] {lignite ? "LIGNITE_COKE_ITEM" : "COKE_ITEM", "COALCREOSOTE", "VENT_WATER"},
                new long[] {coke, tar, total - coke - tar}, 200, 0, 2000000, 500, false);
    }
    public static ProcessRecipe woodCokeOven(String feed) {
        long total = mass(feed, 1);
        if(feed.equals("SAPLING_ITEM")) return new ProcessRecipe("cokeoven:" + feed, new String[] {}, new int[] {}, new int[] {}, feed, 1,
                new String[] {"WOOD_ASH_ITEM", "WOODOIL", "VENT_WATER"},
                PetroleumProcesses.split(total, new double[] {.1, .2, .7}), 150, 0, 1500000, 500, false);
        return new ProcessRecipe("cokeoven:" + feed, new String[] {}, new int[] {}, new int[] {}, feed, 1,
                new String[] {"CHARCOAL_ITEM", "WOODOIL", "VENT_WATER"},
                new long[] {mass("CHARCOAL_ITEM", 1), mass("WOODOIL", 1), total - mass("CHARCOAL_ITEM", 1) - mass("WOODOIL", 1)},
                600, 0, 6000000, 500, false);
    }
    public static ProcessRecipe tarCoking(String feed) {
        String liquid = feed.equals("TAR_COAL_ITEM") ? "COALCREOSOTE" : feed.equals("TAR_WOOD_ITEM") ? "WOODOIL" : "OIL_COKER";
        String coke = feed.equals("TAR_CRUDE_ITEM") || feed.equals("TAR_CRACK_ITEM") ? "PETCOKE_ITEM" : "COKE_ITEM";
        long total = mass(feed, 1), liquidMass = ratio(total, ".75", "1");
        return new ProcessRecipe("cokeoven:" + feed, new String[] {}, new int[] {}, new int[] {}, feed, 1,
                new String[] {coke, liquid}, new long[] {total - liquidMass, liquidMass}, 330, 0, 3300000, 500, false);
    }
    public static ProcessRecipe tarMelting(String feed, String product) {
        return new ProcessRecipe("chem:melt_" + feed, new String[] {}, new int[] {}, new int[] {}, feed, 1,
                new String[] {product}, new long[] {mass(feed, 1)}, 120, 5000, 300000, 150, true);
    }
    public static ProcessRecipe bitumenFreezing() {
        return new ProcessRecipe("solidify:BITUMEN", new String[] {"BITUMEN"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"TAR_CRUDE_ITEM"}, new long[] {mass("BITUMEN", 1)}, 100, 5000, 0, 20, true);
    }
    public static ProcessRecipe reactorFraction() {
        ProcessRecipe bill = PetroleumProcesses.defaults("fraction").get("SMEAR");
        // Chemistry's raw duty is independent of the native Fraction Tower's electrical utility adapter.
        return new ProcessRecipe("chem:lubricant", bill.inputs, bill.quantities, bill.pressures, null, 0, bill.outputs, bill.productMass,
                126, PetroleumProcesses.joules(bill.inputMass(), 5000), PetroleumProcesses.joules(bill.inputMass(), 640000), bill.referenceTemperatureC, true);
    }
    public static ProcessRecipe reclaimed() {
        return new ProcessRecipe("chem:reclaimed", new String[] {"SMEAR"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"RECLAIMED", "BITUMEN"}, PetroleumProcesses.split(mass("SMEAR", 1), new double[] {.5, .5}),
                126, 4900, 627200, 300, true);
    }
    public static ProcessRecipe oilSand() {
        long ore = mass("OIL_SAND_BLOCK", 1), sand = mass("SAND_BLOCK", 1);
        return new ProcessRecipe("chem:oil_sand", new String[] {}, new int[] {}, new int[] {}, "OIL_SAND_BLOCK", 1,
                new String[] {"BITUMEN", "SAND_BLOCK"}, new long[] {ore - sand, sand}, 40000, 10000000, 200000000, 150, true);
    }
    public static ProcessRecipe additive() {
        // Declared reduced TEL synthesis: Pb + 4 C2H4 + 2 H2 -> Pb(C2H5)4.
        // Catalysts/halide intermediates are internal recycled equipment, with paid utilities.
        long lead = mass("LEAD_ITEM", 1);
        long ethylene = ratio(lead, "112.21264", "207.2"), hydrogen = ratio(lead, "4.03176", "207.2");
        int qe = Math.toIntExact(PetroleumProcesses.ceilDiv(ethylene, mass("UNSATURATEDS", 1)));
        int qh = Math.toIntExact(PetroleumProcesses.ceilDiv(hydrogen, mass("HYDROGEN_GAS", 1)));
        return new ProcessRecipe("chem:trace_additive", new String[] {"UNSATURATEDS", "HYDROGEN_GAS"}, new int[] {qe, qh}, new int[] {1, 1}, "LEAD_ITEM", 1,
                new String[] {"FUEL_ADDITIVE_ITEM", "VENT_UNSATURATEDS", "VENT_HYDROGEN_GAS"},
                new long[] {lead + ethylene + hydrogen, mass("UNSATURATEDS", qe) - ethylene, mass("HYDROGEN_GAS", qh) - hydrogen}, 1000, 400000, 1000000, 300, false);
    }
    public static void validateRating(ProcessRecipe bill) { validateRating(bill, false); }
    public static void validateReactorRating(ProcessRecipe bill) { validateRating(bill, true); }
    private static void validateRating(ProcessRecipe bill, boolean reactor) {
        if(reactor ? Math.addExact(bill.workQuanta, bill.heatQuanta) > (long) bill.duration * 10800L
                : bill.workQuanta > (long) bill.duration * 800L || bill.heatQuanta > (long) bill.duration * 10000L)
            throw new IllegalArgumentException(reactor ? "Chemical Reactor bill exceeds 108 kW combined electrical duty; lengthen duration" : "Mixer physical bill exceeds 8 kWe / 100 kWth; lengthen duration");
        for(int i = 0; i < bill.inputs.length; i++) {
            PhysicalState state = ProcessRecipe.resolved(bill.inputs[i]);
            if(state.substance.equals("hydrogen") && state.phase == PhysicalState.Phase.LIQUID) throw new IllegalArgumentException("Ordinary synthesis requires HYDROGEN_GAS");
            long rate = state.phase == PhysicalState.Phase.GAS ? 6000L : state.basis == PhysicalState.Basis.NOMINAL ? 1200L : waterService(bill.inputs[i]) || reactor && commodityService(bill.inputs[i]) ? 60L : 12L;
            if((long) bill.quantities[i] * 1200L > (long) bill.duration * rate) throw new IllegalArgumentException("Reactor/mixer feed exceeds nameplate");
        }
        for(int i = 0; i < bill.outputs.length; i++) if(ProcessRecipe.resolved(bill.outputs[i]).basis == PhysicalState.Basis.LIQUID_LITER
                && BigDecimal.valueOf(bill.productMass[i]).multiply(BigDecimal.valueOf(1200))
                    .compareTo(BigDecimal.valueOf(ProcessRecipe.resolved(bill.outputs[i]).massUnits).multiply(BigDecimal.valueOf((long) bill.duration * (waterService(bill.outputs[i]) || reactor && commodityService(bill.outputs[i]) ? 60 : 12)))) > 0)
            throw new IllegalArgumentException("Reactor/mixer liquid output exceeds nameplate");
    }
    private static boolean waterService(String name) {
        return name.equals("WATER") || name.equals("FRESH_WATER") || name.equals("LIGHT_WATER") || name.equals("BORATED_WATER");
    }
    /** Existing general-purpose reactor plumbing handles basic aqueous reagents at water-service flow.
     * Oil/fuel and specialty synthesis keep their separate process ceilings. */
    private static boolean commodityService(String name) {
        for(String commodity : new String[] {"HCL", "PEROXIDE", "NITRIC_ACID", "SULFURIC_ACID", "HYDROFLUORIC_ACID",
                "BRINE", "MINERAL_BRINE", "BITTERN", "IODINE_BRINE", "SODIUM_HYDROXIDE", "SODIUM_CARBONATE", "POTASSIUM_CHLORIDE", "CALCIUM_CHLORIDE"})
            if(name.equals(commodity)) return true;
        return false;
    }
    public static boolean requiresBill(String output) {
        for(String name : new String[] {"GAS", "CO", "RAW_NATURAL_GAS", "UNSATURATEDS", "AROMATICS", "XYLENE", "HEAVYOIL", "LIGHTOIL", "NAPHTHA", "SMEAR", "HEATINGOIL", "BITUMEN", "COALCREOSOTE", "HOTOIL", "OIL", "COALOIL", "COALGAS", "GASOLINE", "DIESEL", "KEROSENE", "PETROIL", "BIOFUEL", "ETHANOL", "WOODOIL", "LPG", "SYNGAS", "REFORMGAS", "GAS_COKER", "PETROLEUM", "METHANOL", "LUBRICANT", "RECLAIMED"})
            if(output.equals(name) || output.startsWith(name + "_")) return true;
        return false;
    }
}
