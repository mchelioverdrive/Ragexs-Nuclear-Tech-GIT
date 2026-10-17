package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntitySteamEngine extends TileEntityLoadedBase implements api.hbm.energymk2.IRatedGenerator, IFluidStandardTransceiver, INBTPacketReceiver, IConfigurableMachine, IFluidCopiable, com.hbm.realsim.IMachineInfo {
    public long getSteamFlowMassPerTick() { return 15000L; }
    public int getExpansionEfficiencyPpm() { if(worldObj != null && worldObj.isRemote && thermal.installedExpansionEfficiencyPpm >= 0) return thermal.installedExpansionEfficiencyPpm; return com.hbm.realsim.ThermalMachine.efficiency(efficiency, 700000); }
    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return 25000L; }

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
	private static int steamCap = 100;
	private static int ldsCap = 2;
	private static double efficiency = 0.7D;

	public TileEntitySteamEngine() {

		tanks = new FluidTank[2];
		tanks[0] = new com.hbm.realsim.SteamInletTank(com.hbm.realsim.SteamLedger.State.ORDINARY, steamCap, false);
		tanks[1] = new FluidTank(Fluids.SPENTSTEAM, ldsCap).withDefaultCapacity();
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
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.trackRuntimeTanks()) this.markMachineFluidDirty();
		this.refreshRuntimeConnections();
		this.updatePowerConnections();
		this.subscribeToInput();
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeFluidMutation = true;
		runtimeEnergyMutation = true;
		try {
            this.reconcileTankTypes();
            long generated = com.hbm.realsim.ThermalMachine.ungovernedTurbine(thermal, worldObj.getTotalWorldTime(), tanks[0], tanks[1],
                    getSteamFlowMassPerTick(), 25000L, getExpansionEfficiencyPpm(), powerBuffer, getEnergyCapacityQuanta());
            int ops = thermal.currentMass > 0 ? 1 : 0;
            this.setStoredEnergyQuanta(Math.addExact(powerBuffer, generated));

			if(ops > 0) this.acceleration += 0.1F;
			else this.acceleration -= 0.1F;
			this.acceleration = MathHelper.clamp_float(this.acceleration, 0F, 40F);
			this.rotor += this.acceleration;
			if(this.rotor >= 360D) {
				this.rotor -= 360D;
				this.worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:block.steamEngineOperate", getVolume(1.0F), 0.5F + (acceleration / 80F));
			}

			for(DirPos pos : runtimeConnections) {
				if(this.powerBuffer > 0) this.providePowerToDirectReceiver(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
                if(!((com.hbm.realsim.SteamInletTank) tanks[0]).compatible()) this.sendFluid(tanks[0], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
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
			tanks[0].configureForRecipe(Fluids.STEAM, tanks[0].getPressure());
			tanks[1].configureForRecipe(Fluids.SPENTSTEAM, tanks[1].getPressure());
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

	private void updatePowerConnections() {
		for(DirPos pos : runtimeConnections) this.registerPowerConnection(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
        boolean canConvert = (tanks[0].getFill() > 0 || thermal.ownedMass() > 0) && tanks[1].getFill() < tanks[1].getMaxFill();
		if(canConvert || tanks[1].getFill() > 0 || acceleration > 0F || powerBuffer > 0L) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data, getExpansionEfficiencyPpm());
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
        thermal.read(nbt); outputBudget.read(nbt);

		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "powerBuffer");
		this.acceleration = nbt.getFloat("acceleration");
		this.tanks[0].readFromNBT(nbt, "s");
		this.tanks[1].readFromNBT(nbt, "w");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); outputBudget.write(nbt);

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
		return com.hbm.realsim.GeneratorContract.buffer(getRatedOutputWatts());
	}

	@Override
	public void setStoredEnergyQuanta(long power) {
		if(this.powerBuffer == power) return;
		this.powerBuffer = power;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public long getPowerOutputWatts() {
        return EnergyUnits.quantaPerTickToWatts(thermal.currentElectricQuanta);
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[1]} : tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[0]} : new FluidTank[0];
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
        thermal.readStatus(nbt);
		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.syncRotor = nbt.getFloat("rotor");
		this.turnProgress = 3; //use 3-ply for extra smoothness
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[0], nbt, "s");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[1], nbt, "w");
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Steam admission continues unloaded; unused work is rejected through the ambient sink");
        if(live) lines.add("Current electrical output: " + com.hbm.realsim.MachineInfo.watts(thermal.currentElectricQuanta * 10L));
        lines.add(String.format(java.util.Locale.US, "Steam mass-flow ceiling: %.2f kg/s", getSteamFlowMassPerTick() / 50000D));
        lines.add(String.format(java.util.Locale.US, "Expansion work factor: %.1f%%", getExpansionEfficiencyPpm() / 10000D));
        lines.add("Ordinary steam only; pauses when electricity or exhaust cannot leave");
    }
}
