package com.hbm.realsim;

import static org.junit.Assert.*;
import org.junit.Test;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.realsim.SteamLedger.Route;
import com.hbm.items.machine.ItemPistons.EnumPistonType;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.render.util.EnumSymbol;
import net.minecraft.nbt.NBTTagCompound;

/** Production accounting and native tank contracts. World/port ordering remains a runtime acceptance check. */
public class GeneratorContractTest {
    @Test public void dieselGovernorRetainsIdleFiringAndCannotInstantlyFollowAFullLoadStep() {
        long firing = 0;
        for(int tick=0;tick<20;tick++) firing = GeneratorContract.governedInput(firing, 20000, 20000);
        assertEquals(20000, firing);
        firing = GeneratorContract.governedInput(firing, 0, 20000); assertEquals(19000, firing);
        for(int tick=0;tick<20;tick++) firing = GeneratorContract.governedInput(firing, 0, 20000);
        assertEquals(2000, firing);
        assertEquals(3000, GeneratorContract.governedInput(firing, 20000, 20000));
        assertEquals(40000, GeneratorContract.admit(40000, 40000, firing));
    }
    @Test public void manualFuelCreditIsSpentUnderSaturationWithoutBecomingStoredPower() {
        FuelCredit credit = new FuelCredit(); credit.reserve("DIESEL", 1000000000000L, 1, 320000);
        long before = credit.usefulMicrojoules(), generated = credit.release(20000);
        assertEquals(20000, generated); assertEquals(before - generated * 500000L, credit.usefulMicrojoules());
        assertEquals(40000, GeneratorContract.admit(40000, 40000, generated));
        TurbineFuelAccount turbine = new TurbineFuelAccount();
        assertEquals(0, turbine.admitElectrical(100000, 0)); assertEquals(100000, turbine.loadRejectedQuanta);
    }
    @Test public void cableDirectAndChargingShareOneBudget() {
        OutputBudget b = new OutputBudget();
        b.use(1, 20000, 8000); // cable
        b.use(1, 20000, 7000); // adjacent receiver
        b.use(1, 20000, 5000); // charge slot
        assertEquals(0, b.available(1, 20000));
        try { b.use(1, 20000, 1); fail(); } catch(IllegalArgumentException expected) { }
        assertEquals(20000, b.available(2, 20000));
    }
    @Test public void repeatedAndBackwardTicksCannotResetExports() {
        OutputBudget b = new OutputBudget(); b.use(40, 1000, 900);
        assertEquals(0, b.available(39, 1000)); assertEquals(100, b.available(40, 1000));
        NBTTagCompound tag = new NBTTagCompound(); b.write(tag); b = new OutputBudget(); b.read(tag);
        assertEquals(100, b.available(40, 1000)); assertEquals(1000, b.available(41, 1000));
    }
    @Test public void conventionalBuffersHoldExactlyTwoTicks() {
        for(long watts : new long[] {10000, 40000, 25000, 75000, 200000, 750000, 1000000, 3000000, 5000000, 25000000}) {
            assertEquals(2 * watts / 10, GeneratorContract.buffer(watts));
            assertEquals(0, GeneratorContract.room(GeneratorContract.buffer(watts), GeneratorContract.buffer(watts)));
        }
    }
    @Test public void fractionalNuggetAssaysCannotPreventGeneratorFuelRegistryStartup() {
        for(String name : new String[] {"GALLIUM_NUGGET_ITEM", "ARSENIC_NUGGET_ITEM", "GAAS_NUGGET_ITEM"})
            assertEquals(PhysicalState.MASS_UNITS_PER_KG / 9, PhysicalRegistry.get(name).massUnits);
    }
    @Test public void loadRemovalAndRestorationDoNotCreateABattery() {
        long stored = 0, capacity = GeneratorContract.buffer(10000);
        for(int tick = 0; tick < 10000; tick++) stored = GeneratorContract.admit(stored, capacity, 1000);
        assertEquals(2000, stored);
        stored -= 1000; stored = GeneratorContract.admit(stored, capacity, 1000); assertEquals(2000, stored);
    }
    @Test public void legacyElectricityDrainsWithoutNewChargingOrClipping() {
        long stored = 100000000000L, capacity = GeneratorContract.buffer(25000000);
        assertEquals(stored, GeneratorContract.admit(stored, capacity, 2500000));
        stored -= 2500000; assertEquals(99997500000L, stored);
        assertEquals(stored, GeneratorContract.admit(stored, capacity, 2500000));
    }
    @Test public void steamCapabilityFollowsTemperatureAndPressureRows() {
        State[] limits = {State.ORDINARY, State.HOT, State.SUPERHOT, State.ULTRAHOT};
        for(State limit : limits) for(State state : State.values())
            assertEquals(state.ordinal() >= State.ORDINARY.ordinal() && state.ordinal() <= limit.ordinal(), GeneratorContract.acceptsSteam(state, limit));
        assertEquals(1000000, State.HOT.pressurePa); assertEquals(300, State.HOT.temperatureC);
        assertEquals(4000000, State.SUPERHOT.pressurePa); assertEquals(16000000, State.ULTRAHOT.pressurePa);
    }
    @Test public void inletSizingFollowsMassInsteadOfCompressedCount() {
        assertEquals(100, GeneratorContract.steamCapacity(100, State.ORDINARY));
        assertEquals(10, GeneratorContract.steamCapacity(100, State.HOT));
        assertEquals(16, GeneratorContract.steamCapacity(1600, State.SUPERHOT));
        assertEquals(8, GeneratorContract.steamCapacity(8000, State.ULTRAHOT));
    }
    @Test public void legacyTankCapacityChangesPreserveContentsAcrossNBT() {
        FluidTank old = new FluidTank(new FluidType("STEAM", 0, 0, 0, 0, EnumSymbol.NONE), 64000); old.setFill(50000);
        NBTTagCompound n = new NBTTagCompound(); old.writeToNBT(n, "in");
        FluidTank revised = new FluidTank(old.getTankType(), 100).withDefaultCapacity(); revised.readFromNBT(n, "in");
        assertEquals(100, revised.getMaxFill()); assertEquals(50000, revised.getFill());
        revised.setFill(revised.getFill() - 100); assertEquals(49900, revised.getFill());
    }
    private ThermalAccount.Result steam(ThermalAccount a, long tick, State state, long flow, long watts, int efficiency, int exhaustRoom, long electricRoom) {
        return a.advance(tick, SteamLedger.transition(state, State.SPENT, Route.TURBINE, efficiency), 10000,
                exhaustRoom, flow, 0, 0, electricRoom, 0, 0, watts / 10, Long.MAX_VALUE);
    }
    @Test public void smallTurbineFlowAndRatingAreIndependent() {
        ThermalAccount a = new ThermalAccount();
        ThermalAccount.Result ordinary = steam(a, 1, State.ORDINARY, 10000, 75000, 750000, 2, 15000);
        assertEquals(10000, ordinary.mass); assertEquals(3750, ordinary.electricQuanta);
        ThermalAccount.Result hot = steam(new ThermalAccount(), 1, State.HOT, 10000, 75000, 750000, 2, 15000);
        assertTrue(hot.mass <= 10000); assertTrue(hot.electricQuanta <= 7500); assertTrue(hot.electricQuanta >= 7490);
    }
    @Test public void allSteamConvertersRespectNameplateAndMassFlow() {
        long[] flows = {15000, 10000, 400000, 2000000}, watts = {25000, 75000, 5000000, 25000000};
        int[] factors = {700000, 750000, 850000, 880000};
        State[] grades = {State.ORDINARY, State.HOT, State.SUPERHOT, State.ULTRAHOT};
        for(int i = 0; i < flows.length; i++) {
            ThermalAccount.Result r = steam(new ThermalAccount(), 1, grades[i], flows[i], watts[i], factors[i], 80, watts[i]);
            assertTrue(r.mass <= flows[i]); assertTrue(r.electricQuanta <= watts[i] / 10);
        }
    }
    @Test public void fullElectricityOrBlockedExhaustConsumesNoSteam() {
        ThermalAccount a = new ThermalAccount();
        assertEquals(0, steam(a, 1, State.HOT, 10000, 75000, 750000, 2, 0).consumed);
        assertEquals(0, steam(a, 2, State.HOT, 10000, 75000, 750000, 0, 15000).consumed);
        assertTrue(steam(a, 3, State.HOT, 10000, 75000, 750000, 2, 15000).mass > 0);
    }
    @Test public void fractionalExhaustAndAdmittedMassSurviveSave() {
        ThermalAccount a = new ThermalAccount(); long converted = 0, inlet = 0, exhaust = 0;
        for(int tick = 1; tick <= 300; tick++) {
            ThermalAccount.Result r = steam(a, tick, State.HOT, 10000, 75000, 750000, 2, 15000);
            converted += r.mass; inlet += r.consumed * State.HOT.massPerUnit; exhaust += r.produced * State.SPENT.massPerUnit;
            if(tick % 17 == 0) { NBTTagCompound n = new NBTTagCompound(); a.write(n); a = new ThermalAccount(); a.read(n); }
        }
        assertEquals(inlet, converted + a.ownedMass());
        assertEquals(converted, exhaust + a.remainder(State.SPENT));
    }
    @Test public void pistonSetsRetainFuelDependentEfficiency() {
        assertEquals(95000, EnumPistonType.STEEL.getElectricalEfficiencyPpm(FuelGrade.HIGH));
        assertEquals(342000, EnumPistonType.DURA.getElectricalEfficiencyPpm(FuelGrade.HIGH));
        assertEquals(380000, EnumPistonType.DESH.getElectricalEfficiencyPpm(FuelGrade.HIGH));
        assertEquals(380000, EnumPistonType.STARMETAL.getElectricalEfficiencyPpm(FuelGrade.HIGH));
        assertEquals(0, EnumPistonType.STEEL.getElectricalEfficiencyPpm(FuelGrade.AERO));
    }
    @Test public void gasStartupHas580PaidStepsAndMinimumLoadIsExplicit() {
        assertEquals(580, GasTurbineCycle.STARTUP_TICKS);
        assertEquals(100, GasTurbineCycle.startupRpm(20, 10)); assertEquals(0, GasTurbineCycle.startupRpm(40, 10));
        assertEquals(0, GasTurbineCycle.startupRpm(50, 10)); assertEquals(10, GasTurbineCycle.startupRpm(580, 10));
        assertEquals(300, GasTurbineCycle.startupTemperature(580, 300));
        assertEquals(32, GasTurbineCycle.targetRpm(1, 0, 10));
        assertEquals(0, GasTurbineCycle.load(0, 100, 100, 10));
        assertEquals(100, GasTurbineCycle.load(1, 100, 100, 10));
        assertEquals(50, GasTurbineCycle.LUBE_MICROLITERS_PER_TICK);
    }
    @Test public void currentCombinedCycleExampleIncludesPumpingCondensingAndBlowdown() {
        SteamLedger.Transition boiler = SteamLedger.transition(State.COLD, State.SUPERHOT, Route.BOILER, 900000);
        SteamLedger.Transition turbine = SteamLedger.transition(State.SUPERHOT, State.SPENT, Route.TURBINE, 850000);
        SteamLedger.Transition condenser = SteamLedger.transition(State.SPENT, State.COLD, Route.CONDENSER, 850000);
        double feedKgPerSecond = 5500000D / boiler.heatJPerKg;
        double steamKgPerSecond = feedKgPerSecond * .995; // Existing boiler blowdown
        double net = 2940000 + steamKgPerSecond * turbine.electricJPerKg
                - feedKgPerSecond * boiler.workJPerKg - steamKgPerSecond * condenser.workJPerKg;
        assertEquals(3910160, net, 100);
        assertTrue(feedKgPerSecond < 4); // HRSG's released mass-flow ceiling
        assertTrue(steamKgPerSecond < 8); // Industrial turbine's ceiling
        assertEquals(.391016, net / 10000000D, .00001);
    }
    @Test public void isotopeElectricityAndProcessServiceShareTheSameSource() {
        IsotopeLedger source = new IsotopeLedger(); source.convert(1, 10000, 400, 1000, 3000);
        assertEquals(1000, source.thermalQuanta); assertEquals(400, source.processQuanta); assertEquals(30, source.electricQuanta);
        assertEquals(source.thermalQuanta, source.processQuanta + source.electricQuanta + source.rejectedQuanta);
    }
    @Test public void isotopeFractionalConversionPersistsWithoutOfflineOrRepeatProduction() {
        IsotopeLedger source = new IsotopeLedger(); long produced = 0;
        for(int tick = 1; tick <= 100; tick++) {
            produced += source.convert(tick, 1140, 0, 12, 6);
            NBTTagCompound n = new NBTTagCompound(); source.write(n); source = new IsotopeLedger(); source.read(n);
            assertEquals(0, source.convert(tick, 1140, 0, 12, 6));
        }
        assertEquals(570, produced);
        assertEquals(5, source.convert(10000, 1140, 0, 12, 6));
    }
    @Test public void fullIsotopeGeneratorRejectsHeatInsteadOfDeletingStoredEnergy() {
        IsotopeLedger a = new IsotopeLedger(); assertEquals(0, a.convert(1, 1140, 0, 0, 6));
        assertEquals(114, a.rejectedQuanta); assertEquals(0, a.electricQuanta);
    }
    @Test public void solarPeaksAtNoonAndRespectsEnvironment() {
        assertEquals(100, GeneratorContract.solarQuanta(0, 1)); assertEquals(0, GeneratorContract.solarQuanta(.5F, 1));
        assertEquals(0, GeneratorContract.solarQuanta(.25F, 1)); assertEquals(0, GeneratorContract.solarQuanta(.75F, 1));
        assertEquals(200, GeneratorContract.solarQuanta(0, 2)); assertEquals(0, GeneratorContract.solarQuanta(0, Float.NaN));
    }
}
