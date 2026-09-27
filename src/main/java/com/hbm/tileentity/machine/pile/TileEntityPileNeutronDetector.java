package com.hbm.tileentity.machine.pile;

import api.hbm.block.IPileNeutronReceiver;
import com.hbm.blocks.machine.pile.BlockGraphiteNeutronDetector;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.nbt.NBTTagCompound;

public class TileEntityPileNeutronDetector extends TileEntityLoadedBase implements IPileNeutronReceiver {
	private static final int TASK_DETECT = 0;

	public int lastNeutrons;
	public int neutrons;
	public int maxNeutrons = 10;
	public int averagedNeutrons;
	public int cooldown;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 || needsSimulation()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_DETECT, 0);
		else cancelMachineTransition(TASK_DETECT, 0);
	}

	private boolean needsSimulation() {
		return neutrons != 0 || averagedNeutrons != 0 || cooldown > 0;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_DETECT || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;

		// Smooth detector readings (primitive instrumentation realism).
		this.averagedNeutrons = (int)(this.averagedNeutrons * 0.8D + this.neutrons * 0.2D);

		// Rod movement delay.
		if(this.cooldown > 0) this.cooldown--;

		int insertThreshold = this.maxNeutrons;
		int retractThreshold = this.maxNeutrons - 4;

		boolean rodsInserted = (this.getBlockMetadata() & 8) > 0;

		// Insert rods.
		if(this.averagedNeutrons >= insertThreshold && rodsInserted && this.cooldown <= 0) {
			((BlockGraphiteNeutronDetector)worldObj.getBlock(xCoord, yCoord, zCoord)).triggerRods(worldObj, xCoord, yCoord, zCoord);
			this.cooldown = 20;
		}

		// Retract rods.
		if(this.averagedNeutrons <= retractThreshold && this.lastNeutrons <= retractThreshold && !rodsInserted && this.cooldown <= 0) {
			((BlockGraphiteNeutronDetector)worldObj.getBlock(xCoord, yCoord, zCoord)).triggerRods(worldObj, xCoord, yCoord, zCoord);
			this.cooldown = 20;
		}

		this.lastNeutrons = this.averagedNeutrons;
		this.neutrons = 0;

		if(needsSimulation()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_DETECT, 0);
	}

	@Override
	public void updateEntity() { }

	@Override
	public void receiveNeutrons(int n) {
		this.neutrons += n;
		if(n != 0) markMachineDirty(MachineDirtyCause.ENVIRONMENT);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("maxNeutrons", this.maxNeutrons);
		nbt.setInteger("avgNeutrons", this.averagedNeutrons);
		nbt.setInteger("cooldown", this.cooldown);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.maxNeutrons = nbt.getInteger("maxNeutrons");
		this.averagedNeutrons =
			nbt.getInteger("avgNeutrons");
		this.cooldown =
			nbt.getInteger("cooldown");
	}
}
