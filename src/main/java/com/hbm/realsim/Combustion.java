package com.hbm.realsim;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.util.ItemStackUtil;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Chemical input is mass x LHV. Traits select equipment suitability, never chemical energy. */
public final class Combustion {
    public static final int FIREBOX_EFFICIENCY_PPM = 850_000;
    private Combustion() { }
    public static boolean isBurnerFuel(FluidType type) {
        return type.hasTrait(FT_Flammable.class) && PhysicalRegistry.get(type.getName()).isResolved()
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
        return isBurnerFuel(type) && type.hasTrait(FT_Combustible.class) && !type.getName().equals("HYDRAZINE") && !type.getName().equals("HYDROGEN");
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
        if(stack == null) return null;
        if(stack.getItem() == Items.coal) return stack.getItemDamage() == 1 ? "CHARCOAL_ITEM" : "COAL_ITEM";
        if(stack.getItem() == Item.getItemFromBlock(Blocks.coal_block)) return "COAL_ITEM";
        if(stack.getItem() == Items.stick) return "WOOD_STICK";
        for(String name : ItemStackUtil.getOreDictNames(stack)) {
            if(name.equals("gemCoke") || name.equals("fuelCoke") || name.equals("dustCoke")) return "COKE_ITEM";
            if(name.equals("gemPetroleumCoke") || name.equals("dustPetroleumCoke")) return "PETCOKE_ITEM";
            if(name.equals("gemCoal") || name.equals("dustCoal")) return "COAL_ITEM";
            if(name.equals("gemCharcoal") || name.equals("dustCharcoal")) return "CHARCOAL_ITEM";
            if(name.equals("logWood")) return "WOOD_LOG";
            if(name.equals("plankWood")) return "WOOD_PLANK";
        }
        return null;
    }
    public static long solidChemicalMicrojoules(ItemStack stack) {
        String identity = solidIdentity(stack);
        if(identity == null) return 0;
        long energy = PhysicalRegistry.get(identity).chemicalMicrojoulesPerUnit();
        return stack.getItem() == Item.getItemFromBlock(Blocks.coal_block) ? energy * 9 : energy;
    }
}
