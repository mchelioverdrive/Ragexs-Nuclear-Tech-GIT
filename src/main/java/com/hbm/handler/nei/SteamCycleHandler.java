package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.realsim.*;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.tileentity.machine.*;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/** Mass parcels and rated continuous limits, never synthetic executable recipes. */
public class SteamCycleHandler extends ProcessInfoHandler {
    private static volatile List<Process> cache;
    private static long revision = -1;
    public static void invalidate() { cache = null; }
    @Override public String getRecipeName() { return "Steam, condensation and heat recovery"; }
    @Override public String getOverlayIdentifier() { return "ntmSteamCycle"; }
    @Override protected List<Process> processes() { return snapshot(); }
    @Override protected Block[] machineBlocks() { return new Block[] {ModBlocks.machine_condenser, ModBlocks.machine_condenser_powered, ModBlocks.machine_tower_small, ModBlocks.machine_tower_large, ModBlocks.machine_radiator, ModBlocks.machine_steam_engine, ModBlocks.machine_turbine, ModBlocks.machine_large_turbine, ModBlocks.machine_chungus, ModBlocks.heater_heatex, ModBlocks.machine_turbinegas}; }
    private static synchronized List<Process> snapshot() {
        long current = dataRevision();
        if(cache != null && revision == current) return cache;
        revision = current;
        List<Process> rows = new ArrayList<>();
        condenser(rows, ModBlocks.machine_condenser, new TileEntityCondenser());
        condenser(rows, ModBlocks.machine_condenser_powered, new TileEntityCondenserPowered());
        condenser(rows, ModBlocks.machine_tower_small, new TileEntityTowerSmall());
        condenser(rows, ModBlocks.machine_tower_large, new TileEntityTowerLarge());
        condenser(rows, ModBlocks.machine_radiator, new TileEntityRadiator());
        TileEntitySteamEngine engine = new TileEntitySteamEngine();
        turbine(rows, ModBlocks.machine_steam_engine, State.ORDINARY, engine.getSteamFlowMassPerTick(), engine.getExpansionEfficiencyPpm(), 25000);
        TileEntityMachineTurbine small = new TileEntityMachineTurbine();
        turbine(rows, ModBlocks.machine_turbine, State.HOT, Math.min(small.getSteamFlowMassPerTick(), State.ORDINARY.mass(Math.max(0, TileEntityMachineTurbine.maxSteamPerTick))), small.getExpansionEfficiencyPpm(), small.getRatedOutputWatts());
        TileEntityMachineLargeTurbine large = new TileEntityMachineLargeTurbine();
        turbine(rows, ModBlocks.machine_large_turbine, State.SUPERHOT, large.getSteamFlowMassPerTick(), large.getExpansionEfficiencyPpm(), large.getRatedOutputWatts());
        TileEntityChungus leviathan = new TileEntityChungus();
        turbine(rows, ModBlocks.machine_chungus, State.ULTRAHOT, leviathan.getSteamFlowMassPerTick(), leviathan.getExpansionEfficiencyPpm(), leviathan.getRatedOutputWatts());
        for(State in : State.values()) {
            State out = in == State.SPENT ? State.COLD : SteamLedger.expansionOutlet(in);
            if(out == null) continue;
            SteamLedger.Transition bill = SteamLedger.transition(in, out, SteamLedger.Route.HEAT_EXCHANGER, 1000000);
            rows.add(new Process(ModBlocks.heater_heatex, inputs(parcel(in)), new ItemStack[] {parcel(out)},
                    "1 kg mass parcel; fractional outlet accumulates", "Recovered heat: " + energy(bill.rejectionJPerKg) + "/kg",
                    "Up to 2 MWth and 4 kg/s; heat/output room required", "Heat quality: " + Math.max(0, out.temperatureC - 30) + " C; stored heat loses 0.1%/tick"));
        }
        for(FluidType in : Fluids.getInNiceOrder()) {
            if(SteamLedger.state(in.getName()) != null || !in.hasTrait(FT_Coolable.class)) continue;
            FT_Coolable trait = in.getTrait(FT_Coolable.class);
            if(trait.coolsTo == null || trait.amountReq <= 0 || trait.amountProduced <= 0) continue;
            PhysicalState hot = in.getPhysicalState(), cold = trait.coolsTo.getPhysicalState();
            long heat = (long) (Math.max(0, trait.heatEnergy) * Math.max(0, Math.min(1, trait.getEfficiency(FT_Coolable.CoolingType.HEATEXCHANGER))));
            if(hot.isResolved() && cold.isResolved()) {
                long mass = Math.multiplyExact(hot.massUnits, trait.amountReq);
                if(mass != Math.multiplyExact(cold.massUnits, trait.amountProduced) || cold.lhvJoulesPerKg.compareTo(hot.lhvJoulesPerKg) > 0) continue;
                long available = ProcessRecipe.sensible(in.getName(), mass).subtract(ProcessRecipe.sensible(trait.coolsTo.getName(), mass)).max(java.math.BigDecimal.ZERO)
                        .multiply(java.math.BigDecimal.valueOf(2)).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
                heat = Math.min(heat, available);
                if(heat <= 0) continue;
            }
            rows.add(new Process(ModBlocks.heater_heatex, inputs(ItemFluidIcon.make(in, trait.amountReq)), new ItemStack[] {ItemFluidIcon.make(trait.coolsTo, trait.amountProduced)},
                    "Coolant heat recovery; configured inlet cadence", "Useful heat: " + api.hbm.energymk2.EnergyUnits.formatJoules(heat) + " per parcel",
                    "Up to 2 MWth; retained parcel waits for heat/output room", "Same pipe pressure class at both ends"));
        }
        for(State out : new State[] {State.ORDINARY, State.HOT, State.SUPERHOT}) {
            SteamLedger.Transition bill = SteamLedger.transition(State.COLD, out, SteamLedger.Route.BOILER, 900000);
            rows.add(new Process(ModBlocks.machine_turbinegas, inputs(parcel(State.COLD)), new ItemStack[] {parcel(out)},
                    "HRSG: 1 kg feed/output parcel; not free steam", "Requires burning eligible gas and paid lubricant",
                    "Exhaust >= " + (out.temperatureC + HeatRecovery.APPROACH_C) + " C; heat: " + energy(bill.heatJPerKg) + "/kg",
                    "Pump work: " + energy(bill.workJPerKg) + "/kg; up to 6 MWth", "Steam tier/flow varies with exhaust and load"));
        }
        cache = java.util.Collections.unmodifiableList(rows);
        return cache;
    }
    static ItemStack parcel(State state) {
        return ItemFluidIcon.makePhysical(new com.hbm.inventory.FluidStack(Fluids.fromName(state.identity), 1), SteamLedger.MASS_PER_KG);
    }
    static String energy(long joules) { return api.hbm.energymk2.EnergyUnits.formatJoules(Math.multiplyExact(joules, 2)); }
    private static void condenser(List<Process> rows, Block block, TileEntityCondenser machine) {
        boolean powered = machine instanceof TileEntityCondenserPowered;
        SteamLedger.Transition bill = SteamLedger.transition(State.SPENT, State.COLD, SteamLedger.Route.CONDENSER, powered ? 850000 : 1000000);
        rows.add(new Process(block, inputs(parcel(State.SPENT)), new ItemStack[] {parcel(State.COLD)},
                "1 kg steam -> 1 kg recovered fresh water", "Heat rejection: " + energy(bill.rejectionJPerKg) + "/kg; " + MachineInfo.thermalWatts(machine.getRatedRejectionWatts()) + " rated",
                "Auxiliary work: " + energy(bill.workJPerKg) + "/kg; " + MachineInfo.watts(machine.getRatedAuxiliaryWatts()) + " ceiling",
                String.format(Locale.US, "Flow <= %.3f kg/s (also <= 20 kg/s)", Math.min(20, machine.getRatedRejectionWatts() / (double) bill.rejectionJPerKg)),
                machine instanceof TileEntityRadiator ? "Vacuum compatible; disabled during world fire" : "Atmosphere >= 0.01; outside orbit; no world fire"));
    }
    private static void turbine(List<Process> rows, Block block, State maximum, long massPerTick, int efficiency, long watts) {
        for(State in : State.values()) {
            if(!GeneratorContract.acceptsSteam(in, maximum) || efficiency <= 0) continue;
            SteamLedger.Transition bill = SteamLedger.transition(in, State.SPENT, SteamLedger.Route.TURBINE, efficiency);
            rows.add(new Process(block, inputs(parcel(in)), new ItemStack[] {parcel(State.SPENT)},
                    "1 kg inlet -> 1 kg Low Pressure Steam", "Electrical work: " + energy(bill.electricJPerKg) + "/kg",
                    "Rated output: " + MachineInfo.watts(watts) + "; depends on flow/load",
                    String.format(Locale.US, "Flow <= %.3f kg/s; work factor %.1f%%", massPerTick / 50000D, efficiency / 10000D),
                    "Drain exhaust; ambient sink rejects unused heat/work"));
        }
    }
}
