package com.hbm.realsim;

import net.minecraft.nbt.NBTTagCompound;

/** Owned, already removed input. No debt, elapsed-time catch-up, or tank refund. */
public final class FuelCredit {
    public static final long MICROJOULES_PER_QUANTUM = 500_000L;
    private long usefulMicrojoules;
    private long chemicalPerUnit;
    private int efficiencyPpm;
    private String fuel = "";

    public boolean hasCredit() { return usefulMicrojoules >= MICROJOULES_PER_QUANTUM; }
    public long usefulMicrojoules() { return usefulMicrojoules; }
    public String fuel() { return fuel; }
    public int efficiencyPpm() { return efficiencyPpm; }
    public double remainingChemicalJoules() { return efficiencyPpm == 0 ? 0 : usefulMicrojoules / (double) efficiencyPpm; }
    public double unitsPerQuantum() { return chemicalPerUnit == 0 || efficiencyPpm == 0 ? 0 : MICROJOULES_PER_QUANTUM * 1_000_000D / chemicalPerUnit / efficiencyPpm; }

    /** Caller must remove the input before this method. Refuses overwriting spendable credit. */
    public void reserve(String fuel, long chemicalPerUnit, int units, int efficiencyPpm) {
        if(hasCredit() || chemicalPerUnit <= 0 || units <= 0 || efficiencyPpm <= 0 || efficiencyPpm > 1_000_000) throw new IllegalArgumentException("Invalid owned fuel reservation");
        long chemical = Math.multiplyExact(chemicalPerUnit, units);
        this.usefulMicrojoules = new CombustionLedger(chemical, efficiencyPpm, 0).usefulMicrojoules;
        this.chemicalPerUnit = chemicalPerUnit;
        this.efficiencyPpm = efficiencyPpm;
        this.fuel = fuel;
    }
    public long release(long requestedQuanta) {
        if(requestedQuanta <= 0) return 0;
        long output = Math.min(requestedQuanta, usefulMicrojoules / MICROJOULES_PER_QUANTUM);
        usefulMicrojoules -= output * MICROJOULES_PER_QUANTUM;
        return output;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", 1); tag.setString("fuel", fuel);
        tag.setLong("usefulMicroJ", usefulMicrojoules); tag.setLong("chemicalMicroJPerUnit", chemicalPerUnit); tag.setInteger("efficiencyPpm", efficiencyPpm);
        parent.setTag("realSimFuel", tag);
    }
    public void read(NBTTagCompound parent) {
        usefulMicrojoules = 0; chemicalPerUnit = 0; efficiencyPpm = 0; fuel = "";
        if(!parent.hasKey("realSimFuel", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("realSimFuel");
        if(tag.getInteger("version") != 1) throw new IllegalStateException("Unsupported RealSim fuel account version");
        usefulMicrojoules = tag.getLong("usefulMicroJ"); chemicalPerUnit = tag.getLong("chemicalMicroJPerUnit"); efficiencyPpm = tag.getInteger("efficiencyPpm"); fuel = tag.getString("fuel");
        if(usefulMicrojoules < 0 || chemicalPerUnit <= 0 || efficiencyPpm <= 0 || efficiencyPpm > 1_000_000) {
            usefulMicrojoules = 0; chemicalPerUnit = 0; efficiencyPpm = 0; fuel = "";
        }
    }
}
