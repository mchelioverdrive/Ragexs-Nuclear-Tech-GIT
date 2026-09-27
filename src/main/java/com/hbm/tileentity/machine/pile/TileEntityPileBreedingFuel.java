package com.hbm.tileentity.machine.pile;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;

import api.hbm.block.IPileNeutronReceiver;
import net.minecraft.nbt.NBTTagCompound;

public class TileEntityPileBreedingFuel extends TileEntityPileBase implements IPileNeutronReceiver {
	private static final int TASK_REACTION = 1;

	public int neutrons;
	public int lastNeutrons;
	public int progress;
	public static final int maxProgress =
		GeneralConfig.enable528 ? 120000 : 80000;

	public double heat;
	public static final double maxHeat = 500D;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj != null && !worldObj.isRemote && (heat > 0 || progress > 0 || neutrons > 0))
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_REACTION, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_REACTION || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		this.runReaction();
		if(heat > 0 || progress > 0 || neutrons > 0)
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_REACTION, 0);
	}

	private void runReaction() {
		if(!worldObj.isRemote) {
			react();

			if(this.progress >= this.maxProgress) {
				worldObj.setBlock(xCoord, yCoord, zCoord, ModBlocks.block_graphite_tritium, this.getBlockMetadata(), 3);
			}
		}
	}

	private void react() {

		this.lastNeutrons = this.neutrons;

		double efficiency =
			GeneralConfig.enable528 ? 0.12D : 0.20D;

		double heatPenalty =
			1D - Math.min(
				0.4D,
				(heat / maxHeat) * 0.4D);

		int absorbed =
			(int)(this.neutrons *
				efficiency *
				heatPenalty);

		this.progress += absorbed;

		heat += absorbed * 0.015D;
		heat *= 0.995D;

		if(lastNeutrons <= 0) {
			this.neutrons = 0;
			return;
		}

		this.neutrons = 0;

		int secondary =
			Math.min(4,
					 Math.max(1, lastNeutrons / 8));

		for(int i = 0; i < secondary; i++) {
			this.castRay(1, 3);
		}
	}

	@Override
	public void receiveNeutrons(int n) {
		this.neutrons += n;
		this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.heat = nbt.getDouble("heat");
		this.progress = nbt.getInteger("progress");
		this.neutrons = nbt.getInteger("neutrons");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setDouble("heat", this.heat);
		nbt.setInteger("progress", this.progress);
		nbt.setInteger("neutrons", this.neutrons);
	}
}
