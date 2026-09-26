package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntitySteamEngine extends TileEntityLoadedBase implements IEnergyProviderMK2, IFluidStandardTransceiver, INBTPacketReceiver, IConfigurableMachine, IFluidCopiable {
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private boolean runtimeEnergyMutation;
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

	public long powerBuffer;

	public float rotor;
	public float lastRotor;
	private float syncRotor;
	public FluidTank[] tanks;

	private int turnProgress;
	private float acceleration = 0F;

	/* CONFIGURABLE */
	private static int steamCap = 2_000;
	private static int ldsCap = 20;
	private static double efficiency = 0.85D;

	public TileEntitySteamEngine() {

		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.STEAM, steamCap);
		tanks[1] = new FluidTank(Fluids.SPENTSTEAM, ldsCap);
	}

	@Override
	public String getConfigName() {
		return "steamengine";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		steamCap = IConfigurableMachine.grab(obj, "I:steamCap", steamCap);
		ldsCap = IConfigurableMachine.grab(obj, "I:ldsCap", ldsCap);
		efficiency = IConfigurableMachine.grab(obj, "D:efficiency", efficiency);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:steamCap").value(steamCap);
		writer.name("I:ldsCap").value(ldsCap);
		writer.name("D:efficiency").value(efficiency);
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			this.lastRotor = this.rotor;

			if(this.turnProgress > 0) {
				double d = MathHelper.wrapAngleTo180_double(this.syncRotor - (double) this.rotor);
				this.rotor = (float) ((double) this.rotor + d / (double) this.turnProgress);
				--this.turnProgress;
			} else {
				this.rotor = this.syncRotor;
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.trackRuntimeTanks();
		this.reconcileTankTypes();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.refreshRuntimeConnections();
		this.subscribeToInput();
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeFluidMutation = true;
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(0);
			this.reconcileTankTypes();

			FT_Coolable trait = tanks[0].getTankType().getTrait(FT_Coolable.class);
			double eff = trait.getEfficiency(CoolingType.TURBINE) * efficiency;
			int inputOps = tanks[0].getFill() / trait.amountReq;
			int outputOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / trait.amountProduced;
			int ops = Math.min(inputOps, outputOps);
			tanks[0].setFill(tanks[0].getFill() - ops * trait.amountReq);
			tanks[1].setFill(tanks[1].getFill() + ops * trait.amountProduced);
			this.setStoredEnergyQuanta(this.powerBuffer + (long) (ops * trait.heatEnergy * eff));

			if(ops > 0) this.acceleration += 0.1F;
			else this.acceleration -= 0.1F;
			this.acceleration = MathHelper.clamp_float(this.acceleration, 0F, 40F);
			this.rotor += this.acceleration;
			if(this.rotor >= 360D) {
				this.rotor -= 360D;
				this.worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:block.steamEngineOperate", getVolume(1.0F), 0.5F + (acceleration / 80F));
			}

			for(DirPos pos : runtimeConnections) {
				if(this.powerBuffer > 0) this.tryProvide(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			this.sendRuntimeState();
		} finally {
			runtimeEnergyMutation = false;
			runtimeFluidMutation = false;
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private boolean trackRuntimeTanks() {
		if(runtimeTrackedTanks == null || runtimeTrackedTanks.length != tanks.length) runtimeTrackedTanks = new FluidTank[tanks.length];
		boolean changed = false;
		for(int i = 0; i < tanks.length; i++) {
			if(runtimeTrackedTanks[i] != tanks[i]) {
				if(runtimeTrackedTanks[i] != null) runtimeTrackedTanks[i].setChangeListener(null);
				runtimeTrackedTanks[i] = tanks[i];
				if(tanks[i] != null) tanks[i].setChangeListener(runtimeTankListener);
				changed = true;
			}
		}
		return changed;
	}

	private void reconcileTankTypes() {
		runtimeFluidMutation = true;
		try {
			tanks[0].setTankType(Fluids.STEAM);
			tanks[1].setTankType(Fluids.SPENTSTEAM);
		} finally {
			runtimeFluidMutation = false;
		}
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.buildConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToInput() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		FT_Coolable trait = tanks[0].getTankType().getTrait(FT_Coolable.class);
		int inputOps = tanks[0].getFill() / trait.amountReq;
		int outputOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / trait.amountProduced;
		boolean canConvert = Math.min(inputOps, outputOps) > 0;
		if(canConvert || tanks[1].getFill() > 0 || acceleration > 0F || powerBuffer > 0L) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		tanks[0].writeToNBT(data, "s");
		EnergyUnits.writeEnergyQuanta(data, this.powerBuffer);
		data.setFloat("rotor", this.rotor);
		tanks[1].writeToNBT(data, "w");
		INBTPacketReceiver.networkPack(this, data, 150);
	}

	protected DirPos[] getConPos() {
		this.refreshRuntimeConnections();
		return runtimeConnections;
	}

	private DirPos[] buildConnections() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 2, yCoord + 1, zCoord + rot.offsetZ * 2, rot),
				new DirPos(xCoord + rot.offsetX * 2 + dir.offsetX, yCoord + 1, zCoord + rot.offsetZ * 2 + dir.offsetZ, rot),
				new DirPos(xCoord + rot.offsetX * 2 - dir.offsetX, yCoord + 1, zCoord + rot.offsetZ * 2 - dir.offsetZ, rot)
		};
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "powerBuffer");
		this.acceleration = nbt.getFloat("acceleration");
		this.tanks[0].readFromNBT(nbt, "s");
		this.tanks[1].readFromNBT(nbt, "w");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, powerBuffer);
		nbt.setFloat("acceleration", acceleration);
		tanks[0].writeToNBT(nbt, "s");
		tanks[1].writeToNBT(nbt, "w");
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UP && dir != ForgeDirection.DOWN && dir != ForgeDirection.UNKNOWN;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return powerBuffer;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return powerBuffer;
	}

	@Override
	public void setStoredEnergyQuanta(long power) {
		if(this.powerBuffer == power) return;
		this.powerBuffer = power;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public long getPowerOutputWatts() {
		FT_Coolable trait = tanks[0].getTankType().getTrait(FT_Coolable.class);
		int inputOps = tanks[0].getFill() / trait.amountReq;
		int outputOps = (tanks[1].getMaxFill() - tanks[1].getFill()) / trait.amountProduced;
		long generationQuantaPerTick = (long) (Math.min(inputOps, outputOps) * trait.heatEnergy * trait.getEfficiency(CoolingType.TURBINE) * efficiency);
		return EnergyUnits.quantaPerTickToWatts(generationQuantaPerTick);
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.syncRotor = nbt.getFloat("rotor");
		this.turnProgress = 3; //use 3-ply for extra smoothness
		this.tanks[0].readFromNBT(nbt, "s");
		this.tanks[1].readFromNBT(nbt, "w");
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}
}
