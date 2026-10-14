package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable, mass-based process bill. Tank counts are inventory units, never interchangeable liters. */
public final class ProcessRecipe {
    public final String id;
    public final String[] inputs, outputs;
    public final int[] quantities, pressures;
    public final long[] productMass;
    public final String solidInput;
    public final int solidCount;
    public final int duration, temperatureC;
    public final long workQuanta, heatQuanta;
    public final long electricalRatingWatts, thermalRatingWatts, rejectedWatts;
    public final boolean separation;
    public final BigDecimal chemicalInputJ, chemicalOutputJ, sensibleInputJ, sensibleOutputJ, rejectedJ;

    public ProcessRecipe(String id, String[] inputs, int[] quantities, int[] pressures,
            String solidInput, int solidCount, String[] outputs, long[] productMass,
            int duration, long workJ, long heatJ, int temperatureC, boolean separation) {
        this.id = id; this.inputs = inputs.clone(); this.quantities = quantities.clone(); this.pressures = pressures.clone();
        this.solidInput = solidInput; this.solidCount = solidCount; this.outputs = outputs.clone(); this.productMass = productMass.clone();
        this.duration = duration; this.workQuanta = Math.multiplyExact(workJ, 2L); this.heatQuanta = Math.multiplyExact(heatJ, 2L);
        this.temperatureC = temperatureC; this.separation = separation;
        if(inputs.length != quantities.length || inputs.length != pressures.length || outputs.length != productMass.length
                || duration <= 0 || workJ < 0 || heatJ < 0 || solidCount < 0) throw new IllegalArgumentException("Invalid process: " + id);
        long in = 0, out = 0;
        BigDecimal inputEnergy = BigDecimal.ZERO, outputEnergy = BigDecimal.ZERO;
        BigDecimal inputSensible = BigDecimal.ZERO, outputSensible = BigDecimal.ZERO;
        for(int i = 0; i < inputs.length; i++) {
            if(quantities[i] <= 0 || pressures[i] < 0) throw new IllegalArgumentException("Invalid process input: " + id);
            long mass = Math.multiplyExact(resolved(inputs[i]).massUnits, quantities[i]);
            in = Math.addExact(in, mass); inputEnergy = inputEnergy.add(energy(inputs[i], mass));
            inputSensible = inputSensible.add(sensible(inputs[i], mass));
        }
        if(solidCount > 0) {
            long mass = Math.multiplyExact(resolved(solidInput).massUnits, solidCount);
            in = Math.addExact(in, mass); inputEnergy = inputEnergy.add(energy(solidInput, mass));
            inputSensible = inputSensible.add(sensible(solidInput, mass));
        }
        for(int i = 0; i < outputs.length; i++) {
            resolved(outputs[i]);
            if(productMass[i] < 0) throw new IllegalArgumentException("Negative product: " + id);
            for(int j = 0; j < i; j++) if(outputs[j].equals(outputs[i])) throw new IllegalArgumentException("Duplicate output: " + id);
            out = Math.addExact(out, productMass[i]); outputEnergy = outputEnergy.add(energy(outputs[i], productMass[i]));
            outputSensible = outputSensible.add(sensible(outputs[i], productMass[i]));
        }
        if(in != out) throw new IllegalArgumentException("Unbalanced mass: " + id + " " + in + " != " + out);
        BigDecimal difference = outputEnergy.subtract(inputEnergy);
        // Fixed mass coefficients round at <1 microgram; allow at most 1 microjoule per batch.
        BigDecimal tolerance = new BigDecimal("0.000001");
        if(separation ? difference.abs().compareTo(tolerance) > 0 : difference.compareTo(BigDecimal.valueOf(workJ + heatJ).add(tolerance)) > 0)
            throw new IllegalArgumentException("Unbalanced chemical energy: " + id + " " + difference + " J");
        chemicalInputJ = inputEnergy; chemicalOutputJ = outputEnergy; sensibleInputJ = inputSensible; sensibleOutputJ = outputSensible;
        rejectedJ = inputEnergy.add(inputSensible).add(BigDecimal.valueOf(Math.addExact(workJ, heatJ))).subtract(outputEnergy).subtract(outputSensible);
        if(rejectedJ.compareTo(tolerance.negate()) < 0) throw new IllegalArgumentException("Unpaid sensible heat: " + id);
        electricalRatingWatts = Math.multiplyExact(workQuanta / duration + (workQuanta % duration == 0 ? 0 : 1), 10L);
        thermalRatingWatts = Math.multiplyExact(heatQuanta / duration + (heatQuanta % duration == 0 ? 0 : 1), 10L);
        rejectedWatts = rejectedJ.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(20L)).divide(BigDecimal.valueOf(duration), 0, RoundingMode.CEILING).longValueExact();
    }
    public static PhysicalState resolved(String name) {
        PhysicalState state = PhysicalRegistry.get(name);
        if(!state.isResolved()) throw new IllegalArgumentException("Unresolved physical substance: " + name);
        return state;
    }
    public static BigDecimal energy(String name, long mass) {
        return resolved(name).lhvJoulesPerKg.multiply(BigDecimal.valueOf(mass)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 18, RoundingMode.DOWN);
    }
    public static BigDecimal sensible(String name, long mass) {
        return resolved(name).sensibleJoulesPerKg.multiply(BigDecimal.valueOf(mass)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 18, RoundingMode.DOWN);
    }
    public long inputMass() {
        long mass = solidCount == 0 ? 0 : Math.multiplyExact(resolved(solidInput).massUnits, solidCount);
        for(int i = 0; i < inputs.length; i++) mass = Math.addExact(mass, Math.multiplyExact(resolved(inputs[i]).massUnits, quantities[i]));
        return mass;
    }
    public ProcessRecipe snapshot() {
        return new ProcessRecipe(id, inputs, quantities, pressures, solidInput, solidCount, outputs, productMass,
                duration, workQuanta / 2, heatQuanta / 2, temperatureC, separation);
    }
    public void write(NBTTagCompound tag) {
        tag.setString("id", id); tag.setInteger("duration", duration); tag.setLong("work", workQuanta / 2); tag.setLong("heat", heatQuanta / 2);
        tag.setInteger("temperature", temperatureC); tag.setBoolean("separation", separation);
        tag.setString("solid", solidInput == null ? "" : solidInput); tag.setInteger("solidCount", solidCount);
        tag.setInteger("inputs", inputs.length); tag.setInteger("outputs", outputs.length);
        for(int i = 0; i < inputs.length; i++) { tag.setString("i" + i, inputs[i]); tag.setInteger("q" + i, quantities[i]); tag.setInteger("p" + i, pressures[i]); }
        for(int i = 0; i < outputs.length; i++) { tag.setString("o" + i, outputs[i]); tag.setLong("m" + i, productMass[i]); }
    }
    public static ProcessRecipe read(NBTTagCompound tag) {
        int ni = tag.getInteger("inputs"), no = tag.getInteger("outputs");
        if(ni < 0 || ni > 2 || no < 1 || no > 8) throw new IllegalArgumentException("Invalid stored process dimensions");
        String[] in = new String[ni], out = new String[no]; int[] q = new int[ni], p = new int[ni]; long[] m = new long[no];
        for(int i = 0; i < ni; i++) { in[i] = tag.getString("i" + i); q[i] = tag.getInteger("q" + i); p[i] = tag.getInteger("p" + i); }
        for(int i = 0; i < no; i++) { out[i] = tag.getString("o" + i); m[i] = tag.getLong("m" + i); }
        return new ProcessRecipe(tag.getString("id"), in, q, p, tag.getString("solid"), tag.getInteger("solidCount"), out, m,
                tag.getInteger("duration"), tag.getLong("work"), tag.getLong("heat"), tag.getInteger("temperature"), tag.getBoolean("separation"));
    }
}
