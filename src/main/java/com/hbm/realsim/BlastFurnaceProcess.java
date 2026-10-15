package com.hbm.realsim;

import net.minecraft.item.ItemStack;

/** Coke-fired shaft furnace: paid heat, retained fuel and modest top-gas recovery; refining remains abstract. */
public final class BlastFurnaceProcess {
    private BlastFurnaceProcess() { }
    public static String fuelIdentity(ItemStack stack) {
        if(stack == null || stack.stackSize <= 0 || stack.getItem().hasContainerItem(stack)) return null;
        SolidFuel fuel = SolidFuel.resolve(stack);
        // Shaft burden/top-gas bills use coal/coal coke only, one item, not storage blocks.
        return fuel != null && fuel.units == 1 && fuel.has(MaterialRole.REDUCTANT)
                && (fuel.identity.equals("COKE_ITEM") || fuel.identity.equals("COAL_ITEM")) ? fuel.identity : null;
    }
    public static int efficiency(String identity) { return identity.equals("COKE_ITEM") ? 550000 : 300000; }
    public static int gasRecovery(String identity) { return identity.equals("COKE_ITEM") ? 2500 : 1250; }
    // cp = 1 kJ/(kg K), dry-air assay 1.281255 kg per 1000 standard L.
    public static long blastJoulesPerLiter(String identity) {
        int delta = identity.equals("AIRBLAST") ? 780 : identity.equals("AIR_HEATED") ? 580 : 0;
        return SteamLedger.mulDiv(PhysicalRegistry.get("AIR").massUnits, delta * 1000L, PhysicalState.MASS_UNITS_PER_KG);
    }
    public static long ratedWatts(boolean coke, String blast, boolean enriched) {
        if(!coke) return blast.equals("AIR") ? 1500000 : 2000000;
        long watts = blast.equals("AIRBLAST") ? 8000000 : blast.equals("AIR_HEATED") ? 4000000 : 2000000;
        return enriched ? watts * 5 / 4 : watts;
    }
}
