package com.hbm.realsim;

/** Pump nameplates, independent of storage. No aquifer or steam enthalpy simulation. */
public final class Groundwater {
    public static final long ELECTRIC_WATTS = 10_000;
    public static final long STEAM_SHAFT_WATTS = 2_000;
    public static final int ELECTRIC_BUFFER = 400;
    public static final int STEAM_BUFFER = 200;
    public static final double EFFICIENCY = 0.35;
    public static final double EFFECTIVE_HEAD_METERS = 100;
    /** Transitional pump-only work allowance per existing 100:1 steam/exhaust parcel. */
    public static final long STEAM_PARCEL_SHAFT_JOULES = 100_000;
    private Groundwater() { }

    public static long microlitersPerTick(long watts, double nameplateLitersPerSecond) {
        if(watts <= 0 || !Double.isFinite(nameplateLitersPerSecond) || nameplateLitersPerSecond <= 0) return 0;
        double hydraulicLitersPerSecond = watts * EFFICIENCY / (1000D * 9.81D * EFFECTIVE_HEAD_METERS) * 1000D;
        return (long) (Math.min(hydraulicLitersPerSecond, nameplateLitersPerSecond) * 1_000_000D / 20D);
    }
}
