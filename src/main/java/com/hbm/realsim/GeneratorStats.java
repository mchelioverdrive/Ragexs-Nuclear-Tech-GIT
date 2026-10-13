package com.hbm.realsim;

import java.util.Locale;
import api.hbm.energymk2.EnergyUnits;
import net.minecraft.nbt.NBTTagCompound;
import io.netty.buffer.ByteBuf;

/** Server-owned observed generation, separate from storage and network export. */
public final class GeneratorStats {
    public long outputQuanta;
    public double fuelUnitsPerSecond;
    public void idle() { outputQuanta = 0; fuelUnitsPerSecond = 0; }
    public void record(long output, FuelCredit credit) {
        outputQuanta = output; fuelUnitsPerSecond = output * credit.unitsPerQuantum() * 20D;
    }
    public void write(NBTTagCompound tag, FuelCredit credit) { tag.setLong("realSimOutput", outputQuanta); tag.setDouble("realSimFlow", fuelUnitsPerSecond); credit.write(tag); }
    public void read(NBTTagCompound tag, FuelCredit credit) { outputQuanta = tag.getLong("realSimOutput"); fuelUnitsPerSecond = tag.getDouble("realSimFlow"); credit.read(tag); }
    public void write(ByteBuf buf, FuelCredit credit) { NBTTagCompound tag = new NBTTagCompound(); write(tag, credit); cpw.mods.fml.common.network.ByteBufUtils.writeTag(buf, tag); }
    public void read(ByteBuf buf, FuelCredit credit) { read(cpw.mods.fml.common.network.ByteBufUtils.readTag(buf), credit); }
    /** Retained external API; native callers supply their installed/rated efficiency explicitly. */
    public String[] info(long ratedWatts, FuelCredit credit, String tankFuel, int fill) {
        return info(ratedWatts, credit, tankFuel, fill, credit.efficiencyPpm(), false);
    }
    public String[] info(long ratedWatts, FuelCredit credit, String tankFuel, int fill, int ratedEfficiencyPpm, boolean advanced) {
        long watts = EnergyUnits.quantaPerTickToWatts(outputQuanta);
        double runtime = outputQuanta <= 0 ? 0 : credit.usefulMicrojoules() / 1_000_000D / (outputQuanta * 10D);
        if(tankFuel.equals(credit.fuel()) && fuelUnitsPerSecond > 0) runtime += fill / fuelUnitsPerSecond;
        String activeFuel = (outputQuanta > 0 || credit.hasCredit()) && !credit.fuel().isEmpty() ? credit.fuel() : tankFuel;
        PhysicalState state = PhysicalRegistry.get(activeFuel);
        String unit = state.basis == PhysicalState.Basis.MATERIAL_ITEM ? "items" : state.basis == PhysicalState.Basis.STANDARD_GAS_LITER ? "standard L" : state.basis == PhysicalState.Basis.LIQUID_LITER ? "L" : "nominal units";
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Rated output: " + EnergyUnits.formatWatts(ratedWatts).replace(" (J/S)", ""));
        lines.add("Current output: " + (watts == 0 ? "0 W" : EnergyUnits.formatWatts(watts).replace(" (J/S)", "")));
        lines.add(String.format(Locale.US, "Load: %.1f%%", ratedWatts == 0 ? 0D : watts * 100D / ratedWatts));
        lines.add(ratedEfficiencyPpm > 0 ? String.format(Locale.US, "Efficiency: %.1f%% rated", ratedEfficiencyPpm / 10000D) : "Efficiency: no compatible fuel/equipment selected");
        String fuelLabel = activeFuel.startsWith("legacy_") ? "Legacy fuel" : activeFuel.toLowerCase(Locale.US).replace('_', ' ');
        lines.add("Fuel in use: " + (fuelLabel.isEmpty() ? "None" : Character.toUpperCase(fuelLabel.charAt(0)) + fuelLabel.substring(1)));
        lines.add(String.format(Locale.US, "Fuel flow: %.4f %s/min", fuelUnitsPerSecond * 60D, unit));
        lines.add(outputQuanta <= 0 ? "Runtime: idle" : String.format(Locale.US, "Runtime at current load: %.2f h", runtime / 3600D));
        if(advanced) {
            lines.add((credit.fuel().startsWith("legacy_") ? "Owned legacy work: " : "Chemical credit: ") + EnergyUnits.formatJoules((long) (credit.remainingChemicalJoules() * 2D)));
            lines.add(String.format(Locale.US, "Owned batch efficiency: %.1f%%", credit.efficiencyPpm() / 10000D));
            lines.add("Owned fuel identity: " + credit.fuel());
        }
        return lines.toArray(new String[0]);
    }
}
