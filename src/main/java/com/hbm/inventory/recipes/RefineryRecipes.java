package com.hbm.inventory.recipes;

import java.util.Map;
import java.util.HashMap;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.loader.PetroleumRecipeLoader;
import com.hbm.realsim.PetroleumProcesses;
import com.hbm.realsim.ProcessRecipe;
import com.hbm.realsim.PhysicalRegistry;
import com.hbm.util.Tuple.Quintet;
import net.minecraft.item.ItemStack;

/** Atmospheric and vacuum bills, with a read-only legacy display adapter. */
public class RefineryRecipes {
    /** Historical volume-table constants retained for external source compatibility only. */
    @Deprecated public static final int oil_frac_heavy = 50;
    @Deprecated public static final int oil_frac_naph = 25;
    @Deprecated public static final int oil_frac_light = 15;
    @Deprecated public static final int oil_frac_petro = 10;
    @Deprecated public static final int crack_frac_naph = 40;
    @Deprecated public static final int crack_frac_light = 30;
    @Deprecated public static final int crack_frac_aroma = 15;
    @Deprecated public static final int crack_frac_unsat = 15;
    @Deprecated public static final int oilds_frac_heavy = 30;
    @Deprecated public static final int oilds_frac_naph = 35;
    @Deprecated public static final int oilds_frac_light = 20;
    @Deprecated public static final int oilds_frac_unsat = 15;
    @Deprecated public static final int crackds_frac_naph = 35;
    @Deprecated public static final int crackds_frac_light = 35;
    @Deprecated public static final int crackds_frac_aroma = 15;
    @Deprecated public static final int crackds_frac_unsat = 15;
    @Deprecated public static final int vac_frac_heavy = 40;
    @Deprecated public static final int vac_frac_reform = 25;
    @Deprecated public static final int vac_frac_light = 20;
    @Deprecated public static final int vac_frac_sour = 15;

    private static Map<String, ProcessRecipe> refinery = PetroleumProcesses.defaults("refinery");
    private static Map<String, ProcessRecipe> vacuum = PetroleumProcesses.defaults("vacuum");
    public static void registerRefinery() {
        refinery = PetroleumProcesses.defaults("refinery"); vacuum = PetroleumProcesses.defaults("vacuum");
    }
    public static ProcessRecipe getPhysical(FluidType type) { return refinery.get(type.getName()); }
    public static ProcessRecipe getPhysicalVacuum(FluidType type) { return vacuum.get(type.getName()); }
    public static HashMap getRefineryRecipe() {
        HashMap result = PetroleumRecipeLoader.display(refinery);
        HashMap padded = new HashMap();
        for(Object key : result.keySet()) {
            ItemStack[] out = (ItemStack[]) result.get(key); ItemStack[] five = new ItemStack[5];
            System.arraycopy(out, 0, five, 0, out.length); padded.put(key, five);
        }
        return padded;
    }
    public static HashMap getVacuumRecipe() { return PetroleumRecipeLoader.display(vacuum); }
    public static Quintet<FluidStack, FluidStack, FluidStack, FluidStack, ItemStack> getRefinery(FluidType type) {
        ProcessRecipe bill = getPhysical(type); if(bill == null) return null;
        FluidStack[] display = new FluidStack[4];
        for(int i = 0; i < 4; i++) display[i] = new FluidStack(Fluids.fromName(bill.outputs[i]),
                (int) (bill.productMass[i] * 100L / PhysicalRegistry.get(bill.outputs[i]).massUnits));
        return new Quintet<FluidStack, FluidStack, FluidStack, FluidStack, ItemStack>(display[0], display[1], display[2], display[3], null);
    }
    @Deprecated public static com.hbm.util.Tuple.Quartet<FluidStack, FluidStack, FluidStack, FluidStack> getVacuum(FluidType input) {
        ProcessRecipe bill = getPhysicalVacuum(input); if(bill == null) return null;
        return new com.hbm.util.Tuple.Quartet<FluidStack, FluidStack, FluidStack, FluidStack>(PetroleumRecipeLoader.preview(bill, 0, 100), PetroleumRecipeLoader.preview(bill, 1, 100), PetroleumRecipeLoader.preview(bill, 2, 100), PetroleumRecipeLoader.preview(bill, 3, 100));
    }
}
