package com.hbm.realsim;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

/** Pump nameplates, independent of storage. No aquifer or steam enthalpy simulation. */
public final class Groundwater {
    public static final long ELECTRIC_WATTS = 10_000;
    public static final long STEAM_SHAFT_WATTS = 2_000;
    public static final int ELECTRIC_BUFFER = 400;
    public static final int STEAM_BUFFER = 200;
    public static final double EFFICIENCY = 0.35;
    public static final double EFFECTIVE_HEAD_METERS = 100;
    /** 40% shaft efficiency on the common ordinary-steam expansion credit. */
    public static final long STEAM_PARCEL_SHAFT_JOULES = com.hbm.realsim.SteamLedger.expansionWork(com.hbm.realsim.SteamLedger.State.ORDINARY) * 2 / 5;
    private Groundwater() { }

    /** Only ordinary water tables use biome salinity; exotic planetary fluids retain their identity. */
    public static FluidType fluidForBiome(FluidType table, BiomeGenBase biome) {
        if(table == null || table == Fluids.NONE) return null;
        if(table != Fluids.SALT_WATER && table != Fluids.FRESH_WATER) return table;
        if(biome == null) return null; // unavailable is not evidence of an ocean
        return BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.OCEAN) ? Fluids.SALT_WATER : Fluids.FRESH_WATER;
    }

    /** The center column immediately below the pump core, never an outlet or a footprint majority. */
    public static FluidType fluidAt(World world, int x, int y, int z, FluidType table) {
        if(world == null || world.isRemote || !world.blockExists(x, y - 1, z)) return null;
        return fluidForBiome(table, table == Fluids.SALT_WATER || table == Fluids.FRESH_WATER ? world.getBiomeGenForCoords(x, z) : null);
    }

    public static long microlitersPerTick(long watts, double nameplateLitersPerSecond) {
        if(watts <= 0 || !Double.isFinite(nameplateLitersPerSecond) || nameplateLitersPerSecond <= 0) return 0;
        double hydraulicLitersPerSecond = watts * EFFICIENCY / (1000D * 9.81D * EFFECTIVE_HEAD_METERS) * 1000D;
        return (long) (Math.min(hydraulicLitersPerSecond, nameplateLitersPerSecond) * 1_000_000D / 20D);
    }
}
