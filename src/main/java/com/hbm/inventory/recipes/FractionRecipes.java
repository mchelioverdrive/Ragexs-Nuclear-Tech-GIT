package com.hbm.inventory.recipes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.ProcessRecipe;

/** All active fractionator routes use the shared paid, mass-accounted process bill. */
public class FractionRecipes extends PetroleumRecipeLoader {
    private static final Map<String,ProcessRecipe> recipes=new LinkedHashMap<String,ProcessRecipe>();
    @Override protected Map<String,ProcessRecipe> bills() { return recipes; }
    @Override protected String machine() { return "fraction"; }
    @Override public String getFileName() { return "hbmFractions.json"; }
    public static ProcessRecipe getPhysical(FluidType input) { return recipes.get(input.getName()); }
    @Override public void registerDefaults() { super.registerDefaults(); recipes.put("CHLOROCALCITE_MIX",com.hbm.realsim.ChemistryProcesses.chlorocalciteClarification()); }
    /** Compatibility query for removed volume-only recipes. */
    @Deprecated public static com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack,com.hbm.inventory.FluidStack> getNominal(FluidType input) { return null; }
    public static HashMap getFractionRecipesForNEI() { return display(recipes); }
    @Deprecated public static com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack,com.hbm.inventory.FluidStack> getFractions(FluidType input) {
        ProcessRecipe bill=getPhysical(input);return bill == null ? null:new com.hbm.util.Tuple.Pair<com.hbm.inventory.FluidStack,com.hbm.inventory.FluidStack>(preview(bill,0,100),preview(bill,1,100));
    }
}
