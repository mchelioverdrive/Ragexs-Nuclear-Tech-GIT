package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

/** Frozen Phase 5C bills. Recipe loaders own the live maps; this class only constructs bills. */
public final class PetroleumProcesses {
    private PetroleumProcesses() { }
    public static Map<String, ProcessRecipe> defaults(String machine) {
        Map<String, ProcessRecipe> result = new LinkedHashMap<String, ProcessRecipe>();
        if(machine.equals("heat")) {
            for(String feed : new String[] {"OIL", "OIL_DS", "CRACKOIL", "CRACKOIL_DS"})
                put(result, separation(machine, feed, new String[] {"HOT" + feed}, new double[] {1}, 24, 0, 561000, 350));
        } else if(machine.equals("refinery")) {
            // Hot liquid already owns its 561 kJ/L preheat. The separate 163 kJ/L is vaporization,
            // not a second cold-to-350-C charge; auxiliaries remain mechanical electrical work.
            for(String feed : new String[] {"HOTOIL", "HOTOIL_DS"})
                put(result, separation(machine, feed, new String[] {"LIGHTOIL", "PETROLEUM", "HEAVYOIL", "NAPHTHA"}, new double[] {.35, .02}, 24, 36000, 163000, 350));
            for(String feed : new String[] {"HOTCRACKOIL", "HOTCRACKOIL_DS"})
                put(result, separation(machine, feed, new String[] {"NAPHTHA_CRACK", "LIGHTOIL_CRACK", "AROMATICS", "UNSATURATEDS"}, new double[] {.15, .25}, 24, 36000, 163000, 350));
        } else if(machine.equals("vacuum")) {
            put(result, separation(machine, "HEAVYOIL", new String[] {"HEAVYOIL_VACUUM", "SMEAR", "LIGHTOIL_VACUUM", "LUBRICANT"}, new double[] {.5, .25}, 12, 40000, 1360000, 400));
            // Old saved crude inlet class 2 is a feed boundary, not the vacuum vessel pressure.
            // Its products terminate in the same canonical assay; heat includes cold-feed heating.
            for(String feed : new String[] {"OIL", "OIL_DS"}) {
                ProcessRecipe cuts = defaults("refinery").get("HOT" + feed);
                put(result, new ProcessRecipe("vacuum:" + feed, new String[] {feed}, new int[] {1}, new int[] {2}, null, 0,
                        cuts.outputs, cuts.productMass, 100, joules(ProcessRecipe.resolved(feed).massUnits, 40000), 1000000, 400, true));
            }
        } else if(machine.equals("fraction")) {
            for(String feed : new String[] {"LIGHTOIL", "LIGHTOIL_DS", "LIGHTOIL_CRACK", "LIGHTOIL_VACUUM"})
                put(result, separation(machine, feed, new String[] {feed.equals("LIGHTOIL_CRACK") ? "DIESEL_CRACK" : "DIESEL", "KEROSENE"}, new double[] {}, 12, 5000, 440000, 200));
            for(String feed : new String[] {"HEAVYOIL", "HEAVYOIL_VACUUM"})
                put(result, separation(machine, feed, new String[] {feed.equals("HEAVYOIL") ? "HEATINGOIL" : "HEATINGOIL_VACUUM", "BITUMEN"}, new double[] {}, 12, 5000, 740000, 350));
            put(result, separation(machine, "SMEAR", new String[] {"LUBRICANT", "BITUMEN"}, new double[] {}, 12, 5000, 640000, 300));
            put(result, separation(machine, "OIL_COKER", new String[] {"HEATINGOIL"}, new double[] {1}, 12, 5000, 440000, 200));
            put(result, separation(machine, "NAPHTHA_COKER", new String[] {"NAPHTHA_CRACK"}, new double[] {1}, 12, 5000, 440000, 200));
        } else if(machine.equals("crack")) {
            for(String feed : new String[] {"OIL", "OIL_DS", "NAPHTHA", "NAPHTHA_DS", "NAPHTHA_COKER"})
                put(result, conversion(machine, feed, new String[] {feed.startsWith("NAPHTHA") ? "NAPHTHA_CRACK" : feed.equals("OIL_DS") ? "CRACKOIL_DS" : "CRACKOIL", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.85, .10, .05}, 12, 50000, 2000000, 500));
            for(String feed : new String[] {"HEAVYOIL", "HEAVYOIL_VACUUM", "BITUMEN", "SMEAR", "RECLAIMED"})
                put(result, conversion(machine, feed, new String[] {"CRACKOIL", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.7, .05, .25}, 12, 50000, 2000000, 500));
            for(String feed : new String[] {"DIESEL", "DIESEL_CRACK", "DIESEL_REFORM", "DIESEL_CRACK_REFORM", "HEATINGOIL", "HEATINGOIL_VACUUM"})
                put(result, conversion(machine, feed, new String[] {"KEROSENE", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.8, .15, .05}, 12, 50000, 2000000, 500));
            put(result, conversion(machine, "KEROSENE", new String[] {"UNSATURATEDS", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.8, .1, .1}, 12, 50000, 3100000, 500));
            put(result, conversion(machine, "XYLENE", new String[] {"AROMATICS", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.7, .05, .25}, 12, 50000, 2000000, 500));
            put(result, conversion(machine, "REFORMATE", new String[] {"UNSATURATEDS", "REFORMGAS", "PETCOKE_ITEM"}, new double[] {.7, .05, .25}, 12, 50000, 2000000, 500));
            put(result, conversion(machine, "LPG", new String[] {"UNSATURATEDS", "PETROLEUM", "PETCOKE_ITEM"}, new double[] {.8, .15, .05}, 12, 50000, 2000000, 500));
        } else if(machine.equals("reform")) {
            for(String feed : new String[] {"NAPHTHA", "NAPHTHA_DS", "NAPHTHA_CRACK", "NAPHTHA_COKER"})
                put(result, conversion(machine, feed, new String[] {"REFORMATE", "REFORMGAS", "HYDROGEN_GAS"}, new double[] {.94, .045, .015}, 12, 100000, 2600000, 500));
        } else if(machine.equals("hydro")) {
            String[] feeds = {"OIL", "CRACKOIL", "NAPHTHA", "DIESEL_CRACK", "DIESEL_CRACK_REFORM", "COALOIL"};
            String[] products = {"OIL_DS", "CRACKOIL_DS", "NAPHTHA_DS", "DIESEL", "DIESEL_REFORM", "OIL_DS"};
            for(int i = 0; i < feeds.length; i++) {
                long feedMass = ProcessRecipe.resolved(feeds[i]).massUnits;
                int hydrogen = Math.toIntExact(ceilDiv(feedMass / 200, ProcessRecipe.resolved("HYDROGEN_GAS").massUnits));
                long total = feedMass + hydrogen * ProcessRecipe.resolved("HYDROGEN_GAS").massUnits;
                put(result, new ProcessRecipe(machine + ":" + feeds[i], new String[] {feeds[i], "HYDROGEN_GAS"}, new int[] {1, hydrogen}, new int[] {0, 1}, null, 0,
                        new String[] {products[i], "SOURGAS"}, split(total, new double[] {.98, .02}), 100, joules(feedMass, 100000), joules(feedMass, 400000), 350, false));
            }
        } else if(machine.equals("alkyl")) {
            long total = ProcessRecipe.resolved("UNSATURATEDS").massUnits * 1000L + ProcessRecipe.resolved("LPG").massUnits;
            put(result, new ProcessRecipe("alkyl:UNSATURATEDS", new String[] {"UNSATURATEDS", "LPG"}, new int[] {1000, 1}, new int[] {0, 0}, null, 0,
                    new String[] {"REFORMATE", "REFORMGAS"}, split(total, new double[] {.98, .02}), 400, 50000, 100000, 100, false));
        } else if(machine.equals("coker")) {
            for(String feed : new String[] {"HEAVYOIL", "HEAVYOIL_VACUUM", "SMEAR", "BITUMEN"})
                put(result, conversion(machine, feed, new String[] {"OIL_COKER", "GAS_COKER", "PETCOKE_ITEM"}, new double[] {.75, .05, .20}, 6, 20000, 3300000, 500));
            put(result, conversion(machine, "RECLAIMED", new String[] {"NAPHTHA_COKER", "GAS_COKER", "WATER", "PETCOKE_ITEM"}, new double[] {.7, .03, .05, .22}, 6, 20000, 3300000, 500));
            put(result, conversion(machine, "COALCREOSOTE", new String[] {"NAPHTHA_COKER", "GAS_COKER", "WATER", "PETCOKE_ITEM"}, new double[] {.4, .05, .35, .20}, 6, 20000, 3300000, 500));
        }
        return result;
    }
    private static void put(Map<String, ProcessRecipe> map, ProcessRecipe recipe) {
        map.put(recipe.inputs[0], forEquipment(recipe.id.substring(0, recipe.id.indexOf(':')), recipe));
    }
    /** These powered vessels have no heater contact. Preserve feed enthalpy and pay only their added duty electrically. */
    public static ProcessRecipe forEquipment(String machine, ProcessRecipe recipe) {
        return machine.equals("refinery") || machine.equals("vacuum") || machine.equals("fraction") || machine.equals("crack")
                || machine.equals("reform") || machine.equals("hydro") || machine.equals("alkyl") ? recipe.electricallyDriven() : recipe;
    }
    /** Compressed propane deasphalting and solvent recovery: recover base oil and reject asphaltic bottoms.
     * The existing lubricant identity represents plain mineral base oil, not an additive package.
     * Mass and chemical energy use the same assay solver as the refinery/vacuum cuts.
     */
    public static ProcessRecipe lubricantBaseOil() {
        ProcessRecipe cuts = separation("chem_base_oil", "HEAVYOIL", new String[] {"HEATINGOIL", "LUBRICANT", "BITUMEN"},
                new double[] {.10}, 6, 40000, 1360000, 350);
        // LPG uses the existing propane assay. Solvent circulates; it is returned, not turned into extra oil.
        return new ProcessRecipe(cuts.id, new String[] {"HEAVYOIL", "LPG"}, new int[] {1, 4}, new int[] {0, 0}, null, 0,
                new String[] {"HEATINGOIL", "LUBRICANT", "BITUMEN", "LPG"},
                new long[] {cuts.productMass[0], cuts.productMass[1], cuts.productMass[2], ProcessRecipe.resolved("LPG").massUnits * 4L},
                400, cuts.workQuanta / 2, cuts.heatQuanta / 2, cuts.temperatureC, true);
    }
    public static long ceilDiv(long a, long b) { return a == 0 ? 0 : 1 + (a - 1) / b; }
    public static long joules(long mass, long joulesPerKg) {
        return BigDecimal.valueOf(mass).multiply(BigDecimal.valueOf(joulesPerKg)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 0, RoundingMode.CEILING).longValueExact();
    }
    public static long[] split(long total, double[] fractions) {
        long[] mass = new long[fractions.length]; long used = 0;
        for(int i = 0; i < mass.length - 1; i++) { mass[i] = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(fractions[i])).longValue(); used = Math.addExact(used, mass[i]); }
        mass[mass.length - 1] = total - used; return mass;
    }
    private static ProcessRecipe conversion(String machine, String feed, String[] products, double[] fractions, int litersPerMinute, long workPerKg, long heatPerKg, int temp) {
        long mass = ProcessRecipe.resolved(feed).massUnits;
        return new ProcessRecipe(machine + ":" + feed, new String[] {feed}, new int[] {1}, new int[] {0}, null, 0,
                products, split(mass, fractions), 1200 / litersPerMinute, joules(mass, workPerKg), joules(mass, heatPerKg), temp, false);
    }
    private static ProcessRecipe separation(String machine, String feed, String[] products, double[] fixed, int litersPerMinute, long work, long heat, int temp) {
        long total = ProcessRecipe.resolved(feed).massUnits, left = total;
        long[] mass = new long[products.length]; BigDecimal energy = ProcessRecipe.energy(feed, total);
        if(products.length == 1) mass[0] = total;
        else {
            for(int i = 0; i < products.length - 2; i++) {
                mass[i] = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(fixed[i])).longValueExact(); left -= mass[i];
                energy = energy.subtract(ProcessRecipe.energy(products[i], mass[i]));
            }
            int a = products.length - 2, b = a + 1;
            BigDecimal la = ProcessRecipe.resolved(products[a]).lhvJoulesPerKg, lb = ProcessRecipe.resolved(products[b]).lhvJoulesPerKg;
            mass[a] = energy.multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).subtract(BigDecimal.valueOf(left).multiply(lb))
                    .divide(la.subtract(lb), 0, RoundingMode.HALF_UP).longValueExact();
            mass[b] = left - mass[a];
        }
        boolean byLiter = machine.equals("refinery") || machine.equals("heat");
        if(machine.equals("refinery") && feed.startsWith("HOTOIL")) {
            products = new String[] {products[2], feed.endsWith("_DS") ? "NAPHTHA_DS" : products[3], feed.endsWith("_DS") ? "LIGHTOIL_DS" : products[0], products[1]};
            mass = new long[] {mass[2], mass[3], mass[0], mass[1]};
        }
        return new ProcessRecipe(machine + ":" + feed, new String[] {feed}, new int[] {1}, new int[] {0}, null, 0,
                products, mass, 1200 / litersPerMinute, byLiter ? work : joules(total, work), byLiter ? heat : joules(total, heat), temp, true);
    }
    /** Explicit physical JSON replaces stock bills; old volume fields cannot silently override mass yields. */
    public static ProcessRecipe read(JsonObject obj) {
        if(!obj.has("physical")) throw new IllegalArgumentException("Phase 5C petroleum JSON requires a physical bill; regenerate defaults or supply massUnits, utilities and duration");
        JsonObject bill = obj.getAsJsonObject("physical"); NbtJson tag = new NbtJson(bill);
        return ProcessRecipe.read(tag.nbt);
    }
    public static void write(ProcessRecipe recipe, JsonWriter writer) throws IOException {
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound(); recipe.write(tag);
        writer.name("physical").beginObject();
        for(Object entry : tag.func_150296_c()) {
            String key = (String) entry; writer.name(key);
            byte type = tag.getTag(key).getId();
            if(type == 8) writer.value(tag.getString(key)); else if(type == 1) writer.value(tag.getBoolean(key)); else writer.value(tag.getLong(key));
        }
        writer.endObject();
    }
    private static final class NbtJson {
        final net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        NbtJson(JsonObject obj) {
            for(Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey(); com.google.gson.JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
                if(value.isString()) nbt.setString(key, value.getAsString());
                else if(value.isBoolean()) nbt.setBoolean(key, value.getAsBoolean()); else nbt.setLong(key, value.getAsLong());
            }
        }
    }
}
