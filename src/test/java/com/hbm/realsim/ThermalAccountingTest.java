package com.hbm.realsim;

import static org.junit.Assert.*;
import com.hbm.realsim.SteamLedger.State;
import com.hbm.realsim.SteamLedger.Route;
import com.hbm.realsim.SteamLedger.Transition;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.render.util.EnumSymbol;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.Test;

/** Tests of production ownership/state APIs, real fluid tanks and compressed NBT. No fake world or alternate model. */
public class ThermalAccountingTest {
    private FluidTank tank(String name, int count) {
        FluidTank tank = new FluidTank(new FluidType(name, 0, 0, 0, 0, EnumSymbol.NONE), Math.max(100000, count));
        tank.setFill(count); return tank;
    }
    private NBTTagCompound binary(NBTTagCompound n) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(n, bytes);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
    }
    private ThermalAccount.Result tick(ThermalAccount a, long tick, Transition t, int input, int output, long flow, long heat, long work, long room) {
        return a.advance(tick, t, input, output, flow, heat, work, room, 1200000, 20000, 2500000, Long.MAX_VALUE);
    }
    @Test public void releasedMassPreservesTransportRatiosAndCounts() {
        assertEquals(State.COLD.mass(1), State.ORDINARY.mass(100));
        assertEquals(State.COLD.mass(1), State.HOT.mass(10));
        assertEquals(State.COLD.mass(1), State.SUPERHOT.mass(1));
        assertEquals(State.COLD.mass(10), State.ULTRAHOT.mass(1));
        assertEquals(State.COLD.mass(1), State.SPENT.mass(1));
        for(State s : new State[] {State.ORDINARY, State.HOT, State.SUPERHOT, State.ULTRAHOT, State.SPENT}) {
            assertEquals(s.massPerUnit / 1000000D, PhysicalRegistry.get(s.identity).kilogramsPerUnit(), 1e-12);
            assertEquals(s.enthalpyJPerKg, s.sensibleJPerKg + s.latentJPerKg());
        }
    }
    @Test public void eachExpansionClosesEnthalpyAndBoundsWork() {
        for(State in : State.values()) {
            State out = SteamLedger.expansionOutlet(in); if(out == null) continue;
            for(int efficiency : new int[] {400000, 700000, 750000, 850000, 880000, 1000000}) {
                Transition t = SteamLedger.transition(in, out, Route.TURBINE, efficiency);
                assertEquals(in.enthalpyJPerKg, out.enthalpyJPerKg + t.electricJPerKg + t.rejectionJPerKg);
                assertTrue(t.electricJPerKg <= SteamLedger.expansionWork(in));
            }
            Transition full = SteamLedger.transition(in, State.SPENT, Route.TURBINE, 850000);
            assertEquals(in.enthalpyJPerKg, State.SPENT.enthalpyJPerKg + full.electricJPerKg + full.rejectionJPerKg);
            long sum = 0; State staged = in;
            while(staged != State.SPENT) { sum += SteamLedger.expansionWork(staged); staged = SteamLedger.expansionOutlet(staged); }
            assertEquals(sum, SteamLedger.fullExpansionWork(in));
        }
    }
    @Test public void compressorPaysPressureWorkAndAftercooler() {
        long[] expected = {735610, 599730, 739500}; int i = 0;
        for(State in : new State[] {State.ORDINARY, State.HOT, State.SUPERHOT}) {
            State out = SteamLedger.compressorOutlet(in);
            Transition t = SteamLedger.transition(in, out, Route.COMPRESSOR, 1000000);
            assertEquals(expected[i++], t.workJPerKg);
            assertTrue(t.workJPerKg > out.enthalpyJPerKg - in.enthalpyJPerKg);
            assertEquals(in.enthalpyJPerKg + t.workJPerKg, out.enthalpyJPerKg + t.rejectionJPerKg);
        }
    }
    @Test public void illegalTransitionsCannotRelabelSteam() {
        for(Route route : Route.values()) {
            try { SteamLedger.transition(State.ORDINARY, State.ULTRAHOT, route, 850000); fail(route.name()); }
            catch(IllegalArgumentException expected) { }
        }
        assertFalse(SteamLedger.canHeat(State.SUPERHOT, 350, 30));
        assertTrue(SteamLedger.canHeat(State.SUPERHOT, 500, 30));
        assertFalse(SteamLedger.canHeat(State.ULTRAHOT, 500, 30));
    }
    @Test public void fuelSplitUsesAssaysAndClosesExactly() {
        for(String id : new String[] {"GAS", "PETROLEUM", "REFORMGAS", "GAS_COKER", "SYNGAS"}) {
            assertTrue(TurbineFuelAccount.suitable(id));
            TurbineFuelAccount a = new TurbineFuelAccount(); FluidTank tank = tank(id, 10000);
            long chemical = 0, electricity = 0;
            for(int tick = 0; tick < 100; tick++) {
                long electric = a.burn(tick, tank, 500000000000L, 300000, 550000, 500);
                assertEquals(500000000000L, a.chemicalInputMicroJ);
                assertEquals(a.chemicalInputMicroJ, a.electricMicroJ + a.recoverableMicroJ + a.rejectedMicroJ);
                assertTrue(electric <= 300000); chemical += a.chemicalInputMicroJ; electricity += electric;
            }
            assertEquals(50000000000000L, chemical); assertEquals(30000000, electricity);
            assertEquals(Math.multiplyExact(10000L - tank.getFill(), PhysicalRegistry.get(id).chemicalMicrojoulesPerUnit()), chemical + a.chemicalCredit());
        }
        assertNotEquals(PhysicalRegistry.get("GAS").chemicalMicrojoulesPerUnit(), PhysicalRegistry.get("REFORMGAS").chemicalMicrojoulesPerUnit());
        assertFalse(TurbineFuelAccount.suitable("SOURGAS")); assertFalse(TurbineFuelAccount.suitable("LPG"));
        assertFalse(TurbineFuelAccount.suitable("HYDROGEN_GAS")); assertFalse(TurbineFuelAccount.suitable("OXYHYDROGEN"));
    }
    @Test public void noDuplicateExhaustAcrossTicksAndBinaryReload() throws Exception {
        TurbineFuelAccount a = new TurbineFuelAccount(); FluidTank fuel = tank("GAS", 1000);
        a.burn(10, fuel, 500000000000L, 300000, 550000, 500);
        long exhaust = a.exhaust.remainingMicroJ(), credit = a.chemicalCredit(); int stock = fuel.getFill();
        assertEquals(0, a.burn(10, fuel, 500000000000L, 300000, 550000, 500));
        assertEquals(exhaust, a.exhaust.remainingMicroJ()); assertEquals(stock, fuel.getFill());
        for(int repeat = 0; repeat < 20; repeat++) {
            NBTTagCompound tag = new NBTTagCompound(); a.write(tag); a = new TurbineFuelAccount(); a.read(binary(tag));
            assertEquals(exhaust, a.exhaust.remainingMicroJ()); assertEquals(credit, a.chemicalCredit());
        }
        assertEquals(0, a.burn(9, fuel, 500000000000L, 300000, 550000, 500));
        assertEquals(0, a.burn(10, fuel, 500000000000L, 300000, 550000, 500));
        assertEquals(exhaust, a.exhaust.vent()); assertEquals(0, a.exhaust.remainingMicroJ());
    }
    @Test public void fuelSwitchDoesNotUpgradeStoredExhaust() {
        TurbineFuelAccount a = new TurbineFuelAccount();
        a.burn(1, tank("GAS", 100), 500000000000L, 300000, 550000, 350);
        long low = a.exhaust.remainingMicroJ(); assertEquals(0, a.exhaust.availableMicroJ(480));
        a.burn(2, tank("REFORMGAS", 100), 500000000000L, 300000, 550000, 500);
        assertEquals(low, a.exhaust.remainingMicroJ() - a.exhaust.availableMicroJ(480));
        long high = a.exhaust.availableMicroJ(480); assertEquals(high, a.exhaust.take(Long.MAX_VALUE, 480));
        assertEquals(low, a.exhaust.remainingMicroJ());
    }
    @Test public void throttleAndFinitePlenumStayBounded() {
        TurbineFuelAccount a = new TurbineFuelAccount(); FluidTank fuel = tank("GAS", 100000);
        for(int tick = 0; tick < 1200; tick++) {
            int load = tick % 100 + 1;
            long electric = a.burn(tick, fuel, 5000000000L * load, 300000, 550000, load >= 75 ? 500 : 350);
            assertTrue(electric <= 300000); assertTrue(a.exhaust.remainingMicroJ() <= ExhaustAccount.CAPACITY_MICROJ);
            assertEquals(a.chemicalInputMicroJ, a.electricMicroJ + a.recoverableMicroJ + a.rejectedMicroJ);
        }
    }
    @Test public void fractionallyBoiledWaterAndRejectedMassClose() throws Exception {
        ThermalAccount a = new ThermalAccount(); Transition t = SteamLedger.transition(State.COLD, State.ORDINARY, Route.BOILER, 980000);
        int stock = 20, produced = 0; long converted = 0, lost = 0, heat = 0, rejected = 0;
        for(int tick = 0; tick < 10000; tick++) {
            ThermalAccount.Result r = tick(a, tick, t, stock, 1000000, 10000, 20000, 0, 0);
            stock -= r.consumed; produced += r.produced; converted += r.mass; lost += r.ventedMass; heat += r.heatQuanta; rejected += r.rejectionMicroJ;
            assertTrue(r.heatQuanta <= 20000); assertTrue(r.produced <= 1);
            if(tick % 17 == 0) { NBTTagCompound n = new NBTTagCompound(); a.write(n); a = new ThermalAccount(); a.read(binary(n)); }
        }
        assertEquals(20 * State.COLD.massPerUnit, State.COLD.mass(stock) + a.ownedMass() + converted);
        assertEquals(converted, State.ORDINARY.mass(produced) + a.remainder(State.ORDINARY) + lost);
        NBTTagCompound n = new NBTTagCompound(); a.write(n);
        long prepaid = n.getCompoundTag("realSimThermal").getLong("heat");
        assertEquals(heat * 500000L, (State.ORDINARY.mass(produced) + a.remainder(State.ORDINARY)) * State.ORDINARY.enthalpyJPerKg + rejected + prepaid);
    }
    @Test public void boilersCannotExceedNameplateOrRunUnpaid() {
        ThermalAccount a = new ThermalAccount(); Transition t = SteamLedger.transition(State.COLD, State.HOT, Route.BOILER, 980000);
        assertEquals(0, tick(a, 1, t, 1000, 100000, 400000, 0, 20000, 0).mass);
        assertEquals(0, tick(a, 2, t, 1000, 100000, 400000, 1200000, 0, 0).mass);
        ThermalAccount.Result r = tick(a, 3, t, 1000, 100000, 400000, 12000000, 20000, 0);
        assertTrue(r.heatQuanta <= 1200000); assertTrue(r.workQuanta <= 20000); assertTrue(r.mass <= 400000);
    }
    @Test public void fullPowerOrSteamOutputPausesWithoutReservation() {
        ThermalAccount a = new ThermalAccount(); Transition t = SteamLedger.transition(State.ULTRAHOT, State.SUPERHOT, Route.TURBINE, 850000);
        assertEquals(0, tick(a, 1, t, 1000000000, 10000, 2000000, 0, 0, 0).consumed);
        assertEquals(0, tick(a, 2, t, 1000000000, 0, 2000000, 0, 0, 2500000).consumed);
        ThermalAccount.Result r = tick(a, 3, t, 1000000000, 10000, 2000000, 0, 0, 2500000);
        assertTrue(r.electricQuanta <= 2500000);
        long owned = a.ownedMass();
        assertEquals(0, tick(a, 4, t, 1000000000, 10000, 2000000, 0, 0, 0).mass); assertEquals(owned, a.ownedMass());
        assertTrue(r.electricQuanta * 500000L <= r.mass * SteamLedger.expansionWork(State.ULTRAHOT));
    }
    @Test public void condenserConservesMassAndRejectsStateHeatPlusAuxiliary() throws Exception {
        ThermalAccount a = new ThermalAccount(); Transition t = SteamLedger.transition(State.SPENT, State.COLD, Route.CONDENSER, 850000);
        int stock = 5, returned = 0; long mass = 0, rejection = 0, work = 0;
        for(int tick = 0; tick < 2000; tick++) {
            ThermalAccount.Result r = a.advance(tick, t, stock, 100, 1000000, 0, 20000, 0, 0, 20000, 0, 2000000);
            stock -= r.consumed; returned += r.produced; mass += r.mass; rejection += r.rejectionMicroJ; work += r.workQuanta;
            assertEquals(0, r.ventedMass); assertTrue(r.rejectionMicroJ <= 2000000L * 500000);
            if(tick == 7) { NBTTagCompound n = new NBTTagCompound(); a.write(n); a = new ThermalAccount(); a.read(binary(n)); }
        }
        assertEquals(State.SPENT.mass(5), State.SPENT.mass(stock) + a.ownedMass() + State.COLD.mass(returned) + a.remainder(State.COLD));
        assertEquals(mass * (State.SPENT.enthalpyJPerKg + t.workJPerKg), rejection);
        NBTTagCompound n = new NBTTagCompound(); a.write(n);
        assertEquals(work * 500000L, mass * t.workJPerKg + n.getCompoundTag("realSimThermal").getLong("work"));
    }
    @Test public void condenserHasNoFreeCoolingAndWarmReturnCloses() {
        Transition warm = SteamLedger.transition(State.SPENT, State.WARM, Route.CONDENSER, 1000000);
        Transition cold = SteamLedger.transition(State.WARM, State.COLD, Route.CONDENSER, 1000000);
        assertEquals(2208190, warm.rejectionJPerKg); assertEquals(107900, cold.rejectionJPerKg);
        ThermalAccount a = new ThermalAccount();
        assertEquals(0, a.advance(1, warm, 1, 1, 1000000, 0, 0, 0, 0, 0, 0, 0).mass);
        assertEquals(State.SPENT.enthalpyJPerKg, warm.rejectionJPerKg + cold.rejectionJPerKg);
    }
    @Test public void partialSteamCompressorSurvivesReloadAndCannotRepeatTick() throws Exception {
        ThermalAccount a = new ThermalAccount(); Transition t = SteamLedger.transition(State.SUPERHOT, State.ULTRAHOT, Route.COMPRESSOR, 1000000);
        ThermalAccount.Result r = tick(a, 3, t, 20, 100, 2500, 0, 2500, 0); assertTrue(r.mass > 0); assertEquals(0, r.produced);
        NBTTagCompound tag = new NBTTagCompound(); a.write(tag); long owned = a.ownedMass(), pending = a.remainder(State.ULTRAHOT);
        a = new ThermalAccount(); a.read(binary(tag));
        assertEquals(owned, a.ownedMass()); assertEquals(pending, a.remainder(State.ULTRAHOT));
        assertEquals(0, tick(a, 3, t, 20, 100, 2500, 0, 2500, 0).mass);
        assertEquals(0, tick(a, 2, t, 20, 100, 2500, 0, 2500, 0).mass);
        assertTrue(tick(a, 10003, t, 20, 100, 2500, 0, 2500, 0).mass <= 2500); // no offline catch-up
    }
    @Test public void ownedMassCannotSwitchStatesMidConversion() {
        ThermalAccount a = new ThermalAccount(); Transition old = SteamLedger.transition(State.ULTRAHOT, State.SUPERHOT, Route.TURBINE, 850000);
        tick(a, 1, old, 2, 100, 10000, 0, 0, 10000); long owned = a.ownedMass();
        Transition switched = SteamLedger.transition(State.ORDINARY, State.SPENT, Route.TURBINE, 850000);
        assertEquals(0, tick(a, 2, switched, 100, 100, 10000, 0, 0, 10000).mass); assertEquals(owned, a.ownedMass());
    }
    @Test public void legacyNbtPreservesStockWithoutCertifyingProgress() {
        NBTTagCompound n = new NBTTagCompound(); n.setInteger("progress", 99); n.setInteger("heat", Integer.MAX_VALUE); n.setString("custom", "retained");
        ThermalAccount a = new ThermalAccount(); a.read(n); assertEquals(0, a.ownedMass());
        TurbineFuelAccount fuel = new TurbineFuelAccount(); fuel.read(n); assertEquals(0, fuel.chemicalCredit()); assertEquals(0, fuel.exhaust.remainingMicroJ());
        FluidTank stock = tank("ULTRAHOTSTEAM", 1000000); stock.writeToNBT(n, "stock"); stock.readFromNBT(n, "stock");
        assertEquals(1000000, stock.getFill()); a.write(n); assertEquals("retained", n.getString("custom"));
    }
    @Test public void checkedArithmeticSupportsGigaJoulesAndRejectsOverflow() {
        assertEquals(3490490000000000L, State.ULTRAHOT.enthalpyMicroJ(State.ULTRAHOT.mass(100)));
        assertEquals(1000000000L, SteamLedger.mulDiv(Long.MAX_VALUE, 1000000000L, Long.MAX_VALUE));
        try { State.ULTRAHOT.enthalpyMicroJ(State.ULTRAHOT.mass(1000000000)); fail(); } catch(ArithmeticException expected) { }
        try { SteamLedger.mulDiv(Long.MAX_VALUE, Long.MAX_VALUE, 1); fail(); } catch(ArithmeticException expected) { }
        ThermalAccount a = new ThermalAccount();
        ThermalAccount.Result r = tick(a, 1, SteamLedger.transition(State.ULTRAHOT, State.SUPERHOT, Route.TURBINE, 880000), 1000000000, 1000000000, 2000000, 0, 0, 2500000);
        assertTrue(r.electricQuanta > 0); assertTrue(r.electricQuanta <= 2500000);
    }
    @Test public void hrsgCannotOverdrawOrUpgradeLowQuality() throws Exception {
        ExhaustAccount exhaust = new ExhaustAccount(); exhaust.add(1000000000000L, 350);
        ThermalAccount water = new ThermalAccount();
        assertEquals(0, HeatRecovery.advance(1, exhaust, water, State.SUPERHOT, 10, 100, 20000).mass);
        long before = exhaust.remainingMicroJ();
        ThermalAccount.Result r = HeatRecovery.advance(2, exhaust, water, State.HOT, 10, 100, 20000);
        assertTrue(r.mass > 0); assertEquals(before - exhaust.remainingMicroJ(), r.heatQuanta * 500000L);
        assertTrue(r.heatQuanta <= HeatRecovery.RATED_HEAT_WATTS / 10);
        NBTTagCompound n = new NBTTagCompound(); exhaust.write(n); water.write(n); n = binary(n);
        exhaust = new ExhaustAccount(); exhaust.read(n); water = new ThermalAccount(); water.read(n);
        before = exhaust.remainingMicroJ(); assertEquals(0, HeatRecovery.advance(2, exhaust, water, State.HOT, 10, 100, 20000).mass); assertEquals(before, exhaust.remainingMicroJ());
        try { HeatRecovery.advance(3, exhaust, water, State.ULTRAHOT, 10, 100, 20000); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void combinedCycleClosesOriginalFuelWithoutSecondCopy() throws Exception {
        TurbineFuelAccount gas = new TurbineFuelAccount(); FluidTank fuel = tank("GAS", 100000);
        ThermalAccount hrsg = new ThermalAccount(), high = new ThermalAccount(), hot = new ThermalAccount(), ordinary = new ThermalAccount(), condenser = new ThermalAccount();
        int feed = 10000, superStock = 0, hotStock = 0, ordinaryStock = 0, spentStock = 0, returned = 0;
        long chemical = 0, electricMicroJ = 0, rejected = 0, pumpWork = 0, condenserWork = 0, ventMass = 0;
        for(int tick = 0; tick < 4000; tick++) {
            gas.burn(tick, fuel, 500000000000L, 300000, 550000, 500);
            chemical += gas.chemicalInputMicroJ; electricMicroJ += gas.electricMicroJ; rejected += gas.rejectedMicroJ;
            ThermalAccount.Result b = HeatRecovery.advance(tick, gas.exhaust, hrsg, State.SUPERHOT, feed, 100000, 20000);
            feed -= b.consumed; superStock += b.produced; pumpWork += b.workQuanta; rejected += b.rejectionMicroJ; ventMass += b.ventedMass;
            ThermalAccount.Result a = tick(high, tick, SteamLedger.transition(State.SUPERHOT, State.HOT, Route.TURBINE, 850000), superStock, 100000, 400000, 0, 0, 500000);
            superStock -= a.consumed; hotStock += a.produced; rejected += a.rejectionMicroJ; electricMicroJ += a.mass * 187000;
            a = tick(hot, tick, SteamLedger.transition(State.HOT, State.ORDINARY, Route.TURBINE, 850000), hotStock, 1000000, 400000, 0, 0, 500000);
            hotStock -= a.consumed; ordinaryStock += a.produced; rejected += a.rejectionMicroJ; electricMicroJ += a.mass * 255000;
            a = tick(ordinary, tick, SteamLedger.transition(State.ORDINARY, State.SPENT, Route.TURBINE, 850000), ordinaryStock, 100000, 400000, 0, 0, 500000);
            ordinaryStock -= a.consumed; spentStock += a.produced; rejected += a.rejectionMicroJ; electricMicroJ += a.mass * 212500;
            a = tick(condenser, tick, SteamLedger.transition(State.SPENT, State.COLD, Route.CONDENSER, 850000), spentStock, 100000, 400000, 0, 20000, 0);
            spentStock -= a.consumed; returned += a.produced; rejected += a.rejectionMicroJ; condenserWork += a.workQuanta;
            if(tick == 71) { NBTTagCompound n = new NBTTagCompound(); gas.write(n); gas = new TurbineFuelAccount(); gas.read(binary(n)); }
        }
        long stored = gas.exhaust.remainingMicroJ() + storedEnthalpy(hrsg, State.COLD, State.SUPERHOT) + State.SUPERHOT.enthalpyMicroJ(State.SUPERHOT.mass(superStock))
                + storedEnthalpy(high, State.SUPERHOT, State.HOT) + State.HOT.enthalpyMicroJ(State.HOT.mass(hotStock))
                + storedEnthalpy(hot, State.HOT, State.ORDINARY) + State.ORDINARY.enthalpyMicroJ(State.ORDINARY.mass(ordinaryStock))
                + storedEnthalpy(ordinary, State.ORDINARY, State.SPENT) + State.SPENT.enthalpyMicroJ(State.SPENT.mass(spentStock)) + storedEnthalpy(condenser, State.SPENT, State.COLD);
        NBTTagCompound n = new NBTTagCompound(); hrsg.write(n); condenser.write(n);
        long utilityCredit = n.getCompoundTag("realSimThermal").getLong("work");
        n = new NBTTagCompound(); hrsg.write(n); utilityCredit += n.getCompoundTag("realSimThermal").getLong("work");
        long heatCredit = n.getCompoundTag("realSimThermal").getLong("heat");
        assertEquals(chemical + (pumpWork + condenserWork) * 500000L, electricMicroJ + rejected + stored + utilityCredit + heatCredit);
        assertEquals(State.COLD.mass(10000), State.COLD.mass(feed + returned) + ventMass + totalMass(hrsg, State.SUPERHOT) + State.SUPERHOT.mass(superStock)
                + totalMass(high, State.HOT) + State.HOT.mass(hotStock) + totalMass(hot, State.ORDINARY) + State.ORDINARY.mass(ordinaryStock)
                + totalMass(ordinary, State.SPENT) + State.SPENT.mass(spentStock) + totalMass(condenser, State.COLD));
        assertTrue(electricMicroJ / (double) chemical > 0.39); assertTrue(electricMicroJ / (double) chemical < 0.41);
    }
    private long storedEnthalpy(ThermalAccount a, State in, State out) { return in.enthalpyMicroJ(a.ownedMass()) + out.enthalpyMicroJ(a.remainder(out)); }
    private long totalMass(ThermalAccount a, State out) { return a.ownedMass() + a.remainder(out); }
    @Test public void heatingOvenImportedHeatReducesFiringAndPreservesFraction() throws Exception {
        HeatTransfer intake = new HeatTransfer(); long input = 0, output = 0, rejection = 0;
        for(int tick = 0; tick < 999; tick++) {
            long delivered = intake.accept(1, 500000); input++; output += delivered; rejection += intake.rejectedMicroJ;
            long fired = HeatTransfer.remainingAllowance(500000, delivered);
            assertEquals(50000, fired + delivered);
            if(tick % 13 == 0) { NBTTagCompound n = new NBTTagCompound(); intake.write(n); intake = new HeatTransfer(); intake.read(binary(n)); }
        }
        NBTTagCompound n = new NBTTagCompound(); intake.write(n);
        assertEquals(input * 500000, output * 500000 + rejection + n.getLong("realSimHeatTransferRemainder"));
        assertEquals(0, HeatTransfer.remainingAllowance(500000, 50000));
        try { intake.accept(10000, 1000001); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void heatExportBudgetCannotBeReusedThroughMultipleConsumers() throws Exception {
        OutputBudget budget = new OutputBudget(); budget.use(42, 50000, 20000); budget.use(42, 50000, 30000);
        assertEquals(0, budget.available(42, 50000));
        NBTTagCompound n = new NBTTagCompound(); budget.write(n); budget = new OutputBudget(); budget.read(binary(n));
        assertEquals(0, budget.available(42, 50000)); assertEquals(50000, budget.available(43, 50000));
    }
    @Test public void afterburningIncreasesFuelAndExhaustWithoutEfficiencyBonus() {
        FluidType aero = new FluidType("KEROSENE", 0, 0, 0, 0, EnumSymbol.NONE).addTraits(new com.hbm.inventory.fluid.trait.FT_Combustible(com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade.AERO, 1));
        FluidTank tank = new FluidTank(aero, 100); tank.setFill(100);
        for(int factor = 1; factor <= 4; factor++) {
            TurbineFuelAccount a = new TurbineFuelAccount();
            long electric = a.burn(factor, tank, 200000000000L * factor, 250000 / factor, factor > 1 ? 750000 : 550000, factor > 1 ? 700 : 500, false);
            assertTrue(electric <= 100000);
            assertTrue(a.electricMicroJ * factor <= a.chemicalInputMicroJ / 4);
            assertEquals(a.chemicalInputMicroJ, a.electricMicroJ + a.recoverableMicroJ + a.rejectedMicroJ);
        }
    }
}
