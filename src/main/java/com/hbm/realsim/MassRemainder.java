package com.hbm.realsim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;

/** Already consumed conversion mass, owned by target identity; never restored to an input tank. */
public final class MassRemainder {
    private final Map<String, Long> mass = new HashMap<String, Long>();
    public long remaining(String output) { Long value = mass.get(output); return value == null ? 0 : value; }
    public long preview(String output, long inputMass) {
        long unit = PhysicalRegistry.get(output).massUnits;
        if(unit <= 0 || inputMass < 0) throw new IllegalArgumentException("Unresolved conversion mass");
        return Math.addExact(remaining(output), inputMass) / unit;
    }
    /** Input must be removed before committing. */
    public int convert(String output, long inputMass) {
        long total = Math.addExact(remaining(output), inputMass);
        long unit = PhysicalRegistry.get(output).massUnits;
        int count = Math.toIntExact(preview(output, inputMass));
        mass.put(output, total % unit);
        return count;
    }
    public void write(NBTTagCompound parent) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("version", 1);
        NBTTagCompound entries = new NBTTagCompound();
        for(Map.Entry<String, Long> entry : mass.entrySet()) entries.setLong(entry.getKey(), entry.getValue());
        tag.setTag("mass", entries); parent.setTag("realSimMass", tag);
    }
    public void read(NBTTagCompound parent) {
        mass.clear(); if(!parent.hasKey("realSimMass", 10)) return;
        NBTTagCompound tag = parent.getCompoundTag("realSimMass");
        if(tag.getInteger("version") != 1) throw new IllegalStateException("Unsupported RealSim mass account version");
        NBTTagCompound entries = tag.getCompoundTag("mass");
        for(Object key : entries.func_150296_c()) {
            String name = (String) key; long amount = entries.getLong(name), unit = PhysicalRegistry.get(name).massUnits;
            if(amount >= 0 && amount < unit) mass.put(name, amount);
        }
    }
}
