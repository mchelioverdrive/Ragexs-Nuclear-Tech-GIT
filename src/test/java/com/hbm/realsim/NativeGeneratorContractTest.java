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
                "HEAVYOIL", "HEAVYOIL_VACUUM", "SMEAR", "LIGHTOIL_VACUUM", "REFORMATE", "SOURGAS", "OIL", "OIL_DS", "LIGHTOIL", "LIGHTOIL_DS", "NAPHTHA", "NAPHTHA_DS", "PETROLEUM", "BITUMEN", "HEATINGOIL", "LPG"}) {
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
    @After public void restoreIdentities() throws Exception {
        for(Map.Entry<Field, Object> e : fields.entrySet()) e.getKey().set(null, e.getValue());
        names.clear(); names.putAll(oldNames);
        ids.clear(); ids.putAll(oldIds); order.clear(); order.addAll(oldOrder);
        ItemRTGPellet.pelletList.clear(); ItemRTGPellet.pelletList.addAll(oldPellets);
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
                visible = true; assertEquals(lubricant, com.hbm.items.machine.ItemFluidIcon.getQuantity(stack));
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
