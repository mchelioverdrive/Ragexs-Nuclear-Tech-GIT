package com.hbm.tileentity.machine;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.saveddata.TomSaveData;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumSkyBlock;

public class TileEntityCondenser extends TileEntityLoadedBase implements IFluidStandardTransceiver, INBTPacketReceiver, IInfoProviderEC, IConfigurableMachine, IFluidCopiable {
	private static final int TASK_CONDENSE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	protected boolean runtimeFluidMutation;
	private FluidTank[] runtimeTrackedTanks;
	private final FluidTank.ChangeListener runtimeTankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank changedTank) {
			if(!runtimeFluidMutation && worldObj != null && !worldObj.isRemote) markMachineFluidDirty();
			if(worldObj != null) markDirty();
		}
	};

	public int age = 0;
	public FluidTank[] tanks;
	
	public int waterTimer = 0;
	protected int throughput;

	public boolean vacuumOptimised = false;
	
	//Configurable values
	public static int inputTankSize = 100;
	public static int outputTankSize = 100;

	public TileEntityCondenser() {
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.SPENTSTEAM, inputTankSize);
		tanks[1] = new FluidTank(Fluids.FRESH_WATER, outputTankSize).migrateFrom(Fluids.WATER);
	}

	@Override
	public String getConfigName() {
		return "condenser";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		inputTankSize = IConfigurableMachine.grab(obj, "I:inputTankSize", inputTankSize);
		outputTankSize = IConfigurableMachine.grab(obj, "I:outputTankSize", outputTankSize);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:inputTankSize").value(inputTankSize);
		writer.name("I:outputTankSize").value(outputTankSize);
	}


	
	@Override
	public void updateEntity() {
		// Condensation and fluid transfer are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.subscribeToAllAround(tanks[0].getTankType(), this);
		this.updateThroughput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.subscribeToAllAround(tanks[0].getTankType(), this);
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CONDENSE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.age++;
		if(this.age >= 2) this.age = 0;
		runtimeFluidMutation = true;
		try {
			if(this.waterTimer > 0) this.waterTimer--;
			int convert = Math.min(tanks[0].getFill(), tanks[1].getMaxFill() - tanks[1].getFill());
			this.throughput = convert;
			if(extraCondition(convert)) {
				tanks[0].setFill(tanks[0].getFill() - convert);
				if(convert > 0) this.waterTimer = 20;
				int light = this.worldObj.getSavedLightValue(EnumSkyBlock.Sky, this.xCoord, this.yCoord, this.zCoord);
				boolean shouldEvaporate = TomSaveData.forWorld(worldObj).fire > 1e-5 && light > 7;
				if(!shouldEvaporate && !vacuumOptimised) {
					CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
					if(CelestialBody.inOrbit(worldObj) || atmosphere == null || atmosphere.getPressure() < 0.01) shouldEvaporate = true;
				}
				if(shouldEvaporate) tanks[1].setFill(tanks[1].getFill() - convert);
				else tanks[1].setFill(tanks[1].getFill() + convert);
				postConvert(convert);
			}
			if(tanks[1].getFill() > 0) this.sendFluidToAll(tanks[1], this);
			this.sendRuntimeState();
		} finally {
			runtimeFluidMutation = false;
		}
		this.updateThroughput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private boolean trackRuntimeTanks() {
		if(runtimeTrackedTanks == null || runtimeTrackedTanks.length != tanks.length) runtimeTrackedTanks = new FluidTank[tanks.length];
		boolean changed = false;
		for(int i = 0; i < tanks.length; i++) {
			if(runtimeTrackedTanks[i] != tanks[i]) {
				runtimeTrackedTanks[i] = tanks[i];
				if(tanks[i] != null) tanks[i].setChangeListener(runtimeTankListener);
				changed = true;
			}
		}
		return changed;
	}

	private int updateThroughput() {
		int convert = Math.min(tanks[0].getFill(), tanks[1].getMaxFill() - tanks[1].getFill());
		this.throughput = convert;
		return convert;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		int convert = this.updateThroughput();
		boolean outputPending = tanks[1].getFill() > 0;
		boolean timerPending = this.waterTimer > 0;
		if(outputPending || timerPending || (convert > 0 && extraCondition(convert))) this.scheduleMachineTransition(now + 1L, TASK_CONDENSE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_CONDENSE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		tanks[0].writeToNBT(data, "0");
		tanks[1].writeToNBT(data, "1");
		data.setByte("timer", (byte) this.waterTimer);
		packExtra(data);
		INBTPacketReceiver.networkPack(this, data, 150);
	}
	
	public void packExtra(NBTTagCompound data) { }
	public boolean extraCondition(int convert) { return true; }
	public void postConvert(int convert) { }

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.tanks[0].readFromNBT(nbt, "0");
		this.tanks[1].readFromNBT(nbt, "1");
		this.waterTimer = nbt.getByte("timer");
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks [1]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks [0]};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, throughput);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, throughput);
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}
}
