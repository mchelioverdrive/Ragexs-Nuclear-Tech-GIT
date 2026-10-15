package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.Random;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.CompatHandler;
import com.hbm.inventory.container.ContainerMachineLargeTurbine;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineLargeTurbine;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityMachineLargeTurbine extends TileEntityMachineBase implements api.hbm.energymk2.IRatedGenerator, IFluidStandardTransceiver, IGUIProvider, SimpleComponent, IInfoProviderEC, CompatHandler.OCComponent, IConfigurableMachine, IFluidCopiable, com.hbm.realsim.IMachineInfo {
    public long getSteamFlowMassPerTick() { return 400000L; }
    public int getExpansionEfficiencyPpm() { if(worldObj != null && worldObj.isRemote && thermal.installedExpansionEfficiencyPpm >= 0) return thermal.installedExpansionEfficiencyPpm; return com.hbm.realsim.ThermalMachine.efficiency(efficiency, 850000); }
    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return 5000000L; }

	private static final int TASK_CONVERT = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;

	public long energyQuanta;
	public FluidTank[] tanks;
	protected double[] info = new double[3];
	
	private boolean shouldTurn;
	private boolean runtimeOperational;
	public float rotor;
	public float lastRotor;
	public float fanAcceleration = 0F;

	private AudioWrapper audio;
	private float audioDesync;
	
	//Configurable Values
	public static long maxPower = 1000000L;
	public static int inputTankSize = 1600;
	public static int outputTankSize = 16;
	public static boolean massSizedInlet = true;
	public static double efficiency = 0.85;


	public TileEntityMachineLargeTurbine() {
		super(7);
		
		tanks = new FluidTank[2];
		tanks[0] = new com.hbm.realsim.SteamInletTank(com.hbm.realsim.SteamLedger.State.SUPERHOT, inputTankSize, massSizedInlet);
		tanks[1] = new FluidTank(Fluids.SPENTSTEAM, outputTankSize).withDefaultCapacity();
		this.trackMachineFluidTank(tanks[0]);
		this.trackMachineFluidTank(tanks[1]);

		Random rand = new Random();
		audioDesync = rand.nextFloat() * 0.05F;
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeConnections();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.beginMachineFluidMutation();
		try { this.loadInputContainers(); }
		finally { this.endMachineFluidMutation(); }
		this.subscribeToInput();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimePacket(runtimeOperational);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshRuntimeConnections();
		this.updatePowerConnections();
		this.subscribeToInput();
		this.sendRuntimePacket(runtimeOperational);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CONVERT || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		this.beginMachineFluidMutation();
		try { this.runTurbineStep(); }
		finally {
			this.endMachineFluidMutation();
			runtimeEnergyMutation = false;
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void loadInputContainers() {
		tanks[0].setType(0, 1, slots);
		if(((com.hbm.realsim.SteamInletTank) tanks[0]).compatible()) tanks[0].loadTank(2, 3, slots);
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
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		this.registerPowerConnection(worldObj, xCoord + dir.offsetX * -4, yCoord, zCoord + dir.offsetZ * -4, dir.getOpposite());
	}

	private void evaluateAndSchedule(long now) {
		if(tanks[0].getFill() > 0 || tanks[1].getFill() > 0 || energyQuanta > 0) this.scheduleMachineTransition(now + 1L, TASK_CONVERT, 0);
		else {
			this.runtimeOperational = false;
			this.shouldTurn = false;
			this.cancelMachineTransition(TASK_CONVERT, 0);
		}
	}

	private void sendRuntimePacket(boolean operational) {
		NBTTagCompound data = new NBTTagCompound();
        thermal.writeStatus(data, getExpansionEfficiencyPpm());
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setBoolean("operational", operational);
		tanks[0].writeToNBT(data, "t0");
		tanks[1].writeToNBT(data, "t1");
		this.networkPack(data, 50);
	}

	@Override
	public String getConfigName() {
		return "steamturbineIndustrial";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "L:energyCapacityQuanta", "L:maxPower", maxPower);
		massSizedInlet = IConfigurableMachine.grab(obj, "B:massSizedInlet", !obj.has("I:inputTankSize"));
		inputTankSize = IConfigurableMachine.grab(obj, "I:inputTankSize", inputTankSize);
		outputTankSize = IConfigurableMachine.grab(obj, "I:outputTankSize", outputTankSize);
		efficiency = IConfigurableMachine.grab(obj, "D:efficiency", efficiency);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("L:energyCapacityQuanta").value(maxPower);
		writer.name("INFO").value("industrial steam turbine is rated at 8 kg/s and 5 MWe; direct condensing expansion");
		writer.name("I:inputTankSize").value(inputTankSize);
		writer.name("B:massSizedInlet").value(massSizedInlet);
		writer.name("I:outputTankSize").value(outputTankSize);
		writer.name("D:efficiency").value(efficiency);
	}

	@Override
	public String getName() {
		return "container.machineLargeTurbine";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) this.updateClientRotor();
	}

	private void runTurbineStep() {
            // Exhaust saturation governs admission; it is not a pressure measurement.

			
			this.info[0] = this.info[1] = this.info[2] = 0;
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
			this.providePowerToDirectReceiver(worldObj, xCoord + dir.offsetX * -4, yCoord, zCoord + dir.offsetZ * -4, dir.getOpposite());

			for(DirPos pos : runtimeConnections) {
				this.sendFluid(tanks[1], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
                if(!((com.hbm.realsim.SteamInletTank) tanks[0]).compatible()) this.sendFluid(tanks[0], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}

			this.loadInputContainers();
			this.setStoredEnergyQuanta(this.chargeRatedItems(slots, 4, maxPower));
			
			boolean operational = false;
			
			FluidType in = tanks[0].getTankType();
			boolean valid = false;

            if((thermal.ownedMass() > 0 || com.hbm.realsim.GeneratorContract.acceptsSteam(com.hbm.realsim.ThermalMachine.state(tanks[0]), com.hbm.realsim.SteamLedger.State.SUPERHOT))) {
                long generated = com.hbm.realsim.ThermalMachine.turbine(thermal, worldObj.getTotalWorldTime(), tanks[0], tanks[1], getSteamFlowMassPerTick(),
                        5000000L, getExpansionEfficiencyPpm(), energyQuanta, maxPower);
                this.setStoredEnergyQuanta(Math.addExact(energyQuanta, generated));
                info[0] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) thermal.inlet().massPerUnit;
                info[1] = thermal.currentMass == 0 ? 0 : thermal.currentMass / (double) com.hbm.realsim.ThermalMachine.state(tanks[1]).massPerUnit;
                info[2] = generated;
                valid = true;
                operational = thermal.currentMass > 0;
            }

			if(!valid && tanks[1].getFill() <= 0) tanks[1].setTankType(Fluids.NONE);

			
			tanks[1].unloadTank(5, 6, slots);
			
			this.runtimeOperational = operational;
			this.sendRuntimePacket(operational);
	}

	private void updateClientRotor() {
			this.lastRotor = this.rotor;
			this.rotor += this.fanAcceleration;
				
			if(this.rotor >= 360) {
				this.rotor -= 360;
				this.lastRotor -= 360;
			}
			
			if(shouldTurn) {
				// Fan accelerates with a random offset to ensure the audio doesn't perfectly align, makes for a more pleasant hum
				this.fanAcceleration = Math.max(0F, Math.min(15F, this.fanAcceleration += 0.075F + audioDesync));

				if(audio == null) {
					audio = MainRegistry.proxy.getLoopedSound("hbm:block.largeTurbineRunning", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F);
					audio.startSound();
				}

				float turbineSpeed = this.fanAcceleration / 15F;
				audio.updateVolume(getVolume(0.4f * turbineSpeed));
				audio.updatePitch(0.25F + 0.75F * turbineSpeed);
			} else {
				this.fanAcceleration = Math.max(0F, Math.min(15F, this.fanAcceleration -= 0.1F));
				
				if(audio != null) {
					if(this.fanAcceleration > 0) {
						float turbineSpeed = this.fanAcceleration / 15F;
						audio.updateVolume(getVolume(0.4f * turbineSpeed));
						audio.updatePitch(0.25F + 0.75F * turbineSpeed);
					} else {
						audio.stopSound();
						audio = null;
					}
				}
			}
	}


	protected DirPos[] buildConnections() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2, rot),
				new DirPos(xCoord - rot.offsetX * 2, yCoord, zCoord - rot.offsetZ * 2, rot.getOpposite()),
				new DirPos(xCoord + dir.offsetX * 2, yCoord, zCoord + dir.offsetZ * 2, dir)
		};
	}
	
	public void networkUnpack(NBTTagCompound data) {
        thermal.readStatus(data);
		super.networkUnpack(data);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
		this.shouldTurn = data.getBoolean("operational");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[0], data, "t0");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[1], data, "t1");
	}
	
	public long getPowerScaled(int i) {
		return (Math.min(energyQuanta, maxPower) * i) / Math.max(1, maxPower);
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts((long) info[2]);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermal.read(nbt); outputBudget.read(nbt);
		tanks[0].readFromNBT(nbt, "water");
		tanks[1].readFromNBT(nbt, "steam");
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); outputBudget.write(nbt);
		tanks[0].writeToNBT(nbt, "water");
		tanks[1].writeToNBT(nbt, "steam");
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
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
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[1]} : tanks;
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return ((com.hbm.realsim.SteamInletTank) tanks[0]).compatible() ? new FluidTank[] {tanks[0]} : new FluidTank[0];
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
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineLargeTurbine(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineLargeTurbine(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, info[1] > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, info[0]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, info[1]);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(info[2]));
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        if(live) lines.add("Current electrical output: " + com.hbm.realsim.MachineInfo.watts(thermal.currentElectricQuanta * 10L));
        lines.add(String.format(java.util.Locale.US, "Steam mass-flow ceiling: %.2f kg/s", getSteamFlowMassPerTick() / 50000D));
        lines.add(String.format(java.util.Locale.US, "Expansion work factor: %.1f%%", getExpansionEfficiencyPpm() / 10000D));
        lines.add("Accepts steam through Superhot; pauses when electricity or exhaust cannot leave");
    }
}
