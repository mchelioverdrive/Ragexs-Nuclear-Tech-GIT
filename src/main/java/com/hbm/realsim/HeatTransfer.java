package com.hbm.realsim;

import net.minecraft.nbt.NBTTagCompound;

/** Fixed-point transferred heat; source debit and explicit rejection, with no double accumulation. */
public final class HeatTransfer {
    private long remainder;
    public long rejectedMicroJ;
    public static long remainingAllowance(long watts, long deliveredQuanta) {
        if(watts < 0 || deliveredQuanta < 0) throw new IllegalArgumentException("Invalid heat rating");
        return Math.max(0, watts / 10 - deliveredQuanta);
    }
    public long accept(long inputQuanta, int efficiencyPpm) {
        if(inputQuanta < 0 || efficiencyPpm < 0 || efficiencyPpm > 1000000) throw new IllegalArgumentException("Invalid heat transfer");
        long useful = SteamLedger.mulDiv(Math.multiplyExact(inputQuanta, 500000L), efficiencyPpm, 1000000);
        rejectedMicroJ = Math.multiplyExact(inputQuanta, 500000L) - useful;
        remainder = Math.addExact(remainder, useful);
        long output = remainder / 500000L; remainder %= 500000L;
        return output;
    }
    public void write(NBTTagCompound parent) { parent.setInteger("realSimHeatTransferVersion", 1); parent.setLong("realSimHeatTransferRemainder", remainder); }
    public void read(NBTTagCompound parent) {
        remainder = parent.getLong("realSimHeatTransferRemainder");
        if(remainder < 0 || remainder >= 500000 || parent.hasKey("realSimHeatTransferVersion") && parent.getInteger("realSimHeatTransferVersion") != 1) throw new IllegalStateException("Invalid heat-transfer ownership");
    }
}
