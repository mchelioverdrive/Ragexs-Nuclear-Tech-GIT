package com.hbm.realsim;

/** Aggregate export/charging limit across all ports in one server tick. */
public final class OutputBudget {
    private long tick = Long.MIN_VALUE;
    private long used;
    public long available(long now, long ratingQuanta) {
        if(now != tick) { tick = now; used = 0; }
        return Math.max(0, ratingQuanta - used);
    }
    public void use(long now, long ratingQuanta, long amount) {
        if(amount < 0 || amount > available(now, ratingQuanta)) throw new IllegalArgumentException("Generator export exceeds nameplate");
        used += amount;
    }
    public void write(net.minecraft.nbt.NBTTagCompound tag) { tag.setLong("realSimExportTick", tick); tag.setLong("realSimExportUsed", used); }
    public void read(net.minecraft.nbt.NBTTagCompound tag) { tick = tag.hasKey("realSimExportTick") ? tag.getLong("realSimExportTick") : Long.MIN_VALUE; used = Math.max(0, tag.getLong("realSimExportUsed")); }
}
