package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;

public class TileEntityMachineSPP extends TileEntityLoadedBase implements IEnergyProviderMK2, IInfoProviderEC {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	
	public long energyQuanta;
	public static final long maxPower = 100000;
	public int age = 0;
	public int gen = 0;
	
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
		if(energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int previousGeneration = gen;
		gen = checkStructure() * 15;
		if(gen != previousGeneration) this.markDirty();
		if(gen > 0 || energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		try {
			this.tryProvide(worldObj, xCoord + 1, yCoord, zCoord, Library.POS_X);
			this.tryProvide(worldObj, xCoord - 1, yCoord, zCoord, Library.NEG_X);
			this.tryProvide(worldObj, xCoord, yCoord, zCoord + 1, Library.POS_Z);
			this.tryProvide(worldObj, xCoord, yCoord, zCoord - 1, Library.NEG_Z);
			this.tryProvide(worldObj, xCoord, yCoord - 1, zCoord, Library.NEG_Y);
			if(gen > 0) this.setStoredEnergyQuanta(this.energyQuanta + gen);
			if(energyQuanta > maxPower) this.setStoredEnergyQuanta(maxPower);
		} finally {
			runtimeEnergyMutation = false;
		}
		if(gen > 0 || energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_MAIN);
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.gen);
	}
	
	public int checkStructure() {

		int h = 0;
		
		for(int i = yCoord + 1; i < 254; i++)
			if(worldObj.getBlock(xCoord, i, zCoord) == ModBlocks.machine_spp_top) {
				h = i;
				break;
			}
		
		for(int i = yCoord + 1; i < h; i++)
			if(!checkSegment(i))
				return 0;

		
		return h - yCoord - 1;
	}
	
	public boolean checkSegment(int y) {
		
		//   BBB
		//   BAB
		//   BBB
		
		return (worldObj.getBlock(xCoord + 1, y, zCoord) != Blocks.air &&
				worldObj.getBlock(xCoord + 1, y, zCoord + 1) != Blocks.air &&
				worldObj.getBlock(xCoord + 1, y, zCoord - 1) != Blocks.air &&
				worldObj.getBlock(xCoord - 1, y, zCoord + 1) != Blocks.air &&
				worldObj.getBlock(xCoord - 1, y, zCoord) != Blocks.air &&
				worldObj.getBlock(xCoord - 1, y, zCoord - 1) != Blocks.air &&
				worldObj.getBlock(xCoord, y, zCoord + 1) != Blocks.air &&
				worldObj.getBlock(xCoord, y, zCoord - 1) != Blocks.air &&
				worldObj.getBlock(xCoord, y, zCoord) == Blocks.air);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.gen > 0);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.gen));
	}
}
