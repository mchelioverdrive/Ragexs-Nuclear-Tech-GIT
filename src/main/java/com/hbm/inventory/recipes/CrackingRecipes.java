package com.hbm.inventory.recipes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.ProcessRecipe;

public class CrackingRecipes extends PetroleumRecipeLoader {
    /** Historical volume-table constants retained for external source compatibility only. */
    @Deprecated public static final int oil_crack_oil = 80;
    @Deprecated public static final int oil_crack_petro = 20;
    @Deprecated public static final int bitumen_crack_oil = 80;
    @Deprecated public static final int bitumen_crack_aroma = 20;
    @Deprecated public static final int smear_crack_napht = 60;
    @Deprecated public static final int smear_crack_petro = 40;
    @Deprecated public static final int gas_crack_petro = 30;
    @Deprecated public static final int gas_crack_unsat = 20;
    @Deprecated public static final int diesel_crack_kero = 40;
    @Deprecated public static final int diesel_crack_petro = 30;
    @Deprecated public static final int kero_crack_petro = 60;
    @Deprecated public static final int wood_crack_aroma = 10;
    @Deprecated public static final int wood_crack_heat = 40;
    @Deprecated public static final int tekto_crack_atmo = 80;
    @Deprecated public static final int tekto_crack_hydride = 20;
    @Deprecated public static final int xyl_crack_aroma = 80;
    @Deprecated public static final int xyl_crack_petro = 20;

    private static final Map<String, ProcessRecipe> recipes = new LinkedHashMap<String, ProcessRecipe>();
    @Override protected Map<String, ProcessRecipe> bills() { return recipes; }
    @Override protected String machine() { return "crack"; }
    @Override public String getFileName() { return "hbmCracking.json"; }
    public static ProcessRecipe getPhysical(FluidType input) { return recipes.get(input.getName()); }
    public static HashMap getCrackingRecipesForNEI() { return display(recipes); }
    @Deprecated public static com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> getCracking(FluidType input) {
        ProcessRecipe bill = getPhysical(input); if(bill == null) return null;
        return new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(preview(bill, 0, 100), preview(bill, 1, 100));
    }
}
