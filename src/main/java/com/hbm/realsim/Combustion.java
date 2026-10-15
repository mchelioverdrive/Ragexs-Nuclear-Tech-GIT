package com.hbm.realsim;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import net.minecraft.item.ItemStack;

/** Chemical input is mass x LHV. Traits select equipment suitability, never chemical energy. */
public final class Combustion {
    public static final int FIREBOX_EFFICIENCY_PPM = 850_000;
    private Combustion() { }
    public static boolean isBurnerFuel(FluidType type) {
        return type != null && FuelSuitability.has(type.getName(), MaterialRole.HEATING_FUEL) && type.hasTrait(FT_Flammable.class) && PhysicalRegistry.get(type.getName()).isResolved()
                && PhysicalRegistry.get(type.getName()).chemicalMicrojoulesPerUnit() > 0;
    }
    public static int dieselEfficiency(FluidType type) {
        if(!isBurnerFuel(type) || !type.hasTrait(FT_Combustible.class)) return 0;
        String name = type.getName();
        // Compression-ignition distillates; the historical HIGH grade also includes spark fuels.
        if(name.equals("DIESEL") || name.equals("DIESEL_CRACK")) return 320_000;
        if(name.equals("DIESEL_REFORM") || name.equals("DIESEL_CRACK_REFORM")) return 300_000;
        if(name.equals("PETROIL")) return 270_000;
        return 0;
    }
    public static boolean isReciprocatingFuel(FluidType type) {
        return isBurnerFuel(type) && type.hasTrait(FT_Combustible.class) && FuelSuitability.has(type.getName(), MaterialRole.RECIPROCATING_FUEL);
    }
    public static long generate(FluidTank tank, FuelCredit account, long request, int efficiencyPpm) {
        if(request <= 0) return 0;
        long output = account.release(request);
        long left = request - output;
        if(left <= 0 || efficiencyPpm <= 0 || efficiencyPpm > 1_000_000 || tank.getFill() <= 0 || !isBurnerFuel(tank.getTankType())) return output;
        long chemical = PhysicalRegistry.get(tank.getTankType().getName()).chemicalMicrojoulesPerUnit();
        long perUnit = java.math.BigInteger.valueOf(chemical).multiply(java.math.BigInteger.valueOf(efficiencyPpm)).divide(java.math.BigInteger.valueOf(1_000_000L)).longValueExact();
        if(perUnit < FuelCredit.MICROJOULES_PER_QUANTUM) return output;
        long needed = Math.multiplyExact(left, FuelCredit.MICROJOULES_PER_QUANTUM);
        int units = (int) Math.min(tank.getFill(), 1L + (needed - 1L) / perUnit);
        Math.multiplyExact(chemical, units); // Reject overflow before removing input.
        tank.setFill(tank.getFill() - units);
        account.reserve(tank.getTankType().getName(), chemical, units, efficiencyPpm);
        return output + account.release(left);
    }
    public static String solidIdentity(ItemStack stack) {
        SolidFuel fuel = SolidFuel.resolve(stack);
        return fuel != null && fuel.has(MaterialRole.HEATING_FUEL) ? fuel.identity : null;
    }
    public static long solidChemicalMicrojoules(ItemStack stack) {
        SolidFuel fuel = SolidFuel.resolve(stack);
        return fuel != null && fuel.has(MaterialRole.HEATING_FUEL) ? fuel.chemicalMicrojoules() : 0;
    }
    /** Compatibility work/burn ticks, normalized to coal; these ticks are never joules. */
    public static int legacySolidTicks(ItemStack stack, int coalTicks) {
        long chemical = solidChemicalMicrojoules(stack);
        if(chemical <= 0 || coalTicks <= 0) return 0;
        return java.math.BigInteger.valueOf(chemical).multiply(java.math.BigInteger.valueOf(coalTicks))
                .divide(java.math.BigInteger.valueOf(PhysicalRegistry.get("COAL_ITEM").chemicalMicrojoulesPerUnit()))
                .min(java.math.BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
    }
}
