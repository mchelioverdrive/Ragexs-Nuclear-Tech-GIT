package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable, mass-based process bill. Tank counts are inventory units, never interchangeable liters. */
public final class ProcessRecipe {
    /** One batch or sixty seconds of rated flow, whichever is larger. Computed once per immutable bill. */
    public final int[] inputCapacities, outputCapacities;
    public final String id;
    public final String[] inputs, outputs;
    public final int[] quantities, pressures;
    public final long[] productMass;
    public final String solidInput;
    public final int solidCount;
    public final String[] solidInputs;
    public final int[] solidCounts;
    public final int duration, temperatureC;
    /** Informational recipe temperature reference; neither a feed-state thermometer nor an execution gate. */
    public final int referenceTemperatureC;
    public final long workQuanta, heatQuanta;
    public final long electricalRatingWatts, thermalRatingWatts, rejectedWatts;
    public final boolean separation, continuousOutputs;
    public final BigDecimal chemicalInputJ, chemicalOutputJ, sensibleInputJ, sensibleOutputJ, rejectedJ;

    private static String[] canonicalNames(String[] names) {
        String[] result = names.clone();
        for(int i = 0; i < result.length; i++) result[i] = com.hbm.inventory.fluid.Fluids.canonicalName(result[i]);
        return result;
    }

