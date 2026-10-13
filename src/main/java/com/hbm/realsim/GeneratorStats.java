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
    public String[] info(long ratedWatts, FuelCredit credit, String tankFuel, int fill) {
        long watts = EnergyUnits.quantaPerTickToWatts(outputQuanta);
        double runtime = outputQuanta <= 0 ? 0 : credit.usefulMicrojoules() / 1_000_000D / (outputQuanta * 10D);
        if(tankFuel.equals(credit.fuel()) && fuelUnitsPerSecond > 0) runtime += fill / fuelUnitsPerSecond;
        return new String[] { "Rated: " + EnergyUnits.formatWatts(ratedWatts), "Current: " + EnergyUnits.formatWatts(watts),
            String.format(Locale.US, "Load: %.1f%%", ratedWatts == 0 ? 0D : watts * 100D / ratedWatts),
            "Fuel in use: " + (credit.fuel().isEmpty() ? tankFuel : credit.fuel()),
            (credit.fuel().startsWith("legacy_") ? "Owned legacy work: " : "Chemical credit: ") + EnergyUnits.formatJoules((long) (credit.remainingChemicalJoules() * 2D)),
            String.format(Locale.US, "Fuel flow: %.4f %s/min", fuelUnitsPerSecond * 60D, PhysicalRegistry.get(credit.fuel()).basis == PhysicalState.Basis.MATERIAL_ITEM ? "items" : "L"),
            String.format(Locale.US, "LHV efficiency: %.1f%%", credit.efficiencyPpm() / 10000D),
            outputQuanta <= 0 ? "Runtime: idle" : String.format(Locale.US, "Runtime at current load: %.2f h", runtime / 3600D) };
    }
}
