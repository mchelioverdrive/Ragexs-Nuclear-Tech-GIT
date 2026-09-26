package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineDetector extends TileEntityLoadedBase implements IEnergyReceiverMK2 {

	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	
	private long energyQuanta;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.updateActiveState();
		if(energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote) return;
		if(energyQuanta <= 0) {
			this.updateActiveState();
			return;
		}

		// One half-joule quantum per 20 TPS tick is a 10 W sustained draw.
		this.updateActiveState();
		this.setStoredEnergyQuanta(this.energyQuanta - 1L);
		if(energyQuanta > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence == 20 && worldObj != null && !worldObj.isRemote) this.updateConnections();
	}

	private void updateActiveState() {
		int state = energyQuanta > 0 ? 1 : 0;
		if(this.getBlockMetadata() != state) {
			worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, state, 3);
			this.markDirty();
		}
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 5;
	}

	@Override
	public ConnectionPriority getPriority() {
		return ConnectionPriority.HIGH;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
	}
}
