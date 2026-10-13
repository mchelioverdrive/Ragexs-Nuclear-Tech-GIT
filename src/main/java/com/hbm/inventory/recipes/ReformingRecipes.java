package com.hbm.inventory.recipes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.ProcessRecipe;

public class ReformingRecipes extends PetroleumRecipeLoader {
    private static final Map<String, ProcessRecipe> recipes = new LinkedHashMap<String, ProcessRecipe>();
    @Override protected Map<String, ProcessRecipe> bills() { return recipes; }
    @Override protected String machine() { return "reform"; }
    @Override public String getFileName() { return "hbmReforming.json"; }
    public static ProcessRecipe getPhysical(FluidType input) { return recipes.get(input.getName()); }
    public static HashMap getRecipes() { return display(recipes); }
    @Deprecated public static com.hbm.util.Tuple.Triplet<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> getOutput(FluidType input) {
        ProcessRecipe bill = getPhysical(input); if(bill == null) return null;
        return new com.hbm.util.Tuple.Triplet<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack>(preview(bill, 0, 100), preview(bill, 1, 100), preview(bill, 2, 100));
    }
}
