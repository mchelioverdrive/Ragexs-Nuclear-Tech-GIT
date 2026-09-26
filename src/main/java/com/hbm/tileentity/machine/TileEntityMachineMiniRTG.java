package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineMiniRTG extends TileEntityLoadedBase implements IEnergyProviderMK2, IInfoProviderEC {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;

	public long energyQuanta;
	boolean tact = false;
	
	@Override
	public void updateEntity() {
		// Generation and export are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(this.energyQuanta + this.getOutput());
			if(energyQuanta > getEnergyCapacityQuanta()) this.setStoredEnergyQuanta(getEnergyCapacityQuanta());

			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				this.tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}
		} finally {
			runtimeEnergyMutation = false;
		}
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
	}
	
	public long getOutput() {
		if(this.getBlockType() == ModBlocks.machine_powerrtg) return 2_500;
		return 700;
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.getOutput());
	}

	@Override
	public long getEnergyCapacityQuanta() {
		if(this.getBlockType() == ModBlocks.machine_powerrtg) return 50_000;
		return 1_400;
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


	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, true);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.getOutput()));
	}
}
