package com.hbm.realsim;

import net.minecraft.nbt.NBTTagCompound;

/** A paid feed reservation. No offline catch-up, refund, or recipe-dependent progress. */
public final class ProcessBatch {
    private ProcessRecipe owned;
    private int ticks, releasedTicks;
    private long workPaid, heatPaid;
    private int workRoundingDebt;
    private long lastTick = Long.MIN_VALUE;
    private long observedDrawTick = Long.MIN_VALUE, observedDrawQuanta;
    public final MassRemainder products = new MassRemainder();
    /** Instrumentation only: no saved credit and no offline/repeated-tick power claim. */
    public void recordDraw(long tick, long debitedQuanta) { observedDrawTick = tick; observedDrawQuanta = debitedQuanta; }
    public long currentDrawWatts(long tick) { return tick == observedDrawTick ? Math.multiplyExact(observedDrawQuanta, 10L) : 0L; }
    public boolean active() { return owned != null; }
    public ProcessRecipe recipe() { return owned; }
    public int ticks() { return ticks; }
    public boolean complete() { return active() && ticks == owned.duration; }
    /** Caller must atomically remove every fluid/solid reactant before this call. */
    public void reserve(ProcessRecipe recipe) {
        if(active() || recipe == null) throw new IllegalStateException("Cannot overwrite owned process");
        owned = recipe.snapshot(); workRoundingDebt = 0; releasedTicks = 0; ticks = 0; workPaid = 0; heatPaid = 0;
    }
    private long cumulative(long total, int t) { return total / owned.duration * t + total % owned.duration * t / owned.duration; }
    /** Explicit electric-equipment migration: retain inputs, products, progress and every paid utility quantum. */
    public void electrifyUtilities() {
        if(!active() || owned.heatQuanta == 0 && owned.temperatureC == 0) return;
        owned = owned.electricallyDriven();
        workPaid = Math.addExact(workPaid, heatPaid); heatPaid = 0;
        // floor(a) + floor(b) can trail floor(a+b) by one quantum. Pay it on the next tick.
        workRoundingDebt = Math.toIntExact(cumulative(owned.workQuanta, ticks) - workPaid);
    }
    public long nextWork() { return !active() || complete() ? 0 : cumulative(owned.workQuanta, ticks + 1) - workPaid; }
    public long nextHeat() { return !active() || complete() ? 0 : cumulative(owned.heatQuanta, ticks + 1) - heatPaid; }
    public long remainingHeat() { return active() ? owned.heatQuanta - heatPaid : 0; }
    public boolean canAdvance(long now, long work, long heat) {
        return active() && !complete() && now != lastTick && work >= nextWork() && heat >= nextHeat();
    }
    /** Both exact debits must be made by the owner before advancing. */
    public void advance(long now) {
        if(!active() || complete() || now == lastTick) throw new IllegalStateException("Invalid process advance");
        workPaid += nextWork(); heatPaid += nextHeat(); ticks++; workRoundingDebt = 0; lastTick = now;
    }
    /** Material still owned by this batch, excluding products already released at paid progress. */
    public long pendingMass(ProcessRecipe recipe, int product) {
        return active() && recipe == owned ? owned.productMass[product] - cumulative(owned.productMass[product], releasedTicks) : recipe.productMass[product];
    }
    private int paidProgress(long paid, long total) {
        if(total == 0) return ticks;
        if(paid <= Long.MAX_VALUE / owned.duration) return (int) (paid * owned.duration / total);
        return java.math.BigInteger.valueOf(paid).multiply(java.math.BigInteger.valueOf(owned.duration)).divide(java.math.BigInteger.valueOf(total)).intValueExact();
    }
    private int releasableTicks() { return Math.min(ticks, Math.min(paidProgress(workPaid, owned.workQuanta), paidProgress(heatPaid, owned.heatQuanta))); }
    public boolean hasContinuousProducts() { return active() && owned.continuousOutputs && releasableTicks() > releasedTicks; }
    /** Caller checked space for all remaining products. No work or material is borrowed from future ticks. */
    public int[] releaseProgress() {
        if(!active() || !owned.continuousOutputs || workPaid != cumulative(owned.workQuanta, ticks) || heatPaid != cumulative(owned.heatQuanta, ticks)) throw new IllegalStateException("Unpaid continuous output");
        int[] result = new int[owned.outputs.length]; int paidTicks = Math.max(releasedTicks, releasableTicks());
        for(int i = 0; i < result.length; i++) result[i] = products.convert(owned.outputs[i], cumulative(owned.productMass[i], paidTicks) - cumulative(owned.productMass[i], releasedTicks));
        releasedTicks = paidTicks; return result;
    }
    /** Called after product capacity checks; input mass becomes species-isolated output ownership. */
    public int[] finish() {
        if(!complete() || workPaid != owned.workQuanta || heatPaid != owned.heatQuanta) throw new IllegalStateException("Unpaid process output");
        int[] result = new int[owned.outputs.length];
        for(int i = 0; i < result.length; i++) result[i] = products.convert(owned.outputs[i], pendingMass(owned, i));
        owned = null; ticks = 0; releasedTicks = 0; workPaid = 0; heatPaid = 0; return result;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("version", 1); tag.setLong("lastTick", lastTick);
        tag.setInteger("workRoundingDebt", workRoundingDebt);
        tag.setInteger("ticks", ticks); tag.setInteger("releasedTicks", releasedTicks); tag.setLong("workPaid", workPaid); tag.setLong("heatPaid", heatPaid);
        if(active()) { NBTTagCompound recipe = new NBTTagCompound(); owned.write(recipe); tag.setTag("owned", recipe); }
        products.write(tag); parent.setTag("realSimProcess", tag);
    }
    public void read(NBTTagCompound parent) {
        owned = null; ticks = 0; releasedTicks = 0; workPaid = 0; heatPaid = 0; workRoundingDebt = 0; lastTick = Long.MIN_VALUE;
        observedDrawTick = Long.MIN_VALUE; observedDrawQuanta = 0L;
        NBTTagCompound tag = parent.getCompoundTag("realSimProcess"); products.read(tag);
        if(!parent.hasKey("realSimProcess", 10)) return;
        if(tag.getInteger("version") != 1) throw new IllegalStateException("Unsupported physical process version");
        lastTick = tag.getLong("lastTick");
        if(tag.hasKey("owned", 10)) {
            owned = ProcessRecipe.read(tag.getCompoundTag("owned")); ticks = tag.getInteger("ticks"); releasedTicks = tag.getInteger("releasedTicks");
            workPaid = tag.getLong("workPaid"); heatPaid = tag.getLong("heatPaid"); workRoundingDebt = tag.getInteger("workRoundingDebt");
            if(ticks < 0 || ticks > owned.duration || releasedTicks < 0 || releasedTicks > ticks || !owned.continuousOutputs && releasedTicks != 0 || workRoundingDebt < 0 || workRoundingDebt > 1 || workPaid != cumulative(owned.workQuanta, ticks) - workRoundingDebt || heatPaid != cumulative(owned.heatQuanta, ticks))
                throw new IllegalStateException("Invalid stored physical process credit");
        }
    }
}
