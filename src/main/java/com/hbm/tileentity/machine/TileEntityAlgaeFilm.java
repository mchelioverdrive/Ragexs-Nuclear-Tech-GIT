package com.hbm.tileentity.machine;

import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.fluid.IFluidStandardTransceiver;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityAlgaeFilm extends TileEntityMachineBase implements IFluidStandardTransceiver {
	private static final int TASK_ENVIRONMENTAL_CYCLE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;

	public FluidTank[] tanks;
	public boolean canOperate;

	public TileEntityAlgaeFilm() {
		super(0);
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.CARBONDIOXIDE, 8_000);
		tanks[1] = new FluidTank(Fluids.OXYGEN, 8_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.algaeFilm";
	}

	@Override
	public void updateEntity() {
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		canOperate = worldObj.provider instanceof WorldProviderOrbit;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(20);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ENVIRONMENTAL_CYCLE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean fluidChanged = false;
		this.beginMachineFluidMutation();
		try {
			if(canOperate && worldObj.rand.nextBoolean() && tanks[0].getFill() > 0 && tanks[1].getFill() < tanks[1].getMaxFill()) {
				tanks[0].setFill(tanks[0].getFill() - 1);
				tanks[1].setFill(tanks[1].getFill() + 1);
				fluidChanged = true;
			}
			ForgeDirection d = ForgeDirection.getOrientation(this.getBlockMetadata()).getRotation(ForgeDirection.UP);
			this.updatePort(d);
			this.updatePort(d.getOpposite());
		} finally { this.endMachineFluidMutation(); }
		if(fluidChanged) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(20);
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && canOperate) this.scheduleMachineTransition(now + 1L, TASK_ENVIRONMENTAL_CYCLE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_ENVIRONMENTAL_CYCLE, TASK_SLOT_MAIN);
	}

	private void updatePort(ForgeDirection dir) {
		this.trySubscribe(tanks[0].getTankType(), worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
		if(tanks[1].getFill() > 0) this.sendFluid(tanks[1], worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeBoolean(canOperate);
		for(FluidTank tank : tanks) tank.serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		canOperate = buf.readBoolean();
		for(FluidTank tank : tanks) tank.deserialize(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "t" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1] };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}
	
}
