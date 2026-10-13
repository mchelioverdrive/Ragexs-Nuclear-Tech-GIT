package com.hbm.inventory.recipes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.ProcessRecipe;

public class CokerRecipes extends PetroleumRecipeLoader {
    private static final Map<String, ProcessRecipe> recipes = new LinkedHashMap<String, ProcessRecipe>();
    @Override protected Map<String, ProcessRecipe> bills() { return recipes; }
    @Override protected String machine() { return "coker"; }
    @Override public String getFileName() { return "hbmCoker.json"; }
    public static ProcessRecipe getPhysical(FluidType input) { return recipes.get(input.getName()); }
    public static HashMap getRecipes() { return display(recipes); }
    @Deprecated public static com.hbm.util.Tuple.Triplet<Integer, net.minecraft.item.ItemStack, com.hbm.inventory.FluidStack> getOutput(FluidType input) {
        ProcessRecipe bill = getPhysical(input); if(bill == null) return null;
        int coke = (int) (bill.productMass[bill.productMass.length - 1] * 100L / com.hbm.realsim.PhysicalRegistry.get("PETCOKE_ITEM").massUnits);
        return new com.hbm.util.Tuple.Triplet<Integer, net.minecraft.item.ItemStack, com.hbm.inventory.FluidStack>(100, com.hbm.realsim.ProcessMachine.solid("PETCOKE_ITEM", coke), preview(bill, 0, 100));
    }
}
