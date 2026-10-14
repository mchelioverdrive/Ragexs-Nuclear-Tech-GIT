package com.hbm.realsim;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.EnumSymbol;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

/** Invariant tests against production accounting, using real tanks and binary NBT. */
public class PhysicalAccountingTest {
    private FluidTank tank(String name, int fill) {
        FluidType type = new FluidType(name, 0xffffff, 0, 0, 0, EnumSymbol.NONE)
                .addTraits(new FT_Flammable(1), new FT_Combustible(FuelGrade.HIGH, Long.MAX_VALUE));
        FluidTank result = new FluidTank(type, 4000); result.setFill(fill); return result;
    }
    private NBTTagCompound roundTrip(NBTTagCompound tag) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(tag, out);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(out.toByteArray()));
    }
    @Test public void dieselHasOneChemicalBudget() {
        assertEquals(35_819_000_000_000L, PhysicalRegistry.get("DIESEL").chemicalMicrojoulesPerUnit());
        assertEquals(PhysicalRegistry.get("DIESEL").chemicalMicrojoulesPerUnit(), PhysicalRegistry.get("DIESEL_CRACK").chemicalMicrojoulesPerUnit());
        for(int efficiency : new int[] {70_000, 320_000, 380_000, 850_000}) {
            FluidTank tank = tank("DIESEL", 1); FuelCredit credit = new FuelCredit();
            long output = Combustion.generate(tank, credit, Long.MAX_VALUE / 1_000_000L, efficiency);
            assertEquals(0, tank.getFill());
            BigInteger budget = BigInteger.valueOf(35_819_000_000_000L).multiply(BigInteger.valueOf(efficiency)).divide(BigInteger.valueOf(1_000_000L));
            assertTrue(BigInteger.valueOf(output).multiply(BigInteger.valueOf(500_000L)).compareTo(budget) <= 0);
        }
    }
    @Test public void fractionalCreditAndEmptyTankSurviveBinaryNbt() throws Exception {
        FluidTank input = tank("DIESEL", 1); FuelCredit first = new FuelCredit();
        assertEquals(20_000L, Combustion.generate(input, first, 20_000L, 320_000));
        assertEquals(0, input.getFill());
        NBTTagCompound tag = new NBTTagCompound(); first.write(tag); input.writeToNBT(tag, "tank");
        tag = roundTrip(tag);
        FuelCredit reloaded = new FuelCredit(); reloaded.read(tag); input.readFromNBT(tag, "tank");
        assertEquals(first.usefulMicrojoules(), reloaded.usefulMicrojoules());
        assertEquals(0, input.getFill());
        assertEquals(20_000L, Combustion.generate(input, reloaded, 20_000L, 320_000));
    }
    @Test public void reloadDoesNotAddCreditAndFuelSwitchCannotOverwriteIt() throws Exception {
        FuelCredit credit = new FuelCredit(); credit.reserve("DIESEL", 35_819_000_000_000L, 1, 320_000); credit.release(1234);
        long expected = credit.usefulMicrojoules();
        for(int i = 0; i < 20; i++) { NBTTagCompound tag = new NBTTagCompound(); credit.write(tag); credit = new FuelCredit(); credit.read(roundTrip(tag)); assertEquals(expected, credit.usefulMicrojoules()); }
        try { credit.reserve("METHANOL", 15_783_000_000_000L, 1, 850_000); fail("live credit overwritten"); } catch(IllegalArgumentException expectedFailure) { }
        assertEquals(expected, credit.usefulMicrojoules());
        assertEquals("DIESEL", credit.fuel());
    }
    @Test public void switchingAndDrainingCannotRefundOwnedInput() {
        FluidTank input = tank("DIESEL", 20); FuelCredit credit = new FuelCredit();
        Combustion.generate(input, credit, 20_000L, 320_000);
        assertEquals(19, input.getFill());
        long remaining = credit.usefulMicrojoules();
        input.setFill(0); input.setTankType(tank("METHANOL", 0).getTankType());
        assertEquals(20_000L, Combustion.generate(input, credit, 20_000L, 850_000));
        assertEquals(remaining - 10_000_000_000L, credit.usefulMicrojoules());
        assertEquals("DIESEL", credit.fuel());
    }
    @Test public void consumptionTracksDemandRatherThanWholeLitersPerTick() {
        FluidTank full = tank("DIESEL", 4000), half = tank("DIESEL", 4000);
        FuelCredit a = new FuelCredit(), b = new FuelCredit();
        for(int i = 0; i < 6000; i++) { assertEquals(20_000L, Combustion.generate(full, a, 20_000L, 320_000)); assertEquals(10_000L, Combustion.generate(half, b, 10_000L, 320_000)); }
        assertEquals(2D, (4000 - full.getFill() - a.remainingChemicalJoules() / 35_819_000D) / (4000 - half.getFill() - b.remainingChemicalJoules() / 35_819_000D), 1e-8);
    }
    @Test public void unpaidIdleAndEmptyFuelProduceNothing() {
        FuelCredit credit = new FuelCredit(); FluidTank input = tank("DIESEL", 1);
        assertEquals(0, credit.release(1000)); assertEquals(0, Combustion.generate(input, credit, 0, 320_000)); assertEquals(1, input.getFill());
        input.setFill(0); assertEquals(0, Combustion.generate(input, credit, 1000, 320_000));
        assertEquals(0, credit.usefulMicrojoules());
    }
    @Test public void noNegativeDebtOrOverflow() {
        FuelCredit credit = new FuelCredit(); assertEquals(0, credit.release(-1));
        try { credit.reserve("bad", Long.MAX_VALUE, 2, 500_000); fail("overflow accepted"); } catch(ArithmeticException expected) { }
        assertEquals(0, credit.usefulMicrojoules());
        credit.reserve("large", Long.MAX_VALUE, 1, 1_000_000);
        assertEquals(Long.MAX_VALUE / 500_000L, credit.release(Long.MAX_VALUE)); assertTrue(credit.usefulMicrojoules() >= 0);
    }
    @Test public void efficiencyCannotExceedChemicalInput() {
        for(int efficiency : new int[] {-1, 0, 1_000_001}) {
            try { new FuelCredit().reserve("bad", 1000, 1, efficiency); fail("invalid efficiency accepted"); } catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void phaseConversionsPreserveExactAdoptedMassAcrossReload() throws Exception {
        for(String name : new String[] {"HYDROGEN", "OXYGEN", "NITROGEN"}) {
            MassRemainder account = new MassRemainder(); long massIn = 0, massOut = 0;
            for(int i = 0; i < 1000; i++) {
                long mass = PhysicalRegistry.get(name).massUnits;
                int gas = account.convert(name + "_GAS", mass); massIn += mass;
                int liquid = account.convert(name, Math.multiplyExact(gas, PhysicalRegistry.get(name + "_GAS").massUnits));
                massOut += liquid * PhysicalRegistry.get(name).massUnits;
                if(i % 31 == 0) { NBTTagCompound tag = new NBTTagCompound(); account.write(tag); account = new MassRemainder(); account.read(roundTrip(tag)); }
                assertEquals(massIn, massOut + account.remaining(name) + account.remaining(name + "_GAS"));
            }
        }
    }
    @Test public void hydrogenLhvDoesNotChangeWithPhase() {
        assertEquals(PhysicalRegistry.get("HYDROGEN").lhvJoulesPerKg, PhysicalRegistry.get("HYDROGEN_GAS").lhvJoulesPerKg);
        assertEquals(0, PhysicalRegistry.get("OXYGEN_GAS").chemicalMicrojoulesPerUnit());
    }
    @Test public void dryAirSeparationPreservesItsAdoptedMassAssay() {
        long input = PhysicalRegistry.get("AIR").massUnits * 100L;
        long output = PhysicalRegistry.get("NITROGEN_GAS").massUnits * 77L + PhysicalRegistry.get("OXYGEN_GAS").massUnits * 21L + PhysicalRegistry.get("ARGON").massUnits + PhysicalRegistry.get("NOBLE_GAS_MIX").massUnits;
        assertEquals(input, output);
        assertEquals(PhysicalRegistry.get("NOBLE_GAS_MIX").massUnits * 100L, PhysicalRegistry.get("ARGON").massUnits * 96L + PhysicalRegistry.get("NEON").massUnits * 2L + PhysicalRegistry.get("KRYPTON").massUnits + PhysicalRegistry.get("XENON").massUnits);
    }
    @Test public void generationRequestsRespectEverySelectedNameplate() {
        for(long watts : new long[] {40000, 200000, 750000}) {
            FluidTank input = tank("DIESEL", 100); FuelCredit credit = new FuelCredit();
            for(int i = 0; i < 200; i++) { long request = i % 3 == 0 ? 0 : i % 3 == 1 ? watts / 20L : watts / 10L; assertTrue(Combustion.generate(input, credit, request, 380000) <= watts / 10L); }
        }
    }
    @Test public void massRemaindersCannotCrossSubstances() {
        MassRemainder account = new MassRemainder(); account.convert("HYDROGEN", PhysicalRegistry.get("HYDROGEN_GAS").massUnits);
        assertEquals(0, account.remaining("OXYGEN")); assertEquals(0, account.preview("OXYGEN", 0));
    }
    @Test public void outputBudgetBoundsAllPortsAndBatteryChargingTogether() {
        OutputBudget budget = new OutputBudget(); budget.use(100, 75000, 50000); budget.use(100, 75000, 25000);
        assertEquals(0, budget.available(100, 75000));
        try { budget.use(100, 75000, 1); fail("nameplate exceeded"); } catch(IllegalArgumentException expected) { }
        assertEquals(75000, budget.available(101, 75000));
    }
    @Test public void exportBudgetSurvivesReloadInSameTick() throws Exception {
        OutputBudget budget = new OutputBudget(); budget.use(50, 20000, 16000);
        NBTTagCompound tag = new NBTTagCompound(); budget.write(tag); budget = new OutputBudget(); budget.read(roundTrip(tag));
        assertEquals(4000, budget.available(50, 20000)); assertEquals(20000, budget.available(51, 20000));
    }
    @Test public void combustionCannotRecoverTheSameChemicalEnergyTwice() {
        CombustionLedger ledger = new CombustionLedger(Long.MAX_VALUE, 380000, 500000);
        assertEquals(Long.MAX_VALUE, ledger.usefulMicrojoules + ledger.recoverableHeatMicrojoules + ledger.rejectedMicrojoules);
        try { new CombustionLedger(100, 600000, 600000); fail("duplicate recovery accepted"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void existingFluidDeclarationOrderIsUnchanged() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/hbm/inventory/fluid/Fluids.java")), java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^\\s*(\\w+)\\s*=\\s*new FluidType\\(").matcher(source);
        java.util.List<String> names = new java.util.ArrayList<String>();
        while(matcher.find()) if(!matcher.group(1).endsWith("_GAS") || !matcher.group(1).matches("(HYDROGEN|OXYGEN|NITROGEN)_GAS")) names.add(matcher.group(1));
        assertEquals(224, names.size());
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n", names).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(); for(byte value : hash) hex.append(String.format("%02x", value & 255));
        assertEquals("a628424d8f32be3cc376cc1084c1f44e47b3cda4cdb5913a487c1005024ca11a", hex.toString());
    }
    @Test public void unresolvedIsNotZero() {
        assertFalse(PhysicalRegistry.get("NITAN").isResolved()); assertFalse(PhysicalRegistry.get("COOLANT").isResolved());
        try { PhysicalRegistry.get("NITAN").chemicalMicrojoulesPerUnit(); fail("unresolved became zero"); } catch(IllegalStateException expected) { }
    }
    @Test public void oldWorldHasNoNewCredit() {
        FuelCredit credit = new FuelCredit(); credit.read(new NBTTagCompound()); assertEquals(0, credit.usefulMicrojoules());
    }
    @Test public void legacyNumericTankIdAndAmountRoundTripUnchanged() throws Exception {
        FluidTank old = tank("HYDROGEN", 317); int id = old.getTankType().getID();
        NBTTagCompound tag = new NBTTagCompound(); old.writeToNBT(tag, "old");
        FluidTank loaded = new FluidTank(Fluids.NONE, 4000); loaded.readFromNBT(roundTrip(tag), "old");
        assertEquals(id, loaded.getTankType().getID()); assertEquals(317, loaded.getFill());
        FluidType gas = tank("HYDROGEN_GAS", 0).getTankType(); assertFalse(loaded.configureForRecipe(gas, 0)); assertEquals(317, loaded.getFill()); assertEquals(id, loaded.getTankType().getID());
    }
    @Test public void compressorRejectsAnOutputTankHoldingTheOldPhase() {
        FluidTank input = tank("HYDROGEN", 1), output = tank("HYDROGEN", 317), gas = tank("HYDROGEN_GAS", 0);
        com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe recipe = new com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe(1, new com.hbm.inventory.FluidStack(gas.getTankType(), 798, 0));
        recipe.massConversion = true;
        assertFalse(com.hbm.inventory.recipes.CompressorRecipes.canAcceptOutput(input, output, recipe, new MassRemainder()));
        assertEquals(317, output.getFill());
        assertTrue(com.hbm.inventory.recipes.CompressorRecipes.canAcceptOutput(input, gas, recipe, new MassRemainder()));
    }
    @Test public void configurationReloadDoesNotReregisterProcessGases() throws Exception {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/hbm/inventory/fluid/Fluids.java")), java.nio.charset.StandardCharsets.UTF_8);
        String reload = source.substring(source.indexOf("public static void reloadFluids()"));
        for(String name : new String[] {"HYDROGEN_GAS", "OXYGEN_GAS", "NITROGEN_GAS"}) {
            assertFalse(reload.contains(name + " = new FluidType"));
            assertEquals(1, source.split(name + " = new FluidType", -1).length - 1);
        }
    }
    @Test public void customFluidRegistrationCannotReplaceAStoredBuiltinIdentity() {
        FluidTank original = tank("OXYGEN_GAS", 312);
        int id = original.getTankType().getID();
        try { new FluidType("CUSTOM_COLLISION", 0xffffff, 0, 0, 0, EnumSymbol.NONE, "water", 0xffffff, id, "Collision"); fail("builtin numeric ID replaced"); }
        catch(IllegalArgumentException expected) { }
        assertSame(original.getTankType(), Fluids.fromID(id)); assertEquals(312, original.getFill());
    }

    @Test public void quantityAndAssayFormattingUsesCanonicalBasis() {
        FluidType diesel = tank("DIESEL", 0).getTankType(), hydrogen = tank("HYDROGEN_GAS", 0).getTankType();
        assertEquals("1,842 / 4,000 L", com.hbm.inventory.fluid.FluidInfo.pair(diesel, 1842, 4000));
        assertEquals("420 / 2,000 standard L", com.hbm.inventory.fluid.FluidInfo.pair(hydrogen, 420, 2000));
        assertEquals("Capacity: 20 L", com.hbm.inventory.fluid.FluidInfo.containerQuantity(diesel, 0, 20));
        assertEquals("18 / 20 L", com.hbm.inventory.fluid.FluidInfo.containerQuantity(diesel, 18, 20));
        assertEquals("Capacity: 500 standard L", com.hbm.inventory.fluid.FluidInfo.containerQuantity(hydrogen, 0, 500));
        assertEquals("LHV: 35.819 MJ/L", com.hbm.inventory.fluid.FluidInfo.lhv(diesel.getPhysicalState()));
        assertEquals("Stored chemical energy: 644.742 MJ", com.hbm.inventory.fluid.FluidInfo.storedEnergy(diesel.getPhysicalState(), 18));
        assertNull(com.hbm.inventory.fluid.FluidInfo.lhv(PhysicalRegistry.get("NITAN")));
        assertNull(com.hbm.inventory.fluid.FluidInfo.storedEnergy(PhysicalRegistry.get("NITAN"), 100));
        assertNull(com.hbm.inventory.fluid.FluidInfo.storedEnergy(diesel.getPhysicalState(), 0));
        java.util.List<String> info = new java.util.ArrayList<>();
        com.hbm.inventory.fluid.FluidInfo.appendPhysical(info, hydrogen, true);
        assertTrue(info.contains("Canonical gas reference: 0 \u00b0C / 1 bar; not vessel volume"));
    }
    @Test public void revisedCapacityPreservesLegacyAmountIdentityPressureAndReload() throws Exception {
        FluidTank old = tank("DIESEL", 1842).withPressure(2); old.setFill(1842);
        NBTTagCompound tag = new NBTTagCompound(); old.writeToNBT(tag, "stock");
        FluidTank revised = new FluidTank(Fluids.NONE, 400).withDefaultCapacity();
        revised.readFromNBT(roundTrip(tag), "stock");
        assertEquals(400, revised.getMaxFill()); assertEquals(1842, revised.getFill());
        assertSame(old.getTankType(), revised.getTankType()); assertEquals(2, revised.getPressure());
        revised.resizePreservingContents(200); assertEquals(1842, revised.getFill());
        revised.writeToNBT(tag, "stock");
        FluidTank loaded = new FluidTank(Fluids.NONE, 200).withDefaultCapacity(); loaded.readFromNBT(roundTrip(tag), "stock");
        assertEquals(1842, loaded.getFill()); assertEquals(200, loaded.getMaxFill());
        // Missing saved capacity also cannot erase a valid amount.
        tag.removeTag("stock_max"); loaded.readFromNBT(tag, "stock"); assertEquals(1842, loaded.getFill());
    }
    @Test public void overCapReceiverRejectsFillsWithoutDeletingStock() {
        final FluidTank store = tank("DIESEL", 1842); store.resizePreservingContents(400);
        api.hbm.fluid.IFluidStandardTransceiver receiver = new api.hbm.fluid.IFluidStandardTransceiver() {
            public FluidTank[] getAllTanks() { return new FluidTank[] {store}; }
            public FluidTank[] getReceivingTanks() { return getAllTanks(); }
            public FluidTank[] getSendingTanks() { return getAllTanks(); }
            public boolean isLoaded() { return true; }
        };
        assertEquals(0, receiver.getDemand(store.getTankType(), 0));
        assertEquals(100, receiver.transferFluid(store.getTankType(), 0, 100)); assertEquals(1842, store.getFill());
        store.setFill(390); assertEquals(90, receiver.transferFluid(store.getTankType(), 0, 100)); assertEquals(400, store.getFill());
        assertEquals(Long.MAX_VALUE, receiver.transferFluid(store.getTankType(), 0, Long.MAX_VALUE));
    }
    @Test public void stampedContainersKeepUntaggedStockAndCannotMergeAcrossQuantities() throws Exception {
        net.minecraft.item.Item previous = com.hbm.items.ModItems.canister_full;
        try {
            net.minecraft.item.Item item = new net.minecraft.item.Item(); com.hbm.items.ModItems.canister_full = item;
            FluidType diesel = tank("DIESEL", 0).getTankType();
            com.hbm.inventory.FluidContainer descriptor = new com.hbm.inventory.FluidContainer(new net.minecraft.item.ItemStack(item), new net.minecraft.item.ItemStack(new net.minecraft.item.Item()), diesel, 1000);
            net.minecraft.item.ItemStack old = new net.minecraft.item.ItemStack(item), fresh = descriptor.fullContainer;
            assertEquals(20, descriptor.content); assertEquals(1000, descriptor.amount(old)); assertEquals(20, descriptor.amount(fresh));
            assertTrue(com.hbm.inventory.FluidContainerRegistry.matchesFilled(fresh, old));
            assertFalse(net.minecraft.item.ItemStack.areItemStackTagsEqual(fresh, old));
            NBTTagCompound tag = roundTrip(fresh.getTagCompound()); fresh.setTagCompound(tag);
            assertEquals(20, descriptor.amount(fresh)); assertEquals(1000, descriptor.amount(old));
            assertEquals(500, com.hbm.inventory.FluidContainerRegistry.normalizedCapacity(item, tank("HYDROGEN_GAS", 0).getTankType(), 1000));
            assertEquals(1000, com.hbm.inventory.FluidContainerRegistry.normalizedCapacity(item, tank("NITAN", 0).getTankType(), 1000));
        } finally { com.hbm.items.ModItems.canister_full = previous; }
    }
    @Test public void groundwaterRatingsAreBoundedAndIndependentOfBuffers() {
        assertEquals(100000, Groundwater.microlitersPerTick(10000, 2));
        assertEquals(25000, Groundwater.microlitersPerTick(2000, 0.5));
        assertEquals(0, Groundwater.microlitersPerTick(0, 2));
        assertEquals(0, Groundwater.microlitersPerTick(10000, Double.NaN));
        assertTrue(Groundwater.microlitersPerTick(1000, 200) < 20000);
        assertEquals(120000000, Groundwater.microlitersPerTick(10000, 2) * 20 * 60);
        assertEquals(50000, Groundwater.STEAM_PARCEL_SHAFT_JOULES * 1000 / Groundwater.STEAM_SHAFT_WATTS);
    }
    @Test public void idleGeneratorShowsItsInstalledRatedEfficiencyAndHidesAccounting() {
        GeneratorStats stats = new GeneratorStats(); FuelCredit credit = new FuelCredit();
        java.util.List<String> normal = java.util.Arrays.asList(stats.info(750000, credit, "DIESEL", 20, 380000, false));
        assertTrue(normal.contains("Efficiency: 38.0% rated")); assertTrue(normal.contains("Current output: 0 W"));
        assertTrue(normal.contains("Fuel flow: 0.0000 L/min")); assertFalse(String.join(" ", normal).contains("Chemical credit"));
        assertTrue(String.join(" ", stats.info(750000, credit, "DIESEL", 20, 380000, true)).contains("Chemical credit"));
    }

    @Test public void industrialUpgradeWorkIsBoundedAndTracksThroughput() {
        long base = IndustrialDemand.FABRICATION_W;
        for(int duration : new int[] {100, 75, 50, 25, 10, 1}) {
            long watts = IndustrialDemand.atSpeed(base, duration, 0);
            assertEquals(base * 100L, watts * duration);
            assertTrue(IndustrialDemand.atSpeed(base, duration, 3) >= watts * 7L / 10L);
            assertTrue(IndustrialDemand.atSpeed(base, duration, 3) <= watts);
        }
        assertTrue(IndustrialDemand.atSpeed(base, 0, 100) > 0);
    }
    @Test public void arcWorkEnvelopeScalesWithMassAndInstalledThroughput() {
        long mass = 10000000L;
        assertEquals(IndustrialDemand.arcWorkQuanta(mass) * 2L, IndustrialDemand.arcWorkQuanta(mass * 2L));
        assertEquals(IndustrialDemand.ARC_WORK_J_PER_KG * 20L, IndustrialDemand.arcWorkQuanta(mass));
        assertEquals(IndustrialDemand.arcRatedWatts(0) * 7L, IndustrialDemand.arcRatedWatts(3));
        assertEquals(IndustrialDemand.arcRatedWatts(3), IndustrialDemand.arcRatedWatts(100));
    }
    @Test public void coolingCirculationIsHeatDrivenRatherThanWaterConsumption() {
        assertEquals(0D, IndustrialDemand.circulationLitersPerMinute(0), 0D);
        assertEquals(60D, IndustrialDemand.circulationLitersPerMinute(83600), 0.000001D);
        assertEquals(600D, IndustrialDemand.circulationLitersPerMinute(836000), 0.000001D);
        assertTrue(IndustrialDemand.COOLING_CHARGE_L <= Groundwater.ELECTRIC_BUFFER);
        assertEquals(500L, IndustrialDemand.coolingAuxiliaryWatts(8000L));
        assertEquals(60000L, IndustrialDemand.coolingAuxiliaryWatts(3000000L));
    }
    @Test public void processPresentationMatchesMaximumPaidTickAndThermalRating() {
        for(ProcessRecipe recipe : PetroleumProcesses.defaults("refinery").values()) {
            ProcessBatch batch = new ProcessBatch(); batch.reserve(recipe);
            long maximumWork = 0, maximumHeat = 0;
            for(int tick = 0; tick < recipe.duration; tick++) {
                maximumWork = Math.max(maximumWork, batch.nextWork()); maximumHeat = Math.max(maximumHeat, batch.nextHeat());
                assertTrue(batch.canAdvance(tick, recipe.electricalRatingWatts / 10L, recipe.thermalRatingWatts / 10L));
                batch.advance(tick);
            }
            assertEquals(maximumWork * 10L, recipe.electricalRatingWatts);
            assertEquals(maximumHeat * 10L, recipe.thermalRatingWatts);
            java.util.List<String> lines = new java.util.ArrayList<>(); MachineInfo.process(lines, recipe);
            assertTrue(lines.contains("Process electrical demand: " + MachineInfo.watts(recipe.electricalRatingWatts)));
            assertTrue(lines.contains("Process thermal demand: " + MachineInfo.thermalWatts(recipe.thermalRatingWatts)));
            ProcessRecipe loaded = ProcessRecipe.read(writeProcessForInfo(recipe));
            assertEquals(recipe.electricalRatingWatts, loaded.electricalRatingWatts);
            assertEquals(recipe.rejectedWatts, loaded.rejectedWatts);
        }
    }
    private NBTTagCompound writeProcessForInfo(ProcessRecipe recipe) { NBTTagCompound tag = new NBTTagCompound(); recipe.write(tag); return tag; }
    @Test public void generatorPresentationUsesConfiguredRatingAndInstalledEfficiency() {
        GeneratorStats stats = new GeneratorStats(); FuelCredit credit = new FuelCredit();
        String[] lines = stats.info(750000, credit, "DIESEL", 100, 380000, false);
        assertEquals("Rated output: " + MachineInfo.watts(750000), lines[0]);
        assertTrue(java.util.Arrays.asList(lines).contains("Efficiency: 38.0% rated"));
        assertTrue(java.util.Arrays.asList(lines).contains("Current output: 0 W"));
        NBTTagCompound status = new NBTTagCompound(); stats.write(status, credit, 100000L, 270000);
        GeneratorStats client = new GeneratorStats(); client.read(status, new FuelCredit());
        String[] remote = client.info(750000L, credit, "DIESEL", 100, 380000, false);
        assertEquals("Rated output: " + MachineInfo.watts(100000L), remote[0]);
        assertTrue(java.util.Arrays.asList(remote).contains("Efficiency: 27.0% rated"));
    }
    @Test public void stirlingNameplatesUseThermalIntakeAndConfiguredEfficiency() {
        com.hbm.tileentity.machine.TileEntityStirling starter = new com.hbm.tileentity.machine.TileEntityStirling();
        assertEquals(10000L, starter.getRatedOutputWatts());
        assertEquals(66670L, starter.getRatedHeatInputWatts());
        assertEquals(0, starter.getOperatingEfficiencyPpm(20));
        assertTrue(starter.getOperatingEfficiencyPpm(21) < starter.getConfiguredEfficiencyPpm());
        assertEquals(starter.getConfiguredEfficiencyPpm(), starter.getOperatingEfficiencyPpm(900));
        java.util.List<String> info = MachineInfo.describe(starter, false);
        assertTrue(info.contains("Rated electrical output: " + MachineInfo.watts(starter.getRatedOutputWatts())));
        assertTrue(info.contains("Thermal intake ceiling: " + MachineInfo.thermalWatts(starter.getRatedHeatInputWatts())));
    }
}
