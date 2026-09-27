package com.hbm.tileentity.machine.rbmk;

import api.hbm.fluid.IFluidStandardReceiver;
import com.hbm.blocks.machine.rbmk.RBMKBase;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityRBMKInlet extends TileEntityLoadedBase implements IFluidStandardReceiver {
	private static final int TASK_FEED = 1;
	private final TileEntityRBMKBase[] controllers = new TileEntityRBMKBase[4];
	
	public FluidTank water;
	
	public TileEntityRBMKInlet() {
		water = new FluidTank(Fluids.LIGHT_WATER, 32000).migrateFrom(Fluids.WATER);
		water.setChangeListener(changed -> { if(worldObj != null && !worldObj.isRemote) markMachineFluidDirty(); });
	}
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) onMachineCoarsePoll(20);
		if(worldObj != null && !worldObj.isRemote && water.getFill() > 0)
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_FEED, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
		this.subscribeToAllAround(water.getTankType(), this);
		for(int i = 2; i < 6; i++) {
				ForgeDirection dir = ForgeDirection.getOrientation(i);
				Block b = worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);
				controllers[i - 2] = null;
				if(b instanceof RBMKBase) {
					int[] pos = ((RBMKBase)b).findCore(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);
					if(pos != null) {
						TileEntity te = worldObj.getTileEntity(pos[0], pos[1], pos[2]);
						if(te instanceof TileEntityRBMKBase) controllers[i - 2] = (TileEntityRBMKBase) te;
					}
				}
			}
		onMachineRuntimeDirty(0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_FEED || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		for(int i = 2; i < 6 && water.getFill() > 0; i++) {
			ForgeDirection dir = ForgeDirection.getOrientation(i);
			TileEntityRBMKBase rbmk = controllers[i - 2];
			if(rbmk == null || rbmk.isInvalid() || !(worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ) instanceof RBMKBase)) continue;
			int amount = Math.max(0, Math.min(rbmk.maxWater - rbmk.water, water.getFill()));
			rbmk.water += amount;
			water.setFill(water.getFill() - amount);
		}
		if(water.getFill() > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_FEED, 0);
	}

	@Override
	public void onChunkUnload() {
		for(int i = 0; i < controllers.length; i++) controllers[i] = null;
		super.onChunkUnload();
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.water.readFromNBT(nbt, "tank");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		this.water.writeToNBT(nbt, "tank");
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {water};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {water};
	}

}
