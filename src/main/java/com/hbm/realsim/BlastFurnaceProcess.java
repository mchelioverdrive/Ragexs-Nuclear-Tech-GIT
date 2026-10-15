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
    /** Reduce work until both stored/process heat and this step's hot blast can pay it. */
    public static long heatLimitedStep(long required, long paid, int totalAir, int airPaid,
            long proposed, long availableJoules, long blastJoulesPerLiter) {
        long step = proposed;
        while(step > 0) {
            long air = SteamLedger.ceilDiv((paid + step) * totalAir, required) - airPaid;
            long supply = Math.max(0, availableJoules) + Math.max(0, air) * blastJoulesPerLiter;
            if(supply >= step) return step;
            step = supply;
        }
        return 0;
    }
    /** Clip paid heating to the available blast parcel; oxygen enriches that same parcel. */
    public static long availableHeatStep(long required, long paid, int totalAir, int airPaid, int oxygenPaid,
            int availableAir, int availableOxygen, boolean enriched, long ceiling) {
        if(required <= paid || totalAir <= 0 || ceiling <= 0) return 0;
        long airLimit = Math.min((long) totalAir, (long) airPaid + Math.max(0, availableAir));
        if(enriched) airLimit = Math.min(airLimit, ((long) oxygenPaid + Math.max(0, availableOxygen)) * 10L);
        long supportedHeat = airLimit * required / totalAir;
        return Math.max(0L, Math.min(ceiling, Math.min(required - paid, supportedHeat - paid)));
    }
}
