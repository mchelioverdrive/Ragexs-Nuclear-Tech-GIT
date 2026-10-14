package com.hbm.realsim;

import java.util.List;
import java.util.Locale;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.util.ThermalUnits;

/** Shared SI presentation. Floating point is confined to formatting. */
public final class ThermalInfo {
    private ThermalInfo() { }
    public static void states(List<String> lines, FluidTank inlet, FluidTank outlet, ThermalAccount account) {
        lines.add("State: " + inlet.getTankType().getLocalizedName() + " -> " + outlet.getTankType().getLocalizedName());
        lines.add(String.format(Locale.US, "Water mass flow: %.4f kg/s", account.currentMass / 50000D));
    }
    public static void generator(List<String> lines, ThermalAccount account, long watts, int efficiencyPpm) {
        lines.add("Rated electrical output: " + MachineInfo.watts(watts));
        lines.add("Current electrical output: " + MachineInfo.watts(account.currentElectricQuanta * 10L));
        lines.add(String.format(Locale.US, "Expansion work factor: %.1f%%", efficiencyPpm / 10000D));
    }
    public static void boiler(List<String> lines, ThermalAccount account, long watts, long storedQuanta) {
        lines.add("Rated thermal transfer: " + MachineInfo.thermalWatts(watts));
        lines.add("Current heat input: " + ThermalUnits.formatThermalPower(account.currentHeatQuanta));
        lines.add("Stored thermal energy: " + ThermalUnits.formatThermalEnergy(storedQuanta));
    }
    public static void condenser(List<String> lines, ThermalAccount account, long watts) {
        lines.add("Rated heat rejection: " + MachineInfo.thermalWatts(watts));
        lines.add("Current heat rejection: " + ThermalUnits.formatThermalPower(account.currentRejectedMicroJ / 500000D));
        lines.add(String.format(Locale.US, "Condensate flow: %.4f kg/s", account.currentMass / 50000D));
        lines.add("Closed feedwater return; separate ambient cooling sink");
    }
}
