package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import com.hbm.realsim.PhysicalState.Basis;
import com.hbm.realsim.PhysicalState.Phase;

/** Adopted Phase 1-4 assays. Unlisted/fictional identities remain nominal and unresolved. */
public final class PhysicalRegistry {
    private static final Map<String, PhysicalState> STATES = new HashMap<String, PhysicalState>();
    private static final PhysicalState UNRESOLVED = new PhysicalState("unresolved", Phase.NOMINAL, Basis.NOMINAL, 0, null, "unresolved", null);
    static {
        liquid("crude", "0.850", "42300000", "OIL", "OIL_DS", "CRACKOIL", "CRACKOIL_DS", "COALOIL", "HOTOIL", "HOTOIL_DS", "HOTCRACKOIL", "HOTCRACKOIL_DS");
        liquid("diesel", "0.833", "43000000", "DIESEL", "DIESEL_CRACK");
        liquid("gasoline", "0.730", "44300000", "GASOLINE", "COALGAS", "REFORMATE");
        liquid("kerosene", "0.800", "43800000", "KEROSENE");
        liquid("heating_oil", "0.833", "43000000", "HEATINGOIL", "HEATINGOIL_VACUUM");
        liquid("residual_oil", "0.980", "40400000", "HEAVYOIL", "HEAVYOIL_VACUUM");
        putLiquid("BITUMEN", "bitumen", "0.990", new BigDecimal("39750000"), "150 C flowing bitumen; reference-temperature liquid L");
        liquid("lubricating_residue", "0.980", "39975000", "SMEAR");
        liquid("coker_distillate", "0.833", "43000000", "OIL_COKER");
        liquid("naphtha", "0.700", "44500000", "NAPHTHA", "NAPHTHA_DS", "NAPHTHA_CRACK", "NAPHTHA_COKER");
        liquid("light_oil", "0.800", "43360000", "LIGHTOIL", "LIGHTOIL_DS", "LIGHTOIL_CRACK", "LIGHTOIL_VACUUM");
        liquid("aromatic_feedstock", "0.870", "40600000", "AROMATICS", "SOLVENT");
        liquid("xylene", "0.860", "40800000", "XYLENE");
        liquid("triglyceride_oil", "0.920", "37500000", "FISHOIL", "SUNFLOWEROIL", "OLIVEOIL");
        liquid("reclaimed_oil", "0.900", "40200000", "RECLAIMED");
        liquid("lubricant", "0.890", "40200000", "LUBRICANT");
        liquid("lpg", "0.520", "47300000", "LPG");
        liquid("ethanol", "0.789", "26806000", "ETHANOL");
        liquid("methanol", "0.792", "19928000", "METHANOL");
        liquid("pyrolysis_oil", "1.200", "17000000", "WOODOIL");
        liquid("coal_tar", "1.100", "28000000", "COALCREOSOTE");
        liquid("hydrazine", "1.000", "16673000", "HYDRAZINE");
        blend("PETROIL", "RECLAIMED", "0.8", "LUBRICANT", "0.2");
        blend("BIOFUEL", "SUNFLOWEROIL", "0.8", "ETHANOL", "0.2");
        blend("DIESEL_REFORM", "DIESEL", "0.9", "REFORMATE", "0.1");
        blend("DIESEL_CRACK_REFORM", "DIESEL_CRACK", "0.9", "REFORMATE", "0.1");
        blend("KEROSENE_REFORM", "KEROSENE", "0.9", "REFORMATE", "0.1");
        // One molar combustion reference for both H2 phases; no oxygen energy credit.
        BigDecimal hydrogenLhv = new BigDecimal("241826").divide(new BigDecimal("0.00201588"), 18, RoundingMode.DOWN);
        putLiquid("HYDROGEN", "hydrogen", "0.0708", hydrogenLhv, "20.15 K saturated liquid");
        putGas("HYDROGEN_GAS", "hydrogen", "2.01588", hydrogenLhv);
        putLiquid("OXYGEN", "oxygen", "1.141", BigDecimal.ZERO, "90.15 K saturated liquid");
        putGas("OXYGEN_GAS", "oxygen", "31.9988", BigDecimal.ZERO);
        putLiquid("NITROGEN", "nitrogen", "0.8085", BigDecimal.ZERO, "77.15 K saturated liquid");
        putGas("NITROGEN_GAS", "nitrogen", "28.0134", BigDecimal.ZERO);
        putGas("ARGON", "argon", "39.948", BigDecimal.ZERO);
        putGas("CARBONDIOXIDE", "carbon_dioxide", "44.0095", BigDecimal.ZERO);
        putGas("HELIUM4", "helium", "4.002602", BigDecimal.ZERO);
        putGas("CO", "carbon_monoxide", "28.0101", new BigDecimal("282980").divide(new BigDecimal("0.0280101"), 18, RoundingMode.DOWN));
        // Adopted dry-air proxy matches the existing separator's component assay.
        // This is a gameplay assay, rather than a claim of exact terrestrial composition.
        putGas("NEON", "neon", "20.1797", BigDecimal.ZERO);
        putGas("KRYPTON", "krypton", "83.798", BigDecimal.ZERO);
        putGas("XENON", "xenon", "131.293", BigDecimal.ZERO);
        gasBlend("NOBLE_GAS_MIX", "ARGON", "0.96", "trace_ne_kr_xe", "0.04", "40.904584", "0");
        gasBlend("AIR", "NITROGEN_GAS", "0.77", "OXYGEN_GAS", "0.21", "29.09859184", "0");
        replaceMixture("AIR", new String[] {"NITROGEN_GAS", "OXYGEN_GAS", "ARGON", "NOBLE_GAS_MIX"}, new String[] {"0.77", "0.21", "0.01", "0.01"});
        replaceMixture("NOBLE_GAS_MIX", new String[] {"ARGON", "NEON", "KRYPTON", "XENON"}, new String[] {"0.96", "0.02", "0.01", "0.01"});
        // Released methane and 50:50 syngas proxies; refinery/MAPD offgas is still unresolved.
        putGas("GAS", "methane", "16.04246", new BigDecimal("802340").divide(new BigDecimal("0.01604246"), 18, RoundingMode.DOWN));
        gasBlend("SYNGAS", "HYDROGEN_GAS", "0.5", "CO", "0.5", "15.01299", "262403");
        gasBlend("BIOGAS", "GAS", "0.6", "CARBONDIOXIDE", "0.4", "27.229276", "481404");
        putGas("LPG_VAPOR_ASSAY", "lpg", "44.09562", get("LPG").lhvJoulesPerKg);
        putGas("HYDROGEN_SULFIDE", "hydrogen_sulfide", "34.08088", new BigDecimal("518000").divide(new BigDecimal("0.03408088"), 18, RoundingMode.DOWN));
        putGas("UNSATURATEDS", "ethylene_surrogate", "28.05316", new BigDecimal("47200000"));
        mixture("PETROLEUM", new String[] {"GAS", "LPG_VAPOR_ASSAY"}, new String[] {"0.5", "0.5"});
        mixture("REFORMGAS", new String[] {"HYDROGEN_GAS", "GAS"}, new String[] {"0.5", "0.5"});
        mixture("GAS_COKER", new String[] {"HYDROGEN_GAS", "GAS"}, new String[] {"0.75", "0.25"});
        mixture("SOURGAS", new String[] {"GAS", "HYDROGEN_SULFIDE"}, new String[] {"0.9", "0.1"});
        gasBlend("OXYHYDROGEN", "HYDROGEN_GAS", "0.666666666666666667", "OXYGEN_GAS", "0.333333333333333333", "12.010186666666666667", "161217.333333333333333333");
        solid("COAL_ITEM", "coal", "1", "25800000");
        solid("LIGNITE_ITEM", "lignite", "1", "18000000");
        solid("COAL_BRIQUETTE_ITEM", "coal_briquette", "1", "25800000");
        solid("LIGNITE_BRIQUETTE_ITEM", "lignite_briquette", "1", "18000000");
        solid("CHARCOAL_ITEM", "charcoal", "1", "29500000");
        solid("COKE_ITEM", "coke", "1", "28200000");
        solid("LIGNITE_COKE_ITEM", "lignite_coke", "1", "25000000");
        solid("WOOD_ASH_ITEM", "wood_ash", "1", "0");
        solid("SAPLING_ITEM", "sapling", "1", "15600000");
        solid("WOOD_BRIQUETTE_ITEM", "wood_briquette", "4", "15600000");
        solid("PETCOKE_ITEM", "petroleum_coke", "1", "32500000");
        solid("CHAR_ITEM", "carbon_char", "1", "32800000");
        solid("ASH_ITEM", "mineral_ash", "1", "0");
        solid("SULFUR_ITEM", "sulfur", "1", "9260000");
        solid("BIOMASS_ITEM", "wet_biomass", "1", "15600000");
        solid("FUEL_ADDITIVE_ITEM", "trace_organolead_assay", "0.010", "14000000");
        solid("SUGAR_ITEM", "glucose_surrogate", "1", "16500000");
        solid("FISH_FEED_ITEM", "oily_fish_feed_assay", "1", "19980000");
        solid("SEED_FEED_ITEM", "sunflower_feed_assay", "1", "24360000");
        solid("TAR_CRUDE_ITEM", "solid_bitumen", "1", "39750000");
        solid("TAR_COAL_ITEM", "solid_coal_tar", "1", "28000000");
        solid("TAR_WOOD_ITEM", "solid_pyrolysis_tar", "1", "17000000");
        solid("TAR_CRACK_ITEM", "solid_cracked_assay", "1", "42300000");
        solid("LEAD_ITEM", "lead", "1", "0");
        solid("OIL_SAND_BLOCK", "five_percent_bitumen_ore", "2000", "1987500");
        solid("SAND_BLOCK", "mineral_sand", "1900", "0");
        putGas("VENT_UNSATURATEDS", "vented_ethylene", "28.05316", new BigDecimal("47200000"));
        liquid("water", "1", "0", "WATER", "FRESH_WATER");
        liquid("vented_water", "1", "0", "VENT_WATER");
        putGas("VENT_HYDROGEN_GAS", "vented_hydrogen", "2.01588", hydrogenLhv);
        // One 10-g trace additive per 10000 L; weighted mass/energy, no legacy 20% bonus.
        for(String name : new String[] {"PETROIL", "GASOLINE", "COALGAS"}) {
            PhysicalState parent = get(name);
            long trace = get("FUEL_ADDITIVE_ITEM").massUnits / 10000L;
            BigDecimal budget = BigDecimal.valueOf(parent.massUnits).multiply(parent.lhvJoulesPerKg)
                    .add(BigDecimal.valueOf(trace).multiply(get("FUEL_ADDITIVE_ITEM").lhvJoulesPerKg));
            STATES.put(name + "_LEADED", new PhysicalState(parent.substance, parent.phase, parent.basis, parent.massUnits + trace,
                    budget.divide(BigDecimal.valueOf(parent.massUnits + trace), 18, RoundingMode.DOWN), "weighted parent plus trace organolead surrogate", parent.mixture));
        }
        // Explicit starter wood assay: 1 kg wet-reference wood per plank, 4 per log.
        solid("WOOD_PLANK", "wood", "1", "15600000");
        solid("WOOD_LOG", "wood", "4", "15600000");
        solid("WOOD_STICK", "wood", "0.5", "15600000");
    }
    private PhysicalRegistry() { }
    private static void mixture(String name, String[] names, String[] fractions) {
        BigDecimal mass = BigDecimal.ZERO, energy = BigDecimal.ZERO;
        Map<String, BigDecimal> mix = new HashMap<String, BigDecimal>();
        for(int i = 0; i < names.length; i++) {
            PhysicalState component = get(names[i]); BigDecimal fraction = new BigDecimal(fractions[i]);
            BigDecimal part = BigDecimal.valueOf(component.massUnits).multiply(fraction);
            mass = mass.add(part); energy = energy.add(part.multiply(component.lhvJoulesPerKg)); mix.put(names[i], fraction);
        }
        STATES.put(name, new PhysicalState(name, Phase.GAS, Basis.STANDARD_GAS_LITER, mass.longValueExact(),
                energy.divide(mass, 18, RoundingMode.DOWN), "0 C, 1 bar; declared mole assay", mix));
    }
    private static void replaceMixture(String name, String[] names, String[] fractions) {
        PhysicalState state = get(name); Map<String, BigDecimal> mix = new HashMap<String, BigDecimal>();
        for(int i = 0; i < names.length; i++) mix.put(names[i], new BigDecimal(fractions[i]));
        STATES.put(name, new PhysicalState(state.substance, state.phase, state.basis, state.massUnits, state.lhvJoulesPerKg, state.thermalReference, mix));
    }
    public static PhysicalState get(String identity) {
        PhysicalState state = STATES.get(identity);
        return state != null ? state : UNRESOLVED;
    }
    private static void liquid(String substance, String kg, String lhv, String... names) {
        for(String name : names) putLiquid(name, substance, kg, new BigDecimal(lhv), name.startsWith("HOT") ? "350 C liquid; sensible enthalpy deferred" : "20 C reference liquid");
    }
    private static void putLiquid(String name, String substance, String kg, BigDecimal lhv, String reference) {
        BigDecimal sensible = name.startsWith("HOT") ? new BigDecimal("660000") : name.equals("BITUMEN") ? new BigDecimal("260000") : BigDecimal.ZERO;
        STATES.put(name, new PhysicalState(substance, Phase.LIQUID, Basis.LIQUID_LITER, new BigDecimal(kg).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).longValueExact(), lhv, reference, null, sensible));
    }
    private static void putGas(String name, String substance, String molarGrams, BigDecimal lhv) {
        STATES.put(name, new PhysicalState(substance, Phase.GAS, Basis.STANDARD_GAS_LITER, new BigDecimal(molarGrams).multiply(new BigDecimal("1000000000")).longValueExact(), lhv, "0 C, 1 bar absolute, dry standard amount; stored at 20 C", null));
    }
    private static void solid(String name, String substance, String kg, String lhv) {
        STATES.put(name, new PhysicalState(substance, Phase.SOLID, Basis.MATERIAL_ITEM, new BigDecimal(kg).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).longValueExact(), new BigDecimal(lhv), "adopted item mass", null));
    }
    private static void blend(String name, String a, String af, String b, String bf) {
        PhysicalState sa = get(a), sb = get(b);
        BigDecimal fa = new BigDecimal(af), fb = new BigDecimal(bf);
        BigDecimal mass = BigDecimal.valueOf(sa.massUnits).multiply(fa).add(BigDecimal.valueOf(sb.massUnits).multiply(fb));
        BigDecimal energy = BigDecimal.valueOf(sa.massUnits).multiply(sa.lhvJoulesPerKg).multiply(fa).add(BigDecimal.valueOf(sb.massUnits).multiply(sb.lhvJoulesPerKg).multiply(fb));
        Map<String, BigDecimal> mix = new HashMap<String, BigDecimal>(); mix.put(a, fa); mix.put(b, fb);
        STATES.put(name, new PhysicalState(name, Phase.LIQUID, Basis.LIQUID_LITER, mass.longValueExact(), energy.divide(mass, 18, RoundingMode.DOWN), "20 C; volume mixture", mix));
    }
    private static void gasBlend(String name, String a, String af, String b, String bf, String grams, String joulesPerMole) {
        BigDecimal molarMass = new BigDecimal(grams);
        Map<String, BigDecimal> mix = new HashMap<String, BigDecimal>(); mix.put(a, new BigDecimal(af)); mix.put(b, new BigDecimal(bf));
        long mass = molarMass.multiply(new BigDecimal("1000000000")).setScale(0, RoundingMode.DOWN).longValueExact();
        STATES.put(name, new PhysicalState(name, Phase.GAS, Basis.STANDARD_GAS_LITER, mass, new BigDecimal(joulesPerMole).multiply(new BigDecimal("1000")).divide(molarMass, 18, RoundingMode.DOWN), "0 C, 1 bar; mole mixture", mix));
    }
}
