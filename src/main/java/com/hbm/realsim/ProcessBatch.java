package com.hbm.realsim;

import net.minecraft.nbt.NBTTagCompound;

/** A paid feed reservation. No offline catch-up, refund, or recipe-dependent progress. */
public final class ProcessBatch {
    private ProcessRecipe owned;
    private int ticks;
    private long workPaid, heatPaid;
    private long lastTick = Long.MIN_VALUE;
    public final MassRemainder products = new MassRemainder();
    public boolean active() { return owned != null; }
    public ProcessRecipe recipe() { return owned; }
    public int ticks() { return ticks; }
    public boolean complete() { return active() && ticks == owned.duration; }
    /** Caller must atomically remove every fluid/solid reactant before this call. */
    public void reserve(ProcessRecipe recipe) {
        if(active() || recipe == null) throw new IllegalStateException("Cannot overwrite owned process");
        owned = recipe.snapshot(); ticks = 0; workPaid = 0; heatPaid = 0;
    }
    private long cumulative(long total, int t) { return total / owned.duration * t + total % owned.duration * t / owned.duration; }
    public long nextWork() { return !active() || complete() ? 0 : cumulative(owned.workQuanta, ticks + 1) - workPaid; }
    public long nextHeat() { return !active() || complete() ? 0 : cumulative(owned.heatQuanta, ticks + 1) - heatPaid; }
    public boolean canAdvance(long now, long work, long heat) {
        return active() && !complete() && now != lastTick && work >= nextWork() && heat >= nextHeat();
    }
    /** Both exact debits must be made by the owner before advancing. */
    public void advance(long now) {
        if(!active() || complete() || now == lastTick) throw new IllegalStateException("Invalid process advance");
        workPaid += nextWork(); heatPaid += nextHeat(); ticks++; lastTick = now;
    }
    /** Called after product capacity checks; input mass becomes species-isolated output ownership. */
    public int[] finish() {
        if(!complete() || workPaid != owned.workQuanta || heatPaid != owned.heatQuanta) throw new IllegalStateException("Unpaid process output");
        int[] result = new int[owned.outputs.length];
        for(int i = 0; i < result.length; i++) result[i] = products.convert(owned.outputs[i], owned.productMass[i]);
        owned = null; ticks = 0; workPaid = 0; heatPaid = 0; return result;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("version", 1); tag.setLong("lastTick", lastTick);
        tag.setInteger("ticks", ticks); tag.setLong("workPaid", workPaid); tag.setLong("heatPaid", heatPaid);
        if(active()) { NBTTagCompound recipe = new NBTTagCompound(); owned.write(recipe); tag.setTag("owned", recipe); }
        products.write(tag); parent.setTag("realSimProcess", tag);
    }
    public void read(NBTTagCompound parent) {
        owned = null; ticks = 0; workPaid = 0; heatPaid = 0; lastTick = Long.MIN_VALUE;
        NBTTagCompound tag = parent.getCompoundTag("realSimProcess"); products.read(tag);
        if(!parent.hasKey("realSimProcess", 10)) return;
        if(tag.getInteger("version") != 1) throw new IllegalStateException("Unsupported physical process version");
        lastTick = tag.getLong("lastTick");
        if(tag.hasKey("owned", 10)) {
            owned = ProcessRecipe.read(tag.getCompoundTag("owned")); ticks = tag.getInteger("ticks");
            workPaid = tag.getLong("workPaid"); heatPaid = tag.getLong("heatPaid");
            if(ticks < 0 || ticks > owned.duration || workPaid != cumulative(owned.workQuanta, ticks) || heatPaid != cumulative(owned.heatQuanta, ticks))
                throw new IllegalStateException("Invalid stored physical process credit");
        }
    }
}
