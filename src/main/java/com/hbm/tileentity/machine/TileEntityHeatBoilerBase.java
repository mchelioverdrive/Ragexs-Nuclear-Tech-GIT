package com.hbm.tileentity.machine;

import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorStandard;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.lib.Library;
import com.hbm.saveddata.TomSaveData;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IHeatSource;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.util.ForgeDirection;

/** Shared continuous simulation and runtime ownership for both heat boiler variants. */
public abstract class TileEntityHeatBoilerBase extends TileEntityLoadedBase implements INBTPacketReceiver, IFluidStandardTransceiver, IConfigurableMachine, IFluidCopiable {
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private FluidTank[] runtimeTrackedTanks;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;
	private final FluidTank.ChangeListener runtimeTankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank changedTank) {
			if(worldObj == null) return;
			markDirty();
			if(!runtimeFluidMutation && !worldObj.isRemote) markMachineFluidDirty();
		}
	};

	public int heat;
	public FluidTank[] tanks;
	public boolean isOn;

	protected abstract int getMaxBoilerHeat();
	protected abstract double getBoilerDiffusion();
	protected abstract DirPos[] getBoilerConnections();
	protected abstract boolean shouldExplodeOnBlockedOutput();
	protected abstract void handleBlockedOutput();

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void updateEntity() {
		// Boiler physics, fluid conversion, and transfer are driven by MachineRuntime.
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.reconcileTankTypes();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState(heat);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.sendRuntimeState(heat);
		if(!this.isBoilerDisabled() && heat == 0 && tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && this.hasExternalHeatOpportunity()) {
			this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.isBoilerDisabled()) {
			this.sendRuntimeState(heat);
			this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
			return;
		}
		this.runtimeFluidMutation = true;
		int previousHeat = this.heat;
		boolean previousActive = this.isOn;
		try {
			this.setupTanks();
			this.tryPullHeat();
			int reportedHeat = this.heat;
			this.applyAmbientHeat();
			NBTTagCompound data = new NBTTagCompound();
			tanks[0].writeToNBT(data, "0");
			data.setInteger("heat", reportedHeat);
			this.isOn = false;
			this.tryConvert();
			tanks[1].writeToNBT(data, "1");
			if(tanks[1].getFill() > 0) this.sendFluid();
			data.setBoolean("isOn", this.isOn);
			data.setBoolean("muffled", this.muffled);
			this.writeExtraPacketData(data);
			INBTPacketReceiver.networkPack(this, data, 25);
		} finally {
			this.runtimeFluidMutation = false;
		}
		if(previousHeat != this.heat || previousActive != this.isOn) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	protected boolean isBoilerDisabled() { return false; }
	protected void writeExtraPacketData(NBTTagCompound data) { }

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

	private void reconcileTankTypes() {
		runtimeFluidMutation = true;
		try { this.setupTanks(); }
		finally { runtimeFluidMutation = false; }
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.getBoilerConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToInput() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void sendFluid() {
		for(DirPos pos : runtimeConnections) this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir().getOpposite());
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized || this.isBoilerDisabled()) {
			this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
			return;
		}
		if(heat > 0 || tanks[1].getFill() > 0 || this.hasExternalHeatOpportunity()) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private boolean hasExternalHeatOpportunity() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		if(con instanceof IHeatSource && ((IHeatSource) con).getHeatStored() > heat) return true;
		return this.isAmbientHeatAvailable() && heat < this.getMaxBoilerHeat();
	}

	private boolean isAmbientHeatAvailable() {
		int light = worldObj.getSavedLightValue(EnumSkyBlock.Sky, xCoord, yCoord, zCoord);
		return light > 7 && TomSaveData.forWorld(worldObj).fire > 1e-5;
	}

	private void applyAmbientHeat() {
		if(this.isAmbientHeatAvailable()) this.heat += ((this.getMaxBoilerHeat() - heat) * 0.000005D);
	}

	protected void tryPullHeat() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		if(con instanceof IHeatSource) {
			IHeatSource source = (IHeatSource) con;
			int diff = source.getHeatStored() - this.heat;
			if(diff == 0) return;
			if(diff > 0) {
				diff = (int) Math.ceil(diff * this.getBoilerDiffusion());
				source.useUpHeat(diff);
				this.heat += diff;
				if(this.heat > this.getMaxBoilerHeat()) this.heat = this.getMaxBoilerHeat();
				return;
			}
		}
		this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
	}

	protected void setupTanks() {
		if(tanks[0].getTankType().hasTrait(FT_Heatable.class)) {
			FT_Heatable trait = tanks[0].getTankType().getTrait(FT_Heatable.class);
			if(trait.getEfficiency(HeatingType.BOILER) > 0) {
				HeatingStep entry = trait.getFirstStep();
				tanks[1].setTankType(entry.typeProduced);
				tanks[1].changeTankSize(tanks[0].getMaxFill() * entry.amountProduced / entry.amountReq);
				return;
			}
		}
		tanks[0].setTankType(Fluids.NONE);
		tanks[1].setTankType(Fluids.NONE);
	}

	protected void tryConvert() {
		if(!tanks[0].getTankType().hasTrait(FT_Heatable.class)) return;
		FT_Heatable trait = tanks[0].getTankType().getTrait(FT_Heatable.class);
		if(trait.getEfficiency(HeatingType.BOILER) <= 0) return;
		HeatingStep entry = trait.getFirstStep();
		int inputOps = tanks[0].getFill() / entry.amountReq;
		int outputOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / entry.amountProduced;
		int heatOps = this.heat / entry.heatReq;
		int ops = Math.min(inputOps, Math.min(outputOps, heatOps));
		tanks[0].setFill(tanks[0].getFill() - entry.amountReq * ops);
		tanks[1].setFill(tanks[1].getFill() + entry.amountProduced * ops);
		this.heat -= entry.heatReq * ops;
		if(ops > 0 && worldObj.rand.nextInt(400) == 0) worldObj.playSoundEffect(xCoord + 0.5, yCoord + 2, zCoord + 0.5, "hbm:block.boilerGroan", 0.5F, 1.0F);
		if(ops > 0) this.isOn = true;
		if(inputOps > 0 && heatOps > 0 && outputOps == 0 && this.shouldExplodeOnBlockedOutput()) this.handleBlockedOutput();
	}

	private void sendRuntimeState(int reportedHeat) {
		NBTTagCompound data = new NBTTagCompound();
		tanks[0].writeToNBT(data, "0");
		tanks[1].writeToNBT(data, "1");
		data.setInteger("heat", reportedHeat);
		data.setBoolean("isOn", this.isOn);
		data.setBoolean("muffled", this.muffled);
		this.writeExtraPacketData(data);
		INBTPacketReceiver.networkPack(this, data, 25);
	}

	@Override public void networkUnpack(NBTTagCompound nbt) {
		this.heat = nbt.getInteger("heat");
		this.tanks[0].readFromNBT(nbt, "0");
		this.tanks[1].readFromNBT(nbt, "1");
		this.isOn = nbt.getBoolean("isOn");
		this.muffled = nbt.getBoolean("muffled");
	}

	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		heat = nbt.getInteger("heat");
	}

	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		nbt.setInteger("heat", heat);
	}

	@Override public FluidTank[] getAllTanks() { return tanks; }
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] {tanks[1]}; }
	@Override public FluidTank[] getReceivingTanks() { return new FluidTank[] {tanks[0]}; }
}
