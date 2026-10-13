package com.hbm.realsim;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe;
import com.hbm.util.Tuple.Pair;

/** Discrete conversion routes in the existing compressor. Work includes refrigeration/heating. */
public final class PhaseConversions {
    private PhaseConversions() { }
    public static void register() {
        pair(Fluids.HYDROGEN, Fluids.HYDROGEN_GAS, 4_000_000D, 36_000_000D);
        pair(Fluids.OXYGEN, Fluids.OXYGEN_GAS, 400_000D, 1_000_000D);
        pair(Fluids.NITROGEN, Fluids.NITROGEN_GAS, 400_000D, 1_000_000D);
    }
    private static void pair(FluidType liquid, FluidType gas, double warmingJoulesPerKg, double liquefactionJoulesPerKg) {
        conversion(liquid, 0, gas, 0, 1, warmingJoulesPerKg);
        // Local equipment state: these process gases alone define class 1 as 10 bar.
        conversion(gas, 0, gas, 1, 1000, 0D, 1000D * 100D * Math.log(10D) / 0.70D);
        int batch = (int) Math.ceil((double) PhysicalRegistry.get(liquid.getName()).massUnits / PhysicalRegistry.get(gas.getName()).massUnits);
        conversion(gas, 1, liquid, 0, batch, liquefactionJoulesPerKg);
        // The compressor's decompression mode vents class 1 to class 0;
        // no recovered shaft/electrical work is credited on decompression.
        CompressorRecipe vent = new CompressorRecipe(1000, new FluidStack(gas, 1000, 0), 1);
        vent.workQuanta = 1000; vent.massConversion = true;
        CompressorRecipes.decompressions.put(gas, vent);
    }
    private static void conversion(FluidType input, int pressure, FluidType output, int outputPressure, int batch, double joulesPerKg) {
        conversion(input, pressure, output, outputPressure, batch, joulesPerKg, 0D);
    }
    private static void conversion(FluidType input, int pressure, FluidType output, int outputPressure, int batch, double joulesPerKg, double workJoules) {
        long mass = Math.multiplyExact(PhysicalRegistry.get(input.getName()).massUnits, batch);
        int maxOutput = (int) (1L + mass / PhysicalRegistry.get(output.getName()).massUnits);
        long workQuanta = (long) Math.ceil(2D * (workJoules + mass / (double) PhysicalState.MASS_UNITS_PER_KG * joulesPerKg));
        CompressorRecipe recipe = new CompressorRecipe(batch, new FluidStack(output, maxOutput, outputPressure), 1);
        recipe.workQuanta = Math.max(1L, workQuanta); recipe.massConversion = true;
        CompressorRecipes.recipes.put(new Pair<FluidType, Integer>(input, pressure), recipe);
    }
}
