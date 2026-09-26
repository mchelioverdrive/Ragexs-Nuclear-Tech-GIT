package com.hbm.tileentity.machine;

import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.FurnaceGasEmission;

import api.hbm.fluid.IFluidUser;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileEntityChimneyBase extends TileEntityLoadedBase implements IFluidUser, INBTPacketReceiver {
	private static final int TASK_MAINTENANCE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private static final FluidType[] SMOKE_TYPES = new FluidType[] {Fluids.SMOKE, Fluids.SMOKE_LEADED, Fluids.SMOKE_POISON};
	private boolean runtimeInitialized;

	public long ashTick = 0;
	public long sootTick = 0;
	public int onTicks;
	
	@Override
	public void updateEntity() {
		if(worldObj.isRemote && onTicks > 0) this.spawnParticles();
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.subscribeToSmokeNetworks();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.subscribeToSmokeNetworks();
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_MAINTENANCE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(ashTick > 0 || sootTick > 0) {
			TileEntity below = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
			if(below instanceof TileEntityAshpit) {
				TileEntityAshpit ashpit = (TileEntityAshpit) below;
				ashpit.addAsh(EnumAshType.FLY, (int) ashTick);
				ashpit.addAsh(EnumAshType.SOOT, (int) sootTick);
			}
			this.ashTick = 0;
			this.sootTick = 0;
			this.markDirty();
		}
		this.sendRuntimeState();
		if(onTicks > 0) {
			onTicks--;
			if(onTicks == 0) this.sendRuntimeState();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void subscribeToSmokeNetworks() {
		for(FluidType type : SMOKE_TYPES) {
			this.trySubscribe(type, worldObj, xCoord + 2, yCoord, zCoord, Library.POS_X);
			this.trySubscribe(type, worldObj, xCoord - 2, yCoord, zCoord, Library.NEG_X);
			this.trySubscribe(type, worldObj, xCoord, yCoord, zCoord + 2, Library.POS_Z);
			this.trySubscribe(type, worldObj, xCoord, yCoord, zCoord - 2, Library.NEG_Z);
		}
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(ashTick > 0 || sootTick > 0 || onTicks > 0) this.scheduleMachineTransition(now + 1L, TASK_MAINTENANCE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_MAINTENANCE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("onTicks", onTicks);
		INBTPacketReceiver.networkPack(this, data, 150);
	}

	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		ashTick = nbt.getLong("ashTick");
		sootTick = nbt.getLong("sootTick");
	}

	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("ashTick", ashTick);
		nbt.setLong("sootTick", sootTick);
	}

	public boolean cpaturesAsh() {
		return true;
	}
	
	public boolean cpaturesSoot() {
		return false;
	}
	
	public void spawnParticles() { }
	
	public void networkUnpack(NBTTagCompound nbt) {
		this.onTicks = nbt.getInteger("onTicks");
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return (dir == ForgeDirection.NORTH || dir == ForgeDirection.SOUTH || dir == ForgeDirection.EAST || dir == ForgeDirection.WEST) &&
				(type == Fluids.SMOKE || type == Fluids.SMOKE_LEADED || type == Fluids.SMOKE_POISON);
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long fluid) {
		
		if(type != Fluids.SMOKE && type != Fluids.SMOKE_LEADED && type != Fluids.SMOKE_POISON) return fluid;
		
		onTicks = 20;

		if(cpaturesAsh()) ashTick += fluid;
		if(cpaturesSoot()) sootTick += fluid;
		
		fluid *= getPollutionMod();

		if(type == Fluids.SMOKE) {
			PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, fluid / 100F);
			FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 200);
		}
		if(type == Fluids.SMOKE_LEADED) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.HEAVYMETAL, fluid / 100F);
		if(type == Fluids.SMOKE_POISON) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.POISON, fluid / 100F);
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.FLUID);
		
		return 0;
	}
	
	public abstract double getPollutionMod();

	@Override
	public long getDemand(FluidType type, int pressure) {
		return 1_000_000;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {};
	}
}
