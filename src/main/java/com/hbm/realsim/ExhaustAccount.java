package com.hbm.realsim;

import net.minecraft.nbt.NBTTagCompound;

/** Recoverable energy already split from burned fuel. Temperature classes never upgrade on fuel switching. */
public final class ExhaustAccount {
    public static final long CAPACITY_MICROJ = 150_000_000_000_000L; // 150 MJ
    private static final int[] TEMPERATURE_C = {350, 500, 700};
    private final long[] energy = new long[3];
    public long remainingMicroJ() { return Math.addExact(energy[0], Math.addExact(energy[1], energy[2])); }
    public long availableMicroJ(int minimumC) {
        long sum = 0;
        for(int i = 0; i < 3; i++) if(TEMPERATURE_C[i] >= minimumC) sum = Math.addExact(sum, energy[i]);
        return sum;
    }
    /** Returns heat vented when the finite exhaust plenum is full. */
    public long add(long amount, int qualityC) {
        if(amount < 0) throw new IllegalArgumentException("Negative exhaust");
        int index = -1;
        for(int i = 0; i < 3; i++) if(TEMPERATURE_C[i] <= qualityC) index = i;
        if(index < 0) return amount;
        long accepted = Math.min(amount, CAPACITY_MICROJ - remainingMicroJ());
        energy[index] = Math.addExact(energy[index], accepted);
        return amount - accepted;
    }
    public long take(long requested, int minimumC) {
        if(requested < 0) throw new IllegalArgumentException("Negative heat transfer");
        long left = requested;
        for(int i = 0; i < 3; i++) if(TEMPERATURE_C[i] >= minimumC) {
            long amount = Math.min(left, energy[i]); energy[i] -= amount; left -= amount;
        }
        return requested - left;
    }
    public long vent() { long amount = remainingMicroJ(); for(int i = 0; i < 3; i++) energy[i] = 0; return amount; }
    public void write(NBTTagCompound parent) {
        NBTTagCompound n = new NBTTagCompound(); n.setInteger("version", 1);
        for(int i = 0; i < 3; i++) n.setLong("heat" + TEMPERATURE_C[i], energy[i]);
        parent.setTag("realSimExhaust", n);
    }
    public void read(NBTTagCompound parent) {
        for(int i = 0; i < 3; i++) energy[i] = 0;
        if(!parent.hasKey("realSimExhaust", 10)) return;
        NBTTagCompound n = parent.getCompoundTag("realSimExhaust");
        if(n.getInteger("version") != 1) throw new IllegalStateException("Unsupported exhaust account");
        for(int i = 0; i < 3; i++) {
            energy[i] = n.getLong("heat" + TEMPERATURE_C[i]);
            if(energy[i] < 0 || energy[i] > CAPACITY_MICROJ) throw new IllegalStateException("Invalid exhaust account");
        }
        if(remainingMicroJ() > CAPACITY_MICROJ) throw new IllegalStateException("Exhaust capacity exceeded");
    }
}
