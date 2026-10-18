package com.hbm.realsim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IEnergyHandlerMK2;
import api.hbm.energymk2.IRatedGenerator;
import api.hbm.fluid.IFluidUser;
import api.hbm.tile.IProcessHeatSource;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.TileEntityMachineBase;
import net.minecraft.tileentity.TileEntity;

/** One formatter for item tooltips, GUI meters/information icons and NEI work hovers. */
public final class MachineInfo {
    private MachineInfo() { }
    public static String watts(long value) { return EnergyUnits.formatWatts(value).replace(" (J/S)", ""); }
    public static String thermalWatts(long value) { return watts(value) + "th"; }
    public static List<String> describe(TileEntity tile, boolean live) {
        List<String> lines = new ArrayList<>();
        if(tile instanceof IMachineInfo) {
            IMachineInfo owner = (IMachineInfo) tile;
            long demand, draw;
            if(live && tile instanceof TileEntityMachineBase) {
                demand = ((TileEntityMachineBase) tile).machineInfoDemandWatts;
                draw = ((TileEntityMachineBase) tile).machineInfoDrawWatts;
            } else {
                demand = owner.getMachineDemandWatts();
                draw = owner.getMachineCurrentDrawWatts();
            }
            if(demand >= 0) lines.add("Electrical demand: " + watts(demand));
            if(live && draw >= 0) lines.add("Current draw: " + watts(draw));
            owner.appendMachineInfo(lines, live);
        }
        if(tile instanceof IRatedGenerator && !(tile instanceof com.hbm.tileentity.machine.TileEntityStirling && ((com.hbm.tileentity.machine.TileEntityStirling) tile).isCreative())) {
            String rating = "Rated electrical output: " + watts(((IRatedGenerator) tile).getRatedOutputWatts());
            if(!lines.contains(rating)) lines.add(rating);
            if(((IRatedGenerator) tile).getRatedOutputWatts() > 0 && ((IRatedGenerator) tile).getExportLimitWatts() != ((IRatedGenerator) tile).getRatedOutputWatts()) lines.add("Maximum electrical delivery: " + watts(((IRatedGenerator) tile).getExportLimitWatts()));
        }
        if(tile instanceof IProcessHeatSource) {
            IProcessHeatSource heat = (IProcessHeatSource) tile;
            lines.add("Rated thermal output: " + thermalWatts(heat.getRatedProcessHeatWatts()));
            lines.add("Stored heat: " + EnergyUnits.formatJoules(heat.getHeatStored()));
            lines.add("Heat source temperature: " + heat.getProcessTemperatureC() + " \u00b0C");
        }
        if(tile instanceof com.hbm.tileentity.machine.TileEntityHeaterFirebox || tile instanceof com.hbm.tileentity.machine.TileEntityHeaterOilburner || tile instanceof com.hbm.tileentity.machine.TileEntityHeaterOven)
            lines.add(String.format(Locale.US, "Fuel-to-heat efficiency: %.1f%%", Combustion.FIREBOX_EFFICIENCY_PPM / 10000D));
        if(tile instanceof com.hbm.tileentity.machine.TileEntityHeaterOven)
            lines.add(String.format(Locale.US, "Imported heat transfer: %.1f%%; imported heat displaces firing", ((com.hbm.tileentity.machine.TileEntityHeaterOven) tile).getIntakeEfficiencyPpm() / 10000D));
        if(tile instanceof com.hbm.tileentity.machine.storage.TileEntityMachineBattery) {
            com.hbm.tileentity.machine.storage.TileEntityMachineBattery battery = (com.hbm.tileentity.machine.storage.TileEntityMachineBattery) tile;
            lines.add("Current charge ceiling: " + watts(battery.getMaxInputQuantaPerTick() * 10L));
            lines.add("Current discharge ceiling: " + watts(battery.getMaxOutputQuantaPerTick() * 10L));
        }
        if(tile instanceof com.hbm.tileentity.network.TileEntityConverterHeRf || tile instanceof com.hbm.tileentity.network.TileEntityConverterRfHe)
            lines.add("RF conversion: " + (api.hbm.energymk2.EnergyUnits.QUANTA_PER_RF / 2D) + " J/RF; configured conversion losses apply");
        if(tile instanceof IEnergyHandlerMK2) {
            IEnergyHandlerMK2 storage = (IEnergyHandlerMK2) tile;
            if(live || storage.getEnergyCapacityQuanta() > 0 && (!(tile instanceof IRatedGenerator) || ((IRatedGenerator) tile).getIntentionalStorageQuanta() > 0)) lines.add(live ? "Energy: " + EnergyUnits.formatJoules(storage.getStoredEnergyQuanta()) + " / " + EnergyUnits.formatJoules(storage.getEnergyCapacityQuanta())
                    : "Energy storage: " + EnergyUnits.formatJoules(storage.getEnergyCapacityQuanta()));
        }
        if(tile instanceof IFluidUser && !live) {
            FluidTank[] tanks = ((IFluidUser) tile).getAllTanks();
            if(tanks != null && tanks.length > 0) {
                int count = 0;
                for(FluidTank tank : tanks) if(tank != null) count++;
                if(count > 0) lines.add("Fluid tanks: " + count + "; inspect tanks for contents and capacity");
            }
        }
        return lines;
    }
    public static void process(List<String> lines, ProcessRecipe recipe) {
        if(recipe == null) { lines.add("Select a compatible process feed; external heat may be required"); return; }
        lines.add("Process electrical demand: " + watts(recipe.electricalRatingWatts));
        if(recipe.heatQuanta > 0) lines.add("Process thermal demand: " + thermalWatts(recipe.thermalRatingWatts));
        lines.add(String.format(Locale.US, "Process duration: %.2f s", recipe.duration / 20D));
        lines.add("Electrical work: " + EnergyUnits.formatJoules(recipe.workQuanta));
        if(recipe.heatQuanta > 0) lines.add("Required heat source: " + recipe.temperatureC + " \u00b0C or hotter");
        if(recipe.referenceTemperatureC > 0) lines.add("Recipe temperature reference (informational): " + recipe.referenceTemperatureC + " \u00b0C");
        for(int i = 0; i < recipe.inputs.length; i++) {
            PhysicalState state = PhysicalRegistry.get(recipe.inputs[i]);
            String unit = state.basis == PhysicalState.Basis.STANDARD_GAS_LITER ? "standard L/min" : state.basis == PhysicalState.Basis.LIQUID_LITER ? "L/min" : "units/min";
            lines.add(String.format(Locale.US, "%s feed: %.2f %s", recipe.inputs[i].toLowerCase(Locale.US).replace('_', ' '), recipe.quantities[i] * 1200D / recipe.duration, unit));
            if(recipe.pressures[i] > 0) lines.add("Required pipe pressure class: " + recipe.pressures[i] + " PU");
        }
    }
    public static void parallelCooling(List<String> lines, long rejectedWatts) {
        lines.add("Eight parallel lanes; runs the same recipes as the workshop machine");
        lines.add("Cooling: sealed water loop and integral ambient dry cooler");
        lines.add("Loop charge: " + IndustrialDemand.COOLING_CHARGE_L + " L fresh water; no operating makeup or steam output");
        lines.add("Cooling pump/fan: " + watts(IndustrialDemand.COOLING_AUXILIARY_W) + " minimum per active lane, rising to 2% of process heat rejection; included in draw");
        lines.add("Cooling heat load: " + thermalWatts(Math.max(0L, rejectedWatts)));
        lines.add(String.format(Locale.US, "Cooling circulation: %.2f L/min at a 20 K rise", IndustrialDemand.circulationLitersPerMinute(Math.max(0L, rejectedWatts))));
    }
}
