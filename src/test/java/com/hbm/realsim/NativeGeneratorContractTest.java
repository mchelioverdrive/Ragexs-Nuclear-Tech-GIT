package com.hbm.realsim;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IRatedGenerator;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.render.util.EnumSymbol;
import com.hbm.tileentity.machine.*;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemRTGPellet;
import com.hbm.items.machine.ItemRTGPelletDepleted.DepletedRTGMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Native tile constructors/admission/save readers, without launching or substituting a world. */
public class NativeGeneratorContractTest {
    @Test public void poweredNativeReadersRetainPaidThermalCreditWithoutAHeaterGate() {
        com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower fraction = new com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower();
        TileEntityDeuteriumExtractor extractor = new TileEntityDeuteriumExtractor();
        TileEntityMachineMilkReformer dairy = new TileEntityMachineMilkReformer();
        net.minecraft.tileentity.TileEntity[] machines = {fraction, extractor, dairy};
        ProcessBatch[] owned = {fraction.physicalBatch, extractor.processBatch, dairy.physicalBatch};
        ProcessRecipe[] bills = {ChemicalFuelProcesses.reactorFraction(), ChemistryProcesses.heavyWater(), AncillaryProcesses.dairy()};
        long[] oldWork = {4900, 120000, 180000}, oldHeat = {627200, 3000000, 13000000};
        for(int i = 0; i < machines.length; i++) {
            NBTTagCompound recipeTag = new NBTTagCompound(); bills[i].write(recipeTag);
            recipeTag.setLong("work", oldWork[i]); recipeTag.setLong("heat", oldHeat[i]);
            recipeTag.setInteger("temperature", bills[i].referenceTemperatureC);
            ProcessRecipe legacy = ProcessRecipe.read(recipeTag);
            ProcessBatch paid = new ProcessBatch(); paid.reserve(legacy); long payment = 0;
            for(int tick = 0; tick < 13; tick++) { payment += paid.nextWork() + paid.nextHeat(); paid.advance(tick); }
            NBTTagCompound save = new NBTTagCompound(); paid.write(save);
            if(i == 2) { NBTTagCompound root = new NBTTagCompound(); root.setTag("realSimDairy", save); save = root; }
            machines[i].readFromNBT(save);
            assertEquals(13, owned[i].ticks()); assertEquals(0, owned[i].recipe().heatQuanta);
            assertEquals(0, owned[i].recipe().temperatureC);
            assertEquals(legacy.referenceTemperatureC, owned[i].recipe().referenceTemperatureC);
            assertArrayEquals(legacy.productMass, owned[i].recipe().productMass);
            assertEquals(legacy.sensibleInputJ, owned[i].recipe().sensibleInputJ);
            assertNull(ProcessMachine.heater(machines[i]));
            for(int tick = 13; !owned[i].complete(); tick++) { payment += owned[i].nextWork(); owned[i].advance(tick); }
            assertEquals(legacy.workQuanta + legacy.heatQuanta, payment);
        }
    }
    @Test public void externalHeatIsAnExplicitNativeEquipmentCapability() {
        for(Class<?> tile : new Class<?>[] {TileEntityHeatBoiler.class, TileEntityHeatBoilerIndustrial.class, TileEntityFurnaceCombination.class,
                TileEntityMachineBlastFurnace.class, com.hbm.tileentity.machine.oil.TileEntityMachineCoker.class})
            assertTrue(tile.getName(), api.hbm.tile.IProcessHeatReceiver.class.isAssignableFrom(tile));
        for(Class<?> tile : new Class<?>[] {com.hbm.tileentity.machine.oil.TileEntityMachineRefinery.class,
                com.hbm.tileentity.machine.oil.TileEntityMachineVacuumDistill.class, com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower.class,
                com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticCracker.class, com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticReformer.class,
                com.hbm.tileentity.machine.oil.TileEntityMachineHydrotreater.class, com.hbm.tileentity.machine.oil.TileEntityMachineAlkylation.class,
                TileEntityDeuteriumExtractor.class, TileEntityDeuteriumTower.class, TileEntityMachineMilkReformer.class})
            assertFalse(tile.getName(), api.hbm.tile.IProcessHeatReceiver.class.isAssignableFrom(tile));
        api.hbm.tile.IProcessHeatSource source = new TileEntityHeaterFirebox();
        for(net.minecraftforge.common.util.ForgeDirection side : net.minecraftforge.common.util.ForgeDirection.values())
            assertEquals(side == net.minecraftforge.common.util.ForgeDirection.UP, source.canSupplyProcessHeat(side));
    }
    @Test public void retainedNativeHeaterBasesLeaveTheWholeHeaterBelowTheirStructure() {
        com.hbm.blocks.BlockDummyable[] equipment = {new com.hbm.blocks.machine.MachineHeatBoiler(), new com.hbm.blocks.machine.MachineHeatBoilerIndustrial(),
                new com.hbm.blocks.machine.MachineCoker(net.minecraft.block.material.Material.iron), new com.hbm.blocks.machine.MachineBlastFurnace(),
                new com.hbm.blocks.machine.FurnaceCombination(), new com.hbm.blocks.machine.FurnaceSteel(), new com.hbm.blocks.machine.MachineCrucible(),
                new com.hbm.blocks.machine.MachineStirling(), new com.hbm.blocks.machine.MachineSawmill()};
        com.hbm.blocks.BlockDummyable[] heaters = {new com.hbm.blocks.machine.HeaterFirebox(), new com.hbm.blocks.machine.HeaterOven(),
                new com.hbm.blocks.machine.HeaterHeatex(), new com.hbm.blocks.machine.HeaterOilburner()};
        for(com.hbm.blocks.BlockDummyable machine : equipment) {
            int[] body = machine.getDimensions(); assertEquals(machine.getClass().getName(), 0, body[1]);
            for(int axis = 2; axis < 6; axis++) assertEquals(machine.getClass().getName(), 1, body[axis]);
            for(com.hbm.blocks.BlockDummyable heater : heaters) {
                int[] footprint = heater.getDimensions();
                for(int axis = 2; axis < 6; axis++) assertEquals(heater.getClass().getName(), 1, footprint[axis]);
                int heaterCoreY = -1 - footprint[0];
                assertEquals(-1, heaterCoreY + footprint[0]); assertTrue(heaterCoreY - footprint[1] < 0);
            }
        }
    }
    @Test public void customThermalPortsRequireAPlaceableThreeByThreeUnderside() {
        net.minecraft.block.Block old = com.hbm.blocks.ModBlocks.cm_heat;
        try {
            com.hbm.blocks.ModBlocks.cm_heat = new com.hbm.blocks.machine.BlockCMHeat(net.minecraft.block.material.Material.iron, "cm_heat");
            com.hbm.config.CustomMachineConfigJSON.MachineConfiguration config = new com.hbm.config.CustomMachineConfigJSON.MachineConfiguration();
            config.components = new java.util.ArrayList<>();
            ProcessRecipe heated = PetroleumProcesses.defaults("coker").get("HEAVYOIL");
            assertEquals(0, config.physicalBill(heated).heatQuanta);
            com.hbm.config.CustomMachineConfigJSON.MachineConfiguration.ComponentDefinition port = new com.hbm.config.CustomMachineConfigJSON.MachineConfiguration.ComponentDefinition();
            port.block = com.hbm.blocks.ModBlocks.cm_heat; port.x = 3; config.components.add(port);
            assertTrue(config.hasExternalHeatInterface()); assertSame(heated, config.physicalBill(heated));
            com.hbm.config.CustomMachineConfigJSON.MachineConfiguration.ComponentDefinition obstruction = new com.hbm.config.CustomMachineConfigJSON.MachineConfiguration.ComponentDefinition();
            obstruction.x = 4; obstruction.y = -1; config.components.add(obstruction);
            assertFalse(config.hasExternalHeatInterface()); assertEquals(0, config.physicalBill(heated).heatQuanta);
            config.components.remove(obstruction); port.x = 0; port.y = 1;
            assertFalse("Heater would occupy controller", config.hasExternalHeatInterface());
        } finally { com.hbm.blocks.ModBlocks.cm_heat = old; }
    }
    @Test public void boilerRetainsPartialProcessHeatWithoutRelabelingSteamBuffer() throws Exception {
        TileEntityHeatBoilerIndustrial boiler = new TileEntityHeatBoilerIndustrial();
        ProcessRecipe bill = PetroleumProcesses.defaults("heat").get("OIL"); boiler.physicalBatch.reserve(bill);
        Field heat = TileEntityHeatBoilerBase.class.getDeclaredField("processHeatQuanta"); heat.setAccessible(true);
        long partial = boiler.physicalBatch.nextHeat() / 2; heat.setLong(boiler, partial);
        boiler.thermalBufferQuanta = 1234; boiler.pumpEnergyQuanta = 4321;
        // Native readers can be exercised without initializing Minecraft's tile-ID registry.
        NBTTagCompound save = new NBTTagCompound(); boiler.physicalBatch.write(save);
        save.setLong("realSimProcessHeatQuanta", partial); save.setLong("realSimHeatQuanta", boiler.thermalBufferQuanta);
        save.setLong("realSimPumpQuanta", boiler.pumpEnergyQuanta);
        boiler.tanks[0].writeToNBT(save, "water"); boiler.tanks[1].writeToNBT(save, "steam");
        TileEntityHeatBoilerIndustrial restored = new TileEntityHeatBoilerIndustrial(); restored.readFromNBT(save);
        assertEquals(partial, heat.getLong(restored)); assertEquals(1234, restored.thermalBufferQuanta);
        assertEquals(4321, restored.pumpEnergyQuanta); assertEquals(bill.heatQuanta, restored.physicalBatch.recipe().heatQuanta);
        assertEquals(0, restored.physicalBatch.ticks());
        java.lang.reflect.Method firing = TileEntityHeatBoilerIndustrial.class.getDeclaredMethod("additionalHeat", long.class); firing.setAccessible(true);
        assertEquals(0L, ((Long) firing.invoke(restored, 0L)).longValue());
        save.setLong("realSimProcessHeatQuanta", restored.physicalBatch.remainingHeat() + 1);
        try { restored.readFromNBT(save); fail("Unowned process heat accepted"); } catch(IllegalStateException expected) { }
    }
    @Test public void ordinaryBoilerUsesItsRatedSupplyWithoutWastingPartialTickHeat() throws Exception {
        TileEntityHeatBoiler boiler = new TileEntityHeatBoiler();
        ProcessRecipe bill = PetroleumProcesses.defaults("heat").get("OIL"); boiler.physicalBatch.reserve(bill);
        Field buffer = TileEntityHeatBoilerBase.class.getDeclaredField("processHeatQuanta"); buffer.setAccessible(true);
        Field source = TileEntityHeatBoilerBase.class.getDeclaredField("processHeat"); source.setAccessible(true);
        api.hbm.tile.IProcessHeatSource paidHeat = (api.hbm.tile.IProcessHeatSource) source.get(boiler);
        java.lang.reflect.Method request = TileEntityHeatBoilerBase.class.getDeclaredMethod("processHeatRequest"); request.setAccessible(true);
        long supplied = 0; int ticks = 0;
        while(!boiler.physicalBatch.complete() && ticks < 100) {
            long heat = (Long) request.invoke(boiler); assertTrue(heat <= boiler.getRatedThermalWatts() / 10);
            supplied += heat; buffer.setLong(boiler, buffer.getLong(boiler) + heat);
            if(boiler.physicalBatch.canAdvance(ticks, boiler.physicalBatch.nextWork(), paidHeat.getAvailableProcessHeatQuanta(ticks))) {
                long due = boiler.physicalBatch.nextHeat(); assertEquals(due, paidHeat.drawProcessHeatQuanta(ticks, due));
                boiler.physicalBatch.advance(ticks);
            }
            ticks++;
        }
        assertEquals(SteamLedger.ceilDiv(bill.heatQuanta, boiler.getRatedThermalWatts() / 10), ticks);
        assertTrue(boiler.physicalBatch.complete()); assertEquals(bill.heatQuanta, supplied); assertEquals(0, buffer.getLong(boiler));
        assertEquals(0, boiler.physicalBatch.remainingHeat());
    }
    private final Map<Field, Object> fields = new HashMap<>();
    private Map<String, FluidType> names;
    private Map<String, FluidType> oldNames;
    private Map<Integer, FluidType> ids;
    private Map<Integer, FluidType> oldIds;
    private java.util.List<FluidType> order, oldOrder;
    private java.util.List<ItemRTGPellet> oldPellets;
    @Before @SuppressWarnings("unchecked") public void nativeIdentities() throws Exception {
        Field mapping = Fluids.class.getDeclaredField("nameMapping"); mapping.setAccessible(true);
        names = (Map<String, FluidType>) mapping.get(null); oldNames = new HashMap<>(names);
        Field idField = Fluids.class.getDeclaredField("idMapping"); idField.setAccessible(true);
        ids = (Map<Integer, FluidType>) idField.get(null); oldIds = new HashMap<>(ids);
        Field orderField = Fluids.class.getDeclaredField("registerOrder"); orderField.setAccessible(true);
        order = (java.util.List<FluidType>) orderField.get(null); oldOrder = new java.util.ArrayList<>(order);
        oldPellets = new java.util.ArrayList<>(ItemRTGPellet.pelletList);
        for(String name : new String[] {"NONE", "STEAM", "HOTSTEAM", "SUPERHOTSTEAM", "ULTRAHOTSTEAM", "SPENTSTEAM", "DIESEL", "KEROSENE", "BLOOD", "GAS", "LUBRICANT", "WATER", "FRESH_WATER",
                "HEAVYOIL", "HEAVYOIL_VACUUM", "SMEAR", "LIGHTOIL_VACUUM", "REFORMATE", "SOURGAS", "OIL", "OIL_DS", "LIGHTOIL", "LIGHTOIL_DS", "NAPHTHA", "NAPHTHA_DS", "PETROLEUM", "BITUMEN", "HEATINGOIL", "LPG", "AIR", "AIR_HEATED", "AIRBLAST", "BLAST_FURNACE_GAS", "OXYGEN", "OXYGEN_GAS", "NITROGEN", "NITROGEN_GAS", "HYDROGEN", "HYDROGEN_GAS", "SYNGAS", "REFORMGAS", "GAS_COKER", "BIOGAS", "UNSATURATEDS", "HYDROGEN_SULFIDE", "LIGHT_WATER", "HEAVYWATER", "DEPLETED_WATER", "ARGON", "NOBLE_GAS_MIX", "CARBONDIOXIDE", "MILK", "EMILK", "CMILK", "CREAM"}) {
            Field f = Fluids.class.getField(name); fields.put(f, f.get(null));
            FluidType type = new FluidType(name, 0, 0, 0, 0, EnumSymbol.NONE); f.set(null, type); names.put(name, type);
        }
        for(String name : new String[] {"pellet_rtg", "pellet_rtg_tm170", "pellet_rtg_polonium"}) {
            Field f = ModItems.class.getField(name); fields.put(f, f.get(null)); f.set(null, new ItemRTGPellet(10));
        }
        Field icon = ModItems.class.getField("fluid_icon"); fields.put(icon, icon.get(null)); icon.set(null, new com.hbm.items.machine.ItemFluidIcon());
        for(String name : new String[] {"canister_full", "fluid_tank_full", "fluid_barrel_full"}) {
            Field f = ModItems.class.getField(name); fields.put(f, f.get(null)); f.set(null, new net.minecraft.item.Item());
        }
    }
    @Test public void everyRegisteredCompressorRouteHasAnExactPressureAndMassDisplay() {
        com.hbm.inventory.recipes.CompressorRecipes registry = new com.hbm.inventory.recipes.CompressorRecipes();
        Map<com.hbm.util.Tuple.Pair<FluidType, Integer>, com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe> old = new HashMap<>(com.hbm.inventory.recipes.CompressorRecipes.recipes);
        Map<FluidType, com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe> oldVents = new HashMap<>(com.hbm.inventory.recipes.CompressorRecipes.decompressions);
        try {
            registry.deleteRecipes(); registry.registerDefaults();
            Map<Object, Object> display = com.hbm.inventory.recipes.CompressorRecipes.getNEIRecipes();
            assertEquals(com.hbm.inventory.recipes.CompressorRecipes.recipes.size() + com.hbm.inventory.recipes.CompressorRecipes.decompressions.size(), display.size());
            boolean forcedAir = false, decompression = false, liquidOxygen = false;
            for(Map.Entry<Object, Object> entry : display.entrySet()) {
                ItemStack in = (ItemStack) entry.getKey(), out = (ItemStack) entry.getValue();
                FluidType input = Fluids.fromID(in.getItemDamage()), output = Fluids.fromID(out.getItemDamage());
                int pressure = com.hbm.items.machine.ItemFluidIcon.getPressure(in);
                boolean vent = in.getTagCompound().getBoolean("compressorVent");
                com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe bill = vent
                        ? com.hbm.inventory.recipes.CompressorRecipes.decompressions.get(input)
                        : com.hbm.inventory.recipes.CompressorRecipes.recipes.get(new com.hbm.util.Tuple.Pair<>(input, pressure));
                assertNotNull(bill); assertNotSame(Fluids.NONE, input); assertNotSame(Fluids.NONE, output);
                assertSame(ModItems.fluid_icon, in.getItem()); assertSame(ModItems.fluid_icon, out.getItem());
                assertEquals(bill.inputAmount, com.hbm.items.machine.ItemFluidIcon.getQuantity(in));
                assertEquals(bill.output.pressure, com.hbm.items.machine.ItemFluidIcon.getPressure(out));
                assertTrue(in.getTagCompound().getInteger("compressorTicks") > 0);
                assertTrue(com.hbm.handler.nei.NEISafe.matchesRecipeStack(in, com.hbm.items.machine.ItemFluidIcon.make(input, 1)));
                assertTrue(com.hbm.handler.nei.NEISafe.matchesRecipeStack(out, com.hbm.items.machine.ItemFluidIcon.make(output, 1)));
                if(bill.massConversion || SteamLedger.state(input.getName()) != null)
                    assertEquals(Math.multiplyExact(input.getPhysicalState().massUnits, bill.inputAmount), out.getTagCompound().getLong("physicalMass"));
                forcedAir |= input == Fluids.AIR && pressure == 0 && output == Fluids.AIR && bill.output.pressure == 1 && !vent;
                decompression |= input == Fluids.AIR && pressure == 1 && output == Fluids.AIR && bill.output.pressure == 0 && vent;
                liquidOxygen |= input == Fluids.OXYGEN_GAS && pressure == 1 && output == Fluids.OXYGEN;
            }
            assertTrue("Missing forced-blast air", forcedAir); assertTrue("Missing vent mode", decompression); assertTrue("Missing liquid oxygen route", liquidOxygen);
            com.hbm.inventory.recipes.CompressorRecipes.recipes.remove(new com.hbm.util.Tuple.Pair<>(Fluids.AIR, 0));
            assertEquals(display.size() - 1, com.hbm.inventory.recipes.CompressorRecipes.getNEIRecipes().size());
        } finally {
            registry.deleteRecipes(); com.hbm.inventory.recipes.CompressorRecipes.recipes.putAll(old); com.hbm.inventory.recipes.CompressorRecipes.decompressions.putAll(oldVents);
        }
    }
    @Test public void atmosphericAndIsotopeMachinesDescribeTheirNativeProcessBills() {
        TileEntityDeuteriumExtractor[] machines = {new TileEntityDeuteriumExtractor(), new TileEntityDeuteriumTower(), new TileEntityAtmoTower()};
        ProcessRecipe[] bills = {ChemistryProcesses.heavyWater(), ChemistryProcesses.heavyWaterTower(), ChemistryProcesses.airSeparation()};
        for(int i = 0; i < machines.length; i++) {
            assertEquals(bills[i].id, machines[i].selectedBill().id);
            assertEquals(bills[i].duration, machines[i].selectedBill().duration);
            assertEquals(bills[i].electricalRatingWatts, machines[i].getMachineDemandWatts());
            assertTrue(MachineInfo.describe(machines[i], false).toString().contains("Drain"));
            assertEquals(4, machines[i].processOutputs().length);
        }
        TileEntityMachineBlastFurnace furnace = new TileEntityMachineBlastFurnace();
        assertSame(Fluids.OXYGEN_GAS, furnace.tanks[4].getTankType());
        assertEquals(0, furnace.tanks[4].getPressure()); assertEquals(1, furnace.tanks[2].getPressure());
        java.util.List<String> info = MachineInfo.describe(furnace, false);
        assertTrue(info.toString().contains("25%")); assertTrue(info.toString().contains("1.2 MJ"));
    }
    @Test public void blastIntakeRejectsOtherAirGradesButAllowsIndependentOxygen() {
        int[] airTanks = {0, 2, 3};
        for(int selected : airTanks) {
            TileEntityMachineBlastFurnace furnace = new TileEntityMachineBlastFurnace();
            FluidType type = furnace.tanks[selected].getTankType();
            assertEquals(0, furnace.transferFluid(type, 1, 100));
            assertEquals(0, furnace.transferFluid(type, 1, 25));
            for(int other : airTanks) if(other != selected) {
                FluidType rejected = furnace.tanks[other].getTankType();
                assertEquals(0, furnace.getDemand(rejected, 1));
                assertEquals(100, furnace.transferFluid(rejected, 1, 100));
                assertEquals(0, furnace.tanks[other].getFill());
            }
            assertEquals(0, furnace.transferFluid(Fluids.OXYGEN_GAS, 0, 10));
            assertEquals(10, furnace.tanks[4].getFill());
            assertEquals(125, furnace.tanks[selected].getFill());
            assertEquals(5, furnace.transferFluid(type, 0, 5));
            furnace.tanks[selected].setFill(0);
            for(int other : airTanks) assertTrue(furnace.getDemand(furnace.tanks[other].getTankType(), 1) > 0);
        }
    }
    @Test public void gasPreviewsUseGasArtAndPressureStatesHaveDifferentItemNames() {
        for(FluidType gas : new FluidType[] {Fluids.OXYGEN_GAS, Fluids.HYDROGEN_GAS, Fluids.NITROGEN_GAS, Fluids.AIRBLAST, Fluids.AIR_HEATED, Fluids.BLAST_FURNACE_GAS}) {
            assertEquals("textures/gui/fluids/air.png", gas.getTexture().getResourcePath());
            assertEquals(gas.getColor(), gas.getTint());
        }
        assertEquals("textures/gui/fluids/water.png", Fluids.WATER.getTexture().getResourcePath());
        com.hbm.items.machine.ItemFluidIcon icon = (com.hbm.items.machine.ItemFluidIcon) ModItems.fluid_icon;
        assertNotEquals(icon.getItemStackDisplayName(com.hbm.items.machine.ItemFluidIcon.make(Fluids.OXYGEN_GAS, 1000, 0)),
                icon.getItemStackDisplayName(com.hbm.items.machine.ItemFluidIcon.make(Fluids.OXYGEN_GAS, 1000, 1)));
        assertEquals(icon.getItemStackDisplayName(com.hbm.items.machine.ItemFluidIcon.make(Fluids.STEAM, 1000, 0)),
                icon.getItemStackDisplayName(new ItemStack(ModItems.fluid_icon, 1, Fluids.STEAM.getID())));
    }
    @Test public void compressorModeIsIdempotentAndIncludedInInitialClientSync() {
        TileEntityMachineCompressor server = new TileEntityMachineCompressor(), client = new TileEntityMachineCompressor();
        assertTrue(server.setDecompressionMode(true)); server.progress = 17;
        assertFalse(server.setDecompressionMode(true)); assertEquals(17, server.progress);
        io.netty.buffer.ByteBuf snapshot = io.netty.buffer.Unpooled.buffer();
        try {
            server.serialize(snapshot); client.deserialize(snapshot);
            assertTrue(client.decompress); assertEquals(0, snapshot.readableBytes());
            assertTrue(server.setDecompressionMode(false)); assertEquals(0, server.progress);
            snapshot.clear(); server.serialize(snapshot); client.deserialize(snapshot);
            assertFalse(client.decompress); assertEquals(0, snapshot.readableBytes());
        } finally { snapshot.release(); }
    }
    @Test public void blastFurnaceAuxiliaryFuelIsRetrieveOnlyAndProgressDescribesBlast() {
        TileEntityMachineBlastFurnace furnace = new TileEntityMachineBlastFurnace();
        ItemStack oldFuel = new ItemStack(ModItems.fluid_icon);
        furnace.setInventorySlotContents(0, oldFuel);
        assertFalse(furnace.isItemValidForSlot(0, oldFuel)); assertFalse(furnace.canInsertItem(0, oldFuel, 1));
        assertTrue(furnace.canExtractItem(0, oldFuel, 0)); assertSame(oldFuel, furnace.getStackInSlot(0));
        java.util.List<String> info = new java.util.ArrayList<>(); furnace.appendProgressInfo(info);
        assertTrue(info.toString().contains("Blast consumed:")); assertTrue(info.toString().contains("O2:"));
        assertTrue(info.toString().contains("heater below"));
    }
    @Test public void bothBoilersAcceptAirAndIndustrialIncludesOrdinarySteam() throws Exception {
        java.lang.reflect.Method setup = TileEntityHeatBoilerBase.class.getDeclaredMethod("setupTanks"); setup.setAccessible(true);
        for(TileEntityHeatBoilerBase boiler : new TileEntityHeatBoilerBase[] {new TileEntityHeatBoiler(), new TileEntityHeatBoilerIndustrial()}) {
            assertTrue(boiler.selectFeed(Fluids.AIR)); boiler.tanks[0].setFill(100); setup.invoke(boiler);
            assertSame(Fluids.AIR_HEATED, boiler.tanks[1].getTankType());
            assertFalse(boiler.selectFeed(Fluids.AIR_HEATED)); assertEquals(100, boiler.tanks[0].getFill());
            boiler.tanks[0].setFill(0); assertTrue(boiler.selectFeed(Fluids.AIR_HEATED)); setup.invoke(boiler);
            assertSame(Fluids.AIRBLAST, boiler.tanks[1].getTankType());
            assertTrue(boiler.selectFeed(Fluids.FRESH_WATER)); assertTrue(boiler.selectSteamOutlet(SteamLedger.State.ORDINARY)); setup.invoke(boiler);
            assertSame(Fluids.STEAM, boiler.tanks[1].getTankType());
            assertEquals(boiler instanceof TileEntityHeatBoilerIndustrial, boiler.selectSteamOutlet(SteamLedger.State.HOT));
            assertFalse(boiler.selectSteamOutlet(SteamLedger.State.ULTRAHOT));
            assertTrue(boiler.selectFeed(Fluids.OIL)); assertTrue(boiler.selectFeed(Fluids.LIGHT_WATER));
            assertFalse(boiler.selectFeed(Fluids.STEAM));
        }
        NBTTagCompound saved = new NBTTagCompound(); saved.setString("realSimSteamOutlet", "STEAM");
        TileEntityHeatBoilerIndustrial loaded = new TileEntityHeatBoilerIndustrial(); loaded.readFromNBT(saved);
        assertTrue(MachineInfo.describe(loaded, false).toString().contains("Steam outlet: STEAM;"));
        saved.setString("realSimSteamOutlet", "HOTSTEAM"); loaded.networkUnpack(saved);
        assertTrue(MachineInfo.describe(loaded, false).toString().contains("Steam outlet: HOTSTEAM;"));
    }
    @Test public void blastAirSupplyCapacityCoversTheExistingMaximumDemand() {
        com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe air = new com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe(1000,
                new com.hbm.inventory.FluidStack(Fluids.AIR, 1000, 1));
        long work = (long) Math.ceil(2 * 1000 * 100 * Math.log(10) / .70);
        int rate = com.hbm.inventory.recipes.CompressorRecipes.physicalPowerQuanta(Fluids.AIR, 0, air, false);
        assertEquals(75000, rate); assertEquals(9, SteamLedger.ceilDiv(work, rate));
        long maximumAirPerMinute = SteamLedger.ceilDiv(2700L * 10000000 * 60, 15000000);
        assertEquals(108000, maximumAirPerMinute);
        assertTrue(1000 * 1200L / SteamLedger.ceilDiv(work, rate) > maximumAirPerMinute);
        assertTrue(TileEntityAtmoExtractor.collectionPerTick(Fluids.AIR, false) * 1200L > maximumAirPerMinute);
        assertEquals(50, TileEntityAtmoExtractor.collectionPowerWatts(Fluids.AIR, false) / 20 / TileEntityAtmoExtractor.collectionPerTick(Fluids.AIR, false));
        assertEquals(5, TileEntityAtmoExtractor.collectionPerTick(Fluids.NITROGEN_GAS, false));
        assertEquals(1, TileEntityAtmoExtractor.collectionPerTick(Fluids.AIR, true));
        assertEquals(2500, com.hbm.inventory.recipes.CompressorRecipes.physicalPowerQuanta(Fluids.AIR, 1, air, true));
    }
    @Test public void compressorPhysicalWorkSurvivesBrownoutsAndMigratesOldTicksAtTheirOldCost() throws Exception {
        com.hbm.inventory.recipes.CompressorRecipes registry = new com.hbm.inventory.recipes.CompressorRecipes();
        Map<com.hbm.util.Tuple.Pair<FluidType, Integer>, com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe> old = new HashMap<>(com.hbm.inventory.recipes.CompressorRecipes.recipes);
        Map<FluidType, com.hbm.inventory.recipes.CompressorRecipes.CompressorRecipe> vents = new HashMap<>(com.hbm.inventory.recipes.CompressorRecipes.decompressions);
        try {
            registry.deleteRecipes(); registry.registerDefaults();
            TileEntityMachineCompressor compressor = new TileEntityMachineCompressor();
            compressor.tanks[0].configureForRecipe(Fluids.AIR, 0); compressor.tanks[0].setFill(1000);
            compressor.tanks[1].configureForRecipe(Fluids.AIR, 1);
            java.lang.reflect.Method refresh = TileEntityMachineCompressor.class.getDeclaredMethod("refreshRecipe"); refresh.setAccessible(true); refresh.invoke(compressor);
            java.lang.reflect.Method settings = TileEntityMachineCompressor.class.getDeclaredMethod("refreshRuntimeSettings"); settings.setAccessible(true); settings.invoke(compressor);
            Field eligible = TileEntityMachineCompressor.class.getDeclaredField("runtimeMaterialEligible"); eligible.setAccessible(true); eligible.setBoolean(compressor, true);
            Field last = TileEntityMachineCompressor.class.getDeclaredField("lastProgressTick"); last.setAccessible(true); last.setLong(compressor, 0);
            Field paid = TileEntityMachineCompressor.class.getDeclaredField("physicalWorkPaid"); paid.setAccessible(true);
            java.lang.reflect.Method settle = TileEntityMachineCompressor.class.getDeclaredMethod("settleProgressThrough", long.class); settle.setAccessible(true);
            compressor.energyQuanta = 1000; settle.invoke(compressor, 1L);
            assertEquals(0, compressor.energyQuanta); assertEquals(1000, paid.getLong(compressor));
            settle.invoke(compressor, 2L); assertEquals(1000, paid.getLong(compressor));
            compressor.tanks[1].setFill(compressor.tanks[1].getMaxFill()); compressor.energyQuanta = 2000; settle.invoke(compressor, 3L);
            assertEquals(2000, compressor.energyQuanta); assertEquals(1000, paid.getLong(compressor));
            compressor.tanks[1].setFill(0); eligible.setBoolean(compressor, true); settle.invoke(compressor, 4L);
            assertEquals(0, compressor.energyQuanta); assertEquals(3000, paid.getLong(compressor));
            assertEquals(1000, compressor.tanks[0].getFill()); assertEquals(0, compressor.tanks[1].getFill());
            NBTTagCompound legacy = new NBTTagCompound(); legacy.setInteger("realSimProcessVersion", 1); legacy.setInteger("progress", 17);
            compressor.tanks[0].writeToNBT(legacy, "0"); compressor.tanks[1].writeToNBT(legacy, "1");
            TileEntityMachineCompressor loaded = new TileEntityMachineCompressor(); loaded.readFromNBT(legacy); refresh.invoke(loaded); settings.invoke(loaded);
            assertEquals(17 * 2500L, paid.getLong(loaded)); assertEquals(9, loaded.processTime);
            loaded.setDecompressionMode(true); assertEquals(0, paid.getLong(loaded));
        } finally {
            registry.deleteRecipes(); com.hbm.inventory.recipes.CompressorRecipes.recipes.putAll(old); com.hbm.inventory.recipes.CompressorRecipes.decompressions.putAll(vents);
        }
    }
    @Test public void legacyMixedBlastStockIsRetainedWithoutAllowingNewMixedIntake() {
        TileEntityMachineBlastFurnace original = new TileEntityMachineBlastFurnace();
        original.tanks[0].setFill(200); original.tanks[2].setFill(300); original.tanks[3].setFill(400);
        NBTTagCompound saved = new NBTTagCompound();
        for(int tank = 0; tank < original.tanks.length; tank++) original.tanks[tank].writeToNBT(saved, "t" + tank);
        TileEntityMachineBlastFurnace loaded = new TileEntityMachineBlastFurnace(); loaded.readFromNBT(saved);
        assertEquals(200, loaded.tanks[0].getFill()); assertEquals(300, loaded.tanks[2].getFill()); assertEquals(400, loaded.tanks[3].getFill());
        for(int tank : new int[] {0, 2, 3}) assertEquals(0, loaded.getDemand(loaded.tanks[tank].getTankType(), 1));
        assertEquals(0, loaded.transferFluid(Fluids.OXYGEN_GAS, 0, 10));
        loaded.tanks[0].setFill(0); loaded.tanks[3].setFill(0);
        assertTrue(loaded.getDemand(Fluids.AIR, 1) > 0); assertEquals(0, loaded.getDemand(Fluids.AIRBLAST, 1));
    }
    @After public void restoreIdentities() throws Exception {
        for(Map.Entry<Field, Object> e : fields.entrySet()) e.getKey().set(null, e.getValue());
        names.clear(); names.putAll(oldNames);
        ids.clear(); ids.putAll(oldIds); order.clear(); order.addAll(oldOrder);
        ItemRTGPellet.pelletList.clear(); ItemRTGPellet.pelletList.addAll(oldPellets);
    }
    @Test public void everyDeferredElectricalAccountingOwnerSettlesBeforeAdmission() throws Exception {
        Class<?>[] owners = {TileEntityElectrolyser.class, TileEntityMachineArcFurnace.class, TileEntityMachineArcFurnaceLarge.class,
            TileEntityMachineArcWelder.class, TileEntityMachineAssemblerBase.class, TileEntityMachineCentrifuge.class,
            TileEntityMachineChemplant.class, TileEntityMachineChemplantBase.class, TileEntityMachineCompressor.class,
            TileEntityMachineCryoDistill.class, TileEntityMachineCrystallizer.class, TileEntityMachineCyclotron.class,
            TileEntityMachineDriveProcessor.class, TileEntityMachineElectricFurnace.class, TileEntityMachineEPress.class,
            TileEntityMachineExposureChamber.class, TileEntityMachineGasCent.class, TileEntityMachineMilkReformer.class,
            TileEntityMachineMixer.class, TileEntityMachineSchrabidiumTransmutator.class, TileEntityMachineShredder.class,
            TileEntityMachineSolderingStation.class, TileEntityMachineVacuumCircuit.class, TileEntityMicrowave.class,
            com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor.class, com.hbm.tileentity.machine.oil.TileEntityMachinePyroOven.class,
            com.hbm.tileentity.machine.oil.TileEntityMachineShredderLarge.class, com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier.class};
        for(Class<?> owner : owners) assertEquals(owner.getName(), owner, owner.getMethod("prepareEnergyTransfer").getDeclaringClass());
        assertEquals(TileEntityMachineAssemblerBase.class, TileEntityMachineAssembler.class.getMethod("prepareEnergyTransfer").getDeclaringClass());
        assertEquals(TileEntityMachineAssemblerBase.class, TileEntityMachineAssemfac.class.getMethod("prepareEnergyTransfer").getDeclaringClass());
    }
    @Test public void earlySteamKeepsFlowingUnderElectricalSaturationButCannotLoseExhaustMass() {
        TileEntityMachineTurbine tile = new TileEntityMachineTurbine();
        tile.tanks[0].setFill(100); long converted = 0, stored = 15000, rejected = 0;
        for(long tick = 1; tick <= 100; tick++) {
            long admitted = ThermalMachine.ungovernedTurbine(tile.thermal, tick, tile.tanks[0], tile.tanks[1],
                    10000, 75000, 750000, stored, 15000);
            assertEquals(0, admitted); converted += tile.thermal.currentMass;
            rejected += tile.thermal.currentRejectedMicroJ;
        }
        assertTrue(converted > 0); assertTrue(rejected > 0); assertEquals(15000, stored);
        assertEquals((100L - tile.tanks[0].getFill()) * SteamLedger.State.ORDINARY.massPerUnit,
                converted + tile.thermal.ownedMass());
        assertEquals(converted, tile.tanks[1].getFill() * SteamLedger.State.SPENT.massPerUnit + tile.thermal.remainder(SteamLedger.State.SPENT));
        tile.tanks[1].setFill(tile.tanks[1].getMaxFill()); int inlet = tile.tanks[0].getFill();
        assertEquals(0, ThermalMachine.ungovernedTurbine(tile.thermal, 101, tile.tanks[0], tile.tanks[1], 10000, 75000, 750000, stored, 15000));
        assertEquals(inlet, tile.tanks[0].getFill());
    }
    @Test public void fullFurnaceDoesNotReverseChargeItsSupplyBattery() {
        com.hbm.items.machine.ItemBattery battery = new com.hbm.items.machine.ItemBattery(100, 100, 100);
        ItemStack stack = new ItemStack(battery); battery.setStoredEnergyQuanta(stack, 70);
        assertEquals(100000, com.hbm.lib.Library.chargeTEFromItems(new ItemStack[] {stack}, 0, 100000, 1600));
        assertEquals(70, battery.getStoredEnergyQuanta(stack));
    }
    @Test public void everyActiveGeneratorHasAnExplicitRatedContract() {
        Class<?>[] types = {TileEntityStirling.class, TileEntitySteamEngine.class, TileEntityMachineTurbine.class,
            TileEntityMachineLargeTurbine.class, TileEntityChungus.class, TileEntityMachineDiesel.class,
            TileEntityMachineCombustionEngine.class, TileEntityMachineTurbofan.class, TileEntityMachineTurbineGas.class,
            TileEntityMachineWoodBurner.class, TileEntityMachineRTG.class, TileEntityMachineMiniRTG.class,
            TileEntityMachineRadiolysis.class, TileEntityMachineRadGen.class, TileEntityMachineSolarPanel.class,
            TileEntityMachineAmgen.class, TileEntityMachineSPP.class, TileEntityMachineDischarger.class,
            TileEntityCoreReceiver.class, TileEntityCustomMachine.class, com.hbm.tileentity.machine.oil.TileEntityMachineGasFlare.class};
        for(Class<?> type : types) assertTrue(type.getSimpleName(), IRatedGenerator.class.isAssignableFrom(type));
    }
    @Test public void assemblerAndIndustrialLanesHaveExactlyTwelveIngredientPorts() {
        int[] workshop = new TileEntityMachineAssembler().getSlotIndicesFromIndex(0);
        assertEquals(12, workshop[1] - workshop[0] + 1);
        TileEntityMachineAssemfac industrial = new TileEntityMachineAssemfac();
        for(int lane = 0; lane < 8; lane++) {
            int[] ports = industrial.getSlotIndicesFromIndex(lane);
            assertEquals(com.hbm.inventory.recipes.AssemblerRecipes.MAX_INGREDIENTS, ports[1] - ports[0] + 1);
        }
    }
    @Test public void lubricantConstructionKeyUsesTheRegisteredCurrentContainerCapacity() {
        com.hbm.inventory.FluidContainer container = new com.hbm.inventory.FluidContainer(new ItemStack(ModItems.canister_full, 1, Fluids.LUBRICANT.getID()), null, Fluids.LUBRICANT, 1000);
        assertEquals(20, container.content);
        assertEquals(Fluids.LUBRICANT.getDict(container.content), Fluids.LUBRICANT.getCraftingContainerDict(1000));
        assertEquals(Fluids.LUBRICANT.getDict(200), Fluids.LUBRICANT.getCraftingContainerDict(16000));
        assertNotEquals(Fluids.LUBRICANT.getDict(1000), Fluids.LUBRICANT.getCraftingContainerDict(1000));
    }
    @Test public void reactorPreviewShapesKeepFluidIdentityQuantityAndEmptyPorts() {
        java.util.Map<Integer, com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe> old = new java.util.HashMap<>(com.hbm.inventory.recipes.ChemplantRecipes.indexMapping);
        try {
            com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe residue = new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe(30000, "residue", 1).petroleum(ChemicalFuelProcesses.reactorFraction());
            com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe extraction = new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe(30001, "base_oil", 1).petroleum(PetroleumProcesses.lubricantBaseOil());
            com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe mixed = new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe(30002, "mixed", 20)
                    .inputItems(new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.canister_full, 2))
                    .inputFluids(new com.hbm.inventory.FluidStack(Fluids.SMEAR, 1))
                    .outputItems(new ItemStack(ModItems.canister_full)).outputFluids(new com.hbm.inventory.FluidStack(Fluids.LUBRICANT, 1));
            com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe items = new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe(30003, "items", 20)
                    .inputItems(new com.hbm.inventory.RecipesCommon.ComparableStack(ModItems.canister_full))
                    .outputItems(new ItemStack(ModItems.canister_full));
            for(com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe recipe : new com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe[] {residue, extraction, mixed, items}) {
                for(com.hbm.inventory.RecipesCommon.AStack input : recipe.inputs) if(input != null)
                    for(ItemStack stack : input.extractForNEI()) assertNotSame(ModItems.fluid_icon, stack.getItem());
                for(int i = 0; i < 4; i++) {
                    ItemStack display = recipe.outputFluidDisplay(i);
                    if(recipe.outputFluids[i] == null) assertNull(display);
                    else {
                        assertSame(ModItems.fluid_icon, display.getItem());
                        assertEquals(recipe.outputFluids[i].type.getID(), display.getItemDamage());
                        assertTrue(com.hbm.items.machine.ItemFluidIcon.getQuantity(display) > 0);
                    }
                }
            }
            ItemStack baseOil = extraction.outputFluidDisplay(1);
            assertTrue(baseOil.getTagCompound().getLong("physicalMass") < PhysicalRegistry.get("LUBRICANT").massUnits);
            assertTrue(com.hbm.items.machine.ItemFluidIcon.quantityText(baseOil).startsWith("0."));
            assertEquals("LUBRICANT", Fluids.fromID(baseOil.getItemDamage()).getName());
            extraction.outputFluids[3] = new com.hbm.inventory.FluidStack(Fluids.NONE, 0);
            assertNull(extraction.outputFluidDisplay(3));
        } finally {
            com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.clear();
            com.hbm.inventory.recipes.ChemplantRecipes.indexMapping.putAll(old);
        }
    }
    @Test public void vacuumLubricantSurvivesNativeLookupDisplayAndPaidBatchReservation() {
        com.hbm.tileentity.machine.oil.TileEntityMachineVacuumDistill tile = new com.hbm.tileentity.machine.oil.TileEntityMachineVacuumDistill();
        ProcessRecipe recipe = com.hbm.inventory.recipes.RefineryRecipes.getPhysicalVacuum(Fluids.HEAVYOIL);
        assertNotNull(recipe); assertEquals("LUBRICANT", recipe.outputs[3]); assertTrue(recipe.productMass[3] > 0);
        assertSame(Fluids.SMEAR, tile.tanks[2].getTankType()); assertSame(Fluids.LUBRICANT, tile.tanks[4].getTankType());
        FluidTank[] outputs = {tile.tanks[1], tile.tanks[2], tile.tanks[3], tile.tanks[4]};
        tile.tanks[0].setFill(1);
        ProcessMachine.sizeProcessBuffers(recipe, new FluidTank[] {tile.tanks[0]}, outputs);
        assertTrue(ProcessMachine.reserveInputs(tile.physicalBatch, recipe, new FluidTank[] {tile.tanks[0]}, outputs, null, 0, -1, 0, -1));
        assertEquals(0, tile.tanks[0].getFill()); assertTrue(tile.physicalBatch.active());
        long lubricant = 0;
        MassRemainder products = new MassRemainder();
        for(int batch = 0; batch < 1000; batch++) lubricant += products.convert("LUBRICANT", recipe.productMass[3]);
        assertTrue(lubricant > 0);
        boolean visible = false;
        for(Object value : com.hbm.inventory.recipes.RefineryRecipes.getVacuumRecipe().values())
            for(ItemStack stack : (ItemStack[]) value) if(stack.getItemDamage() == Fluids.LUBRICANT.getID()) {
                visible = true;
                assertEquals(recipe.productMass[3], stack.getTagCompound().getLong("physicalMass"));
                assertEquals(PetroleumProcesses.ceilDiv(recipe.productMass[3], PhysicalRegistry.get("LUBRICANT").massUnits), com.hbm.items.machine.ItemFluidIcon.getQuantity(stack));
            }
        assertTrue("Vacuum output disappeared in NEI compilation", visible);
        NBTTagCompound saved = new NBTTagCompound();
        tile.tanks[2].setTankType(Fluids.REFORMATE); tile.tanks[2].setFill(17); tile.tanks[2].writeToNBT(saved, "reformate");
        FluidTank legacy = new FluidTank(Fluids.SMEAR, 10).withDefaultCapacity(); legacy.readFromNBT(saved, "reformate");
        assertSame(Fluids.REFORMATE, legacy.getTankType()); assertEquals(17, legacy.getFill());
        assertFalse(legacy.configureForRecipe(Fluids.SMEAR, 0));
    }
    @Test public void nativeSteamAdmissionRejectsGradesWithoutConsumingOrRelabelingStock() {
        TileEntityMachineTurbine small = new TileEntityMachineTurbine();
        small.tanks[0].setTankType(Fluids.HOTSTEAM); assertSame(Fluids.HOTSTEAM, small.tanks[0].getTankType());
        assertEquals(10, small.tanks[0].getMaxFill()); small.tanks[0].setFill(7);
        small.tanks[0].setTankType(Fluids.ULTRAHOTSTEAM);
        assertSame(Fluids.HOTSTEAM, small.tanks[0].getTankType()); assertEquals(7, small.tanks[0].getFill());
        small.tanks[0].setFill(0); small.tanks[0].setTankType(Fluids.SUPERHOTSTEAM);
        assertSame(Fluids.HOTSTEAM, small.tanks[0].getTankType());
        TileEntityMachineLargeTurbine industrial = new TileEntityMachineLargeTurbine();
        industrial.tanks[0].setTankType(Fluids.SUPERHOTSTEAM); assertSame(Fluids.SUPERHOTSTEAM, industrial.tanks[0].getTankType());
        industrial.tanks[0].setTankType(Fluids.ULTRAHOTSTEAM); assertSame(Fluids.SUPERHOTSTEAM, industrial.tanks[0].getTankType());
        TileEntityChungus leviathan = new TileEntityChungus(); leviathan.tanks[0].setTankType(Fluids.ULTRAHOTSTEAM);
        assertSame(Fluids.ULTRAHOTSTEAM, leviathan.tanks[0].getTankType()); assertEquals(8, leviathan.tanks[0].getMaxFill());
        TileEntitySteamEngine engine = new TileEntitySteamEngine(); engine.tanks[0].setTankType(Fluids.HOTSTEAM);
        assertSame(Fluids.STEAM, engine.tanks[0].getTankType());
    }
    @Test public void incompatibleLegacySteamStaysExportableAndCannotGenerate() {
        TileEntityMachineTurbine small = new TileEntityMachineTurbine(); NBTTagCompound n = new NBTTagCompound();
        n.setInteger("water", 64000); n.setInteger("water_max", 64000); n.setString("water_type", "ULTRAHOTSTEAM");
        small.readFromNBT(n); assertEquals(64000, small.tanks[0].getFill());
        assertEquals(0, small.getReceivingTanks().length); assertEquals(2, small.getSendingTanks().length);
        assertEquals(0, ThermalMachine.turbine(small.thermal, 1, small.tanks[0], small.tanks[1], 10000, 75000, 750000, 0, 15000));
        assertEquals(64000, small.tanks[0].getFill());
        small.tanks[0].setFill(63999); assertEquals(63999, small.tanks[0].getFill());
    }
    @Test public void legacyLeviathanTripAndEnergySurviveRevisedCapacity() {
        NBTTagCompound n = new NBTTagCompound(); EnergyUnits.writeEnergyQuanta(n, 100000000000L);
        n.setBoolean("tripped", true); n.setInteger("tripCause", 2);
        TileEntityChungus tile = new TileEntityChungus(); tile.readFromNBT(n);
        assertEquals(100000000000L, tile.getStoredEnergyQuanta()); assertEquals(5000000L, tile.getEnergyCapacityQuanta());
        assertTrue(tile.isTripped()); assertEquals(2, tile.getTripCause());
    }
    @Test public void dieselConfigurationControlsTankAndOldElectricityIsNotClamped() {
        int original = TileEntityMachineDiesel.fluidCap;
        try {
            TileEntityMachineDiesel.fluidCap = 73; TileEntityMachineDiesel tile = new TileEntityMachineDiesel();
            assertEquals(73, tile.tank.getMaxFill());
            NBTTagCompound n = new NBTTagCompound(); EnergyUnits.writeEnergyQuanta(n, 1234567); EnergyUnits.writeCapacityQuanta(n, 9999999);
            n.setInteger("fuel", 400); n.setInteger("fuel_max", 16000); n.setString("fuel_type", "DIESEL"); tile.readFromNBT(n);
            assertEquals(1234567, tile.getStoredEnergyQuanta()); assertEquals(TileEntityMachineDiesel.maxPower, tile.getEnergyCapacityQuanta());
            assertEquals(400, tile.tank.getFill()); assertEquals(73, tile.tank.getMaxFill());
        } finally { TileEntityMachineDiesel.fluidCap = original; }
    }
    @Test public void gasTurbineRunningStateAndOverCapServiceFluidsSurvive() {
        TileEntityMachineTurbineGas tile = new TileEntityMachineTurbineGas(); NBTTagCompound n = new NBTTagCompound();
        n.setInteger("state", 1); n.setInteger("rpm", 87); n.setInteger("temperature", 480); n.setInteger("slidPos", 80); n.setInteger("counter", 580);
        n.setInteger("lube", 16000); n.setInteger("lube_max", 16000); n.setString("lube_type", "LUBRICANT");
        tile.readFromNBT(n); assertEquals(1, tile.state); assertEquals(87, tile.rpm); assertEquals(580, tile.counter);
        assertEquals(480, tile.temp); assertEquals(80, tile.powerSliderPos);
        assertEquals(16000, tile.tanks[1].getFill()); assertEquals(2, tile.tanks[1].getMaxFill());
    }
    @Test public void isotopeIdentityDeterminesHeatIndependentlyOfLegacyHeatNumbers() {
        assertEquals(1140, IsotopeLedger.thermalWatts(new ItemStack(ModItems.pellet_rtg)));
        assertEquals(27000, IsotopeLedger.thermalWatts(new ItemStack(ModItems.pellet_rtg_tm170)));
        assertEquals(280000, IsotopeLedger.thermalWatts(new ItemStack(ModItems.pellet_rtg_polonium)));
    }
    @Test public void flareHasZeroGenerationAndStorageButRetainsLegacyDrain() {
        com.hbm.tileentity.machine.oil.TileEntityMachineGasFlare tile = new com.hbm.tileentity.machine.oil.TileEntityMachineGasFlare();
        assertEquals(0, tile.getRatedOutputWatts()); assertEquals(0, tile.getEnergyCapacityQuanta());
        assertEquals(100000, tile.getExportLimitWatts());
        String preview = MachineInfo.describe(tile, false).toString();
        assertFalse(preview.contains("Maximum electrical delivery")); assertTrue(preview.contains("no electricity"));
        tile.energyQuanta = 200; String live = MachineInfo.describe(tile, true).toString();
        assertTrue(live.contains("Legacy electrical stock")); assertFalse(live.contains("Maximum electrical delivery"));
    }
    @Test public void unknownMiniSourceIsRetainedWithoutBecomingFreshFuel() {
        NBTTagCompound root = new NBTTagCompound(), data = new NBTTagCompound(), source = new NBTTagCompound();
        source.setShort("id", (short) 32767); source.setByte("Count", (byte) 1); source.setShort("Damage", (short) 7);
        data.setTag("isotopeSource", source); root.setTag("persistent", data);
        TileEntityMachineMiniRTG tile = new TileEntityMachineMiniRTG(); tile.readNBT(root);
        assertTrue(tile.hasUnresolvedSource()); assertEquals(0, tile.getSourceThermalWatts());
        NBTTagCompound saved = new NBTTagCompound(); tile.writeNBT(saved);
        assertEquals(source, saved.getCompoundTag("persistent").getCompoundTag("isotopeSource"));
    }
    @Test public void unidentifiedMiniAdoptsConservativeSourceOnce() {
        TileEntityMachineMiniRTG tile = new TileEntityMachineMiniRTG();
        assertEquals(1140, tile.getSourceThermalWatts()); assertTrue(tile.legacySource);
        ItemStack source = tile.source; assertEquals(1140, tile.getSourceThermalWatts()); assertSame(source, tile.source);
    }
    @Test public void chargingAnOverCapBatteryDoesNotCreateOrDeleteElectricity() {
        com.hbm.items.machine.ItemBattery battery = new com.hbm.items.machine.ItemBattery(100, 100, 100);
        ItemStack stack = new ItemStack(battery); battery.setStoredEnergyQuanta(stack, 200);
        long remaining = com.hbm.lib.Library.chargeItemsFromTE(new ItemStack[] {stack}, 0, 50, 50);
        assertEquals(50, remaining); assertEquals(200, battery.getStoredEnergyQuanta(stack));
        battery.setStoredEnergyQuanta(stack, 70);
        remaining = com.hbm.lib.Library.chargeItemsFromTE(new ItemStack[] {stack}, 0, 50, 50);
        assertEquals(20, remaining); assertEquals(100, battery.getStoredEnergyQuanta(stack));
    }
    @Test public void pelletDecayPreservesExistingNBTAndNondecayingPower() {
        ItemRTGPellet pellet = new ItemRTGPellet(12).setDecays(DepletedRTGMaterial.LEAD, 1000);
        ItemStack stack = new ItemStack(pellet); stack.setTagCompound(new NBTTagCompound()); stack.getTagCompound().setString("owner", "retained");
        assertEquals(1000, pellet.getLifespan(stack)); pellet.decay(stack);
        assertEquals(999, pellet.getLifespan(stack)); assertEquals("retained", stack.getTagCompound().getString("owner"));
        assertEquals(12, ItemRTGPellet.getScaledPower(new ItemRTGPellet(12), new ItemStack(pellet)));
    }
}
