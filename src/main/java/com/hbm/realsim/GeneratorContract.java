package com.hbm.realsim;

/** Electrical quanta are 0.5 J; at 20 TPS a quantum/tick is 10 W. */
public final class GeneratorContract {
    private GeneratorContract() { }
    /** One tick on either side of the server's machine/network ordering. */
    public static long buffer(long watts) { return Math.max(1, watts / 5); }
    public static long room(long stored, long capacity) { return stored >= capacity ? 0 : capacity - Math.max(0, stored); }
    /** Does not clamp legacy over-cap stock. */
    public static long admit(long stored, long capacity, long generated) {
        return Math.addExact(stored, Math.min(Math.max(0, generated), room(stored, capacity)));
    }
    public static boolean acceptsSteam(SteamLedger.State state, SteamLedger.State maximum) {
        return state != null && state.ordinal() >= SteamLedger.State.ORDINARY.ordinal() && state.ordinal() <= maximum.ordinal();
    }
    public static int steamCapacity(int ordinaryUnits, SteamLedger.State state) {
        return state == null ? ordinaryUnits : (int) Math.max(1, (long) ordinaryUnits * SteamLedger.State.ORDINARY.massPerUnit / state.massPerUnit);
    }
    /** Client display adopts the owning server's maximum; save loading keeps the local admission rule. */
    public static void readTankStatus(com.hbm.inventory.fluid.tank.FluidTank tank, net.minecraft.nbt.NBTTagCompound data, String key) {
        tank.readFromNBT(data, key);
        if(data.hasKey(key + "_max")) tank.resizePreservingContents(data.getInteger(key + "_max"));
    }
    /** Celestial angle is zero at noon, one half at midnight, including native celestial providers. */
    public static long solarQuanta(float angle, float sunPower) {
        if(!Float.isFinite(angle) || !Float.isFinite(sunPower) || sunPower <= 0) return 0;
        double elevation = Math.max(0, Math.cos(angle * Math.PI * 2));
        return (long) (100 * elevation * elevation * sunPower);
    }
}
