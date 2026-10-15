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
        for(String blast : new String[] {"AIR_HEATED", "AIRBLAST"}) {
            PhysicalState air = get("AIR");
            int delta = blast.equals("AIRBLAST") ? 780 : 580;
            STATES.put(blast, new PhysicalState(air.substance, Phase.GAS, Basis.STANDARD_GAS_LITER, air.massUnits, BigDecimal.ZERO,
                    (delta + 20) + " C dry blast; cp 1 kJ/(kg K)", air.mixture, BigDecimal.valueOf(delta * 1000L)));
        }
        // Dry combustible top-gas proxy, rather than generic combustion FLUE.
        mixture("BLAST_FURNACE_GAS", new String[] {"CO", "CARBONDIOXIDE", "NITROGEN_GAS", "HYDROGEN_GAS"}, new String[] {"0.22", "0.22", "0.54", "0.02"});
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
        solid("FUEL_ADDITIVE_DOSE_ITEM", "trace_organolead_assay", "0.0001", "14000000");
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
        liquid("deuterium_depleted_water", "1", "0", "DEPLETED_WATER");
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

        for(SteamLedger.State state : SteamLedger.State.values()) {
            if(state == SteamLedger.State.COLD || state == SteamLedger.State.WARM) continue;
            STATES.put(state.identity, new PhysicalState("water", PhysicalState.Phase.GAS, PhysicalState.Basis.WATER_EQUIVALENT,
                    Math.multiplyExact(state.massPerUnit, PhysicalState.MASS_UNITS_PER_KG / SteamLedger.MASS_PER_KG), BigDecimal.ZERO,
                    state.temperatureC + " C / " + state.pressurePa + " Pa; nominal water-equivalent mass", null));
        }
        // Explicit starter wood assay: 1 kg wet-reference wood per plank, 4 per log.
        solid("WOOD_PLANK", "wood", "1", "15600000");
        solid("WOOD_LOG", "wood", "4", "15600000");
        solid("WOOD_STICK", "wood", "0.5", "15600000");
        solid("SAWDUST_ITEM", "wood", "1", "15600000");
        // Released process identities; existing resolved petroleum/water/cryogenic coefficients stay fixed.
        liquid("demineralized_water", "1", "0", "LIGHT_WATER", "LIGHT_WATER_HOT");
        liquid("one_percent_boric_acid_solution", "1.006", "0", "BORATED_WATER", "BORATED_WATER_HOT");
        liquid("heavy_water", "1.1056", "0", "HEAVYWATER", "HEAVYWATER_HOT");
        liquid("3.125_percent_salt_solution", "1.023", "0", "BRINE");
        liquid("30_percent_hydrogen_peroxide", "1.11", "0", "PEROXIDE");
        liquid("98_percent_sulfuric_acid", "1.84", "0", "SULFURIC_ACID");
        liquid("65_percent_nitric_acid", "1.40", "0", "NITRIC_ACID");
        liquid("30_percent_hydrochloric_acid", "1.15", "0", "HCL");
        liquid("48_percent_hydrofluoric_acid", "1.15", "0", "HYDROFLUORIC_ACID");
        putGas("AMMONIA", "ammonia", "17.03052", new BigDecimal("18600000"));
        putGas("CHLORINE", "chlorine", "70.906", BigDecimal.ZERO);
        putGas("FLUORINE", "fluorine", "37.9968064", BigDecimal.ZERO);
        putGas("DEUTERIUM", "deuterium", "4.028203556", BigDecimal.ZERO);
        putGas("PHOSGENE", "phosgene", "98.9161", BigDecimal.ZERO);
        mixture("TEKTOAIR", new String[] {"NITROGEN_GAS", "GAS", "UNSATURATEDS", "HYDROGEN_GAS"}, new String[] {".89", ".08", ".02", ".01"});
        mixture("JOOLGAS", new String[] {"HYDROGEN_GAS", "HELIUM4", "GAS"}, new String[] {".89", ".10", ".01"});
        mixture("NGAS", new String[] {"HYDROGEN_GAS", "HELIUM4", "GAS"}, new String[] {".80", ".19", ".01"});
        mixture("UGAS", new String[] {"HYDROGEN_GAS", "HELIUM4", "GAS"}, new String[] {".82", ".15", ".03"});
        mixture("SARNUSGAS", new String[] {"HYDROGEN_GAS", "HELIUM4", "GAS"}, new String[] {".96", ".03", ".01"});
        mixture("EVEAIR", new String[] {"CARBONDIOXIDE", "NITROGEN_GAS", "SOURGAS", "ARGON"}, new String[] {".95", ".03", ".01", ".01"});
        mixture("DUNAAIR", new String[] {"CARBONDIOXIDE", "NITROGEN_GAS", "ARGON", "OXYGEN_GAS"}, new String[] {".95", ".03", ".01", ".01"});
        blend("COOLANT", "FRESH_WATER", ".1", "ETHANOL", ".9");
        liquid("water_proppant_fracturing_slurry", "1.02", "0", "FRACKSOL");
        liquid("polyethylene", "0.920", "47200000", "POLYTHYLENE");
        solid("POLYMER_ITEM", "polyethylene", "1", "47200000");
        solid("SALT_ITEM", "sodium_chloride", "1", "0");
        solid("BORON_ITEM", "boron", "1", "58000000");
        solid("AMMONIUM_NITRATE_ITEM", "ammonium_nitrate", "1", "0");
        solid("SODIUM_NITRATE_ITEM", "sodium_nitrate", "1", "0");
        solid("POTASSIUM_NITRATE_ITEM", "potassium_nitrate", "1", "0");
        solid("FERTILIZER_ITEM", "dry_nutrient_salt_dose", ".25", "0");
        solid("AMMONIUM_CHLORIDE_ITEM", "ammonium_chloride", "1", "3500000");
        solid("FLUORITE_ITEM", "calcium_fluoride", "1", "0");
        solid("GYPSUM_ITEM", "calcium_sulfate_dihydrate", "1", "0");
        solid("LYE_ITEM", "sodium_hydroxide", "1", "0");
        solid("PROPPANT_ITEM", "quartz_proppant", "1", "0");
        putGas("CHLOROMETHANE", "chloromethane", "50.48752", new BigDecimal("10000000"));
        liquid("40_percent_calcium_chloride", "1.40", "0", "CALCIUM_CHLORIDE");
        liquid("20_percent_sodium_carbonate", "1.20", "0", "SODIUM_CARBONATE");
        solid("QUICKLIME_ITEM", "calcium_oxide", "1", "0");
        solid("STRONTIUM_OXIDE_ITEM", "strontium_oxide", "1", "0");
        solid("STRONTIUM_CHLORIDE_ITEM", "strontium_chloride", "1", "0");
        solid("BARIUM_SULFIDE_ITEM", "barium_sulfide", "1", "5000000");
        solid("BARIUM_CARBONATE_ITEM", "barium_carbonate", "1", "0");
        solid("BARIUM_OXIDE_ITEM", "barium_oxide", "1", "0");
        solid("BARIUM_ALUMINATE_ITEM", "barium_aluminate", "1", "0");
        solid("BARIUM_ITEM", "barium", "1", "4000000");
        solid("ALUMINIUM_ITEM", "aluminium", "1", "31000000");
        solid("ALUMINA_ITEM", "aluminium_oxide", "1", "0");
        solid("SODIUM_ITEM", "sodium", "1", "10000000");
        solid("POTASSIUM_ITEM", "potassium", "1", "6000000");
        for(String name : new String[] {"ICE_BLOCK", "PACKED_ICE_BLOCK", "SNOW_BLOCK", "SNOWBALL_ITEM"}) {
            BigDecimal kg = new BigDecimal(name.equals("PACKED_ICE_BLOCK") ? "2" : name.equals("SNOW_BLOCK") ? ".5" : name.equals("SNOWBALL_ITEM") ? ".125" : "1");
            STATES.put(name, new PhysicalState("ice", Phase.SOLID, Basis.MATERIAL_ITEM,
                    kg.multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).longValueExact(), BigDecimal.ZERO,
                    "0 C; compressed construction parcel, not one cubic metre; includes fusion relative to 20 C water", null, new BigDecimal("-417300")));
        }
        STATES.put("DRY_ICE_BLOCK", new PhysicalState("carbon_dioxide", Phase.SOLID, Basis.MATERIAL_ITEM,
                PhysicalState.MASS_UNITS_PER_KG, BigDecimal.ZERO, "-78.5 C compressed parcel relative to reference gas", null, new BigDecimal("-700000")));
        solid("IODINE_ITEM", "iodine", ".01", "0");
        solid("POTASSIUM_IODIDE_ITEM", "potassium_iodide", ".01", "0");
        solid("POTASSIUM_IODATE_ITEM", "potassium_iodate", ".01", "0");
        solid("CARNALLITE_ITEM", "potassium_magnesium_chloride_hexahydrate", "1", "0");
        solid("MAGNESIUM_CHLORIDE_ITEM", "magnesium_chloride", "1", "0");
        solid("STRONTIUM_ITEM", "strontium", "1", "6000000");
        // A ninth kilogram is repeating in both decimal and the released integer mass lattice.
        // Quantize this adopted nugget assay once; do not reject the entire shared registry at startup.
        for(String name : new String[] {"GALLIUM_NUGGET_ITEM", "ARSENIC_NUGGET_ITEM", "GAAS_NUGGET_ITEM"})
            STATES.put(name, new PhysicalState(name.toLowerCase(java.util.Locale.US), Phase.SOLID, Basis.MATERIAL_ITEM,
                    PhysicalState.MASS_UNITS_PER_KG / 9, BigDecimal.ZERO, "1/9 kg, rounded down to the physical mass quantum", null));
        STATES.put("MOLTEN_STRONTIUM_CHLORIDE", new PhysicalState("strontium_chloride", Phase.LIQUID, Basis.NOMINAL,
                PhysicalState.MASS_UNITS_PER_KG / 144, BigDecimal.ZERO, "900 C; 144 legacy metal parcels/kg", null, new BigDecimal("1000000")));
        putGas("VENT_OXYGEN_GAS", "vented_oxygen", "31.9988", BigDecimal.ZERO);
        putGas("VENT_PHOSGENE", "vented_phosgene", "98.9161", BigDecimal.ZERO);
        putGas("VENT_ARGON", "vented_argon", "39.948", BigDecimal.ZERO);
        // The aromatic cut is a released mass/LHV assay; cumene is its declared reaction surrogate.
        putGas("BUTADIENE", "butadiene", "54.09044", new BigDecimal("47000000"));
        putGas("FORMALDEHYDE", "formaldehyde", "30.02598", new BigDecimal("17400000"));
        liquid("phenol", "1.07", "31100000", "PHENOL");
        PhysicalState phenol = get("PHENOL");
        STATES.put("PHENOL", new PhysicalState(phenol.substance, phenol.phase, phenol.basis, phenol.massUnits, phenol.lhvJoulesPerKg,
                "50 C liquid; includes melting relative to reference solid", null, new BigDecimal("200000")));
        liquid("acetone", ".784", "29600000", "ACETONE");
        liquid("benzene", ".879", "41830000", "BENZENE");
        solid("SULFUR_DOSE_ITEM", "sulfur", ".01", "9260000");
        solid("BISPHENOL_A_ITEM", "bisphenol_a", "1", "32000000");
        solid("TPC_ITEM", "terephthaloyl_chloride", "1", "22000000");
        solid("PPD_ITEM", "phenylenediamine", "1", "32000000");
        solid("RUBBER_ITEM", "vulcanized_polybutadiene", "1", "44000000");
        solid("PVC_ITEM", "polyvinyl_chloride", "1", "18000000");
        solid("BAKELITE_ITEM", "phenolic_resin", "1", "30000000");
        solid("PC_ITEM", "polycarbonate", "1", "28500000");
        solid("ARAMID_ITEM", "poly_paraphenylene_terephthalamide", "1", "28000000");
        solid("COLTAN_ORE_ITEM", "half_concentrate_half_gangue", "1", "0");
        solid("COLTAN_CONCENTRATE_ITEM", "equal_mass_tantalum_niobium_pentoxides", "1", "0");
        solid("GANGUE_ITEM", "mineral_gangue", "1", "0");
        solid("TANTALUM_OXIDE_ITEM", "tantalum_pentoxide", "1", "0");
        solid("NIOBIUM_OXIDE_ITEM", "niobium_pentoxide", "1", "0");
        solid("TANTALUM_ITEM", "tantalum", "1", "5000000");
        solid("NIOBIUM_ITEM", "niobium", "1", "10000000");
        solid("CALCIUM_ITEM", "calcium", "1", "16000000");
        solid("BAUXITE_ORE_ITEM", "bauxite_processing_parcel", "10", "0");
        STATES.put("GALLIUM_TINY_ITEM", new PhysicalState("gallium", Phase.SOLID, Basis.MATERIAL_ITEM, PhysicalState.MASS_UNITS_PER_KG / 9, BigDecimal.ZERO, "one ninth kg parcel", null));
        liquid("caustic_bauxite_residue", "1.5", "0", "BAUXITE_RESIDUE");
        STATES.put("MOLTEN_CALCIUM_CHLORIDE", new PhysicalState("calcium_chloride", Phase.LIQUID, Basis.NOMINAL,
                PhysicalState.MASS_UNITS_PER_KG / 144, BigDecimal.ZERO, "900 C; 144 metal-salt parcels/kg", null, new BigDecimal("1000000")));
        STATES.put("PAPER_ITEM", new PhysicalState("cellulose_paper", Phase.SOLID, Basis.MATERIAL_ITEM,
                PhysicalState.MASS_UNITS_PER_KG / 3, new BigDecimal("15600000"), "one third kg fiber parcel", null));
        STATES.put("VENT_STEAM", new PhysicalState("vented_water", Phase.GAS, Basis.WATER_EQUIVALENT,
                PhysicalState.MASS_UNITS_PER_KG, BigDecimal.ZERO, "paid ordinary steam discharged to environment", null,
                BigDecimal.valueOf(SteamLedger.State.ORDINARY.enthalpyJPerKg)));
        solid("COFFEE_POWDER_ITEM", "instant_coffee", ".02", "16000000");
        solid("TEA_LEAF_ITEM", "tea_leaf", ".005", "16000000");
        solid("FLUX_ITEM", "mineralizing_flux", "1", "0");
        solid("CHLOROCALCITE_ITEM", "potassium_calcium_trichloride", "1", "0");
        liquid("instant_coffee", "1.04", "615384.615384615", "COFFEE");
        liquid("tea_infusion", "1.0005", "7996.0019990005", "TEA");
        solid("VENT_BOTANICAL", "discarded_brewing_leaves", "1", "16000000");
        liquid("mineral_colloid", "1.25", "0", "COLLOID");
        liquid("20_percent_potassium_calcium_chloride", "1.15", "0", "CHLOROCALCITE_SOLUTION", "CHLOROCALCITE_CLEANED");
        liquid("flux_conditioned_chloride_slurry", "1.43", "0", "CHLOROCALCITE_MIX");
        // Nuclear stock uses explicit legacy carrier aliquots, not ordinary-gas standard liters.
        // Intermediate aliquots differ by cascade stage; fixed side-product parcels establish their masses.
        long uraniumNugget = PhysicalState.MASS_UNITS_PER_KG / 9;
        String[] cascadeNames = {"NUF6", "LEUF6", "MEUF6", "HEUF6"};
        int[] uraniumParcels = {9, 5, 3, 2}, packetCounts = {400, 200, 100, 50};
        for(int i = 0; i < cascadeNames.length; i++) {
            long metal = BigDecimal.valueOf(uraniumNugget).multiply(BigDecimal.valueOf(uraniumParcels[i])).divide(BigDecimal.valueOf(packetCounts[i]), 0, RoundingMode.DOWN).longValueExact();
            long boundF = BigDecimal.valueOf(metal).multiply(new BigDecimal("113.9904192")).divide(new BigDecimal("238.02891"), 0, RoundingMode.DOWN).longValueExact();
            PhysicalState state = new PhysicalState("uranium_hexafluoride_aliquot", Phase.NOMINAL, Basis.NOMINAL, metal + boundF,
                    BigDecimal.ZERO, "80 C carrier aliquot; internal stage " + cascadeNames[i] + "; not a standard liter", null, new BigDecimal("35000"));
            STATES.put("CASCADE_" + cascadeNames[i], state);
            if(i == 0) STATES.put("UF6", new PhysicalState(state.substance, Phase.GAS, Basis.NOMINAL, state.massUnits, BigDecimal.ZERO, state.thermalReference, null, state.sensibleJoulesPerKg));
        }
        long puMetal = PhysicalState.MASS_UNITS_PER_KG / 900;
        long puBoundF = BigDecimal.valueOf(puMetal).multiply(new BigDecimal("113.9904192")).divide(new BigDecimal("239.05216"), 0, RoundingMode.DOWN).longValueExact();
        STATES.put("PUF6", new PhysicalState("plutonium_hexafluoride_aliquot", Phase.GAS, Basis.NOMINAL, puMetal + puBoundF, BigDecimal.ZERO,
                "80 C carrier aliquot; 900 counts contain one kg Pu reference", null, new BigDecimal("35000")));
        STATES.put("CASCADE_PF6", new PhysicalState("plutonium_hexafluoride_aliquot", Phase.NOMINAL, Basis.NOMINAL, puMetal + puBoundF, BigDecimal.ZERO,
                "internal carrier aliquot matching PUF6", null, new BigDecimal("35000")));
        for(String name : new String[] {"U238_NUGGET_ITEM", "U235_NUGGET_ITEM", "PU_MIX_NUGGET_ITEM"})
            STATES.put(name, new PhysicalState(name, Phase.SOLID, Basis.MATERIAL_ITEM, uraniumNugget, BigDecimal.ZERO, "nuclear parcel; fission energy is separate from combustion LHV", null));
        long yellowcakeU = uraniumNugget * 27 / 4;
        long yellowcakeO = BigDecimal.valueOf(yellowcakeU).multiply(new BigDecimal("127.9952")).divide(new BigDecimal("714.08673"), 0, RoundingMode.DOWN).longValueExact();
        STATES.put("YELLOWCAKE_ITEM", new PhysicalState("natural_uranium_oxide_concentrate", Phase.SOLID, Basis.MATERIAL_ITEM, yellowcakeU + yellowcakeO,
                BigDecimal.ZERO, "U3O8 concentrate parcel with three quarters kg uranium reference", null));
        solid("PLUTONIUM_ITEM", "mixed_plutonium", "1", "0");
        solid("URANIUM_ORE_ITEM", "20_percent_U3O8_processing_parcel", "10", "0");
        solid("SCANDIUM_OXIDE_ITEM", "scandium_oxide", "1", "0");
        solid("YTTRIUM_OXIDE_ITEM", "yttrium_oxide", "1", "0");
        solid("REE_SLUDGE_ITEM", "rare_earth_oxide_concentrate", "1", "0");
        solid("REE_RESIDUE_ITEM", "rejected_rare_earth_oxide_residue", "1", "0");
        liquid("dilute_rare_earth_oxide_raffinate", "1.05", "0", "RAFFINATE");
        liquid("geological_trace_brine", "1.023", "0", "MINERAL_BRINE");
        liquid("potassium_bromide_2.046_percent", "1.02", "0", "BITTERN");
        liquid("sodium_iodide_1_percent", "1.01", "0", "IODINE_BRINE");
        liquid("mixed_acid_waste_compatibility_assay", "1.1", "0", "ACIDWASTE");
        solid("BROMINE_REAGENT_ITEM", "contained_bromine", "1", "0");
        solid("SCANDIUM_CHLORIDE_ITEM", "scandium_chloride", "1", "0");
        solid("YTTRIUM_CHLORIDE_ITEM", "yttrium_chloride", "1", "0");
        solid("TITANIUM_TRACE_ITEM", "trace_oxide_concentrate", "1", "0");
        STATES.put("SCANDIUM_NUGGET_ITEM", new PhysicalState("scandium", Phase.SOLID, Basis.MATERIAL_ITEM, PhysicalState.MASS_UNITS_PER_KG / 9, new BigDecimal("10000000"), "reference solid parcel", null, BigDecimal.ZERO));
        STATES.put("YTTRIUM_TINY_ITEM", new PhysicalState("yttrium", Phase.SOLID, Basis.MATERIAL_ITEM, PhysicalState.MASS_UNITS_PER_KG / 9, new BigDecimal("10000000"), "reference solid parcel", null, BigDecimal.ZERO));
        liquid("10_percent_osmium_tetroxide", "1.1", "0", "OSMIRIDIUM_SOLUTION");
        liquid("40_percent_rhodium_chloride", "1.3", "0", "RHODIUM_SOLUTION");
        liquid("40_percent_iridium_chloride", "1.4", "0", "IRIDIUM_SOLUTION");
        solid("OSIR_CONCENTRATE_ITEM", "osmium_iridium_30_70", "1", "630000");
        solid("RH_IR_CONCENTRATE_ITEM", "rhodium_iridium_40_60", "1", "0");
        solid("IRIDIUM_RESIDUE_ITEM", "iridium_concentrate", "1", "0");
        solid("IRIDIUM_OXIDE_ITEM", "iridium_dioxide", "1", "0");
        solid("RHODIUM_ITEM", "rhodium", "1", "0");
        solid("IRIDIUM_ITEM", "iridium", "1", "0");
        STATES.put("OSMIUM_TINY_ITEM", new PhysicalState("osmium", Phase.SOLID, Basis.MATERIAL_ITEM, PhysicalState.MASS_UNITS_PER_KG / 9, new BigDecimal("2100000"), "reference solid", null));
        solid("LEPIDOLITE_ITEM", "activated_lithium_potassium_mineral", "1", "0");
        solid("LITHIUM_SULFATE_ITEM", "lithium_sulfate", "1", "0");
        solid("POTASSIUM_SULFATE_ITEM", "potassium_sulfate", "1", "0");
        solid("LITHIUM_CARBONATE_ITEM", "lithium_carbonate", "1", "0");
        solid("LITHIUM_ITEM", "lithium", "1", "43000000");
        STATES.put("MOLTEN_LITHIUM_CHLORIDE", new PhysicalState("lithium_chloride", Phase.LIQUID, Basis.NOMINAL,
                PhysicalState.MASS_UNITS_PER_KG / 144, BigDecimal.ZERO, "molten 700 C; 144 aliquots/kg", null, new BigDecimal("1000000")));
        PhysicalState coVent = STATES.get("CO");
        STATES.put("VENT_CO", coVent);
        solid("CHARGED_MATRIX_ITEM", "fictional_charged_matrix", ".02", "0");
        STATES.put("IONGEL", new PhysicalState("hydrated_exotic_matrix", Phase.LIQUID, Basis.LIQUID_LITER,
                STATES.get("FRESH_WATER").massUnits + STATES.get("HYDROGEN_GAS").massUnits * 200 + STATES.get("CHARGED_MATRIX_ITEM").massUnits,
                BigDecimal.ZERO, "adopted nonfuel gel; charged-matrix energy remains outside chemical LHV", null));
        liquid("dirty_raw_process_water", "1", "0", "SLOP");
        solid("POLLUCITE_ITEM", "activated_pollucite_oxide_assay", "1", "0");
        solid("CESIUM_SALT_ITEM", "cesium_chloride", "1", "0");
        solid("CESIUM_ITEM", "contained_cesium", "1", "2300000");
        solid("RUBIDIUM_ITEM", "rubidium", "1", "3000000");
        liquid("pollucite_mixed_chloride_liquor", "1.15", "0", "POLLUCITE_SOLUTION");
        liquid("pollucite_25_percent_heavy_chlorides", "1.25", "0", "POLLUCITE_SOLUTION_HEAVY");
        liquid("pollucite_25_percent_light_chlorides", "1.2", "0", "POLLUCITE_SOLUTION_LIGHT");
        mixture("NITROGEN_HELIUM", new String[] {"NITROGEN_GAS", "HELIUM4"}, new String[] {"0.111111111111111111", "0.888888888888888889"});
        mixture("CARBONDIOXIDE_NITROGEN_HELIUM", new String[] {"CARBONDIOXIDE", "NITROGEN_GAS", "HELIUM4"}, new String[] {".1", ".1", ".8"});
        mixture("KRYPTON_FLUORINE", new String[] {"KRYPTON", "FLUORINE"}, new String[] {".998", ".002"});
        mixture("XENON_CHLORINE", new String[] {"XENON", "CHLORINE"}, new String[] {".998", ".002"});
        mixture("EXCIMER_GASMIX", new String[] {"KRYPTON_FLUORINE", "XENON_CHLORINE"}, new String[] {".5", ".5"});
        mixture("RAW_NATURAL_GAS", new String[] {"GAS", "HELIUM4"}, new String[] {".98", ".02"});
        solid("BORIC_ACID_ITEM", "boric_acid", "1", "0");
        solid("CEMENT_ITEM", "cement_binder", "0.5", "0");
        solid("AGGREGATE_COARSE_ITEM", "coarse_aggregate", "100", "0");
        solid("AGGREGATE_FINE_ITEM", "fine_aggregate", "100", "0");
        solid("ASBESTOS_ITEM", "asbestos_fiber", "1", "0");
        STATES.put("DU_BILLET_ITEM", new PhysicalState("depleted_uranium", Phase.SOLID, Basis.MATERIAL_ITEM, MASS_UNITS_PER_BILLET(), BigDecimal.ZERO, "six ninth-kg nuggets per native billet", null));
        solid("CONCRETE_ITEM", "cured_concrete_parcel", "7.75", "0");
        solid("FIBER_CONCRETE_ITEM", "fiber_concrete_parcel", "7.78125", "0");
        STATES.put("DUCRETE_ITEM", new PhysicalState("heavy_concrete_parcel", Phase.SOLID, Basis.MATERIAL_ITEM, (248L * PhysicalState.MASS_UNITS_PER_KG + 2 * MASS_UNITS_PER_BILLET()) / 16, BigDecimal.ZERO, "sixteen weighed construction parcels", null));
        solid("SOIL_ITEM", "moist_mineral_soil", "13.125", "0");
        long roadMass = (get("AGGREGATE_COARSE_ITEM").massUnits + get("AGGREGATE_FINE_ITEM").massUnits + 12 * get("BITUMEN").massUnits) / 16;
        BigDecimal roadEnergy = BigDecimal.valueOf(12 * get("BITUMEN").massUnits).multiply(get("BITUMEN").lhvJoulesPerKg);
        STATES.put("ASPHALT_ITEM", new PhysicalState("bitumen_aggregate_road_parcel", Phase.SOLID, Basis.MATERIAL_ITEM, roadMass,
                roadEnergy.divide(BigDecimal.valueOf(roadMass * 16), 18, RoundingMode.DOWN), "cold installed parcel; 200 kg aggregate + 12 reference L bitumen per sixteen parcels", null));
        solid("SODIUM_SULFATE_ITEM", "sodium_sulfate", "1", "0");
        solid("SODIUM_SULFIDE_ITEM", "sodium_sulfide", "1", "11000000");
        // Legacy metal parcels remain explicit nominal amounts: sodium/potassium use 100 counts/kg,
        // salt uses 144 counts/kg. They are not literal liquid liters.
        STATES.put("SODIUM", new PhysicalState("sodium", Phase.LIQUID, Basis.NOMINAL, PhysicalState.MASS_UNITS_PER_KG / 100,
                new BigDecimal("10000000"), "400 C molten metal; 100 nominal counts/kg", null, new BigDecimal("500000")));
        STATES.put("POTASSIUM", new PhysicalState("potassium", Phase.LIQUID, Basis.NOMINAL, PhysicalState.MASS_UNITS_PER_KG / 100,
                new BigDecimal("6000000"), "400 C molten metal; 100 nominal counts/kg", null, new BigDecimal("500000")));
        STATES.put("MOLTEN_SALT", new PhysicalState("sodium_chloride", Phase.LIQUID, Basis.NOMINAL, PhysicalState.MASS_UNITS_PER_KG / 144,
                BigDecimal.ZERO, "800 C molten salt; 144 nominal counts/kg", null, new BigDecimal("1000000")));
        liquid("20_percent_potassium_chloride", "1.13", "0", "POTASSIUM_CHLORIDE");
        solid("POTASH_LYE_ITEM", "potassium_hydroxide", "1", "0");
        solid("POTASH_ITEM", "carbonate_salt_assay", "1", "0");
        solid("RUBIDIUM_SALT_ITEM", "rubidium_chloride", "1", "0");
        liquid("molten_potassium_hydroxide", "1.7", "0", "POTASSIUM_HYDROXIDE");
        PhysicalState koh = get("POTASSIUM_HYDROXIDE");
        STATES.put("POTASSIUM_HYDROXIDE", new PhysicalState(koh.substance, koh.phase, koh.basis, koh.massUnits, BigDecimal.ZERO,
                "400 C molten KOH; adopted 1.1 MJ/kg heating/dehydration", null, new BigDecimal("1100000")));
        solid("GADOLINIUM_ITEM", "gadolinium", "1", "0");
        solid("REFINED_GADOLINIUM_ITEM", "gadolinium", "1", "0");
        STATES.put("GADOLINIUM", new PhysicalState("gadolinium", Phase.LIQUID, Basis.LIQUID_LITER,
                new BigDecimal("7.4").multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).longValueExact(), BigDecimal.ZERO,
                "1400 C liquid; adopted density 7.4 kg/L, heat 0.8 MJ/kg above cold powder", null, new BigDecimal("800000")));
        long ru = PhysicalState.MASS_UNITS_PER_KG / 9;
        STATES.put("RUTHENIUM_NUGGET_ITEM", new PhysicalState("ruthenium", Phase.SOLID, Basis.MATERIAL_ITEM, ru, BigDecimal.ZERO, "1/9 kg local nugget assay", null));
        long oxide = BigDecimal.valueOf(ru).multiply(new BigDecimal("133.0688")).divide(new BigDecimal("101.07"), 0, RoundingMode.DOWN).longValueExact();
        STATES.put("RUTHENIUM_RESIDUE_ITEM", new PhysicalState("ruthenium_dioxide", Phase.SOLID, Basis.MATERIAL_ITEM, oxide, BigDecimal.ZERO, "one nugget-equivalent RuO2 concentrate", null));
        liquid("molten_sodium_hydroxide", "1.78", "0", "SODIUM_HYDROXIDE");
        PhysicalState lye = get("SODIUM_HYDROXIDE");
        STATES.put("SODIUM_HYDROXIDE", new PhysicalState(lye.substance, lye.phase, lye.basis, lye.massUnits, BigDecimal.ZERO,
                "400 C molten NaOH; adopted 1.2 MJ/kg heating/dehydration bill", null, new BigDecimal("1200000")));
        SpecialistProcesses.registerAssays();
        AncillaryProcesses.registerAssays();
        OrganicProcesses.registerAssays();
        UtilityMixtures.registerAssays();
        IrradiationLedger.registerAssays();
        CoolantLedger.registerStates();
    }
    /** Registers a previously uncalibrated process identity; existing released assays cannot be overwritten. */
    static void adopt(String name, PhysicalState state) {
        if(STATES.containsKey(name)) throw new IllegalArgumentException("Already adopted: " + name);
        STATES.put(name, state);
    }
    private static long MASS_UNITS_PER_BILLET() { return PhysicalState.MASS_UNITS_PER_KG * 2 / 3; }
    static void hotCoolant(String name, PhysicalState state) { STATES.put(name, state); }
    private PhysicalRegistry() { }
    private static void mixture(String name, String[] names, String[] fractions) {
        BigDecimal mass = BigDecimal.ZERO, energy = BigDecimal.ZERO;
        Map<String, BigDecimal> mix = new HashMap<String, BigDecimal>();
        for(int i = 0; i < names.length; i++) {
            PhysicalState component = get(names[i]); BigDecimal fraction = new BigDecimal(fractions[i]);
            BigDecimal part = BigDecimal.valueOf(component.massUnits).multiply(fraction);
            mass = mass.add(part); energy = energy.add(part.multiply(component.lhvJoulesPerKg)); mix.put(names[i], fraction);
        }
        STATES.put(name, new PhysicalState(name, Phase.GAS, Basis.STANDARD_GAS_LITER,
                // Repeating molar fractions cannot all be integral in a finite mass quantum; existing integral assays are unchanged.
                mass.setScale(0, RoundingMode.DOWN).longValueExact(),
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
