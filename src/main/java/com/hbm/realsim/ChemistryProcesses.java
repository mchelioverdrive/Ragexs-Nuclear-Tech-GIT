package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Released chemistry bills. Counts at phase boundaries are derived from mass, never copied. */
public final class ChemistryProcesses {
    private ChemistryProcesses() { }
    public static long mass(String name, int count) { return Math.multiplyExact(ProcessRecipe.resolved(name).massUnits, count); }
    /** Adopted dry binder parcel 0.5 kg/item, w/c = 0.5; four parcels consume one retained kg of water. */
    public static int concreteWater(int cementItems) {
        if(cementItems <= 0 || cementItems % 4 != 0) throw new IllegalArgumentException("Concrete needs whole common-denominator binder batches");
        return cementItems / 4;
    }
    private static long ratio(long value, String a, String b) {
        return BigDecimal.valueOf(value).multiply(new BigDecimal(a)).divide(new BigDecimal(b), 0, RoundingMode.DOWN).longValueExact();
    }
    private static ProcessRecipe bill(String id, String[] in, int[] count, String solid, int solidCount,
            String[] out, long[] products, int ticks, long work, long heat, int temperature, boolean separation) {
        int[] pressure = new int[in.length];
        ProcessRecipe result = new ProcessRecipe("chem:" + id, in, count, pressure, solid, solidCount, out, products, ticks, work, heat, temperature, separation);
        ChemicalFuelProcesses.validateRating(result);
        return result;
    }
    public static ProcessRecipe electrolysis(int watts) {
        long water = mass("FRESH_WATER", 1), hydrogen = ratio(water, "2.01588", "18.01528");
        // DOE PEM system envelope, adopted 55 kWh/kg H2. Fractional products remain owned.
        long work = BigDecimal.valueOf(hydrogen).multiply(new BigDecimal("198000000")).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 0, RoundingMode.CEILING).longValueExact();
        int ticks = Math.toIntExact(PetroleumProcesses.ceilDiv(work * 20, watts));
        return new ProcessRecipe("chem:water_electrolysis", new String[] {"FRESH_WATER"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"HYDROGEN_GAS", "OXYGEN_GAS"}, new long[] {hydrogen, water - hydrogen}, ticks, work, 0, 20, false).continuous();
    }
    public static ProcessRecipe ammonia() {
        long total = mass("NITROGEN_GAS", 100) + mass("HYDROGEN_GAS", 300);
        return bill("ammonia", new String[] {"NITROGEN_GAS", "HYDROGEN_GAS"}, new int[] {100, 300}, null, 0,
                new String[] {"AMMONIA"}, new long[] {total}, 200, 60000, 15000, 400, false).rebatch(3);
    }
    public static ProcessRecipe peroxide() {
        long reagent = mass("HYDROGEN_GAS", 100) + mass("OXYGEN_GAS", 100), water = mass("FRESH_WATER", 1);
        long solution = ratio(reagent, "1", ".30");
        return bill("peroxide", new String[] {"HYDROGEN_GAS", "OXYGEN_GAS", "FRESH_WATER"}, new int[] {100, 100, 1}, null, 0,
                new String[] {"PEROXIDE", "FRESH_WATER"}, new long[] {solution, reagent + water - solution}, 200, 20000, 0, 20, false).rebatch(3);
    }
    public static ProcessRecipe hydrochloric() {
        long reagent = mass("HYDROGEN_GAS", 100) + mass("CHLORINE", 100), water = mass("FRESH_WATER", 1);
        long acid = ratio(reagent, "1", ".30");
        return bill("hcl_absorption", new String[] {"HYDROGEN_GAS", "CHLORINE", "FRESH_WATER"}, new int[] {100, 100, 1}, null, 0,
                new String[] {"HCL", "FRESH_WATER"}, new long[] {acid, reagent + water - acid}, 200, 20000, 0, 20, false).rebatch(3);
    }
    public static ProcessRecipe nitric() {
        // NH3 + 2 O2 -> HNO3 + H2O, then aqueous absorption to 65 wt% acid.
        long feed = mass("AMMONIA", 100) + mass("OXYGEN_GAS", 200) + mass("FRESH_WATER", 1);
        long acid = ratio(mass("AMMONIA", 100), "63.01284", "17.03052");
        long solution = ratio(acid, "1", ".65");
        return bill("nitric_acid", new String[] {"AMMONIA", "OXYGEN_GAS", "FRESH_WATER"}, new int[] {100, 200, 1}, null, 0,
                new String[] {"NITRIC_ACID", "FRESH_WATER"}, new long[] {solution, feed - solution}, 200, 60000, 10000, 850, false).rebatch(4);
    }
    public static ProcessRecipe sulfuric() {
        // S + 1.5 O2 + H2O -> H2SO4. Rounded oxidizer excess is exported intact.
        long sulfur = mass("SULFUR_ITEM", 1), oxygen = ratio(sulfur, "47.9982", "32.065");
        int gas = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1)));
        long acid = ratio(sulfur, "98.07848", "32.065"), solution = ratio(acid, "1", ".98");
        long total = sulfur + mass("OXYGEN_GAS", gas) + mass("FRESH_WATER", 1);
        long excess = mass("OXYGEN_GAS", gas) - oxygen;
        return bill("sulfuric_acid", new String[] {"OXYGEN_GAS", "FRESH_WATER"}, new int[] {gas, 1}, "SULFUR_ITEM", 1,
                new String[] {"SULFURIC_ACID", "FRESH_WATER", "OXYGEN_GAS"}, new long[] {solution, total - solution - excess, excess}, 1200, 120000, 100000, 450, false).rebatch(4).withDuration(400);
    }
    public static ProcessRecipe waterTreatment() {
        // Raw water contains NaCl plus trace geological KBr/NaI; manufactured salt brine does not.
        return bill("water_treatment", new String[] {"SALT_WATER"}, new int[] {10}, null, 0,
                new String[] {"FRESH_WATER", "MINERAL_BRINE"}, new long[] {mass("FRESH_WATER", 9), mass("SALT_WATER", 10) - mass("FRESH_WATER", 9)}, 1000, 30000, 0, 20, true).rebatch(5);
    }
    public static ProcessRecipe mineralBrineSeparation() {
        // Tenfold concentration of raw-water trace salts, then selective recovery into existing liquors.
        long feed = mass("MINERAL_BRINE", 10), bittern = ratio(feed, ".002", ".02046"), iodide = ratio(feed, ".00005", ".01");
        return bill("mineral_brine_separation", new String[] {"MINERAL_BRINE", "FRESH_WATER"}, new int[] {10, 2}, null, 0,
                new String[] {"BRINE", "BITTERN", "IODINE_BRINE", "FRESH_WATER"},
                new long[] {feed, bittern, iodide, mass("FRESH_WATER", 2) - bittern - iodide}, 2400, 100000, 0, 20, true).rebatch(2).withDuration(600);
    }
    public static ProcessRecipe halogenRecovery(boolean iodine, boolean solidFeed) {
        String source = solidFeed ? "POTASSIUM_IODIDE_ITEM" : iodine ? "IODINE_BRINE" : "BITTERN";
        long feed = mass(source, 1), salt = solidFeed ? feed : ratio(feed, iodine ? ".01" : ".02046", "1");
        String molar = solidFeed ? "166.00277" : iodine ? "149.89424" : "119.0023";
        long halogen = ratio(salt, iodine ? "126.90447" : "79.904", molar), chlorine = ratio(salt, "35.453", molar);
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(chlorine, mass("CHLORINE", 1)));
        long chloride = salt + chlorine - halogen;
        boolean potassium = solidFeed || !iodine;
        long solution = potassium ? ratio(chloride, "1", ".20") : chloride;
        String[] inputs = solidFeed ? new String[] {"CHLORINE", "FRESH_WATER"} : new String[] {source, "CHLORINE"};
        int[] counts = solidFeed ? new int[] {q, 1} : new int[] {1, q};
        return bill("halogen_recovery_" + source.toLowerCase(java.util.Locale.ROOT), inputs, counts, solidFeed ? source : null, solidFeed ? 1 : 0,
                new String[] {iodine ? "IODINE_ITEM" : "BROMINE_REAGENT_ITEM", potassium ? "POTASSIUM_CHLORIDE" : "SALT_ITEM", "FRESH_WATER", "CHLORINE"},
                new long[] {halogen, solution, feed + (solidFeed ? mass("FRESH_WATER", 1) : 0) + chlorine - halogen - solution, mass("CHLORINE", q) - chlorine},
                200, 20000, 0, 20, false).rebatch(solidFeed ? 1 : 2);
    }
    public static ProcessRecipe demineralization() {
        return bill("demineralization", new String[] {"FRESH_WATER"}, new int[] {10}, null, 0,
                new String[] {"LIGHT_WATER"}, new long[] {mass("FRESH_WATER", 10)}, 1000, 30000, 0, 20, true).rebatch(5);
    }
    public static ProcessRecipe brine() {
        long total = mass("FRESH_WATER", 31) + mass("SALT_ITEM", 1);
        return bill("brine", new String[] {"FRESH_WATER"}, new int[] {31}, "SALT_ITEM", 1,
                new String[] {"BRINE"}, new long[] {total}, 3200, 160000, 0, 20, true);
    }
    public static ProcessRecipe evaporation() {
        long feed = mass("BRINE", 1), salt = ratio(feed, ".03125", "1"), water = feed - salt;
        long heat = BigDecimal.valueOf(water).multiply(BigDecimal.valueOf(SteamLedger.State.ORDINARY.enthalpyJPerKg))
                .divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 0, RoundingMode.CEILING).longValueExact();
        return bill("brine_evaporation", new String[] {"BRINE"}, new int[] {1}, null, 0,
                new String[] {"SALT_ITEM", "STEAM"}, new long[] {salt, water}, 600, 30000, heat, 120, true);
    }
    public static ProcessRecipe boricAcid() {
        long boron = mass("BORON_ITEM", 1), oxygen = ratio(boron, "47.9982", "21.622"), water = ratio(boron, "54.04584", "21.622");
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1)));
        return bill("boric_acid", new String[] {"OXYGEN_GAS", "FRESH_WATER"}, new int[] {q, 3}, "BORON_ITEM", 1,
                new String[] {"BORIC_ACID_ITEM", "OXYGEN_GAS", "FRESH_WATER"}, new long[] {boron + oxygen + water, mass("OXYGEN_GAS", q) - oxygen, mass("FRESH_WATER", 3) - water},
                12000, 100000, 200000, 900, false).withDuration(600);
    }
    public static ProcessRecipe borated() {
        long total = mass("LIGHT_WATER", 99) + mass("BORIC_ACID_ITEM", 1);
        return bill("borated_water", new String[] {"LIGHT_WATER"}, new int[] {99}, "BORIC_ACID_ITEM", 1,
                new String[] {"BORATED_WATER"}, new long[] {total}, 2400, 500000, 0, 20, true);
    }
    public static ProcessRecipe polymer(boolean fluid) {
        long feed = mass("UNSATURATEDS", 1000);
        return bill("polyethylene", new String[] {"UNSATURATEDS"}, new int[] {1000}, null, 0,
                new String[] {fluid ? "POLYTHYLENE" : "POLYMER_ITEM"}, new long[] {feed}, 2000, 200000, 100000, 200, false).rebatch(8).withDuration(fluid ? 1200 : 600);
    }
    public static ProcessRecipe phosgene() {
        long feed = mass("CO", 100) + mass("CHLORINE", 100);
        return bill("phosgene", new String[] {"CO", "CHLORINE"}, new int[] {100, 100}, null, 0,
                new String[] {"PHOSGENE"}, new long[] {feed}, 200, 20000, 0, 150, false).rebatch(5);
    }
    public static ProcessRecipe combustion() {
        long total = mass("GAS", 100) + mass("OXYGEN_GAS", 200), co2 = mass("CARBONDIOXIDE", 100);
        return bill("methane_oxidation", new String[] {"GAS", "OXYGEN_GAS"}, new int[] {100, 200}, null, 0,
                new String[] {"CARBONDIOXIDE", "FRESH_WATER"}, new long[] {co2, total - co2}, 800, 20000, 0, 20, false).rebatch(2);
    }
    public static ProcessRecipe chloralkali(boolean saltFeed) {
        long salt = saltFeed ? mass("SALT_ITEM", 1) : ratio(mass("BRINE", 1), ".03125", "1");
        long total = saltFeed ? salt + mass("FRESH_WATER", 1) : mass("BRINE", 1);
        long chlorine = ratio(salt, "35.453", "58.44277"), hydrogen = ratio(salt, "1.00794", "58.44277");
        long lye = ratio(salt, "39.99711", "58.44277");
        int ticks = saltFeed ? 14000 : 500;
        return bill("chloralkali", new String[] {saltFeed ? "FRESH_WATER" : "BRINE"}, new int[] {1}, saltFeed ? "SALT_ITEM" : null, saltFeed ? 1 : 0,
                new String[] {"CHLORINE", "HYDROGEN_GAS", "FRESH_WATER", "LYE_ITEM"},
                new long[] {chlorine, hydrogen, total - chlorine - hydrogen - lye, lye}, ticks, saltFeed ? 5500000 : 200000, 0, 20, false).rebatch(saltFeed ? 8 : 2).withDuration(saltFeed ? 800 : 500);
    }
    public static ProcessRecipe hydrazine() {
        // Peroxide process: 2 NH3 + H2O2 -> N2H4 + 2 H2O. Organic carrier is recycled internally.
        long ammonia = mass("AMMONIA", 200), purePeroxide = ratio(ammonia, "34.01468", "34.06104");
        long consumedSolution = ratio(purePeroxide, "1", ".30"), product = ratio(ammonia, "32.04516", "34.06104");
        return bill("hydrazine", new String[] {"AMMONIA", "PEROXIDE"}, new int[] {200, 1}, null, 0,
                new String[] {"HYDRAZINE", "FRESH_WATER", "PEROXIDE"},
                new long[] {product, ammonia + consumedSolution - product, mass("PEROXIDE", 1) - consumedSolution}, 400, 80000, 400000, 120, false);
    }
    public static ProcessRecipe ammoniumNitrate() {
        long ammonia = mass("AMMONIA", 100), acid = ratio(ammonia, "63.01284", "17.03052");
        long consumedSolution = ratio(acid, "1", ".65");
        return bill("ammonium_nitrate", new String[] {"AMMONIA", "NITRIC_ACID"}, new int[] {100, 1}, null, 0,
                new String[] {"AMMONIUM_NITRATE_ITEM", "FRESH_WATER", "NITRIC_ACID"},
                new long[] {ammonia + acid, consumedSolution - acid, mass("NITRIC_ACID", 1) - consumedSolution}, 400, 80000, 400000, 120, false).rebatch(4);
    }
    public static ProcessRecipe fluorideElectrolysis() {
        // HF drying and anhydrous KF/HF cell compressed into one bill; water is rejected before electrolysis.
        long feed = mass("HYDROFLUORIC_ACID", 1), acid = ratio(feed, ".48", "1");
        long hydrogen = ratio(acid, "1.00794", "20.0063432");
        return bill("fluoride_electrolysis", new String[] {"HYDROFLUORIC_ACID"}, new int[] {1}, null, 0,
                new String[] {"FLUORINE", "HYDROGEN_GAS", "FRESH_WATER"}, new long[] {acid - hydrogen, hydrogen, feed - acid}, 14000, 5000000, 0, 20, false);
    }
    /** Recover a dry nutrient salt; the generic fertilizer dose does not assert one fixed anion. */
    public static ProcessRecipe fertilizer(boolean sulfate) {
        if(sulfate) return bill("sulfate_fertilizer_packaging", new String[0], new int[0], "POTASSIUM_SULFATE_ITEM", 1,
                new String[] {"FERTILIZER_ITEM"}, new long[] {mass("POTASSIUM_SULFATE_ITEM", 1)}, 200, 20000, 0, 20, true).rebatch(16);
        // Closed carrier recovery uses an abstracted utility bill rather than repeatedly boiling the whole fresh charge.
        long solution = mass("POTASSIUM_CHLORIDE", 5), salt = ratio(solution, ".20", "1");
        return bill("potash_fertilizer_recovery", new String[] {"POTASSIUM_CHLORIDE"}, new int[] {5}, null, 0,
                new String[] {"FERTILIZER_ITEM", "FRESH_WATER"}, new long[] {salt, solution - salt}, 3000, 75000, 1200000, 120, true).rebatch(12).withDuration(1200);
    }
    /** Aqueous salt exchange, selective crystallization and internal carrier-water recovery. */
    public static ProcessRecipe potassiumNitrate(boolean ammonium) {
        String nitrate = ammonium ? "AMMONIUM_NITRATE_ITEM" : "SODIUM_NITRATE_ITEM";
        long solid = mass(nitrate, 1), kcl = ratio(solid, "74.5513", ammonium ? "80.04336" : "84.99467");
        long product = ratio(solid, "101.1032", ammonium ? "80.04336" : "84.99467");
        long usedLiquor = ratio(kcl, "1", ".20"), solution = mass("POTASSIUM_CHLORIDE", 5);
        return bill(ammonium ? "potassium_nitrate_ammonium" : "potassium_nitrate_sodium", new String[] {"POTASSIUM_CHLORIDE"}, new int[] {5}, nitrate, 1,
                new String[] {"POTASSIUM_NITRATE_ITEM", ammonium ? "AMMONIUM_CHLORIDE_ITEM" : "SALT_ITEM", "POTASSIUM_CHLORIDE", "FRESH_WATER"},
                new long[] {product, solid + kcl - product, solution - usedLiquor, usedLiquor - kcl}, 3000, 100000, 10000000, 120, false).rebatch(2, 4000).withDuration(600);
    }
    public static ProcessRecipe hydrofluoric() {
        long fluorite = mass("FLUORITE_ITEM", 1), acid = ratio(fluorite, "98.07848", "78.0748064");
        long acidSolution = ratio(acid, "1", ".98"), hf = ratio(fluorite, "40.0126864", "78.0748064");
        long hfSolution = ratio(hf, "1", ".48"), gypsum = ratio(fluorite, "172.17116", "78.0748064");
        long excess = mass("SULFURIC_ACID", 1) - acidSolution;
        long total = fluorite + mass("SULFURIC_ACID", 1) + mass("FRESH_WATER", 1);
        return bill("hydrofluoric_acid", new String[] {"SULFURIC_ACID", "FRESH_WATER"}, new int[] {1, 1}, "FLUORITE_ITEM", 1,
                new String[] {"HYDROFLUORIC_ACID", "SULFURIC_ACID", "FRESH_WATER", "GYPSUM_ITEM"},
                new long[] {hfSolution, excess, total - hfSolution - excess - gypsum, gypsum}, 1000, 50000, 100000, 80, false).rebatch(8);
    }
    public static ProcessRecipe ammoniumChlorideDecomposition() {
        long salt = mass("AMMONIUM_CHLORIDE_ITEM", 1), ammonia = ratio(salt, "17.03052", "53.49146");
        long acid = salt - ammonia, solution = ratio(acid, "1", ".30");
        return bill("ammonium_chloride_decomposition", new String[] {"FRESH_WATER"}, new int[] {2}, "AMMONIUM_CHLORIDE_ITEM", 1,
                new String[] {"AMMONIA", "HCL", "FRESH_WATER"}, new long[] {ammonia, solution, salt + mass("FRESH_WATER", 2) - ammonia - solution},
                800, 80000, 2500000, 400, false);
    }
    public static ProcessRecipe heavyWater() {
        // Natural D/H 156 ppm, 80% isotope recovery; removed D2O mass replaces identical rejected mass.
        long total = mass("LIGHT_WATER", 10), recovered = ratio(total, ".000156", "1");
        recovered = ratio(recovered, "20.027603556", "18.01528"); recovered = ratio(recovered, ".8", "1");
        return new ProcessRecipe("isotope:heavy_water", new String[] {"LIGHT_WATER"}, new int[] {10}, new int[] {0}, null, 0,
                new String[] {"HEAVYWATER", "DEPLETED_WATER"}, new long[] {recovered, total - recovered}, 1200, 120000, 3000000, 120, false).electricallyDriven();
    }
    public static ProcessRecipe heavyWaterTower() {
        ProcessRecipe base = heavyWater();
        return new ProcessRecipe(base.id, base.inputs, base.quantities, base.pressures, null, 0, base.outputs, base.productMass, 100, 600000, 2000000, 120, false).electricallyDriven();
    }
    public static ProcessRecipe heavyElectrolysis() {
        long water = mass("HEAVYWATER", 1), deuterium = ratio(water, "4.028203556", "20.027603556");
        return new ProcessRecipe("electrolysis:heavy_water", new String[] {"HEAVYWATER"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"DEUTERIUM", "OXYGEN_GAS"}, new long[] {deuterium, water - deuterium}, 24000, 24000000, 0, 20, false);
    }
    public static ProcessRecipe meltLye() {
        return bill("caustic_soda_melting", new String[] {}, new int[] {}, "LYE_ITEM", 1,
                new String[] {"SODIUM_HYDROXIDE"}, new long[] {mass("LYE_ITEM", 1)}, 300, 20000, 1200000, 450, true);
    }
    public static ProcessRecipe frackingSlurry() {
        return bill("fracking_slurry", new String[] {"FRESH_WATER"}, new int[] {20}, "PROPPANT_ITEM", 1,
                new String[] {"FRACKSOL"}, new long[] {mass("FRESH_WATER", 20) + mass("PROPPANT_ITEM", 1)}, 2400, 120000, 0, 20, true);
    }
    public static ProcessRecipe additiveDoses() {
        return bill("fuel_additive_metering", new String[] {}, new int[] {}, "FUEL_ADDITIVE_ITEM", 1,
                new String[] {"FUEL_ADDITIVE_DOSE_ITEM"}, new long[] {mass("FUEL_ADDITIVE_ITEM", 1)}, 100, 5000, 0, 20, true);
    }
    public static ProcessRecipe radiolysis() {
        long water = mass("FRESH_WATER", 1), reacted = water / 4;
        long hydrogen = ratio(reacted, "2.01588", "36.03056"), peroxide = reacted - hydrogen;
        long solution = ratio(peroxide, "1", ".30");
        return new ProcessRecipe("radiolysis:peroxide", new String[] {"FRESH_WATER"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"PEROXIDE", "HYDROGEN_GAS", "FRESH_WATER"}, new long[] {solution, hydrogen, water - solution - hydrogen},
                40000, 20000000, 0, 20, false);
    }
    public static ProcessRecipe airSeparation() {
        return new ProcessRecipe("separation:air", new String[] {"AIR"}, new int[] {100}, new int[] {0}, null, 0,
                new String[] {"NITROGEN_GAS", "OXYGEN_GAS", "ARGON", "NOBLE_GAS_MIX"},
                new long[] {mass("NITROGEN_GAS", 77), mass("OXYGEN_GAS", 21), mass("ARGON", 1), mass("NOBLE_GAS_MIX", 1)},
                100, 50000, 0, 20, true);
    }
    public static ProcessRecipe sulfateReduction() {
        long sulfate = mass("SODIUM_SULFATE_ITEM", 1), carbon = ratio(sulfate, "24.0214", "142.04214");
        long sulfide = ratio(sulfate, "78.04454", "142.04214"), co2 = sulfate + carbon - sulfide;
        ProcessRecipe result = new ProcessRecipe("chem:sodium_sulfate_reduction", new String[] {}, new int[] {}, new int[] {},
                new String[] {"SODIUM_SULFATE_ITEM", "CHAR_ITEM"}, new int[] {1, 1},
                new String[] {"SODIUM_SULFIDE_ITEM", "CARBONDIOXIDE", "CHAR_ITEM"}, new long[] {sulfide, co2, mass("CHAR_ITEM", 1) - carbon},
                2000, 100000, 5000000, 850, false);
        ChemicalFuelProcesses.validateRating(result); return result.rebatch(2).withDuration(600);
    }
    public static ProcessRecipe sulfideAcidification() {
        long sulfide = mass("SODIUM_SULFIDE_ITEM", 1), acid = ratio(sulfide, "72.92188", "78.04454");
        long consumed = ratio(acid, "1", ".30"), salt = ratio(sulfide, "116.88554", "78.04454");
        long h2s = sulfide + acid - salt;
        return bill("sulfide_acidification", new String[] {"HCL"}, new int[] {3}, "SODIUM_SULFIDE_ITEM", 1,
                new String[] {"HYDROGEN_SULFIDE", "SALT_ITEM", "FRESH_WATER", "HCL"},
                new long[] {h2s, salt, consumed - acid, mass("HCL", 3) - consumed}, 800, 20000, 0, 20, false).rebatch(3).withDuration(300);
    }
    public static ProcessRecipe gadoliniumMelting() {
        return bill("gadolinium_melting", new String[] {}, new int[] {}, "GADOLINIUM_ITEM", 1,
                new String[] {"GADOLINIUM"}, new long[] {mass("GADOLINIUM_ITEM", 1)}, 2000, 800000, 0, 20, true);
    }
    public static ProcessRecipe gadoliniumRefining() {
        return bill("gadolinium_refining", new String[] {"GADOLINIUM", "SOLVENT"}, new int[] {1, 1}, null, 0,
                new String[] {"REFINED_GADOLINIUM_ITEM", "SOLVENT"}, new long[] {mass("GADOLINIUM", 1), mass("SOLVENT", 1)}, 2000, 100000, 0, 20, true);
    }
    public static ProcessRecipe rutheniumReduction() {
        long oxide = mass("RUTHENIUM_RESIDUE_ITEM", 1), metal = mass("RUTHENIUM_NUGGET_ITEM", 1);
        long hydrogen = ratio(oxide - metal, "2.01588", "15.9994");
        return bill("ruthenium_reduction", new String[] {"HYDROGEN_GAS"}, new int[] {100}, "RUTHENIUM_RESIDUE_ITEM", 1,
                new String[] {"RUTHENIUM_NUGGET_ITEM", "STEAM", "HYDROGEN_GAS"},
                new long[] {metal, oxide - metal + hydrogen, mass("HYDROGEN_GAS", 100) - hydrogen}, 1000, 50000, 800000, 500, false);
    }
    public static ProcessRecipe sulfideSynthesis() {
        long sulfur = mass("SULFUR_ITEM", 1), hydrogen = ratio(sulfur, "2.01588", "32.065");
        return bill("hydrogen_sulfide", new String[] {"HYDROGEN_GAS"}, new int[] {800}, "SULFUR_ITEM", 1,
                new String[] {"HYDROGEN_SULFIDE", "HYDROGEN_GAS"}, new long[] {sulfur + hydrogen, mass("HYDROGEN_GAS", 800) - hydrogen}, 1200, 60000, 500000, 200, false).rebatch(4).withDuration(400);
    }
    public static ProcessRecipe saltMelting() {
        return bill("salt_melting", new String[] {}, new int[] {}, "SALT_ITEM", 1,
                new String[] {"MOLTEN_SALT"}, new long[] {mass("SALT_ITEM", 1)}, 400, 20000, 1100000, 850, true);
    }
    public static ProcessRecipe moltenSaltElectrolysis() {
        long feed = mass("MOLTEN_SALT", 144), sodium = ratio(feed, "22.98977", "58.44277");
        return new ProcessRecipe("electrolysis:molten_salt", new String[] {"MOLTEN_SALT"}, new int[] {144}, new int[] {0}, null, 0,
                new String[] {"SODIUM", "CHLORINE"}, new long[] {sodium, feed - sodium}, 6000, 6000000, 0, 20, false);
    }
    public static ProcessRecipe sodiumHydroxide() {
        long sodium = mass("SODIUM", 100), water = ratio(sodium, "18.01528", "22.98977");
        long hydrogen = ratio(sodium, "1.00794", "22.98977");
        return bill("sodium_hydroxide", new String[] {"SODIUM", "FRESH_WATER"}, new int[] {100, 1}, null, 0,
                new String[] {"SODIUM_HYDROXIDE", "HYDROGEN_GAS", "FRESH_WATER"}, new long[] {sodium + water - hydrogen, hydrogen, mass("FRESH_WATER", 1) - water},
                1000, 50000, 0, 20, false).rebatch(3).withDuration(400);
    }
    public static ProcessRecipe potassiumChlorideElectrolysis() {
        long feed = mass("POTASSIUM_CHLORIDE", 1), salt = ratio(feed, ".2", "1");
        long chlorine = ratio(salt, "35.453", "74.5513"), hydrogen = ratio(salt, "1.00794", "74.5513"), lye = ratio(salt, "56.10564", "74.5513");
        return new ProcessRecipe("electrolysis:potassium_chloride", new String[] {"POTASSIUM_CHLORIDE"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"CHLORINE", "HYDROGEN_GAS", "FRESH_WATER", "POTASH_LYE_ITEM"},
                new long[] {chlorine, hydrogen, feed - chlorine - hydrogen - lye, lye}, 1000, 1000000, 0, 20, false);
    }
    public static ProcessRecipe potassiumReduction() {
        long feed = mass("POTASSIUM_CHLORIDE", 1), salt = ratio(feed, ".2", "1");
        long potassium = ratio(salt, "39.0983", "74.5513"), sodium = ratio(salt, "22.98977", "74.5513");
        return bill("potassium_reduction", new String[] {"POTASSIUM_CHLORIDE", "SODIUM"}, new int[] {1, 100}, null, 0,
                new String[] {"POTASSIUM", "MOLTEN_SALT", "SODIUM", "STEAM"},
                new long[] {potassium, salt + sodium - potassium, mass("SODIUM", 100) - sodium, feed - salt}, 2000, 100000, 4000000, 850, false).withDuration(800);
    }
    public static ProcessRecipe potashLyeMelting() {
        return bill("potash_lye_melting", new String[] {}, new int[] {}, "POTASH_LYE_ITEM", 1,
                new String[] {"POTASSIUM_HYDROXIDE"}, new long[] {mass("POTASH_LYE_ITEM", 1)}, 300, 20000, 1100000, 450, true);
    }
    public static ProcessRecipe potashChlorides() {
        // Adopted 45/35/20 wt% K2CO3/Na2CO3/Rb2CO3 concentrate, not a universal wood-ash assay.
        long feed = mass("POTASH_ITEM", 1), k = ratio(feed, ".45", "1"), na = ratio(feed, ".35", "1"), rb = feed - k - na;
        long kcl = ratio(k, "149.1026", "138.2055"), nacl = ratio(na, "116.88554", "105.98844"), rbcl = ratio(rb, "241.8416", "230.9445");
        long co2 = ratio(k, "44.0095", "138.2055") + ratio(na, "44.0095", "105.98844") + ratio(rb, "44.0095", "230.9445");
        long acid = ratio(k, "72.92188", "138.2055") + ratio(na, "72.92188", "105.98844") + ratio(rb, "72.92188", "230.9445");
        long excess = mass("HCL", 3) - ratio(acid, "1", ".30"), solution = ratio(kcl, "1", ".2");
        long total = feed + mass("HCL", 3) + mass("FRESH_WATER", 1);
        return bill("potash_chlorides", new String[] {"HCL", "FRESH_WATER"}, new int[] {3, 1}, "POTASH_ITEM", 1,
                new String[] {"POTASSIUM_CHLORIDE", "SALT_ITEM", "RUBIDIUM_SALT_ITEM", "CARBONDIOXIDE", "HCL", "FRESH_WATER"},
                new long[] {solution, nacl, rbcl, co2, excess, total - solution - nacl - rbcl - co2 - excess}, 1200, 60000, 0, 20, false).rebatch(4).withDuration(400);
    }
    public static ProcessRecipe chloromethane() {
        // Radical substitution, not methane/chlorine blending: CH4 + Cl2 -> CH3Cl + HCl.
        long product = ratio(mass("GAS", 100), "50.48752", "16.04246");
        long acid = ratio(mass("GAS", 100), "36.46094", "16.04246"), solution = ratio(acid, "1", ".30");
        long total = mass("GAS", 100) + mass("CHLORINE", 100) + mass("FRESH_WATER", 1);
        return bill("chloromethane", new String[] {"GAS", "CHLORINE", "FRESH_WATER"}, new int[] {100, 100, 1}, null, 0,
                new String[] {"CHLOROMETHANE", "HCL", "FRESH_WATER"}, new long[] {product, solution, total - product - solution}, 400, 40000, 100000, 300, false).rebatch(2);
    }
    public static ProcessRecipe calciumChloride() {
        long lime = mass("QUICKLIME_ITEM", 1), used = ratio(lime, "72.92188", "56.0774");
        long salt = ratio(lime, "110.984", "56.0774"), solution = ratio(salt, "1", ".40");
        long acid = mass("HCL", 4), excess = acid - ratio(used, "1", ".30");
        return bill("calcium_chloride", new String[] {"HCL"}, new int[] {4}, "QUICKLIME_ITEM", 1,
                new String[] {"CALCIUM_CHLORIDE", "HCL", "FRESH_WATER"}, new long[] {solution, excess, lime + acid - solution - excess}, 800, 40000, 0, 20, false).rebatch(2).withDuration(300);
    }
    public static ProcessRecipe strontiumChloride() {
        long oxide = mass("STRONTIUM_OXIDE_ITEM", 1), used = ratio(oxide, "72.92188", "103.6194");
        long salt = ratio(oxide, "158.526", "103.6194"), acid = mass("HCL", 3), excess = acid - ratio(used, "1", ".30");
        return bill("strontium_chloride", new String[] {"HCL"}, new int[] {3}, "STRONTIUM_OXIDE_ITEM", 1,
                new String[] {"STRONTIUM_CHLORIDE_ITEM", "HCL", "FRESH_WATER"}, new long[] {salt, excess, oxide + acid - salt - excess}, 600, 30000, 0, 20, false);
    }
    public static ProcessRecipe bariumCarbonate() {
        long sulfide = mass("BARIUM_SULFIDE_ITEM", 1), usedCO2 = ratio(sulfide, "44.0095", "169.392");
        long salt = ratio(sulfide, "197.3359", "169.392"), gas = ratio(sulfide, "34.08088", "169.392");
        long excess = mass("CARBONDIOXIDE", 150) - usedCO2;
        return bill("barium_carbonate", new String[] {"CARBONDIOXIDE", "FRESH_WATER"}, new int[] {150, 1}, "BARIUM_SULFIDE_ITEM", 1,
                new String[] {"BARIUM_CARBONATE_ITEM", "HYDROGEN_SULFIDE", "CARBONDIOXIDE", "FRESH_WATER"},
                new long[] {salt, gas, excess, sulfide + mass("CARBONDIOXIDE", 150) + mass("FRESH_WATER", 1) - salt - gas - excess}, 600, 30000, 0, 20, false);
    }
    public static ProcessRecipe bariumReduction() {
        // Vacuum aluminothermy: 4 BaO + 2 Al -> 3 Ba + BaAl2O4. Hydrogen cannot reduce BaO.
        long oxide = mass("BARIUM_OXIDE_ITEM", 1), metal = ratio(oxide, "411.981", "613.3056");
        long al = ratio(oxide, "53.963077", "613.3056"), slag = oxide + al - metal;
        ProcessRecipe result = new ProcessRecipe("chem:barium_reduction", new String[] {}, new int[] {}, new int[] {},
                new String[] {"BARIUM_OXIDE_ITEM", "ALUMINIUM_ITEM"}, new int[] {1, 1},
                new String[] {"BARIUM_ITEM", "BARIUM_ALUMINATE_ITEM", "ALUMINIUM_ITEM"}, new long[] {metal, slag, mass("ALUMINIUM_ITEM", 1) - al},
                2000, 200000, 5000000, 1200, false);
        ChemicalFuelProcesses.validateRating(result); return result;
    }
    public static ProcessRecipe sodiumCarbonate() {
        long lye = mass("SODIUM_HYDROXIDE", 1), carbon = ratio(lye, "44.0095", "79.994218");
        int co2 = Math.toIntExact(PetroleumProcesses.ceilDiv(carbon, mass("CARBONDIOXIDE", 1)));
        long salt = ratio(lye, "105.98844", "79.994218"), solution = ratio(salt, "1", ".20");
        long total = lye + mass("CARBONDIOXIDE", co2) + mass("FRESH_WATER", 10), excess = mass("CARBONDIOXIDE", co2) - carbon;
        return bill("sodium_carbonate", new String[] {"SODIUM_HYDROXIDE", "CARBONDIOXIDE", "FRESH_WATER"}, new int[] {1, co2, 10}, null, 0,
                new String[] {"SODIUM_CARBONATE", "CARBONDIOXIDE", "FRESH_WATER"}, new long[] {solution, excess, total - solution - excess}, 1200, 60000, 0, 20, false).withDuration(400);
    }
    public static ProcessRecipe melting(String solid) {
        boolean co2 = solid.equals("DRY_ICE_BLOCK"), metal = solid.equals("SODIUM_ITEM") || solid.equals("POTASSIUM_ITEM");
        String output = co2 ? "CARBONDIOXIDE" : solid.equals("SODIUM_ITEM") ? "SODIUM" : solid.equals("POTASSIUM_ITEM") ? "POTASSIUM" : "SALT_WATER";
        long total = mass(solid, 1), joules = PetroleumProcesses.joules(total, co2 ? 750000 : metal ? 750000 : 425000);
        int ticks = Math.toIntExact(PetroleumProcesses.ceilDiv(joules * 20, 5000));
        return new ProcessRecipe("phase:melting_" + solid, new String[] {}, new int[] {}, new int[] {}, solid, 1,
                new String[] {output}, new long[] {total}, ticks, joules, 0, co2 ? 20 : metal ? 400 : 20, true);
    }
    public static ProcessRecipe freezing(boolean co2) {
        String input = co2 ? "CARBONDIOXIDE" : "SALT_WATER";
        int count = co2 ? 1000 : 1; long total = mass(input, count), work = PetroleumProcesses.joules(total, co2 ? 500000 : 170000);
        return new ProcessRecipe("phase:freezing_" + input, new String[] {input}, new int[] {count}, new int[] {0}, null, 0,
                new String[] {co2 ? "DRY_ICE_BLOCK" : "ICE_BLOCK"}, new long[] {total},
                Math.toIntExact(PetroleumProcesses.ceilDiv(work * 20, 2000)), work, 0, co2 ? -79 : 0, true);
    }
    public static ProcessRecipe metalCooling(boolean potassium) {
        String feed = potassium ? "POTASSIUM" : "SODIUM", product = potassium ? "POTASSIUM_ITEM" : "SODIUM_ITEM";
        return new ProcessRecipe("phase:metal_cooling_" + feed, new String[] {feed}, new int[] {100}, new int[] {0}, null, 0,
                new String[] {product}, new long[] {mass(feed, 100)}, 2000, 20000, 0, 20, true);
    }
    public static ProcessRecipe galliumArsenide() {
        long arsenic = mass("ARSENIC_NUGGET_ITEM", 1), gallium = ratio(arsenic, "69.723", "74.9216");
        ProcessRecipe result = new ProcessRecipe("chem:gallium_arsenide", new String[] {"ARGON"}, new int[] {20}, new int[] {0},
                new String[] {"GALLIUM_NUGGET_ITEM", "ARSENIC_NUGGET_ITEM"}, new int[] {1, 1},
                new String[] {"GAAS_NUGGET_ITEM", "GALLIUM_NUGGET_ITEM", "VENT_ARGON"},
                new long[] {arsenic + gallium, mass("GALLIUM_NUGGET_ITEM", 1) - gallium, mass("ARGON", 20)}, 2000, 100000, 1000000, 1250, false);
        ChemicalFuelProcesses.validateRating(result); return result;
    }
    public static ProcessRecipe potassiumIodide() {
        // Hot alkaline disproportionation: 3 I2 + 6 KOH -> 5 KI + KIO3 + 3 H2O.
        long iodine = mass("IODINE_ITEM", 1), lye = ratio(iodine, "336.63384", "761.42682");
        long iodide = ratio(iodine, "830.01385", "761.42682"), iodate = ratio(iodine, "214.00097", "761.42682");
        ProcessRecipe result = new ProcessRecipe("chem:potassium_iodide", new String[] {"FRESH_WATER"}, new int[] {4}, new int[] {0},
                new String[] {"IODINE_ITEM", "POTASH_LYE_ITEM"}, new int[] {1, 1},
                new String[] {"POTASSIUM_IODIDE_ITEM", "POTASSIUM_IODATE_ITEM", "POTASH_LYE_ITEM", "FRESH_WATER"},
                new long[] {iodide, iodate, mass("POTASH_LYE_ITEM", 1) - lye, mass("FRESH_WATER", 4) + iodine + lye - iodide - iodate},
                2000, 30000, 3000000, 120, false);
        ChemicalFuelProcesses.validateRating(result); return result;
    }
    public static ProcessRecipe carnallite() {
        long ore = mass("CARNALLITE_ITEM", 1), kcl = ratio(ore, "74.5513", "277.85398"), magnesium = ratio(ore, "95.211", "277.85398");
        long solution = ratio(kcl, "1", ".20"), water = mass("FRESH_WATER", 2);
        return bill("carnallite", new String[] {"FRESH_WATER"}, new int[] {2}, "CARNALLITE_ITEM", 1,
                new String[] {"POTASSIUM_CHLORIDE", "MAGNESIUM_CHLORIDE_ITEM", "FRESH_WATER"},
                new long[] {solution, magnesium, ore + water - solution - magnesium}, 1000, 50000, 2000000, 120, true).rebatch(2);
    }
    public static ProcessRecipe strontiumMelting() {
        return bill("strontium_chloride_melting", new String[] {}, new int[] {}, "STRONTIUM_CHLORIDE_ITEM", 1,
                new String[] {"MOLTEN_STRONTIUM_CHLORIDE"}, new long[] {mass("STRONTIUM_CHLORIDE_ITEM", 1)}, 1000, 50000, 1200000, 900, true);
    }
    public static ProcessRecipe strontiumElectrolysis() {
        long salt = mass("MOLTEN_STRONTIUM_CHLORIDE", 144), metal = ratio(salt, "87.62", "158.526");
        return new ProcessRecipe("electrolysis:strontium_chloride", new String[] {"MOLTEN_STRONTIUM_CHLORIDE"}, new int[] {144}, new int[] {0}, null, 0,
                new String[] {"CHLORINE", "STRONTIUM_ITEM"}, new long[] {salt - metal, metal}, 10000, 10000000, 0, 900, false);
    }
    public static ProcessRecipe sulfurDoses() {
        return bill("sulfur_metering", new String[] {}, new int[] {}, "SULFUR_ITEM", 1,
                new String[] {"SULFUR_DOSE_ITEM"}, new long[] {mass("SULFUR_ITEM", 1)}, 100, 5000, 0, 20, true);
    }
    public static ProcessRecipe butadiene() {
        long feed = mass("UNSATURATEDS", 1000), gas = mass("HYDROGEN_GAS", 500);
        return bill("butadiene", new String[] {"UNSATURATEDS"}, new int[] {1000}, null, 0,
                new String[] {"BUTADIENE", "HYDROGEN_GAS"}, new long[] {feed - gas, gas}, 2000, 200000, 4000000, 550, false).rebatch(2).withDuration(600);
    }
    public static ProcessRecipe rubber() {
        return bill("rubber", new String[] {"BUTADIENE"}, new int[] {200}, "SULFUR_DOSE_ITEM", 1,
                new String[] {"RUBBER_ITEM"}, new long[] {mass("BUTADIENE", 200) + mass("SULFUR_DOSE_ITEM", 1)}, 1000, 200000, 300000, 150, false).rebatch(10).withDuration(400);
    }
    public static ProcessRecipe pvc() {
        // Ethylene oxychlorination, EDC cracking and polymerization; recycled HCl stays inside the plant.
        long polymer = ratio(mass("UNSATURATEDS", 400), "62.49822", "28.05316");
        long total = mass("UNSATURATEDS", 400) + mass("CHLORINE", 200) + mass("OXYGEN_GAS", 100);
        return bill("pvc", new String[] {"UNSATURATEDS", "CHLORINE", "OXYGEN_GAS"}, new int[] {400, 200, 100}, null, 0,
                new String[] {"PVC_ITEM", "FRESH_WATER"}, new long[] {polymer, total - polymer}, 2000, 200000, 600000, 500, false).rebatch(8).withDuration(600);
    }
    public static ProcessRecipe phenol() {
        // Cumene surrogate + O2 -> phenol + acetone. No existing aromatic stock coefficient changes.
        long feed = mass("AROMATICS", 1), oxygen = ratio(feed, "31.9988", "120.19158");
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1)));
        long phenol = ratio(feed, "94.11124", "120.19158");
        return bill("phenol", new String[] {"AROMATICS", "OXYGEN_GAS"}, new int[] {1, q}, null, 0,
                new String[] {"PHENOL", "ACETONE", "OXYGEN_GAS"}, new long[] {phenol, feed + oxygen - phenol, mass("OXYGEN_GAS", q) - oxygen}, 2000, 100000, 200000, 120, false).rebatch(4).withDuration(600);
    }
    public static ProcessRecipe formaldehyde() {
        long methanol = mass("METHANOL", 1), oxygen = ratio(methanol, "15.9994", "32.04186");
        int q = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1)));
        long product = ratio(methanol, "30.02598", "32.04186");
        return bill("formaldehyde", new String[] {"METHANOL", "OXYGEN_GAS"}, new int[] {1, q}, null, 0,
                new String[] {"FORMALDEHYDE", "FRESH_WATER", "OXYGEN_GAS"}, new long[] {product, methanol + oxygen - product, mass("OXYGEN_GAS", q) - oxygen}, 2000, 200000, 600000, 250, false).rebatch(4).withDuration(400);
    }
    public static ProcessRecipe bakelite() {
        long phenol = mass("PHENOL", 1), used = ratio(phenol, "30.02598", "94.11124"), polymer = ratio(phenol, "106.12194", "94.11124");
        return bill("bakelite", new String[] {"PHENOL", "FORMALDEHYDE"}, new int[] {1, 300}, null, 0,
                new String[] {"BAKELITE_ITEM", "FRESH_WATER", "FORMALDEHYDE"},
                new long[] {polymer, phenol + used - polymer, mass("FORMALDEHYDE", 300) - used}, 2000, 200000, 400000, 150, false).rebatch(8).withDuration(800);
    }
    public static ProcessRecipe bisphenol() {
        long phenol = mass("PHENOL", 1), used = ratio(phenol, "58.07914", "188.22248"), product = ratio(phenol, "228.28634", "188.22248");
        return bill("bisphenol_a", new String[] {"PHENOL", "ACETONE"}, new int[] {1, 1}, null, 0,
                new String[] {"BISPHENOL_A_ITEM", "FRESH_WATER", "ACETONE"},
                new long[] {product, phenol + used - product, mass("ACETONE", 1) - used}, 2000, 200000, 600000, 150, false);
    }
    public static ProcessRecipe polycarbonate() {
        long feed = mass("BISPHENOL_A_ITEM", 1), gas = ratio(feed, "98.9161", "228.28634"), product = ratio(feed, "254.28056", "228.28634");
        long acid = feed + gas - product, solution = ratio(acid, "1", ".30");
        return bill("polycarbonate", new String[] {"PHOSGENE", "FRESH_WATER"}, new int[] {120, 1}, "BISPHENOL_A_ITEM", 1,
                new String[] {"PC_ITEM", "HCL", "PHOSGENE", "FRESH_WATER"},
                new long[] {product, solution, mass("PHOSGENE", 120) - gas, mass("FRESH_WATER", 1) + acid - solution}, 2000, 200000, 800000, 180, false);
    }
    public static ProcessRecipe benzene() {
        long aromatic = mass("AROMATICS", 1), usedH2 = ratio(aromatic, "2.01588", "120.19158");
        long product = ratio(aromatic, "78.11184", "120.19158");
        return bill("benzene", new String[] {"AROMATICS", "HYDROGEN_GAS"}, new int[] {1, 200}, null, 0,
                new String[] {"BENZENE", "LPG", "HYDROGEN_GAS"}, new long[] {product, aromatic + usedH2 - product, mass("HYDROGEN_GAS", 200) - usedH2}, 2000, 200000, 2500000, 650, false).rebatch(4);
    }
    public static ProcessRecipe diamine() {
        // Nitration + hydrogenation: benzene + 2 HNO3 + 6 H2 -> PPD + 6 H2O.
        long benzene = mass("BENZENE", 1), pureAcid = ratio(benzene, "126.02568", "78.11184"), usedAcid = ratio(pureAcid, "1", ".65");
        long usedH2 = ratio(benzene, "12.09528", "78.11184"), product = ratio(benzene, "108.14112", "78.11184");
        return bill("phenylenediamine", new String[] {"BENZENE", "NITRIC_ACID", "HYDROGEN_GAS"}, new int[] {1, 2, 1600}, null, 0,
                new String[] {"PPD_ITEM", "NITRIC_ACID", "HYDROGEN_GAS", "FRESH_WATER"},
                new long[] {product, mass("NITRIC_ACID", 2) - usedAcid, mass("HYDROGEN_GAS", 1600) - usedH2, benzene + usedAcid + usedH2 - product},
                2000, 200000, 600000, 200, false);
    }
    public static ProcessRecipe acidChloride() {
        // Xylene oxidation, then phosgene chlorination of the internal terephthalic acid.
        long xylene = mass("XYLENE", 1), oxygen = ratio(xylene, "95.9964", "106.165");
        long phosgene = ratio(xylene, "197.8322", "106.165"), product = ratio(xylene, "203.02216", "106.165");
        int qo = Math.toIntExact(PetroleumProcesses.ceilDiv(oxygen, mass("OXYGEN_GAS", 1))), qp = Math.toIntExact(PetroleumProcesses.ceilDiv(phosgene, mass("PHOSGENE", 1)));
        long acid = ratio(xylene, "72.92188", "106.165"), co2 = ratio(xylene, "88.019", "106.165"), water = xylene + oxygen + phosgene - product - acid - co2;
        // Rounded gas excess is explicitly vented; HCl absorption retains its water.
        long solution = ratio(acid, "1", ".30"), feedWater = mass("FRESH_WATER", 2);
        return bill("terephthaloyl_chloride", new String[] {"XYLENE", "OXYGEN_GAS", "PHOSGENE", "FRESH_WATER"}, new int[] {1, qo, qp, 2}, null, 0,
                new String[] {"TPC_ITEM", "HCL", "CARBONDIOXIDE", "FRESH_WATER", "VENT_OXYGEN_GAS", "VENT_PHOSGENE"},
                new long[] {product, solution, co2, feedWater + water + acid - solution, mass("OXYGEN_GAS", qo) - oxygen, mass("PHOSGENE", qp) - phosgene},
                2000, 200000, 1000000, 200, false);
    }
    public static ProcessRecipe aramid() {
        // ARAMID_ITEM retains plate_kevlar: this route abstracts synthesis plus fiber/sheet
        // preparation, not polymerization alone as weaving. Keep the existing mass bill.
        long tpc = mass("TPC_ITEM", 1), diamine = ratio(tpc, "108.14112", "203.02216"), polymer = ratio(tpc, "238.2414", "203.02216");
        long acid = tpc + diamine - polymer, solution = ratio(acid, "1", ".30");
        ProcessRecipe result = new ProcessRecipe("chem:aramid", new String[] {"FRESH_WATER"}, new int[] {1}, new int[] {0},
                new String[] {"TPC_ITEM", "PPD_ITEM"}, new int[] {1, 1},
                new String[] {"ARAMID_ITEM", "PPD_ITEM", "HCL", "FRESH_WATER"}, new long[] {polymer, mass("PPD_ITEM", 1) - diamine, solution, mass("FRESH_WATER", 1) + acid - solution},
                2000, 200000, 600000, 80, false);
        ChemicalFuelProcesses.validateRating(result); return result;
    }
    public static ProcessRecipe xylene() {
        long aromatic = mass("AROMATICS", 1), usedH2 = ratio(aromatic, "2.01588", "120.19158"), product = ratio(aromatic, "106.165", "120.19158");
        return bill("xylene", new String[] {"AROMATICS", "HYDROGEN_GAS"}, new int[] {1, 200}, null, 0,
                new String[] {"XYLENE", "GAS", "HYDROGEN_GAS"}, new long[] {product, aromatic + usedH2 - product, mass("HYDROGEN_GAS", 200) - usedH2}, 2000, 100000, 2500000, 650, false).rebatch(4);
    }
    public static ProcessRecipe syngasSeparation() {
        return bill("syngas_separation", new String[] {"SYNGAS"}, new int[] {1000}, null, 0,
                new String[] {"HYDROGEN_GAS", "CO"}, new long[] {mass("HYDROGEN_GAS", 500), mass("CO", 500)}, 1000, 100000, 0, 20, true).rebatch(5);
    }
    public static ProcessRecipe coltanCleaning() {
        // Adopted 50 wt% concentrate/50 wt% gangue ore assay. Acid wash is recycled, not annihilated.
        return bill("coltan_cleaning", new String[] {"SULFURIC_ACID", "FRESH_WATER"}, new int[] {1, 4}, "COLTAN_ORE_ITEM", 2,
                new String[] {"COLTAN_CONCENTRATE_ITEM", "GANGUE_ITEM", "SULFURIC_ACID", "SALT_WATER"},
                new long[] {mass("COLTAN_CONCENTRATE_ITEM", 1), mass("GANGUE_ITEM", 1), mass("SULFURIC_ACID", 1), mass("FRESH_WATER", 4)}, 1200, 60000, 1000000, 80, true).withDuration(400);
    }
    public static ProcessRecipe coltanSeparation() {
        // Fluoride-complex separation, internal precipitation and reagent recovery; metal reduction is separate.
        return bill("coltan_separation", new String[] {"HYDROFLUORIC_ACID", "FRESH_WATER"}, new int[] {1, 2}, "COLTAN_CONCENTRATE_ITEM", 1,
                new String[] {"TANTALUM_OXIDE_ITEM", "NIOBIUM_OXIDE_ITEM", "HYDROFLUORIC_ACID", "FRESH_WATER"},
                new long[] {mass("COLTAN_CONCENTRATE_ITEM", 1) / 2, mass("COLTAN_CONCENTRATE_ITEM", 1) / 2, mass("HYDROFLUORIC_ACID", 1), mass("FRESH_WATER", 2)}, 2000, 200000, 2000000, 150, true).withDuration(600);
    }
    public static ProcessRecipe oxideReduction(String oxide, String metal, String oxideMolar, String metalMolar, String calciumMolar) {
        long feed = mass(oxide, 1), product = ratio(feed, metalMolar, oxideMolar), calcium = ratio(feed, calciumMolar, oxideMolar);
        ProcessRecipe result = new ProcessRecipe("chem:calciothermy_" + oxide, new String[] {}, new int[] {}, new int[] {},
                new String[] {oxide, "CALCIUM_ITEM"}, new int[] {1, 2}, new String[] {metal, "QUICKLIME_ITEM", "CALCIUM_ITEM"},
                new long[] {product, feed + calcium - product, mass("CALCIUM_ITEM", 2) - calcium}, 4000, 200000, 5000000, 1200, false);
        ChemicalFuelProcesses.validateRating(result); return result.withDuration(800);
    }
    public static ProcessRecipe bayer() {
        // 10-kg bauxite parcel: 60 wt% alumina and 500 ppm Ga; traces remain in residue.
        long ore = mass("BAUXITE_ORE_ITEM", 1), alumina = ratio(ore, ".60", "1"), gallium = ratio(ore, ".0005", "1");
        long recoveredLye = ratio(mass("LYE_ITEM", 1), ".9", "1"), returnWater = mass("FRESH_WATER", 8);
        ProcessRecipe result = new ProcessRecipe("chem:bayer", new String[] {"FRESH_WATER"}, new int[] {10}, new int[] {0},
                new String[] {"BAUXITE_ORE_ITEM", "LYE_ITEM"}, new int[] {1, 1},
                new String[] {"ALUMINA_ITEM", "GALLIUM_TINY_ITEM", "LYE_ITEM", "FRESH_WATER", "BAUXITE_RESIDUE"},
                new long[] {alumina, gallium, recoveredLye, returnWater, ore + mass("FRESH_WATER", 10) + mass("LYE_ITEM", 1) - alumina - gallium - recoveredLye - returnWater},
                5000, 1000000, 10000000, 200, true);
        ChemicalFuelProcesses.validateRating(result); return result.withDuration(1200);
    }
    public static ProcessRecipe calciumDrying() {
        long feed = mass("CALCIUM_CHLORIDE", 1), salt = ratio(feed, ".40", "1");
        return bill("calcium_chloride_drying", new String[] {"CALCIUM_CHLORIDE"}, new int[] {1}, null, 0,
                new String[] {"MOLTEN_CALCIUM_CHLORIDE", "STEAM"}, new long[] {salt, feed - salt}, 1000, 50000, 3000000, 900, true).rebatch(2, 1200).withDuration(600);
    }
    public static ProcessRecipe calciumElectrolysis() {
        long feed = mass("MOLTEN_CALCIUM_CHLORIDE", 144), calcium = ratio(feed, "40.078", "110.984");
        return new ProcessRecipe("electrolysis:calcium_chloride", new String[] {"MOLTEN_CALCIUM_CHLORIDE"}, new int[] {144}, new int[] {0}, null, 0,
                new String[] {"CHLORINE", "CALCIUM_ITEM"}, new long[] {feed - calcium, calcium}, 10000, 10000000, 0, 900, false);
    }
    public static ProcessRecipe paper() {
        long fiber = mass("SAWDUST_ITEM", 1), water = mass("FRESH_WATER", 1);
        return new ProcessRecipe("custom:paper_press", new String[] {"FRESH_WATER"}, new int[] {1}, new int[] {0}, "SAWDUST_ITEM", 1,
                new String[] {"PAPER_ITEM", "VENT_STEAM"}, new long[] {mass("PAPER_ITEM", 3), water + fiber - mass("PAPER_ITEM", 3)}, 4000, 2610000, 0, 120, true);
    }
    public static ProcessRecipe slopTreatment() {
        // Bedrock minerals already go to the slopper's ore account; its carrier cannot yield bulk mercury.
        return new ProcessRecipe("electrolysis:slop_clarification", new String[] {"SLOP"}, new int[] {5}, new int[] {0}, null, 0,
                new String[] {"SALT_WATER"}, new long[] {mass("SLOP", 5)}, 1000, 250000, 0, 20, true);
    }
    public static ProcessRecipe laserGas(String product, String a, int qa, String b, int qb) {
        // Declared laser fill mixture, not synthesis of a bottle of long-lived excimer molecules.
        int ticks = (qa + qb) * 2; long feed = mass(a, qa) + mass(b, qb);
        return bill("laser_fill_" + product.toLowerCase(java.util.Locale.ROOT), new String[] {a, b}, new int[] {qa, qb}, null, 0,
                new String[] {product}, new long[] {feed}, ticks, (qa + qb) * 100L, 0, 20, true);
    }
    public static ProcessRecipe rawGasRecovery() {
        return bill("raw_natural_gas_separation", new String[] {"RAW_NATURAL_GAS"}, new int[] {100}, null, 0,
                new String[] {"GAS", "HELIUM4"}, new long[] {mass("GAS", 98), mass("HELIUM4", 2)}, 200, 50000, 0, 20, true).rebatch(10);
    }
    public static ProcessRecipe oxyhydrogen() {
        long total = mass("HYDROGEN_GAS", 100) + mass("OXYGEN_GAS", 50);
        return bill("oxyhydrogen_blend", new String[] {"HYDROGEN_GAS", "OXYGEN_GAS"}, new int[] {100, 50}, null, 0,
                new String[] {"OXYHYDROGEN"}, new long[] {total}, 300, 15000, 0, 20, true);
    }
    public static boolean requiresBillForOutput(String output) {
        for(String name:new String[]{"SALIENT","EGG","SCHRABIDIC","SMILK","ELBOWGREASE","MINSOL","REDMUD","SCUTTERBLOOD","HTCO4","DICYANOACETYLENE","TOLUENE","GLYCEROL","NITROGLYCERIN","MUSTARDGAS","ROCKET_FUEL","CRYOGEL","NITAN","FULLERENE","THORIUM_SALT","THORIUM_SALT_DEPLETED","RADIOSOLVENT","WASTEFLUID","WASTEGAS","URANIUM_BROMIDE","PLUTONIUM_BROMIDE","THORIUM_BROMIDE"}) if(name.equals(output)) return true;
        for(String name : new String[] {"BRINE", "MINERAL_BRINE", "BITTERN", "IODINE_BRINE", "HEAVYWATER", "DEPLETED_WATER", "BORATED_WATER", "AMMONIA", "PEROXIDE", "SULFURIC_ACID", "NITRIC_ACID", "HCL", "HYDROFLUORIC_ACID", "HYDROGEN_GAS", "OXYGEN_GAS", "NITROGEN_GAS", "HELIUM4", "HYDROGEN", "OXYGEN", "NITROGEN", "CHLORINE", "FLUORINE", "POLYTHYLENE", "BUTADIENE", "PHENOL", "ACETONE", "BENZENE", "FORMALDEHYDE", "PHOSGENE", "OXYHYDROGEN", "IONGEL", "COFFEE", "TEA", "COLLOID", "CHLOROCALCITE_SOLUTION", "CHLOROCALCITE_MIX", "CHLOROCALCITE_CLEANED", "SODIUM_HYDROXIDE", "POTASH_LYE", "SODIUM_CARBONATE", "POTASSIUM_CHLORIDE", "CALCIUM_CHLORIDE", "MOLTEN_SALT", "MOLTEN_CALCIUM_CHLORIDE", "MOLTEN_LITHIUM_CHLORIDE", "MOLTEN_STRONTIUM_CHLORIDE", "NITROGEN_HELIUM", "CARBONDIOXIDE_NITROGEN_HELIUM", "KRYPTON_FLUORINE", "XENON_CHLORINE", "EXCIMER_GASMIX", "POLLUCITE_SOLUTION", "POLLUCITE_SOLUTION_HEAVY", "POLLUCITE_SOLUTION_LIGHT", "OSMIRIDIUM_SOLUTION", "RHODIUM_SOLUTION", "IRIDIUM_SOLUTION", "UF6", "PUF6", "RAFFINATE"}) if(name.equals(output)) return true;
        return ChemicalFuelProcesses.requiresBill(output);
    }
    public static ProcessRecipe ionGel() {
        // Fictional identity, explicit retained water/matrix mass; no chemical fuel credit.
        return bill("ion_gel", new String[] {"FRESH_WATER", "HYDROGEN_GAS"}, new int[] {1, 200}, "CHARGED_MATRIX_ITEM", 1,
                new String[] {"IONGEL"}, new long[] {mass("FRESH_WATER", 1) + mass("HYDROGEN_GAS", 200) + mass("CHARGED_MATRIX_ITEM", 1)}, 1000, 50000, 0, 20, false);
    }
    public static ProcessRecipe coffee() {
        return bill("coffee", new String[] {"FRESH_WATER"}, new int[] {2}, "COFFEE_POWDER_ITEM", 4,
                new String[] {"COFFEE"}, new long[] {mass("FRESH_WATER", 2) + mass("COFFEE_POWDER_ITEM", 4)}, 2000, 700000, 0, 80, true);
    }
    public static ProcessRecipe tea() {
        long leaves = mass("TEA_LEAF_ITEM", 2), extract = leaves / 10;
        return bill("tea", new String[] {"FRESH_WATER"}, new int[] {2}, "TEA_LEAF_ITEM", 2,
                new String[] {"TEA", "VENT_BOTANICAL"}, new long[] {mass("FRESH_WATER", 2) + extract, leaves - extract}, 2000, 700000, 0, 80, true);
    }
    public static ProcessRecipe colloid() {
        return bill("mineral_colloid", new String[] {"FRESH_WATER"}, new int[] {1}, "GANGUE_ITEM", 1,
                new String[] {"COLLOID"}, new long[] {mass("FRESH_WATER", 1) + mass("GANGUE_ITEM", 1)}, 400, 30000, 0, 20, true).rebatch(2);
    }
    public static ProcessRecipe chlorocalciteSolution() {
        return bill("chlorocalcite_dissolution", new String[] {"FRESH_WATER"}, new int[] {4}, "CHLOROCALCITE_ITEM", 1,
                new String[] {"CHLOROCALCITE_SOLUTION"}, new long[] {mass("FRESH_WATER", 4) + mass("CHLOROCALCITE_ITEM", 1)}, 1200, 60000, 0, 20, true).rebatch(2).withDuration(400);
    }
    public static ProcessRecipe chlorocalciteConditioning() {
        return bill("chlorocalcite_conditioning", new String[] {"CHLOROCALCITE_SOLUTION", "FRESH_WATER"}, new int[] {2, 1}, "FLUX_ITEM", 1,
                new String[] {"CHLOROCALCITE_MIX"}, new long[] {mass("CHLOROCALCITE_SOLUTION", 2) + mass("FRESH_WATER", 1) + mass("FLUX_ITEM", 1)}, 1200, 60000, 0, 20, true).rebatch(2).withDuration(400);
    }
    public static ProcessRecipe chlorocalciteClarification() {
        long feed = mass("CHLOROCALCITE_MIX", 1), liquor = ratio(feed, "23", "43");
        return new ProcessRecipe("separation:chlorocalcite_clarification", new String[] {"CHLOROCALCITE_MIX"}, new int[] {1}, new int[] {0}, null, 0,
                new String[] {"CHLOROCALCITE_CLEANED", "COLLOID"}, new long[] {liquor, feed - liquor}, 100, 5000, 300000, 80, true);
    }
    public static ProcessRecipe chlorocalciteSeparation() {
        long feed = mass("CHLOROCALCITE_CLEANED", 1), mineral = ratio(feed, ".2", "1");
        long potassium = ratio(mineral, "74.5513", "185.5353"), calcium = mineral - potassium;
        long kcl = ratio(potassium, "1", ".20"), cacl = ratio(calcium, "1", ".40");
        return bill("chlorocalcite_separation", new String[] {"CHLOROCALCITE_CLEANED"}, new int[] {1}, null, 0,
                new String[] {"POTASSIUM_CHLORIDE", "CALCIUM_CHLORIDE", "FRESH_WATER"}, new long[] {kcl, cacl, feed - kcl - cacl}, 1000, 50000, 300000, 120, true).rebatch(4).withDuration(400);
    }
    public static ProcessRecipe chlorocalciteCrystallization() {
        long calcium = ratio(mass("CALCIUM_CHLORIDE", 1), ".40", "1"), usedK = ratio(calcium, "74.5513", "110.984");
        long crystal = calcium + usedK, remainingK = mass("POTASSIUM_CHLORIDE", 4) - ratio(usedK, "1", ".20");
        return bill("chlorocalcite_crystallization", new String[] {"CALCIUM_CHLORIDE", "POTASSIUM_CHLORIDE"}, new int[] {1, 4}, null, 0,
                new String[] {"CHLOROCALCITE_ITEM", "POTASSIUM_CHLORIDE", "FRESH_WATER"},
                new long[] {crystal, remainingK, mass("CALCIUM_CHLORIDE", 1) + mass("POTASSIUM_CHLORIDE", 4) - crystal - remainingK}, 2000, 100000, 5000000, 400, true).rebatch(2).withDuration(600);
    }
    public static boolean electrolyserRequiresBill(String input) {
        for(String name : new String[] {"FRESH_WATER", "HEAVYWATER", "BRINE", "HYDROFLUORIC_ACID", "MOLTEN_SALT", "POTASSIUM_CHLORIDE", "MOLTEN_STRONTIUM_CHLORIDE", "MOLTEN_CALCIUM_CHLORIDE", "MOLTEN_LITHIUM_CHLORIDE", "RHODIUM_SOLUTION", "IRIDIUM_SOLUTION", "SLOP"}) if(input.equals(name)) return true;
        return false;
    }
    public static boolean reactorRequiresBill(int id) {
        return defaults().containsKey(id) || id == 36 || id == 58 || id == 72 || id == 1008 || id == 1159 || id == 1160 || id == 1161 || id == 1162;
    }
    private static final class DefaultBills { private static final Map<Integer,ProcessRecipe> BILLS=createDefaults(); }
    public static Map<Integer, ProcessRecipe> defaults() { return new LinkedHashMap<Integer,ProcessRecipe>(DefaultBills.BILLS); }
    private static Map<Integer, ProcessRecipe> createDefaults() {
        Map<Integer, ProcessRecipe> result = new LinkedHashMap<Integer, ProcessRecipe>();
        result.putAll(SpecialistProcesses.defaults());
        result.put(1201, MineralChemicalProcesses.chromiteReduction());
        result.put(1086, SpecialistProcesses.vanadiumConsolidation());
        result.putAll(AncillaryProcesses.defaults());
        result.putAll(OrganicProcesses.defaults());
        result.putAll(UtilityMixtures.defaults());
        result.put(35, ConstructionProcesses.asphalt()); result.put(55, ConstructionProcesses.concrete(0)); result.put(56, ConstructionProcesses.concrete(1)); result.put(79, ConstructionProcesses.concrete(2)); result.put(1001, ConstructionProcesses.soil());
        result.put(1051, fertilizer(false)); result.put(1052, fertilizer(true)); result.put(1069, potassiumNitrate(true)); result.put(1071, potassiumNitrate(false));
        result.put(46, NuclearChemicalProcesses.yellowcake()); result.put(47, NuclearChemicalProcesses.uraniumFluorination()); result.put(48, NuclearChemicalProcesses.plutoniumFluorination());
        result.put(1038, NuclearChemicalProcesses.rareEarthRecovery()); result.put(1039, NuclearChemicalProcesses.rareEarthExtraction(true)); result.put(1040, NuclearChemicalProcesses.rareEarthExtraction(false));
        result.put(1015, MineralChemicalProcesses.heavyAlkaliReduction(false)); result.put(1021, MineralChemicalProcesses.heavyAlkaliReduction(true));
        result.put(1017, MineralChemicalProcesses.polluciteLeaching()); result.put(1018, MineralChemicalProcesses.polluciteSeparation()); result.put(1019, MineralChemicalProcesses.polluciteHeavyRecovery()); result.put(1020, MineralChemicalProcesses.polluciteLightRecovery());
        result.put(1049, MineralChemicalProcesses.lepidolite()); result.put(1179, MineralChemicalProcesses.lithiumCarbonate()); result.put(1180, MineralChemicalProcesses.lithiumChloride());
        result.put(1054, MineralChemicalProcesses.osmiumOxidation()); result.put(1055, MineralChemicalProcesses.osmiumReduction());
        result.put(1056, MineralChemicalProcesses.rhodiumExtraction()); result.put(1057, MineralChemicalProcesses.iridiumOxidation()); result.put(1061, MineralChemicalProcesses.iridiumChlorination());
        result.put(1090, laserGas("NITROGEN_HELIUM", "NITROGEN_GAS", 100, "HELIUM4", 800));
        result.put(1091, laserGas("CARBONDIOXIDE_NITROGEN_HELIUM", "CARBONDIOXIDE", 100, "NITROGEN_HELIUM", 900));
        result.put(1099, laserGas("KRYPTON_FLUORINE", "KRYPTON", 998, "FLUORINE", 2));
        result.put(1100, laserGas("XENON_CHLORINE", "XENON", 998, "CHLORINE", 2));
        result.put(1101, laserGas("EXCIMER_GASMIX", "KRYPTON_FLUORINE", 100, "XENON_CHLORINE", 100));
        result.put(1181, rawGasRecovery()); result.put(1177, boricAcid()); result.put(1178, mineralBrineSeparation());
        result.put(1066, halogenRecovery(false, false)); result.put(1067, halogenRecovery(true, false)); result.put(1068, halogenRecovery(true, true));
        result.put(1041, NuclearChemicalProcesses.wasteNeutralization(false)); result.put(1042, NuclearChemicalProcesses.wasteNeutralization(true));
        result.put(1043, NuclearChemicalProcesses.oxideChlorination(true)); result.put(1047, NuclearChemicalProcesses.oxideChlorination(false));
        result.put(1044, NuclearChemicalProcesses.chlorideReduction(true)); result.put(1048, NuclearChemicalProcesses.chlorideReduction(false));
        result.put(1045, NuclearChemicalProcesses.titaniumTraceRecovery(true)); result.put(1046, NuclearChemicalProcesses.titaniumTraceRecovery(false));
        result.put(118, chlorocalciteSeparation()); result.put(1064, chlorocalciteCrystallization());
        result.put(65, coltanCleaning()); result.put(67, coltanSeparation()); result.put(1003, bayer());
        result.put(1173, oxideReduction("TANTALUM_OXIDE_ITEM", "TANTALUM_ITEM", "441.89276", "361.89576", "200.39"));
        result.put(1174, oxideReduction("NIOBIUM_OXIDE_ITEM", "NIOBIUM_ITEM", "265.80976", "185.81276", "200.39"));
        result.put(1175, oxideReduction("ALUMINA_ITEM", "ALUMINIUM_ITEM", "101.961277", "53.963077", "120.234"));
        result.put(1176, calciumDrying());
        result.put(1171, xylene()); result.put(1172, syngasSeparation());
        result.put(81, bakelite()); result.put(82, rubber()); result.put(94, polycarbonate()); result.put(96, pvc()); result.put(54, aramid());
        result.put(1163, sulfurDoses()); result.put(1164, butadiene()); result.put(1165, phenol()); result.put(1166, formaldehyde());
        result.put(1167, bisphenol()); result.put(1168, benzene()); result.put(1169, diamine()); result.put(1170, acidChloride());
        result.put(40, peroxide()); result.put(90, sulfuric()); result.put(92, ChemicalFuelProcesses.finishing("AROMATICS", "SOLVENT"));
        result.put(43, polymer(false)); result.put(44, polymer(false)); result.put(1130, polymer(true));
        result.put(58, electrolysis(8000).withDuration(400)); result.put(77, combustion()); result.put(99, nitric()); result.put(101, ammonia());
        result.put(103, hydrochloric()); result.put(1037, ChemicalFuelProcesses.sulfurRecovery());
        result.put(1063, chloralkali(true));
        result.put(100, hydrazine()); result.put(104, ammoniumNitrate());
        result.put(1062, hydrofluoric()); result.put(1070, ammoniumChlorideDecomposition());
        result.put(1035, sulfateReduction()); result.put(1036, sulfideAcidification());
        result.put(1077, gadoliniumMelting()); result.put(1078, gadoliniumRefining()); result.put(1065, rutheniumReduction());
        result.put(1032, sulfideSynthesis()); result.put(1011, saltMelting()); result.put(1016, sodiumHydroxide());
        result.put(1012, potashChlorides()); result.put(1013, potassiumReduction());
        result.put(105, galliumArsenide()); result.put(1014, potassiumIodide()); result.put(1050, carnallite()); result.put(1029, strontiumMelting());
        result.put(1002, chloromethane()); result.put(1024, sodiumCarbonate()); result.put(1028, calciumChloride());
        result.put(1030, strontiumChloride()); result.put(1031, bariumCarbonate()); result.put(1033, bariumReduction());
        result.put(1145, waterTreatment()); result.put(1146, demineralization()); result.put(1147, borated()); result.put(1148, evaporation());
        return result;
    }
}
