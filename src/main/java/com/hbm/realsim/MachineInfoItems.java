package com.hbm.realsim;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import com.hbm.blocks.ModBlocks;

/** Read-only default native profiles. Constructed once per block, without a world or reflection. */
public final class MachineInfoItems {
    private static final Map<Block, TileEntity> PROFILES = new IdentityHashMap<>();
    private MachineInfoItems() { }
    public static void append(ItemStack stack, List<String> lines) {
        Block block = Block.getBlockFromItem(stack.getItem());
        if(block == net.minecraft.init.Blocks.air) return;
        if(block == ModBlocks.machine_minirtg || block == ModBlocks.machine_powerrtg) {
            com.hbm.tileentity.machine.TileEntityMachineMiniRTG isotope = new com.hbm.tileentity.machine.TileEntityMachineMiniRTG();
            if(stack.hasTagCompound()) isotope.readNBT(stack.getTagCompound());
            if(isotope.source == null && !isotope.hasUnresolvedSource()) isotope.source = new ItemStack(block == ModBlocks.machine_powerrtg ? com.hbm.items.ModItems.pellet_rtg_polonium : com.hbm.items.ModItems.pellet_rtg);
            lines.add("Sealed source: " + (isotope.source == null ? "Unknown isotope source; no electrical output" : isotope.source.getDisplayName()));
            lines.addAll(MachineInfo.describe(isotope, false));
            return;
        }
        if(block instanceof com.hbm.blocks.machine.MachineBattery) {
            com.hbm.blocks.machine.MachineBattery battery = (com.hbm.blocks.machine.MachineBattery) block;
            lines.add("Energy storage: " + api.hbm.energymk2.EnergyUnits.formatJoules(battery.maxPower));
            lines.add("Charge ceiling: " + MachineInfo.watts(battery.maxPower / 200L * 10L));
            lines.add("Discharge ceiling: " + MachineInfo.watts(battery.maxPower / 600L * 10L));
            return;
        }
        TileEntity profile;
        if(PROFILES.containsKey(block)) profile = PROFILES.get(block);
        else { profile = create(block); PROFILES.put(block, profile); }
        if(profile != null) { lines.add("Preview ratings; check the placed machine for server settings"); lines.addAll(MachineInfo.describe(profile, false)); }
    }
    private static TileEntity create(Block block) {
        if(block == ModBlocks.machine_rtg_grey) return new com.hbm.tileentity.machine.TileEntityMachineRTG();
        if(block == ModBlocks.machine_radiolysis) return new com.hbm.tileentity.machine.TileEntityMachineRadiolysis();
        if(block == ModBlocks.machine_radgen) return new com.hbm.tileentity.machine.TileEntityMachineRadGen();
        if(block == ModBlocks.machine_solar) return new com.hbm.tileentity.machine.TileEntityMachineSolarPanel();
        if(block == ModBlocks.machine_geo) return new com.hbm.tileentity.machine.TileEntityMachineAmgen();
        if(block == ModBlocks.machine_spp_bottom) return new com.hbm.tileentity.machine.TileEntityMachineSPP();
        if(block == ModBlocks.machine_discharger) return new com.hbm.tileentity.machine.TileEntityMachineDischarger();
        if(block == ModBlocks.machine_converter_he_rf) return new com.hbm.tileentity.network.TileEntityConverterHeRf();
        if(block == ModBlocks.machine_converter_rf_he) return new com.hbm.tileentity.network.TileEntityConverterRfHe();
        if(block.getClass() == com.hbm.blocks.machine.MachineStirling.class) return new com.hbm.tileentity.machine.TileEntityStirling(block);
        if(block == ModBlocks.pump_electric) return new com.hbm.tileentity.machine.TileEntityMachinePumpElectric();
        if(block == ModBlocks.pump_steam) return new com.hbm.tileentity.machine.TileEntityMachinePumpSteam();
        if(block.getClass() == com.hbm.blocks.machine.FurnaceCombination.class) return new com.hbm.tileentity.machine.TileEntityFurnaceCombination();
        if(block.getClass() == com.hbm.blocks.machine.HeaterElectric.class) return new com.hbm.tileentity.machine.TileEntityHeaterElectric();
        if(block.getClass() == com.hbm.blocks.machine.HeaterFirebox.class) return new com.hbm.tileentity.machine.TileEntityHeaterFirebox();
        if(block.getClass() == com.hbm.blocks.machine.HeaterHeatex.class) return new com.hbm.tileentity.machine.TileEntityHeaterHeatex();
        if(block.getClass() == com.hbm.blocks.machine.HeaterOilburner.class) return new com.hbm.tileentity.machine.TileEntityHeaterOilburner();
        if(block.getClass() == com.hbm.blocks.machine.HeaterOven.class) return new com.hbm.tileentity.machine.TileEntityHeaterOven();
        if(block.getClass() == com.hbm.blocks.machine.MachineAlkylation.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineAlkylation();
        if(block.getClass() == com.hbm.blocks.machine.MachineArcFurnace.class) return new com.hbm.tileentity.machine.TileEntityMachineArcFurnace();
        if(block.getClass() == com.hbm.blocks.machine.MachineArcFurnaceLarge.class) return new com.hbm.tileentity.machine.TileEntityMachineArcFurnaceLarge();
        if(block.getClass() == com.hbm.blocks.machine.MachineArcWelder.class) return new com.hbm.tileentity.machine.TileEntityMachineArcWelder();
        if(block.getClass() == com.hbm.blocks.machine.MachineAssembler.class) return new com.hbm.tileentity.machine.TileEntityMachineAssembler();
        if(block.getClass() == com.hbm.blocks.machine.MachineAssemfac.class) return new com.hbm.tileentity.machine.TileEntityMachineAssemfac();
        if(block.getClass() == com.hbm.blocks.machine.MachineCatalyticCracker.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticCracker();
        if(block.getClass() == com.hbm.blocks.machine.MachineCatalyticReformer.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineCatalyticReformer();
        if(block.getClass() == com.hbm.blocks.machine.MachineCentrifuge.class) return new com.hbm.tileentity.machine.TileEntityMachineCentrifuge();
        if(block.getClass() == com.hbm.blocks.machine.MachineChemfac.class) return new com.hbm.tileentity.machine.TileEntityMachineChemfac();
        if(block.getClass() == com.hbm.blocks.machine.MachineChemplant.class) return new com.hbm.tileentity.machine.TileEntityMachineChemplant();
        if(block.getClass() == com.hbm.blocks.machine.MachineChungus.class) return new com.hbm.tileentity.machine.TileEntityChungus();
        if(block.getClass() == com.hbm.blocks.machine.MachineCoker.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineCoker();
        if(block.getClass() == com.hbm.blocks.machine.MachineCombustionEngine.class) return new com.hbm.tileentity.machine.TileEntityMachineCombustionEngine();
        if(block.getClass() == com.hbm.blocks.machine.MachineCompressor.class) return new com.hbm.tileentity.machine.TileEntityMachineCompressor();
        if(block.getClass() == com.hbm.blocks.machine.MachineCondenser.class) return new com.hbm.tileentity.machine.TileEntityCondenser();
        if(block.getClass() == com.hbm.blocks.machine.MachineCondenserPowered.class) return new com.hbm.tileentity.machine.TileEntityCondenserPowered();
        if(block.getClass() == com.hbm.blocks.machine.MachineCryoDistill.class) return new com.hbm.tileentity.machine.TileEntityMachineCryoDistill();
        if(block.getClass() == com.hbm.blocks.machine.MachineCrystallizer.class) return new com.hbm.tileentity.machine.TileEntityMachineCrystallizer();
        if(block.getClass() == com.hbm.blocks.machine.MachineDiesel.class) return new com.hbm.tileentity.machine.TileEntityMachineDiesel();
        if(block.getClass() == com.hbm.blocks.machine.MachineEPress.class) return new com.hbm.tileentity.machine.TileEntityMachineEPress();
        if(block.getClass() == com.hbm.blocks.machine.MachineElectricFurnace.class) return new com.hbm.tileentity.machine.TileEntityMachineElectricFurnace();
        if(block.getClass() == com.hbm.blocks.machine.MachineElectrolyser.class) return new com.hbm.tileentity.machine.TileEntityElectrolyser();
        if(block.getClass() == com.hbm.blocks.machine.MachineExcavator.class) return new com.hbm.tileentity.machine.TileEntityMachineExcavator();
        if(block.getClass() == com.hbm.blocks.machine.MachineExposureChamber.class) return new com.hbm.tileentity.machine.TileEntityMachineExposureChamber();
        if(block.getClass() == com.hbm.blocks.machine.MachineFrackingTower.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineFrackingTower();
        if(block.getClass() == com.hbm.blocks.machine.MachineFractionTower.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineFractionTower();
        if(block.getClass() == com.hbm.blocks.machine.MachineGasFlare.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineGasFlare();
        if(block.getClass() == com.hbm.blocks.machine.MachineHeatBoiler.class) return new com.hbm.tileentity.machine.TileEntityHeatBoiler();
        if(block.getClass() == com.hbm.blocks.machine.MachineHeatBoilerIndustrial.class) return new com.hbm.tileentity.machine.TileEntityHeatBoilerIndustrial();
        if(block.getClass() == com.hbm.blocks.machine.MachineHephaestus.class) return new com.hbm.tileentity.machine.TileEntityMachineHephaestus();
        if(block.getClass() == com.hbm.blocks.machine.MachineHydrotreater.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineHydrotreater();
        if(block.getClass() == com.hbm.blocks.machine.MachineLargeTurbine.class) return new com.hbm.tileentity.machine.TileEntityMachineLargeTurbine();
        if(block.getClass() == com.hbm.blocks.machine.MachineLiquefactor.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineLiquefactor();
        if(block.getClass() == com.hbm.blocks.machine.MachineMiningLaser.class) return new com.hbm.tileentity.machine.TileEntityMachineMiningLaser();
        if(block.getClass() == com.hbm.blocks.machine.MachineOilWell.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineOilWell();
        if(block.getClass() == com.hbm.blocks.machine.MachinePress.class) return new com.hbm.tileentity.machine.TileEntityMachinePress();
        if(block.getClass() == com.hbm.blocks.machine.MachinePumpjack.class) return new com.hbm.tileentity.machine.oil.TileEntityMachinePumpjack();
        if(block.getClass() == com.hbm.blocks.machine.MachinePyroOven.class) return new com.hbm.tileentity.machine.oil.TileEntityMachinePyroOven();
        if(block.getClass() == com.hbm.blocks.machine.MachineRadiator.class) return new com.hbm.tileentity.machine.TileEntityRadiator();
        if(block.getClass() == com.hbm.blocks.machine.MachineRefinery.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineRefinery();
        if(block.getClass() == com.hbm.blocks.machine.MachineSawmill.class) return new com.hbm.tileentity.machine.TileEntitySawmill();
        if(block.getClass() == com.hbm.blocks.machine.MachineShredder.class) return new com.hbm.tileentity.machine.TileEntityMachineShredder();
        if(block.getClass() == com.hbm.blocks.machine.MachineSolarBoiler.class) return new com.hbm.tileentity.machine.TileEntitySolarBoiler();
        if(block.getClass() == com.hbm.blocks.machine.MachineSolderingStation.class) return new com.hbm.tileentity.machine.TileEntityMachineSolderingStation();
        if(block.getClass() == com.hbm.blocks.machine.MachineSolidifier.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineSolidifier();
        if(block.getClass() == com.hbm.blocks.machine.MachineSteamEngine.class) return new com.hbm.tileentity.machine.TileEntitySteamEngine();
        if(block.getClass() == com.hbm.blocks.machine.MachineStrandCaster.class) return new com.hbm.tileentity.machine.TileEntityMachineStrandCaster();
        if(block.getClass() == com.hbm.blocks.machine.MachineTowerLarge.class) return new com.hbm.tileentity.machine.TileEntityTowerLarge();
        if(block.getClass() == com.hbm.blocks.machine.MachineTowerSmall.class) return new com.hbm.tileentity.machine.TileEntityTowerSmall();
        if(block.getClass() == com.hbm.blocks.machine.MachineTurbine.class) return new com.hbm.tileentity.machine.TileEntityMachineTurbine();
        if(block.getClass() == com.hbm.blocks.machine.MachineTurbineGas.class) return new com.hbm.tileentity.machine.TileEntityMachineTurbineGas();
        if(block.getClass() == com.hbm.blocks.machine.MachineTurbofan.class) return new com.hbm.tileentity.machine.TileEntityMachineTurbofan();
        if(block.getClass() == com.hbm.blocks.machine.MachineVacuumCircuit.class) return new com.hbm.tileentity.machine.TileEntityMachineVacuumCircuit();
        if(block.getClass() == com.hbm.blocks.machine.MachineVacuumDistill.class) return new com.hbm.tileentity.machine.oil.TileEntityMachineVacuumDistill();
        if(block.getClass() == com.hbm.blocks.machine.MachineWoodBurner.class) return new com.hbm.tileentity.machine.TileEntityMachineWoodBurner();
        return null;
    }
}