    public ProcessRecipe(String id, String[] inputs, int[] quantities, int[] pressures,
            String solidInput, int solidCount, String[] outputs, long[] productMass,
            int duration, long workJ, long heatJ, int temperatureC, boolean separation) {
        this(id, inputs, quantities, pressures, solidCount == 0 ? new String[0] : new String[] {solidInput}, solidCount == 0 ? new int[0] : new int[] {solidCount},
                outputs, productMass, duration, workJ, heatJ, temperatureC, separation);
    }
    public ProcessRecipe(String id, String[] inputs, int[] quantities, int[] pressures,
            String[] solids, int[] counts, String[] outputs, long[] productMass,
            int duration, long workJ, long heatJ, int temperatureC, boolean separation) {
        this(id, inputs, quantities, pressures, solids, counts, outputs, productMass, duration, workJ, heatJ, temperatureC, separation, false);
    }
    private ProcessRecipe(String id, String[] inputs, int[] quantities, int[] pressures,
            String[] solids, int[] counts, String[] outputs, long[] productMass,
            int duration, long workJ, long heatJ, int temperatureC, boolean separation, boolean continuousOutputs) {
        this(id, inputs, quantities, pressures, solids, counts, outputs, productMass,
                duration, workJ, heatJ, temperatureC, separation, continuousOutputs, temperatureC);
    }
    private ProcessRecipe(String id, String[] inputs, int[] quantities, int[] pressures,
            String[] solids, int[] counts, String[] outputs, long[] productMass,
            int duration, long workJ, long heatJ, int temperatureC, boolean separation, boolean continuousOutputs, int referenceTemperatureC) {
        inputs = canonicalNames(inputs); outputs = canonicalNames(outputs);
        this.continuousOutputs = continuousOutputs;
        this.referenceTemperatureC = referenceTemperatureC;
        if(solids.length != counts.length || solids.length > 4) throw new IllegalArgumentException("Invalid solid feeds");
        this.solidInputs = solids.clone(); this.solidCounts = counts.clone();
        String solidInput = solids.length == 0 ? null : solids[0]; int solidCount = counts.length == 0 ? 0 : counts[0];
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
        for(int i = 0; i < solidInputs.length; i++) {
            if(solidCounts[i] <= 0) throw new IllegalArgumentException("Invalid solid quantity");
            for(int j = 0; j < i; j++) if(solidInputs[j].equals(solidInputs[i])) throw new IllegalArgumentException("Duplicate solid feed");
            long mass = Math.multiplyExact(resolved(solidInputs[i]).massUnits, solidCounts[i]);
            in = Math.addExact(in, mass); inputEnergy = inputEnergy.add(energy(solidInputs[i], mass));
            inputSensible = inputSensible.add(sensible(solidInputs[i], mass));
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
        inputCapacities = new int[inputs.length]; outputCapacities = new int[outputs.length];
        for(int i = 0; i < inputs.length; i++) inputCapacities[i] = Math.toIntExact(Math.max(quantities[i], PetroleumProcesses.ceilDiv((long) quantities[i] * 1200, duration)));
        for(int i = 0; i < outputs.length; i++) {
            PhysicalState state = resolved(outputs[i]);
            if(outputs[i].startsWith("VENT_") || state.phase == PhysicalState.Phase.SOLID) continue;
            long batch = PetroleumProcesses.ceilDiv(productMass[i], state.massUnits);
            long minute = BigDecimal.valueOf(productMass[i]).multiply(BigDecimal.valueOf(1200))
                    .divide(BigDecimal.valueOf(state.massUnits).multiply(BigDecimal.valueOf(duration)), 0, RoundingMode.CEILING).longValueExact();
            outputCapacities[i] = Math.toIntExact(Math.max(1, Math.max(batch, minute)));
        }
    }
    /** Explicit electrically heated equipment boundary; never infer this from a hot feed or temperature. */
    public ProcessRecipe electricallyDriven() {
        if(heatQuanta == 0 && temperatureC == 0) return this;
        return new ProcessRecipe(id, inputs, quantities, pressures, solidInputs, solidCounts, outputs, productMass,
                duration, Math.addExact(workQuanta, heatQuanta) / 2, 0, 0, separation, continuousOutputs, referenceTemperatureC);
    }
    /** Same total material/work bill, with products released in proportion to paid progress. */
    public ProcessRecipe continuous() {
        return new ProcessRecipe(id, inputs, quantities, pressures, solidInputs, solidCounts, outputs, productMass,
                duration, workQuanta / 2, heatQuanta / 2, temperatureC, separation, true, referenceTemperatureC);
    }
    /** Whole reaction copies, including recovered carriers, losses and both utilities.
     * Equipment registration still validates power/flow; callers choose supported lots. */
    public ProcessRecipe rebatch(int copies) { return rebatch(copies, duration); }
    /** Gameplay timing only: retain the complete bill; the equipment adapter validates its new duty/flow. */
    public ProcessRecipe withDuration(int ticks) {
        return new ProcessRecipe(id, inputs, quantities, pressures, solidInputs, solidCounts, outputs, productMass,
                ticks, workQuanta / 2, heatQuanta / 2, temperatureC, separation, continuousOutputs, referenceTemperatureC);
    }
    public ProcessRecipe rebatch(int copies, int ticks) {
        if(copies < 1 || ticks < duration) throw new IllegalArgumentException("Rebatching cannot shorten a process cycle");
        int[] fluids = quantities.clone(), solids = solidCounts.clone(); long[] products = productMass.clone();
        for(int i = 0; i < fluids.length; i++) fluids[i] = Math.multiplyExact(fluids[i], copies);
        for(int i = 0; i < solids.length; i++) solids[i] = Math.multiplyExact(solids[i], copies);
        for(int i = 0; i < products.length; i++) products[i] = Math.multiplyExact(products[i], copies);
        return new ProcessRecipe(id, inputs, fluids, pressures, solidInputs, solids, outputs, products, ticks,
                Math.multiplyExact(workQuanta / 2, copies), Math.multiplyExact(heatQuanta / 2, copies),
                temperatureC, separation, continuousOutputs, referenceTemperatureC);
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
        SteamLedger.State steam = SteamLedger.state(name);
        if(steam != null) return BigDecimal.valueOf(steam.enthalpyJPerKg).multiply(BigDecimal.valueOf(mass))
                .divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 18, RoundingMode.DOWN);
        return resolved(name).sensibleJoulesPerKg.multiply(BigDecimal.valueOf(mass)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 18, RoundingMode.DOWN);
    }
    public long inputMass() {
        long mass = 0;
        for(int i = 0; i < solidInputs.length; i++) mass = Math.addExact(mass, Math.multiplyExact(resolved(solidInputs[i]).massUnits, solidCounts[i]));
        for(int i = 0; i < inputs.length; i++) mass = Math.addExact(mass, Math.multiplyExact(resolved(inputs[i]).massUnits, quantities[i]));
        return mass;
    }
    public ProcessRecipe snapshot() {
        return new ProcessRecipe(id, inputs, quantities, pressures, solidInputs, solidCounts, outputs, productMass,
                duration, workQuanta / 2, heatQuanta / 2, temperatureC, separation, continuousOutputs, referenceTemperatureC);
    }
    public void write(NBTTagCompound tag) {
        tag.setString("id", id); tag.setInteger("duration", duration); tag.setLong("work", workQuanta / 2); tag.setLong("heat", heatQuanta / 2);
        tag.setInteger("temperature", temperatureC); tag.setBoolean("separation", separation); tag.setBoolean("continuousOutputs", continuousOutputs);
        tag.setInteger("referenceTemperature", referenceTemperatureC);
        tag.setInteger("solidFeeds", solidInputs.length);
        for(int i = 0; i < solidInputs.length; i++) { tag.setString("solid" + i, solidInputs[i]); tag.setInteger("solidQ" + i, solidCounts[i]); }
        tag.setString("solid", solidInput == null ? "" : solidInput); tag.setInteger("solidCount", solidCount);
        tag.setInteger("inputs", inputs.length); tag.setInteger("outputs", outputs.length);
        for(int i = 0; i < inputs.length; i++) { tag.setString("i" + i, inputs[i]); tag.setInteger("q" + i, quantities[i]); tag.setInteger("p" + i, pressures[i]); }
        for(int i = 0; i < outputs.length; i++) { tag.setString("o" + i, outputs[i]); tag.setLong("m" + i, productMass[i]); }
    }
    public static ProcessRecipe read(NBTTagCompound tag) {
        int ni = tag.getInteger("inputs"), no = tag.getInteger("outputs");
        if(ni < 0 || ni > 4 || no < 1 || no > 8) throw new IllegalArgumentException("Invalid stored process dimensions");
        String[] in = new String[ni], out = new String[no]; int[] q = new int[ni], p = new int[ni]; long[] m = new long[no];
        for(int i = 0; i < ni; i++) { in[i] = tag.getString("i" + i); q[i] = tag.getInteger("q" + i); p[i] = tag.getInteger("p" + i); }
        for(int i = 0; i < no; i++) { out[i] = tag.getString("o" + i); m[i] = tag.getLong("m" + i); }
        int ns = tag.hasKey("solidFeeds") ? tag.getInteger("solidFeeds") : tag.getInteger("solidCount") > 0 ? 1 : 0;
        if(ns < 0 || ns > 4) throw new IllegalArgumentException("Invalid solid feed dimensions");
        String[] solids = new String[ns]; int[] counts = new int[ns];
        for(int i = 0; i < ns; i++) { solids[i] = tag.getString(tag.hasKey("solidFeeds") ? "solid" + i : "solid"); counts[i] = tag.getInteger(tag.hasKey("solidFeeds") ? "solidQ" + i : "solidCount"); }
        ProcessRecipe result = new ProcessRecipe(tag.getString("id"), in, q, p, solids, counts, out, m,
                tag.getInteger("duration"), tag.getLong("work"), tag.getLong("heat"), tag.getInteger("temperature"), tag.getBoolean("separation"),
                tag.getBoolean("continuousOutputs"), tag.hasKey("referenceTemperature") ? tag.getInteger("referenceTemperature") : tag.getInteger("temperature"));
        return result;
    }
}
