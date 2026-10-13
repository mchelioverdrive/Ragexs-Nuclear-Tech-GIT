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
}
