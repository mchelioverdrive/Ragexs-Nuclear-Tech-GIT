package com.hbm.realsim;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;
import net.minecraft.nbt.NBTTagCompound;

/** Exercises the same bills and ownership accounts used by native recipe adapters. */
public class FluidRecipeAccountingTest {
    private void assertClosed(ProcessRecipe bill) {
        long total = 0;
        for(long product : bill.productMass) total = Math.addExact(total, product);
        assertEquals(bill.id, bill.inputMass(), total);
        assertTrue(bill.id, bill.rejectedJ.compareTo(new BigDecimal("-0.000001")) >= 0);
        ProcessBatch batch = new ProcessBatch(); batch.reserve(bill);
        for(int tick = 0; tick < bill.duration; tick++) {
            assertTrue(batch.canAdvance(tick, batch.nextWork(), batch.nextHeat())); batch.advance(tick);
        }
        int[] counts = batch.finish(); long exported = 0;
        for(int i = 0; i < counts.length; i++) exported += counts[i] * PhysicalRegistry.get(bill.outputs[i]).massUnits + batch.products.remaining(bill.outputs[i]);
        assertEquals(bill.id, bill.inputMass(), exported);
    }
    @Test public void scrubberCaptureCannotClobberLegacyStockOrBorrowPower() {
        assertEquals(0, com.hbm.tileentity.machine.TileEntityAirScrubber.affordableScrubAmount(1000, 20000, 16000, 10000));
        assertEquals(0, com.hbm.tileentity.machine.TileEntityAirScrubber.affordableScrubAmount(-1, 0, 16000, 10000));
        assertEquals(19, com.hbm.tileentity.machine.TileEntityAirScrubber.affordableScrubAmount(1000, 0, 16000, 199));
        assertEquals(1, com.hbm.tileentity.machine.TileEntityAirScrubber.affordableScrubAmount(Integer.MAX_VALUE, 15999, 16000, Long.MAX_VALUE));
    }
    @Test public void specialtyFactoriesReportEveryInvalidBillTogether() throws Exception {
        java.util.List<String> failures=new java.util.ArrayList<>();
        for(Class<?> family:new Class<?>[]{SpecialistProcesses.class,AncillaryProcesses.class,OrganicProcesses.class,UtilityMixtures.class}) {
            for(java.lang.reflect.Method method:family.getDeclaredMethods()) {
                if(!java.lang.reflect.Modifier.isPublic(method.getModifiers()) || method.getReturnType()!=ProcessRecipe.class) continue;
                Class<?>[] args=method.getParameterTypes();
                if(args.length>1 || args.length==1 && args[0]!=int.class && args[0]!=boolean.class) continue;
                int variants=args.length==0?1:args[0]==boolean.class?2:method.getName().equals("reprocess")?6:method.getName().equals("rareEarthSeparation")?4:3;
                for(int variant=0;variant<variants;variant++) try {
                    Object[] values=args.length==0?new Object[0]:new Object[]{args[0]==boolean.class?(Object)(variant==1):(Object)variant};
                    ProcessRecipe bill=(ProcessRecipe)method.invoke(null,values);
                    ChemicalFuelProcesses.validateRating(bill);
                    int solid=0,fluid=0;
                    for(String output:bill.outputs) if(!output.startsWith("VENT_")) { if(PhysicalRegistry.get(output).phase==PhysicalState.Phase.SOLID) solid++;else fluid++; }
                    if(solid>4 || fluid>4 || bill.rejectedWatts>200000) failures.add(bill.id+" excessive ports/cooling: "+solid+"/"+fluid+"/"+bill.rejectedWatts);
                } catch(Exception error) { failures.add(family.getSimpleName()+"."+method.getName()+"["+variant+"]: "+(error.getCause()==null?error:error.getCause())); }
            }
        }
        assertTrue(failures.toString(),failures.isEmpty());
    }
    @Test public void allReleasedChemistryClosesMassAndUtilityOwnership() {
        for(ProcessRecipe recipe : ChemistryProcesses.defaults().values()) assertClosed(recipe);
        for(ProcessRecipe recipe:UtilityMixtures.mixers().values()) assertClosed(recipe);
        assertClosed(AncillaryProcesses.dairy());assertClosed(AncillaryProcesses.fullereneSuspension());assertClosed(AncillaryProcesses.radioLigand());
        assertClosed(MineralChemicalProcesses.lithiumElectrolysis()); assertClosed(MineralChemicalProcesses.preciousMetalElectrolysis(true)); assertClosed(MineralChemicalProcesses.preciousMetalElectrolysis(false)); assertClosed(ChemistryProcesses.ionGel()); assertClosed(ChemistryProcesses.oxyhydrogen()); assertClosed(ChemistryProcesses.slopTreatment());
        assertClosed(ChemistryProcesses.chloralkali(false)); assertClosed(ChemistryProcesses.brine());
        assertClosed(ChemistryProcesses.fluorideElectrolysis()); assertClosed(ChemistryProcesses.heavyElectrolysis());
        assertClosed(ChemistryProcesses.heavyWater()); assertClosed(ChemistryProcesses.heavyWaterTower());
        assertClosed(ChemistryProcesses.coffee()); assertClosed(ChemistryProcesses.tea()); assertClosed(ChemistryProcesses.colloid());
        assertClosed(ChemistryProcesses.chlorocalciteSolution()); assertClosed(ChemistryProcesses.chlorocalciteConditioning()); assertClosed(ChemistryProcesses.chlorocalciteClarification());
        assertClosed(ChemistryProcesses.paper()); assertClosed(ChemistryProcesses.airSeparation()); assertClosed(ChemistryProcesses.radiolysis());
        assertClosed(ChemistryProcesses.strontiumElectrolysis()); assertClosed(ChemistryProcesses.calciumElectrolysis());
        assertClosed(ChemistryProcesses.moltenSaltElectrolysis()); assertClosed(ChemistryProcesses.potassiumChlorideElectrolysis());
        assertClosed(ChemistryProcesses.meltLye()); assertClosed(ChemistryProcesses.potashLyeMelting());
        assertClosed(ChemistryProcesses.additiveDoses()); assertClosed(ChemistryProcesses.frackingSlurry());
    }
    @Test public void releasedReactorBillsFitPortsPowerFlowAndSingleMachineRejection() throws Exception {
        java.util.List<ProcessRecipe> bills = new java.util.ArrayList<>(ChemistryProcesses.defaults().values());
        bills.add(ChemistryProcesses.phosgene());
        for(java.lang.reflect.Method method : ChemicalFuelProcesses.class.getDeclaredMethods()) if(method.getParameterTypes().length == 0 && method.getReturnType() == ProcessRecipe.class) bills.add((ProcessRecipe) method.invoke(null));
        java.util.List<String> excessiveRejection = new java.util.ArrayList<>();
        for(ProcessRecipe bill : bills) {
            ChemicalFuelProcesses.validateRating(bill);
            int fluidProducts = 0;
            for(String name : bill.outputs) if(!name.startsWith("VENT_") && PhysicalRegistry.get(name).phase != PhysicalState.Phase.SOLID) fluidProducts++;
            assertTrue(bill.id, bill.inputs.length <= 4 && fluidProducts <= 4);
            if(bill.rejectedWatts > 200000) excessiveRejection.add(bill.id + " " + bill.rejectedWatts + " W");
        }
        assertTrue("Bills exceeding integral dry-cooler duty: " + excessiveRejection, excessiveRejection.isEmpty());
    }
    @Test public void gasSeparatorRejectsPhaseCopyingAndUnbalancedConfiguredSplits() {
        com.hbm.inventory.fluid.FluidType air = fluid("AIR");
        com.hbm.util.Tuple.Quartet<com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack, com.hbm.inventory.FluidStack> outputs = new com.hbm.util.Tuple.Quartet<>(
                new com.hbm.inventory.FluidStack(fluid("NITROGEN_GAS"), 77), new com.hbm.inventory.FluidStack(fluid("OXYGEN_GAS"), 21),
                new com.hbm.inventory.FluidStack(fluid("ARGON"), 1), new com.hbm.inventory.FluidStack(fluid("NOBLE_GAS_MIX"), 1));
        ProcessRecipe bill = com.hbm.inventory.recipes.CryoRecipes.physicalBill(air, outputs); assertClosed(bill);
        assertEquals(6000, bill.quantities[0] * 1200 / bill.duration); assertEquals(50000, bill.electricalRatingWatts);
        outputs.getW().type = fluid("NITROGEN");
        try { com.hbm.inventory.recipes.CryoRecipes.physicalBill(air, outputs); fail("Copied gas/liquid counts accepted"); } catch(IllegalArgumentException expected) { }
        outputs.getW().type = fluid("NITROGEN_GAS"); outputs.getW().fill++;
        try { com.hbm.inventory.recipes.CryoRecipes.physicalBill(air, outputs); fail("Mass-creating split accepted"); } catch(IllegalArgumentException expected) { }
    }
    @Test public void geologicalHalogensCannotBeManufacturedFromPlainSaltBrine() {
        ProcessRecipe treatment = ChemistryProcesses.waterTreatment(), separation = ChemistryProcesses.mineralBrineSeparation();
        assertEquals("MINERAL_BRINE", treatment.outputs[1]); assertEquals("MINERAL_BRINE", separation.inputs[0]);
        assertEquals("BRINE", ChemistryProcesses.brine().outputs[0]);
        long iodideInFeed = new BigDecimal(ChemistryProcesses.mass("MINERAL_BRINE", 10)).multiply(new BigDecimal(".00005")).longValueExact();
        assertEquals(iodideInFeed, new BigDecimal(separation.productMass[2]).multiply(new BigDecimal(".01")).longValueExact());
        assertClosed(ChemistryProcesses.halogenRecovery(true, false)); assertClosed(ChemistryProcesses.halogenRecovery(false, false));
    }
    @Test public void electrolysisUsesMolarMassAndTheSameWorkAtBothScales() {
        ProcessRecipe early = ChemistryProcesses.electrolysis(8000), industrial = ChemistryProcesses.electrolysis(20000);
        assertArrayEquals(early.productMass, industrial.productMass); assertEquals(early.workQuanta, industrial.workQuanta);
        double h = early.productMass[0] / (double) PhysicalRegistry.get("HYDROGEN_GAS").massUnits;
        double o = early.productMass[1] / (double) PhysicalRegistry.get("OXYGEN_GAS").massUnits;
        assertEquals(2, h / o, 1E-9);
        // Bills round upward to whole joules; check the exact budget within that declared quantum.
        BigDecimal expectedJ = BigDecimal.valueOf(early.productMass[0]).multiply(new BigDecimal("198000000"))
                .divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG), 18, java.math.RoundingMode.DOWN);
        BigDecimal actualJ = BigDecimal.valueOf(early.workQuanta).divide(new BigDecimal("2"));
        assertTrue(actualJ.compareTo(expectedJ) >= 0);
        assertTrue(actualJ.subtract(expectedJ).compareTo(BigDecimal.ONE) < 0);
        assertTrue(early.duration > industrial.duration);
    }
    @Test public void electrolysisReleasesOnlyPaidMaterialBeforeTheWholeLitreFinishesAndSurvivesReload() {
        ProcessRecipe recipe = ChemistryProcesses.electrolysis(8000); assertTrue(recipe.continuousOutputs);
        ProcessBatch batch = new ProcessBatch(); batch.reserve(recipe); long[] exported = new long[recipe.outputs.length]; long paid = 0;
        for(int tick = 0; tick < recipe.duration; tick++) {
            paid += batch.nextWork(); batch.advance(tick);
            int[] products = batch.releaseProgress();
            for(int i = 0; i < products.length; i++) exported[i] += products[i] * PhysicalRegistry.get(recipe.outputs[i]).massUnits;
            if(tick == 99) {
                assertTrue("First gas must not wait for an entire litre", exported[0] > 0 && exported[1] > 0);
                assertTrue(paid < recipe.workQuanta);
                for(int i = 0; i < exported.length; i++) assertEquals(recipe.productMass[i], exported[i] + batch.products.remaining(recipe.outputs[i]) + batch.pendingMass(batch.recipe(), i));
                NBTTagCompound saved = new NBTTagCompound(); batch.write(saved); batch = new ProcessBatch(); batch.read(saved);
                assertTrue(batch.recipe().continuousOutputs); assertEquals(100, batch.ticks());
                assertArrayEquals(new int[] {0, 0}, batch.releaseProgress());
            }
        }
        assertEquals(recipe.workQuanta, paid);
        int[] finalProducts = batch.finish(); long mass = 0;
        for(int i = 0; i < exported.length; i++) mass += exported[i] + finalProducts[i] * PhysicalRegistry.get(recipe.outputs[i]).massUnits + batch.products.remaining(recipe.outputs[i]);
        assertEquals(recipe.inputMass(), mass);
    }
    @Test public void progressiveOutputCannotBorrowAnUnpaidRoundingQuantum() {
        ProcessRecipe bill = new ProcessRecipe("test:metered_transfer", new String[] {"FRESH_WATER"}, new int[] {4}, new int[] {0}, null, 0,
                new String[] {"FRESH_WATER"}, new long[] {ChemistryProcesses.mass("FRESH_WATER", 4)}, 4, 1, 0, 20, true).continuous();
        ProcessBatch batch = new ProcessBatch(); batch.reserve(bill);
        assertEquals(0, batch.nextWork()); batch.advance(0);
        assertArrayEquals(new int[] {0}, batch.releaseProgress()); assertEquals(0, batch.products.remaining("FRESH_WATER"));
        assertEquals(1, batch.nextWork()); batch.advance(1); assertArrayEquals(new int[] {2}, batch.releaseProgress());
    }
    @Test public void phasesAndPressureNeverUseEqualCountConversion() {
        for(String[] pair : new String[][] {{"HYDROGEN", "HYDROGEN_GAS"}, {"OXYGEN", "OXYGEN_GAS"}, {"NITROGEN", "NITROGEN_GAS"}}) {
            PhysicalState liquid = PhysicalRegistry.get(pair[0]), gas = PhysicalRegistry.get(pair[1]);
            assertEquals(PhysicalState.Basis.LIQUID_LITER, liquid.basis); assertEquals(PhysicalState.Basis.STANDARD_GAS_LITER, gas.basis);
            assertTrue(liquid.massUnits > gas.massUnits * 500);
            MassRemainder remainder = new MassRemainder(); int count = remainder.convert(pair[1], liquid.massUnits);
            assertEquals(liquid.massUnits, count * gas.massUnits + remainder.remaining(pair[1]));
        }
    }
    @Test public void ammoniaAndMethanolUseMolesNotEqualMasses() {
        ProcessRecipe ammonia = ChemistryProcesses.ammonia();
        assertArrayEquals(new int[] {100, 300}, ammonia.quantities);
        assertEquals(200, ammonia.productMass[0] / (double) PhysicalRegistry.get("AMMONIA").massUnits, 1E-8);
        assertClosed(ChemicalFuelProcesses.methanol()); assertClosed(ChemicalFuelProcesses.methaneReforming());
    }
    @Test public void treatmentRejectAndEvaporationOwnAllWater() {
        ProcessRecipe treatment = ChemistryProcesses.waterTreatment();
        assertEquals(9, treatment.productMass[0] / PhysicalRegistry.get("FRESH_WATER").massUnits);
        assertEquals(12, treatment.quantities[0] * 1200D / treatment.duration, 1E-9);
        ProcessRecipe evaporation = ChemistryProcesses.evaporation();
        assertTrue(evaporation.heatQuanta > 4000000); assertEquals("STEAM", evaporation.outputs[1]);
        assertClosed(evaporation);
    }
    @Test public void extraFeedsAndCoproductsSnapshotAcrossReload() {
        ProcessRecipe recipe = ChemistryProcesses.sulfuric(); NBTTagCompound saved = new NBTTagCompound();
        recipe.write(saved); ProcessRecipe loaded = ProcessRecipe.read(saved);
        assertArrayEquals(recipe.productMass, loaded.productMass);
        recipe = ChemistryProcesses.hydrochloric(); recipe.write(saved); loaded = ProcessRecipe.read(saved);
        assertEquals(3, loaded.inputs.length); assertArrayEquals(recipe.quantities, loaded.quantities);
        assertClosed(loaded);
        recipe = ChemistryProcesses.sulfateReduction(); recipe.write(saved); loaded = ProcessRecipe.read(saved);
        assertArrayEquals(recipe.solidInputs, loaded.solidInputs); assertArrayEquals(recipe.solidCounts, loaded.solidCounts);
        assertClosed(loaded);
    }
    private com.hbm.inventory.fluid.FluidType fluid(String name) {
        return new com.hbm.inventory.fluid.FluidType(name, 0, 0, 0, 0, com.hbm.render.util.EnumSymbol.NONE);
    }
    @Test public void blockedCoproductCannotConsumeFeedAndPaidBatchesIgnoreReplacement() {
        com.hbm.inventory.fluid.tank.FluidTank input = new com.hbm.inventory.fluid.tank.FluidTank(fluid("WATER"), 20);
        com.hbm.inventory.fluid.tank.FluidTank clean = new com.hbm.inventory.fluid.tank.FluidTank(fluid("FRESH_WATER"), 9);
        com.hbm.inventory.fluid.tank.FluidTank reject = new com.hbm.inventory.fluid.tank.FluidTank(fluid("MINERAL_BRINE"), 2);
        input.setFill(10); clean.setFill(9);
        ProcessBatch batch = new ProcessBatch(); ProcessRecipe recipe = ChemistryProcesses.waterTreatment();
        com.hbm.inventory.fluid.tank.FluidTank[] outputs = {clean, reject}, inputs = {input};
        assertFalse(ProcessMachine.reserveInputs(batch, recipe, inputs, outputs, null, 0, -1, 0, -1));
        assertEquals(10, input.getFill()); assertFalse(batch.active());
        clean.setFill(0); assertTrue(ProcessMachine.reserveInputs(batch, recipe, inputs, outputs, null, 0, -1, 0, -1));
        assertEquals(0, input.getFill());
        NBTTagCompound save = new NBTTagCompound(); batch.write(save); batch = new ProcessBatch(); batch.read(save);
        assertTrue(ProcessMachine.configure(batch, ChemistryProcesses.hydrochloric(), outputs));
        assertEquals(recipe.id, batch.recipe().id); assertEquals("FRESH_WATER", clean.getTankType().getName());
        assertFalse(ProcessMachine.outputReady(batch, batch.recipe(), new com.hbm.inventory.fluid.tank.FluidTank[] {clean}, null, 0, -1));
        assertTrue(batch.active()); assertEquals(recipe.id, batch.recipe().id);
        clean.setFill(9); assertFalse(ProcessMachine.outputReady(batch, batch.recipe(), outputs, null, 0, -1));
        assertEquals(0, batch.ticks());
    }
    @Test public void eightLanesUseOneSealedChargeWithoutWaterOrSteamConsumption() {
        com.hbm.inventory.fluid.Fluids.NONE = fluid("NONE");
        com.hbm.inventory.fluid.Fluids.FRESH_WATER = fluid("FRESH_WATER");
        com.hbm.inventory.fluid.Fluids.SPENTSTEAM = fluid("SPENTSTEAM");
        class Factory extends com.hbm.tileentity.machine.TileEntityMachineChemfac {
            long available(int lanes) { return additionalResourceFullTicks(lanes); }
            void run(int lanes) { consumeAdditionalResources(20, lanes); }
        }
        Factory factory = new Factory(); assertEquals(8, factory.getRecipeCount());
        assertEquals(0, factory.available(8)); factory.water.setFill(20); factory.steam.setFill(123);
        for(int lane = 0; lane < 8; lane++) {
            assertNotSame(factory.processInputs(lane)[0], factory.processInputs(lane)[2]);
            assertNotSame(factory.processOutputs(lane)[0], factory.processOutputs(lane)[2]);
            if(lane > 0) assertNotSame(factory.processInputs(0)[2], factory.processInputs(lane)[2]);
        }
        assertEquals(Long.MAX_VALUE, factory.available(8)); factory.run(8);
        assertEquals(20, factory.water.getFill()); assertEquals(123, factory.steam.getFill());
    }
    @Test public void reducedProcessBuffersKeepLegacyOverCapStocks() {
        com.hbm.inventory.fluid.tank.FluidTank original = new com.hbm.inventory.fluid.tank.FluidTank(fluid("FRESH_WATER"), 24000);
        original.setFill(15000); NBTTagCompound save = new NBTTagCompound(); original.writeToNBT(save, "feed");
        com.hbm.inventory.fluid.tank.FluidTank loaded = new com.hbm.inventory.fluid.tank.FluidTank(original.getTankType(), 120).withDefaultCapacity();
        loaded.readFromNBT(save, "feed"); assertEquals(120, loaded.getMaxFill()); assertEquals(15000, loaded.getFill());
        loaded.resizePreservingContents(60); assertEquals(15000, loaded.getFill());
        assertFalse(loaded.configureForRecipe(fluid("BRINE"), 0));
    }
    @Test public void extractionCannotRelabelLegacyStockClampItOrLoseABlockedCoproduct() {
        com.hbm.inventory.fluid.FluidType oil = fluid("OIL"), gas = fluid("RAW_NATURAL_GAS"), methane = fluid("GAS");
        com.hbm.inventory.fluid.tank.FluidTank a = new com.hbm.inventory.fluid.tank.FluidTank(oil, 6), b = new com.hbm.inventory.fluid.tank.FluidTank(gas, 6);
        a.setFill(64000); assertFalse(com.hbm.tileentity.machine.oil.TileEntityOilDrillBase.storeExtractionProducts(a, oil, 1, b, gas, 1));
        assertEquals(64000, a.getFill()); assertEquals(0, b.getFill()); a.setFill(0); b.setFill(6);
        assertFalse(com.hbm.tileentity.machine.oil.TileEntityOilDrillBase.storeExtractionProducts(a, oil, 1, b, gas, 1)); assertEquals(0, a.getFill());
        b.setFill(1); assertFalse(com.hbm.tileentity.machine.oil.TileEntityOilDrillBase.storeExtractionProducts(a, oil, 1, b, methane, 1));
        assertSame(gas, b.getTankType()); assertEquals(1, b.getFill()); assertEquals(0, a.getFill());
        assertTrue(com.hbm.tileentity.machine.oil.TileEntityOilDrillBase.storeExtractionProducts(a, oil, 1, b, gas, 1)); assertEquals(1, a.getFill()); assertEquals(2, b.getFill());
    }
    @Test public void fabricationRetainsCleaningBathAndFingerprintsActualFluidBills() {
        com.hbm.inventory.RecipesCommon.AStack[] empty = new com.hbm.inventory.RecipesCommon.AStack[0];
        com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe recipe = new com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe(
                new net.minecraft.item.ItemStack(new net.minecraft.item.Item()), 300, 1000, new com.hbm.inventory.FluidStack(fluid("SOLVENT"), 2), empty, empty, empty);
        recipe.retainFluid = true;
        com.hbm.tileentity.machine.TileEntityMachineSolderingStation machine = new com.hbm.tileentity.machine.TileEntityMachineSolderingStation();
        machine.tank.configureForRecipe(recipe.fluid.type, 0); machine.tank.setFill(8);
        machine.consumeItems(recipe); assertEquals(8, machine.tank.getFill());
        com.hbm.inventory.recipes.SolderingRecipes codec = new com.hbm.inventory.recipes.SolderingRecipes();
        String retained = codec.accountingKey(recipe); recipe.retainFluid = false;
        assertNotEquals(retained, codec.accountingKey(recipe)); machine.consumeItems(recipe); assertEquals(6, machine.tank.getFill());
        String consumed = codec.accountingKey(recipe); recipe.fluid.fill++;
        assertNotEquals(consumed, codec.accountingKey(recipe));
        // Filled identifier selection must leave all stock intact, even if slots are unavailable.
        assertFalse(machine.tank.setType(0, new net.minecraft.item.ItemStack[0])); assertEquals(6, machine.tank.getFill());
    }
    @Test public void rotaryDriveReturnsEverySteamFractionAndBlocksWithoutTakingMore() throws Exception {
        com.hbm.tileentity.machine.TileEntityMachineRotaryFurnace machine = new com.hbm.tileentity.machine.TileEntityMachineRotaryFurnace();
        machine.tanks[1] = new com.hbm.inventory.fluid.tank.FluidTank(fluid("STEAM"), 200);
        machine.tanks[2] = new com.hbm.inventory.fluid.tank.FluidTank(fluid("SPENTSTEAM"), 1);
        machine.tanks[1].setFill(150);
        for(int tick = 1; tick <= 99; tick++) assertTrue(machine.driveTick(tick, 1));
        assertEquals(51, machine.tanks[1].getFill()); assertEquals(0, machine.tanks[2].getFill());
        java.lang.reflect.Field ownership = machine.getClass().getDeclaredField("drive"); ownership.setAccessible(true);
        NBTTagCompound saved = new NBTTagCompound(); ((ThermalAccount) ownership.get(machine)).write(saved);
        com.hbm.tileentity.machine.TileEntityMachineRotaryFurnace loaded = new com.hbm.tileentity.machine.TileEntityMachineRotaryFurnace();
        ((ThermalAccount) ownership.get(loaded)).read(saved);
        // Native drive adapter and its actual saved ownership account, without starting a world.
        loaded.tanks[1] = machine.tanks[1]; loaded.tanks[2] = machine.tanks[2];
        assertFalse(loaded.driveTick(99, 1)); assertTrue(loaded.driveTick(100, 1));
        assertEquals(50, loaded.tanks[1].getFill()); assertEquals(1, loaded.tanks[2].getFill());
        assertFalse(loaded.driveTick(101, 1)); assertEquals(50, loaded.tanks[1].getFill());
        assertEquals(SteamLedger.State.ORDINARY.mass(150), SteamLedger.State.ORDINARY.mass(50) + SteamLedger.State.SPENT.mass(1));
    }
    @Test public void atmosphericAdmissionRejectsVacuumOverflowAndSameTickReload() {
        com.hbm.inventory.fluid.FluidType air = fluid("AIR");
        com.hbm.inventory.fluid.tank.FluidTank tank = new com.hbm.inventory.fluid.tank.FluidTank(air, 10); OutputBudget budget = new OutputBudget();
        assertFalse(com.hbm.tileentity.machine.TileEntityAtmoExtractor.admitAtmosphericParcel(tank, budget, 40, null, 5)); assertEquals(0, tank.getFill());
        assertTrue(com.hbm.tileentity.machine.TileEntityAtmoExtractor.admitAtmosphericParcel(tank, budget, 40, air, 5));
        NBTTagCompound saved = new NBTTagCompound(); budget.write(saved); budget = new OutputBudget(); budget.read(saved);
        assertFalse(com.hbm.tileentity.machine.TileEntityAtmoExtractor.admitAtmosphericParcel(tank, budget, 40, air, 5)); assertEquals(5, tank.getFill());
        tank.setFill(50000); assertFalse(com.hbm.tileentity.machine.TileEntityAtmoExtractor.admitAtmosphericParcel(tank, budget, 41, air, 5)); assertEquals(50000, tank.getFill());
    }
    @Test public void rejectedReactorOverrideCannotLeakIntoEitherActiveRegistry() throws Exception {
        ProcessRecipe excessive = new ProcessRecipe("test:five_feeds", new String[] {"WATER", "WATER", "WATER", "WATER", "WATER"},
                new int[] {1, 1, 1, 1, 1}, new int[5], null, 0, new String[] {"FRESH_WATER"}, new long[] {ChemistryProcesses.mass("WATER", 5)}, 100, 0, 0, 20, true);
        java.io.StringWriter text = new java.io.StringWriter(); com.google.gson.stream.JsonWriter writer = new com.google.gson.stream.JsonWriter(text);
        writer.beginObject(); writer.name("id").value(900001); writer.name("name").value("test"); writer.name("duration").value(100);
        PetroleumProcesses.write(excessive, writer);
        for(String key : new String[] {"fluidInput", "itemInput", "fluidOutput", "itemOutput"}) writer.name(key).beginArray().endArray();
        writer.endObject(); writer.close();
        int before = com.hbm.inventory.recipes.ChemplantRecipes.recipes.size(), indexBefore = com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.size();
        try { new com.hbm.inventory.recipes.ChemplantRecipes().readRecipe(new com.google.gson.JsonParser().parse(text.toString())); fail("Excessive ports accepted"); }
        catch(IllegalArgumentException expected) { }
        assertEquals(before, com.hbm.inventory.recipes.ChemplantRecipes.recipes.size());
        assertEquals(indexBefore, com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.size());
        assertFalse(com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.containsKey(900001));
    }
    @Test public void chemicalPhysicalJsonPreservesEveryFeedProductAndProgressMode() throws Exception {
        for(ProcessRecipe recipe : ChemistryProcesses.defaults().values()) {
            java.io.StringWriter text = new java.io.StringWriter(); com.google.gson.stream.JsonWriter writer = new com.google.gson.stream.JsonWriter(text);
            writer.beginObject(); PetroleumProcesses.write(recipe, writer); writer.endObject(); writer.close();
            ProcessRecipe loaded = PetroleumProcesses.read(new com.google.gson.JsonParser().parse(text.toString()).getAsJsonObject());
            assertEquals(recipe.continuousOutputs, loaded.continuousOutputs); assertArrayEquals(recipe.solidInputs, loaded.solidInputs); assertArrayEquals(recipe.solidCounts, loaded.solidCounts);
            assertArrayEquals(recipe.productMass, loaded.productMass); assertArrayEquals(recipe.quantities, loaded.quantities); assertEquals(recipe.workQuanta, loaded.workQuanta); assertEquals(recipe.heatQuanta, loaded.heatQuanta);
        }
    }
    @Test public void potassiumRecoveryReturnsCarrierAndExcessSaltsAtTheAssayedConcentration() {
        ProcessRecipe dry = ChemistryProcesses.fertilizer(false); assertClosed(dry);
        assertEquals(ChemistryProcesses.mass("POTASSIUM_CHLORIDE", 5), dry.productMass[0] + dry.productMass[1]);
        assertEquals(.20, dry.productMass[0] / (double) dry.inputMass(), 1E-12);
        assertEquals(4, ChemistryProcesses.fertilizer(true).productMass[0] / PhysicalRegistry.get("FERTILIZER_ITEM").massUnits);
        for(boolean ammonium : new boolean[] {true, false}) {
            ProcessRecipe bill = ChemistryProcesses.potassiumNitrate(ammonium); assertClosed(bill);
            assertEquals(5, bill.quantities[0]); assertTrue(bill.productMass[2] > 0); assertTrue(bill.productMass[3] > 0);
            long consumedKCl = bill.inputMass() - ChemistryProcesses.mass(bill.solidInput, 1) - bill.productMass[2];
            double molesKCl = consumedKCl / (double) PhysicalState.MASS_UNITS_PER_KG * .20 * 1000 / 74.5513;
            assertEquals(molesKCl, bill.productMass[0] / (double) PhysicalState.MASS_UNITS_PER_KG * 1000 / 101.1032, 1E-9);
        }
    }
    @Test public void constructionAndIceHaveDeclaredWaterMassInsteadOfBucketTokens() {
        assertEquals(1, ChemistryProcesses.concreteWater(4));
        assertEquals(16, ChemistryProcesses.concreteWater(64));
        assertEquals(0.5, ChemistryProcesses.concreteWater(4) / (4 * PhysicalRegistry.get("CEMENT_ITEM").kilogramsPerUnit()), 1E-9);
        for(String identity : new String[] {"ICE_BLOCK", "PACKED_ICE_BLOCK", "SNOW_BLOCK", "SNOWBALL_ITEM", "DRY_ICE_BLOCK", "SODIUM_ITEM", "POTASSIUM_ITEM"}) assertClosed(ChemistryProcesses.melting(identity));
        assertClosed(ChemistryProcesses.metalCooling(false)); assertClosed(ChemistryProcesses.metalCooling(true));
        assertClosed(ChemistryProcesses.freezing(false)); assertClosed(ChemistryProcesses.freezing(true));
        assertEquals(1, ChemistryProcesses.melting("ICE_BLOCK").productMass[0] / PhysicalRegistry.get("WATER").massUnits);
        assertTrue(ChemistryProcesses.melting("ICE_BLOCK").workQuanta >= 2 * 417300);
        assertEquals("DEPLETED_WATER", ChemistryProcesses.heavyWater().outputs[1]);
    }
    @Test public void parallelCoolingSharesOnePersistedNameplate() {
        OutputBudget cooling = new OutputBudget();
        for(int lane = 0; lane < 8; lane++) {
            assertEquals(100000 - lane * 12500, cooling.available(42, 100000));
            cooling.use(42, 100000, 12500);
        }
        assertEquals(0, cooling.available(42, 100000));
        NBTTagCompound save = new NBTTagCompound(); cooling.write(save); cooling = new OutputBudget(); cooling.read(save);
        assertEquals(0, cooling.available(42, 100000)); assertEquals(100000, cooling.available(43, 100000));
    }
    @Test public void normalizedChemistryFeedGraphHasNoBootstrapCycle() {
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList("WATER", "AIR", "SALT_ITEM", "SULFUR_ITEM", "BORON_ITEM", "FLUORITE_ITEM", "AMMONIUM_CHLORIDE_ITEM", "GAS", "CHAR_ITEM", "SODIUM_SULFATE_ITEM", "POTASH_ITEM", "QUICKLIME_ITEM", "STRONTIUM_OXIDE_ITEM", "BARIUM_SULFIDE_ITEM", "BARIUM_OXIDE_ITEM", "ALUMINIUM_ITEM", "GADOLINIUM_ITEM", "RUTHENIUM_RESIDUE_ITEM", "UNSATURATEDS", "URANIUM_ORE_ITEM", "PLUTONIUM_ITEM", "CHLOROCALCITE_ITEM", "FLUX_ITEM", "BAUXITE_ORE_ITEM", "COLTAN_ORE_ITEM", "AROMATICS", "COAL_ITEM", "SOLVENT", "GALLIUM_NUGGET_ITEM", "ARSENIC_NUGGET_ITEM", "CARNALLITE_ITEM", "TITANIUM_TRACE_ITEM", "OSIR_CONCENTRATE_ITEM", "RH_IR_CONCENTRATE_ITEM", "LEPIDOLITE_ITEM", "POLLUCITE_ITEM", "RAW_NATURAL_GAS", "KRYPTON", "XENON", "ACIDWASTE", "SODIUM_NITRATE_ITEM"));
        // Explicit boundaries: these solids originate in mining, vanilla crafting, existing metallurgical/optical
        // preparation, irradiation/fuel use or biological collection. They are not silently invented fluid sources.
        available.addAll(java.util.Arrays.asList("AGGREGATE_COARSE_ITEM","AGGREGATE_FINE_ITEM","CEMENT_ITEM","ASBESTOS_ITEM","DU_BILLET_ITEM",
                "ITEM:regolith_feed","ITEM:powder_desh_mix","ITEM:ingot_chromium","ITEM:ingot_vanadium","ITEM:powder_nitan_mix",
                "MILK","ITEM:vanilla.egg","ITEM:strawberry","ITEM:particle_empty","ITEM:block.sand_lead","ITEM:ammo_arty@0","GLASS_PANE_ITEM","CARBON_WIRE_ITEM",
                "ITEM:fragment_samarium","ITEM:europium_dust_tiny","ITEM:terbiumsol","ITEM:dysprosiumsol","ITEM:lutetiumsol","ITEM:thuliumsol",
                "ITEM:powder_ytterbium_oxide","ITEM:powder_yttrium","ITEM:powder_ytterbium_tiny","ITEM:powder_erbium_tiny","ITEM:ingot_titanium",
                "ITEM:ingot_phosphorus","ITEM:gem_sapphire","ITEM:nugget_niobium","ITEM:block.glass_quartz","ITEM:powder_cerium","ITEM:barium_nugget",
                "ITEM:ingot_praseodymium","ITEM:ingot_dysprosium","ITEM:ingot_europium","ITEM:ingot_terbium","ITEM:powder_zircon","ITEM:magnesium_ingot",
                "ITEM:block.ore_beryllium","ITEM:block.ore_magnesite","ITEM:powder_spent_haleu","ITEM:powder_spent_leu","ITEM:powder_spent_thorium",
                "ITEM:powder_spent_u233","ITEM:powder_spent_mox","ITEM:powder_du_spent","ITEM:nugget_th232","ITEM:nugget_u235","ITEM:nugget_pu239",
                "ITEM:arc_electrode@0","ITEM:powder_ice","ITEM:mineral_dust","ITEM:block.ore_mineral","ITEM:block.duna_sands","ITEM:scuttertail","ITEM:nugget_technetium",
                "SEEDSLURRY","BLOOD","SAS3","CHARGED_MATRIX_ITEM","ICE_BLOCK","ETHANOL","LUBRICANT","BITUMEN","NEON",
                "SUGAR_ITEM","SAWDUST_ITEM","KEROSENE","MERCURY","WASTEGAS","THORIUM_SALT_DEPLETED"));
        java.util.List<ProcessRecipe> pending = new java.util.ArrayList<>(ChemistryProcesses.defaults().values());
        pending.addAll(UtilityMixtures.mixers().values());pending.add(AncillaryProcesses.dairy());pending.add(AncillaryProcesses.fullereneSuspension());pending.add(AncillaryProcesses.radioLigand());
        pending.add(MineralChemicalProcesses.lithiumElectrolysis());pending.add(ChemistryProcesses.metalCooling(false));pending.add(ChemistryProcesses.metalCooling(true));
        pending.add(ChemicalFuelProcesses.blend("COOLANT","FRESH_WATER",1,"ETHANOL",9));pending.add(ChemistryProcesses.paper());
        pending.add(ChemistryProcesses.electrolysis(8000)); pending.add(ChemistryProcesses.airSeparation()); pending.add(ChemistryProcesses.meltLye()); pending.add(ChemistryProcesses.moltenSaltElectrolysis()); pending.add(ChemicalFuelProcesses.gasification()); pending.add(ChemicalFuelProcesses.methanol()); pending.add(ChemistryProcesses.phosgene()); pending.add(ChemistryProcesses.potassiumChlorideElectrolysis()); pending.add(ChemistryProcesses.calciumElectrolysis()); pending.add(ChemistryProcesses.fluorideElectrolysis()); pending.add(ChemistryProcesses.chlorocalciteSolution()); pending.add(ChemistryProcesses.chlorocalciteConditioning()); pending.add(ChemistryProcesses.chlorocalciteClarification());
        boolean progress;
        do {
            progress = false;
            java.util.Iterator<ProcessRecipe> iterator = pending.iterator();
            while(iterator.hasNext()) {
                ProcessRecipe bill = iterator.next();
                if(!available.containsAll(java.util.Arrays.asList(bill.inputs)) || !available.containsAll(java.util.Arrays.asList(bill.solidInputs))) continue;
                available.addAll(java.util.Arrays.asList(bill.outputs)); iterator.remove(); progress = true;
            }
        } while(progress);
        assertTrue("Disconnected or circular released chemistry: " + pending.stream().map(b -> b.id).collect(java.util.stream.Collectors.toList()), pending.isEmpty());
    }

    @Test public void nuclearCarrierRecoveryAndIsotopeFractionsCloseAcrossTheWholeGameCascade() {
        long uranium235 = 0, uranium238 = 0, fluorine = 0;
        for(String stage : new String[] {"NUF6", "LEUF6", "MEUF6", "HEUF6"}) {
            ProcessRecipe bill = NuclearChemicalProcesses.cascade(stage, true); assertClosed(bill);
            for(int i = 0; i < bill.outputs.length; i++) {
                if(bill.outputs[i].equals("U235_NUGGET_ITEM")) uranium235 += bill.productMass[i];
                if(bill.outputs[i].equals("U238_NUGGET_ITEM")) uranium238 += bill.productMass[i];
                if(bill.outputs[i].equals("FLUORINE")) fluorine += bill.productMass[i];
            }
        }
        long feed = ChemistryProcesses.mass("UF6", 400);
        assertEquals(feed, uranium235 + uranium238 + fluorine);
        assertEquals(.007, uranium235 / (double) (uranium235 + uranium238), 1E-10);
        assertClosed(NuclearChemicalProcesses.cascade("PF6", false));
        assertArrayEquals(new String[] {"PU_MIX_NUGGET_ITEM", "FLUORINE"}, NuclearChemicalProcesses.cascade("PF6", true).outputs);
        assertEquals(PhysicalRegistry.get("UF6").massUnits, PhysicalRegistry.get("CASCADE_NUF6").massUnits);
    }

    private long product(ProcessRecipe recipe,String id) {
        for(int i=0;i<recipe.outputs.length;i++) if(recipe.outputs[i].equals(id)) return recipe.productMass[i];
        throw new AssertionError(recipe.id+" missing product "+id);
    }
    @Test public void constructionUsesWeighedAggregateAndWaterBinderRatio() {
        ProcessRecipe concrete=ConstructionProcesses.concrete(0),road=ConstructionProcesses.asphalt();
        assertClosed(concrete);assertClosed(road);assertClosed(ConstructionProcesses.concrete(1));assertClosed(ConstructionProcesses.concrete(2));
        assertEquals(248,concrete.inputMass()/(double)PhysicalState.MASS_UNITS_PER_KG,1E-10);
        assertEquals(.5,ChemistryProcesses.mass("WATER",16)/(double)ChemistryProcesses.mass("CEMENT_ITEM",64),1E-12);
        assertEquals(3,road.quantities[0]*1200/(double)road.duration,0);
        assertEquals(PhysicalState.MASS_UNITS_PER_KG*2/3,PhysicalRegistry.get("DU_BILLET_ITEM").massUnits);
    }
    @Test public void specialtySeparationsReturnSignificantCarrierAndResidue() {
        ProcessRecipe recovery=AncillaryProcesses.fullereneRecovery();assertClosed(recovery);
        assertEquals(.1,product(recovery,"ITEM:powder_ash@5")/(double)recovery.inputMass(),1E-12);
        assertEquals(.9,product(recovery,"RADIOSOLVENT")/(double)recovery.inputMass(),1E-12);
        ProcessRecipe radium=AncillaryProcesses.radiumRecovery();assertClosed(radium);
        assertEquals("ITEM:radium_residue",radium.solidInput);
        assertEquals(.00001,product(radium,"ITEM:nugget_ra226")/(double)ChemistryProcesses.mass(radium.solidInput,1),1E-12);
        assertClosed(UtilityMixtures.eggSeparation());assertClosed(UtilityMixtures.technetium());
    }
    @Test public void organicRoutesUseMoleculesAndRetainAcidCarrier() {
        ProcessRecipe tnt=OrganicProcesses.tnt(),rdx=OrganicProcesses.rdx();assertClosed(tnt);assertClosed(rdx);assertClosed(OrganicProcesses.tatb());
        assertEquals(227.1311/92.13842,product(tnt,"ITEM:ball_tnt")/(double)ChemistryProcesses.mass("TOLUENE",1),1E-10);
        assertTrue(product(tnt,"FRESH_WATER")>0);assertTrue(product(tnt,"NITRIC_ACID")>0);
        assertEquals(90.07794/140.18628,product(rdx,"FORMALDEHYDE")/(double)ChemistryProcesses.mass("ITEM:hexamine",1),1E-10);
        assertTrue(product(rdx,"AMMONIUM_NITRATE_ITEM")>0);
        assertArrayEquals(new String[]{"HYDRAZINE","FRESH_WATER"},OrganicProcesses.rocketFuel().inputs);
        assertEquals(OrganicProcesses.rocketFuel().chemicalInputJ.doubleValue(),OrganicProcesses.rocketFuel().chemicalOutputJ.doubleValue(),1E-6);
    }
    @Test public void cryogenicPreparationPaysForColdAndPreservesItsFeedEnergy() {
        ProcessRecipe cold=AncillaryProcesses.cryogel();assertClosed(cold);
        assertTrue(cold.sensibleOutputJ.compareTo(cold.sensibleInputJ)<0);
        assertTrue(cold.workQuanta/2>cold.sensibleInputJ.subtract(cold.sensibleOutputJ).longValue());
        assertEquals(cold.chemicalInputJ.doubleValue(),cold.chemicalOutputJ.doubleValue(),1E-6);
    }
    @Test public void coolantBoilerBillsPaySensibleHeatAtTheRatedSourceAndSurviveSnapshots() {
        for(String name:new String[]{"COOLANT","LIGHT_WATER","BORATED_WATER","HEAVYWATER"}) {
            ProcessRecipe r=CoolantLedger.heating(name);assertClosed(r);
            assertTrue(r.thermalRatingWatts<=200000);assertEquals(355,r.temperatureC);
            assertEquals(r.inputMass(),r.productMass[0]);
            assertTrue(r.heatQuanta>=CoolantLedger.heatingQuanta(name));
            assertEquals(r.heatQuanta,r.snapshot().heatQuanta);
        }
        assertNull(CoolantLedger.heating("OIL"));
    }
    @Test public void hotCoolantsKeepMassAndTheirHeatBillMatchesTheirSensibleState() {
        for(String cold:new String[]{"COOLANT","LIGHT_WATER","BORATED_WATER","HEAVYWATER"}) {
            PhysicalState in=PhysicalRegistry.get(cold),out=PhysicalRegistry.get(cold+"_HOT");
            assertEquals(in.massUnits,out.massUnits);assertEquals(cold,CoolantLedger.cold(cold+"_HOT"));
            BigDecimal rise=ProcessRecipe.sensible(cold+"_HOT",out.massUnits).subtract(ProcessRecipe.sensible(cold,in.massUnits));
            assertTrue(CoolantLedger.heatingQuanta(cold)/2D>=rise.doubleValue());
            assertTrue(CoolantLedger.heatingQuanta(cold)/2D-rise.doubleValue()<.500001);
            assertTrue(CoolantLedger.heatingQuanta(cold)>1000000);
        }
    }
    @Test public void nuclearSaltAndBromideInventoriesAreExplicitMassParcels() {
        assertEquals(PhysicalState.Basis.NOMINAL,PhysicalRegistry.get("THORIUM_SALT").basis);
        assertEquals(.001,PhysicalRegistry.get("THORIUM_SALT").kilogramsPerUnit(),1E-15);
        assertClosed(AncillaryProcesses.saltRefuel());assertClosed(AncillaryProcesses.freshSalt());
        for(int i=0;i<3;i++)assertClosed(AncillaryProcesses.bromide(i));
        assertFalse(ChemistryProcesses.defaults().containsKey(1106));
    }
    @Test public void lithiumIrradiationRetainsUnconvertedTargetAndTracksExternalNeutrons() {
        for(int i=0;i<3;i++) {
            IrradiationLedger.Target target=IrradiationLedger.target(i);
            assertEquals(target.lithium+target.capturedNeutrons,target.retainedTarget+target.tritiumMass+target.nuclearMassDefect);
            assertEquals(target.retainedTarget,PhysicalRegistry.get("ITEM:spent_lithium_target@"+i).massUnits);
            assertTrue(target.retainedTarget>target.lithium*.95);assertTrue(target.nuclearMassDefect>0);
        }
        assertEquals(124,IrradiationLedger.target(0).tritiumCounts);
        assertEquals(13,IrradiationLedger.target(2).tritiumCounts);
        assertTrue(IrradiationLedger.target(1).tritiumCounts<9*125);
    }
    @Test public void dairySplitsByMassAndReturnsEvaporatedWater() {
        ProcessRecipe recipe=AncillaryProcesses.dairy();assertClosed(recipe);
        assertEquals(10.3,recipe.inputMass()/(double)PhysicalState.MASS_UNITS_PER_KG,1E-12);
        assertEquals(.1,product(recipe,"CREAM")/(double)recipe.inputMass(),1E-12);
        assertTrue(product(recipe,"FRESH_WATER")>3*PhysicalState.MASS_UNITS_PER_KG);
        assertTrue(recipe.heatQuanta/2>product(recipe,"FRESH_WATER")/(double)PhysicalState.MASS_UNITS_PER_KG*2250000);
    }
    @Test public void newlyStampedContainersDoNotReinterpretLegacyFilledItems() {
        net.minecraft.item.Item full=new net.minecraft.item.Item(),empty=new net.minecraft.item.Item();
        com.hbm.inventory.FluidContainer container=new com.hbm.inventory.FluidContainer(new net.minecraft.item.ItemStack(full),new net.minecraft.item.ItemStack(empty),fluid("WATER"),1000);
        net.minecraft.item.ItemStack legacy=new net.minecraft.item.ItemStack(full),fresh=com.hbm.handler.BucketHandler.stamp(new net.minecraft.item.ItemStack(full),10);
        assertEquals(1000,container.amount(legacy));assertEquals(10,container.amount(fresh));
        assertEquals(1,container.amount(com.hbm.handler.BucketHandler.stamp(new net.minecraft.item.ItemStack(full),1)));
    }
    @Test public void releasedSpecialtyFormsBindRealNativeFields() throws Exception {
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(ProcessRecipe recipe:ChemistryProcesses.defaults().values()) {ids.addAll(java.util.Arrays.asList(recipe.solidInputs));ids.addAll(java.util.Arrays.asList(recipe.outputs));}
        for(ProcessRecipe recipe:UtilityMixtures.mixers().values()){ids.addAll(java.util.Arrays.asList(recipe.solidInputs));ids.addAll(java.util.Arrays.asList(recipe.outputs));}
        for(String id:ids) if(id.startsWith("ITEM:")) {
            String field=id.substring(5).split("@")[0];Class<?> owner=com.hbm.items.ModItems.class;
            if(field.startsWith("block.")){owner=com.hbm.blocks.ModBlocks.class;field=field.substring(6);}
            else if(field.startsWith("vanilla.")){owner=net.minecraft.init.Items.class;field=field.substring(8);}
            assertNotNull(owner.getField(field));
        }
    }

}
