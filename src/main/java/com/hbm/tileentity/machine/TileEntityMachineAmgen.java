package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineAmgen extends TileEntityLoadedBase implements IEnergyProviderMK2, IInfoProviderEC {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;

	public long energyQuanta;
	public long maxPower = 500;
	protected long output = 0;
	
	@Override
	public void updateEntity() {
		// Generation and export are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence == 20 && worldObj != null && !worldObj.isRemote && runtimeInitialized) this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.output = 0;
		Block block = worldObj.getBlock(xCoord, yCoord, zCoord);
		if(block == ModBlocks.machine_geo) {
			this.checkGeoInteraction(xCoord, yCoord + 1, zCoord);
			this.checkGeoInteraction(xCoord, yCoord - 1, zCoord);
		}

		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(this.energyQuanta + this.output);
			if(energyQuanta > maxPower) this.setStoredEnergyQuanta(maxPower);
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
				this.tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
		} finally {
			runtimeEnergyMutation = false;
		}
		if(this.output > 0 || energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean source = this.hasGeoSource();
		if(!source) this.output = 0;
		if(energyQuanta > 0 || source) this.scheduleMachineTransition(now + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	private boolean hasGeoSource() {
		if(worldObj.getBlock(xCoord, yCoord, zCoord) != ModBlocks.machine_geo) return false;
		return this.isGeoSource(worldObj.getBlock(xCoord, yCoord + 1, zCoord)) || this.isGeoSource(worldObj.getBlock(xCoord, yCoord - 1, zCoord));
	}

	private boolean isGeoSource(Block block) {
		return block == ModBlocks.geysir_water || block == ModBlocks.geysir_chlorine || block == ModBlocks.geysir_vapor || block == ModBlocks.geysir_nether || block == Blocks.lava || block == Blocks.flowing_lava;
	}
	
	private void checkGeoInteraction(int x, int y, int z) {
		
		Block b = worldObj.getBlock(x, y, z);
		
		if(b == ModBlocks.geysir_water) {
			this.output += 75;
		} else if(b == ModBlocks.geysir_chlorine) {
			this.output += 100;
		} else if(b == ModBlocks.geysir_vapor) {
			this.output += 50;
		} else if(b == ModBlocks.geysir_nether) {
			this.output += 500;
		} else if(b == Blocks.lava) {
			this.output += 100;
			
			if(worldObj.rand.nextInt(6000) == 0) {
				worldObj.setBlock(xCoord, yCoord - 1, zCoord, Blocks.obsidian);
			}
		} else if(b == Blocks.flowing_lava) {
			this.output += 25;
			
			if(worldObj.rand.nextInt(3000) == 0) {
				worldObj.setBlock(xCoord, yCoord - 1, zCoord, Blocks.cobblestone);
			}
		}
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.output);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.output > 0);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.output));
	}
}
