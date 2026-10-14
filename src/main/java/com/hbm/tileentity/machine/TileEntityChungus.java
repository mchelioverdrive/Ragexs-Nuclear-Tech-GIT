package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.Random;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.CompatHandler;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.NBTPacket;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityChungus extends TileEntityLoadedBase implements api.hbm.energymk2.IRatedGenerator, INBTPacketReceiver, IFluidStandardTransceiver, SimpleComponent, IInfoProviderEC, CompatHandler.OCComponent, IConfigurableMachine, IFluidCopiable, com.hbm.realsim.IMachineInfo {
    public long getSteamFlowMassPerTick() { return 2000000L; }
    public int getExpansionEfficiencyPpm() { if(worldObj != null && worldObj.isRemote && thermal.installedExpansionEfficiencyPpm >= 0) return thermal.installedExpansionEfficiencyPpm; return com.hbm.realsim.ThermalMachine.efficiency(efficiency, 880000); }
    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return 25000000L; }

	private static final int TASK_STEAM = 1;
	private boolean runtimeEnergyMutation;
	private boolean runtimeFluidMutation;
	private DirPos[] runtimeConnections;
	public static final int TRIP_NONE = 0;
	public static final int TRIP_OVERPRESSURE = 1;
	public static final int TRIP_OVERSPEED = 2;

	public long energyQuanta;
	/** The trip valves are closed while this is set, preventing any more steam admission. */
	private boolean tripped;
	private int tripCause = TRIP_NONE;
	private int turnTimer;
	public float rotor;
	public float lastRotor;
	public float fanAcceleration = 0F;

	public FluidTank[] tanks;
	protected double[] info = new double[3];

	private AudioWrapper audio;
	private float audioDesync;

	//Configurable values
	public static long maxPower = 100000000000L;
	public static int inputTankSize = 1_000_000_000;
	public static int outputTankSize = 1_000_000_000;
	public static double efficiency = 0.88D;

	public TileEntityChungus() {
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.STEAM, inputTankSize);
		tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize);
		FluidTank.ChangeListener listener = changed -> { if(!runtimeFluidMutation && worldObj != null && !worldObj.isRemote) markMachineFluidDirty(); };
		tanks[0].setChangeListener(listener);
		tanks[1].setChangeListener(listener);

		Random rand = new Random();
		audioDesync = rand.nextFloat() * 0.05F;
	}

	@Override
	public String getConfigName() {
		return "steamturbineLeviathan";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "L:energyCapacityQuanta", "L:maxPower", maxPower);
		inputTankSize = IConfigurableMachine.grab(obj, "I:inputTankSize", inputTankSize);
		outputTankSize = IConfigurableMachine.grab(obj, "I:outputTankSize", outputTankSize);
		efficiency = IConfigurableMachine.grab(obj, "D:efficiency", efficiency);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:energyCapacityQuanta").value(maxPower);
		writer.name("INFO").value("leviathan steam turbine is rated at 40 kg/s and 25 MWe; direct condensing expansion");
		writer.name("I:inputTankSize").value(inputTankSize);
		writer.name("I:outputTankSize").value(outputTankSize);
		writer.name("D:efficiency").value(efficiency);
	}



	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	private boolean shouldSimulate() {
		return energyQuanta > 0 || tanks[1].getFill() > 0 || turnTimer > 0 || (!tripped && tanks[0].getFill() > 0);
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if(tripped && worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) resetTrip();
		if(shouldSimulate()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_STEAM, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_STEAM || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		runtimeEnergyMutation = true;
		runtimeFluidMutation = true;
		try { runSteamStep(); markDirty(); }
		finally {
			runtimeFluidMutation = false;
			runtimeEnergyMutation = false;
		}
		if(shouldSimulate()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_STEAM, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
		if(!tripped) for(DirPos pos : getConPos())
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		if(!shouldSimulate()) sendRuntimePacket();
		onMachineRuntimeDirty(0);
	}

	private void sendRuntimePacket() {
		NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data, getExpansionEfficiencyPpm());
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setInteger("operational", turnTimer);
		data.setBoolean("tripped", tripped);
		data.setInteger("tripCause", tripCause);
		tanks[0].writeToNBT(data, "inputTank");
		tanks[1].writeToNBT(data, "outputTank");
		this.networkPack(data, 150);
	}

	private void runSteamStep() {
		if(!worldObj.isRemote) {
			// Export stored power before evaluating a reset or an overspeed trip. This
			// DOES NOT : lets a newly connected load drain a full buffer so redstone can reset an
			//basically redstone shit still doesn't work because FUCK YOU it's a multi block cry about it!!!!!
			// overspeed trip instead of immediately latching it again.
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			this.tryProvide(worldObj, xCoord - dir.offsetX * 11, yCoord, zCoord - dir.offsetZ * 11, dir.getOpposite());

			// A powered controller is an intentional remote reset for the trip valves.
			if(tripped && worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) {
				resetTrip();
			}

			java.util.Arrays.fill(this.info, 0D);

			boolean operational = false;
			boolean valid = false;
			FluidType in = tanks[0].getTankType();

			if(!tripped && isOverpressurized()) {
				trip(TRIP_OVERPRESSURE);
			} else if(!tripped && isOverspeeding()) {
				trip(TRIP_OVERSPEED);
			}


            if(!tripped && com.hbm.realsim.SteamLedger.expansionOutlet(thermal.ownedMass() > 0 ? thermal.inlet() : com.hbm.realsim.ThermalMachine.state(tanks[0])) != null) {
                long generated = com.hbm.realsim.ThermalMachine.turbine(thermal, worldObj.getTotalWorldTime(), tanks[0], tanks[1], getSteamFlowMassPerTick(),
                        25000000L, getExpansionEfficiencyPpm(), energyQuanta, maxPower);
                this.setStoredEnergyQuanta(Math.addExact(energyQuanta, generated));
                info[0] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) thermal.inlet().massPerUnit;
                info[1] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) com.hbm.realsim.ThermalMachine.state(tanks[1]).massPerUnit;
                info[2] = generated;
                valid = true;
                operational = thermal.currentMass > 0;
            }

			if(!valid && tanks[1].getFill() <= 0) tanks[1].setTankType(Fluids.NONE);


			// Export power produced during this tick as well as the stored power above.
			this.tryProvide(worldObj, xCoord - dir.offsetX * 11, yCoord, zCoord - dir.offsetZ * 11, dir.getOpposite());

			for(DirPos pos : this.getConPos()) {
				this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}


			turnTimer--;

			if(operational) turnTimer = 25;

			NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data, getExpansionEfficiencyPpm());
			EnergyUnits.writeEnergyQuanta(data, energyQuanta);
			data.setInteger("operational", turnTimer);
			data.setBoolean("tripped", tripped);
			data.setInteger("tripCause", tripCause);
			tanks[0].writeToNBT(data, "inputTank");
			tanks[1].writeToNBT(data, "outputTank");
			this.networkPack(data, 150);

		}
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;


			this.lastRotor = this.rotor;
			this.rotor += this.fanAcceleration;

			if(this.rotor >= 360) {
				this.rotor -= 360;
				this.lastRotor -= 360;
			}

			if(turnTimer > 0) {
				// Fan accelerates with a random offset to ensure the audio doesn't perfectly align, makes for a more pleasant hum
				this.fanAcceleration = Math.max(0F, Math.min(25F, this.fanAcceleration += 0.075F + audioDesync));

				Random rand = worldObj.rand;
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
				ForgeDirection side = dir.getRotation(ForgeDirection.UP);

				for(int i = 0; i < 10; i++) {
					worldObj.spawnParticle("cloud",
							xCoord + 0.5 + dir.offsetX * (rand.nextDouble() + 1.25) + rand.nextGaussian() * side.offsetX * 0.65,
							yCoord + 2.5 + rand.nextGaussian() * 0.65,
							zCoord + 0.5 + dir.offsetZ * (rand.nextDouble() + 1.25) + rand.nextGaussian() * side.offsetZ * 0.65,
							-dir.offsetX * 0.2, 0, -dir.offsetZ * 0.2);
				}


				if(audio == null) {
					audio = MainRegistry.proxy.getLoopedSound("hbm:block.chungusTurbineRunning", xCoord, yCoord, zCoord, 1.0F, 20F, 1.0F);
					audio.startSound();
				}

				float turbineSpeed = this.fanAcceleration / 25F;
				audio.updateVolume(getVolume(0.5f * turbineSpeed));
				audio.updatePitch(0.25F + 0.75F * turbineSpeed);
			} else {
				this.fanAcceleration = Math.max(0F, Math.min(25F, this.fanAcceleration -= 0.1F));

				if(audio != null) {
					if(this.fanAcceleration > 0) {
						float turbineSpeed = this.fanAcceleration / 25F;
						audio.updateVolume(getVolume(0.5f * turbineSpeed));
						audio.updatePitch(0.25F + 0.75F * turbineSpeed);
					} else {
						audio.stopSound();
						audio = null;
					}
				}
			}
	}

	public void onLeverPull(FluidType previous) {
		for(BlockPos pos : getConPos()) {
			this.tryUnsubscribe(previous, worldObj, pos.getX(), pos.getY(), pos.getZ());
		}
	}

	/** Reset the closed trip valves. This is deliberately separate from fluid-stage selection. */
	public void resetTrip() {
		this.tripped = false;
		this.tripCause = TRIP_NONE;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	private void trip(int cause) {
		this.tripped = true;
		this.tripCause = cause;
		this.turnTimer = 0;
		onLeverPull(tanks[0].getTankType());
		this.markDirty();
	}

	private boolean isOverpressurized() {
		return tanks[0].getFill() >= tanks[0].getMaxFill() && tanks[1].getFill() >= tanks[1].getMaxFill();
	}

	/**
	 * Steam admission must be closed when the generator's internal energy buffer is
	 * full and no connected consumer is exporting energy from it. Continuing to
	 * admit steam in this state would overspeed the turbine.
	 */
	private boolean isOverspeeding() {
		return energyQuanta >= maxPower && tanks[0].getFill() > 0;
	}

	/** Stops the rotor when there is no room for another generated power operation. */
	public boolean isTripped() {
		return tripped;
	}

	public int getTripCause() {
		return tripCause;
	}

	public DirPos[] getConPos() {
		if(runtimeConnections != null) return runtimeConnections;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		runtimeConnections = new DirPos[] {
				new DirPos(xCoord + dir.offsetX * 5, yCoord + 2, zCoord + dir.offsetZ * 5, dir),
				new DirPos(xCoord + rot.offsetX * 3, yCoord, zCoord + rot.offsetZ * 3, rot),
				new DirPos(xCoord - rot.offsetX * 3, yCoord, zCoord - rot.offsetZ * 3, rot.getOpposite())
		};
		return runtimeConnections;
	}

	public void networkPack(NBTTagCompound nbt, int range) {
		PacketDispatcher.wrapper.sendToAllAround(new NBTPacket(nbt, xCoord, yCoord, zCoord), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
        thermal.readStatus(data);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		this.turnTimer = data.getInteger("operational");
		this.tripped = data.getBoolean("tripped");
		this.tripCause = data.getInteger("tripCause");
		tanks[0].readFromNBT(data, "inputTank");
		tanks[1].readFromNBT(data, "outputTank");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermal.read(nbt); outputBudget.read(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tripped = nbt.getBoolean("tripped");
		tripCause = nbt.getInteger("tripCause");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); outputBudget.write(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("tripped", tripped);
		nbt.setInteger("tripCause", tripCause);
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
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "ntm_turbine";
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFluid(Context context, Arguments args) {
		return new Object[] {tanks[0].getFill(), tanks[0].getMaxFill(), tanks[1].getFill(), tanks[1].getMaxFill()};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getType(Context context, Arguments args) {
		return CompatHandler.steamTypeToInt(tanks[1].getTankType());
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setType(Context context, Arguments args) {
		tanks[0].setTankType(CompatHandler.intToSteamType(args.checkInteger(0)));
		return new Object[] {};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getInfo(Context context, Arguments args) {
		return new Object[] {tanks[0].getFill(), tanks[0].getMaxFill(), tanks[1].getFill(), tanks[1].getMaxFill(), CompatHandler.steamTypeToInt(tanks[0].getTankType())};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		return new String[] {
				"getFluid",
				"getType",
				"setType",
				"getInfo"
		};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		switch(method) {
			case ("getFluid"):
				return getFluid(context, args);
			case ("getType"):
				return getType(context, args);
			case ("setType"):
				return setType(context, args);
			case ("getInfo"):
				return getInfo(context, args);
		}
	throw new NoSuchMethodException();
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
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, info[1] > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, info[0]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, info[1]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(info[2]));
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        if(live) lines.add("Current electrical output: " + com.hbm.realsim.MachineInfo.watts(thermal.currentElectricQuanta * 10L));
        lines.add(String.format(java.util.Locale.US, "Steam mass-flow ceiling: %.2f kg/s", getSteamFlowMassPerTick() / 50000D));
        lines.add(String.format(java.util.Locale.US, "Expansion work factor: %.1f%%", getExpansionEfficiencyPpm() / 10000D));
        lines.add("Accepts ledger steam grades; full power/exhaust buffers stop admission");
    }
}
