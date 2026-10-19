package com.hbm.realsim;

/** Adopted pre-nuclear equipment envelopes, independent of storage capacity. */
public final class IndustrialDemand {
    public static final long FURNACE_W = 8000, PRESS_W = 2000, FABRICATION_W = 3000;
    public static final long CHEMICAL_W = 4000, CRUSHER_W = 3000;
    public static final long MINING_LASER_W = 500000, EXCAVATOR_W = 250000;
    public static final long COOLING_AUXILIARY_W = 500;
    public static final int COOLING_CHARGE_L = 20;
    /** Gameplay reactor envelope; physical recipe work remains payable in full. */
    public static final long CHEMICAL_PROCESS_CEILING_W = 2_000_000L;
    public static final long CHEMICAL_COOLING_W = 5_000_000L;
    public static final long CHEMICAL_BUFFER_QUANTA = 4_000_000L;
    public static final long CHEMICAL_GAS_L_PER_MINUTE = 24_000L;
    public static final long ARC_WORK_J_PER_KG = 1800000;
    public static final long ARC_CEILING_W = 500000;
    private IndustrialDemand() { }

    /** Constant work per job; efficiency upgrades save 10% per level, rather than 90%. */
    public static long atSpeed(long baseWatts, int durationPercent, int efficiencyLevel) {
        long watts = baseWatts * (100L - 10L * Math.max(0, Math.min(3, efficiencyLevel))) / Math.max(1, durationPercent);
        return Math.max(10, ((watts + 9) / 10) * 10);
    }

    /** Generic chemistry keeps broad utility families; released physical bills bypass this approximation. */
    public static long chemicalBaseWatts(String name) {
        if(name.contains("FERMENT") || name.contains("BIOGAS")) return 1000;
        if(name.contains("MIX") || name.contains("DILUT") || name.contains("NEUTRAL")) return 2000;
        if(name.contains("POLYMER") || name.contains("RUBBER") || name.contains("PLASTIC")) return 10000;
        return CHEMICAL_W;
    }

    /** Adopted 1 kg/ingot-equivalent work convention; exact electrical work, independent of upgrades. */
    public static long arcWorkQuanta(long batchMilligrams) {
        return Math.multiplyExact(Math.max(0L, batchMilligrams), ARC_WORK_J_PER_KG * 2L) / 1000000L;
    }
    public static long arcRatedWatts(int upgrade) {
        return ARC_CEILING_W * (Math.min(3, Math.max(0, upgrade)) * 2L + 1L);
    }

    /** Minimum small-loop allowance; high-duty circulation/fans rise with rejected process heat. */
    public static long coolingAuxiliaryWatts(long processRejectedWatts) {
        long heat = Math.max(0L, processRejectedWatts);
        long watts = Math.max(COOLING_AUXILIARY_W, heat / 50L + (heat % 50L == 0L ? 0L : 1L));
        return ((watts + 9L) / 10L) * 10L;
    }

    /** Internal sealed-loop circulation, 20 K rise, cp = 4.18 kJ/(kg K); zero makeup/evaporation. */
    public static double circulationLitersPerMinute(long rejectedWatts) {
        return Math.max(0, rejectedWatts) * 60D / 83600D;
    }
}
