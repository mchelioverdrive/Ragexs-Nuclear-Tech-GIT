package com.hbm.tileentity.machine;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.realsim.Combustion;
import com.hbm.realsim.FuelCredit;
import com.hbm.realsim.GeneratorStats;
import com.hbm.inventory.container.ContainerOilburner;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.gui.GUIOilburner;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachinePolluting;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IHeatSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class TileEntityHeaterOilburner extends TileEntityMachinePolluting implements IGUIProvider, IFluidStandardTransceiver, IHeatSource, IControlReceiver, IFluidCopiable, api.hbm.tile.IProcessHeatSource {
	private static final int TASK_BURN = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeWaterlogged;
	private boolean waterloggedInitialized;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;
	
	public boolean isOn = false;
	public FluidTank tank;
	public int setting = 1;

	public int heatEnergy;
	public static final int maxHeatEnergy = 100_000;
	public static final long ratedThermalWatts = 200_000L;
	public final FuelCredit fuelCredit = new FuelCredit();
	public final GeneratorStats generatorStats = new GeneratorStats();
	private long lastBurnTick = Long.MIN_VALUE;

	public TileEntityHeaterOilburner() {
		super(3, 100);
		tank = new FluidTank(Fluids.HEATINGOIL, 200).withDefaultCapacity();
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.heaterOilburner";
	}
	
	public DirPos[] getConPos() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z)
			};
			observedOrientation = orientation;
		}
		return runtimeConnections;
	}

	@Override
	public void updateEntity() {
		// Fuel, heat, and smoke processing are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.beginMachineFluidMutation();
		try {
			tank.loadTank(0, 1, slots);
			tank.setType(2, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.refreshWaterlogged();
		this.refreshRuntimeConnections();
		this.subscribeToFuel();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(25);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(cadence == 5) {
			if(this.refreshWaterlogged()) this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
		} else if(cadence == 20) {
			this.refreshRuntimeConnections();
			this.subscribeToFuel();
			this.networkPackNT(25);
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_BURN || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(lastBurnTick == worldObj.getTotalWorldTime()) return;
		lastBurnTick = worldObj.getTotalWorldTime();
		int previousHeat = heatEnergy;
		generatorStats.idle();
		this.beginMachineFluidMutation();
		try {
			tank.loadTank(0, 1, slots);
			tank.setType(2, slots);
			for(DirPos pos : runtimeConnections) this.sendSmoke(pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			boolean shouldCool = true;
			long demand = Math.min(ratedThermalWatts / 10L * Math.max(1, Math.min(10, setting)) / 10L, Math.max(0, maxHeatEnergy - heatEnergy));
			if(!runtimeWaterlogged && this.isOn && demand > 0 && (fuelCredit.hasCredit() || Combustion.isBurnerFuel(tank.getTankType()) && tank.getFill() > 0) && breatheAir(1)) {
				long heat = Combustion.generate(tank, fuelCredit, demand, 850_000);
				heatEnergy += (int) heat;
				generatorStats.record(heat, fuelCredit);
				if(heat > 0) FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
				if(worldObj.getTotalWorldTime() % 5 == 0 && heat > 0) super.pollute(Fluids.fromName(fuelCredit.fuel()), FluidReleaseType.BURN, (float) (generatorStats.fuelUnitsPerSecond * 1.25D));
				shouldCool = heat == 0;
			}
			if(heatEnergy >= maxHeatEnergy) shouldCool = false;
			if(shouldCool) heatEnergy = Math.max(heatEnergy - Math.max(heatEnergy / 1000, 1), 0);
			this.networkPackNT(25);
		} finally {
			this.endMachineFluidMutation();
		}
		if(previousHeat != heatEnergy || generatorStats.outputQuanta > 0) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private boolean refreshWaterlogged() {
		boolean current = this.isWaterlogged();
		boolean changed = waterloggedInitialized && current != runtimeWaterlogged;
		runtimeWaterlogged = current;
		waterloggedInitialized = true;
		return changed;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean burnOpportunity = !runtimeWaterlogged && isOn && heatEnergy < maxHeatEnergy && (fuelCredit.hasCredit() || Combustion.isBurnerFuel(tank.getTankType()) && tank.getFill() > 0);
		if(heatEnergy > 0 || burnOpportunity || this.hasSmoke()) this.scheduleMachineTransition(now + 1L, TASK_BURN, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_BURN, TASK_SLOT_MAIN);
	}

	private boolean hasSmoke() {
		for(FluidTank smokeTank : this.getSmokeTanks()) if(smokeTank.getFill() > 0) return true;
		return false;
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.getConPos();
			observedOrientation = orientation;
		}
	}

	private void subscribeToFuel() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		tank.serialize(buf);
		
		buf.writeBoolean(isOn);
		buf.writeInt(heatEnergy);
		buf.writeByte((byte) this.setting);
		generatorStats.write(buf, fuelCredit);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		tank.deserialize(buf);
		
		isOn = buf.readBoolean();
		heatEnergy = buf.readInt();
		setting = buf.readByte();
		generatorStats.read(buf, fuelCredit);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		tank.readFromNBT(nbt, "tank");
		isOn = nbt.getBoolean("isOn");
		heatEnergy = nbt.getInteger("heatEnergy");
		setting = nbt.getByte("setting");
		setting = Math.max(1, Math.min(10, setting));
		fuelCredit.read(nbt);
		lastBurnTick = nbt.hasKey("realSimGenerationTick") ? nbt.getLong("realSimGenerationTick") : Long.MIN_VALUE;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		tank.writeToNBT(nbt, "tank");
		nbt.setBoolean("isOn", isOn);
		nbt.setInteger("heatEnergy", heatEnergy);
		nbt.setByte("setting", (byte) this.setting);
		fuelCredit.write(nbt);
		nbt.setLong("realSimGenerationTick", lastBurnTick);
	}
	
	public void toggleSetting() {
		setting++;
		
		if(setting > 10)
			setting = 1;
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks()  {
		return new FluidTank[] { tank };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerOilburner(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIOilburner(player.inventory, this);
	}

	@Override
	public int getHeatStored() {
		return heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		if(heat <= 0) return;
		this.heatEnergy = Math.max(0, this.heatEnergy - heat);
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistanceSq(xCoord, yCoord, zCoord) <= 256;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("toggle")) {
			this.isOn = !this.isOn;
		}
		this.markChanged();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 1,
					yCoord,
					zCoord - 1,
					xCoord + 2,
					yCoord + 2,
					zCoord + 2
					);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.getSmokeTanks();
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setIntArray("fluidID", new int[]{tank.getTankType().getID()});
		tag.setInteger("burnRate", setting);
		tag.setBoolean("isOn", isOn);
		return tag;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		int id = nbt.getIntArray("fluidID")[index];
		tank.setTankType(Fluids.fromID(id));
		if(nbt.hasKey("isOn")) isOn = nbt.getBoolean("isOn");
		if(nbt.hasKey("burnRate")) setting = Math.max(1, Math.min(10, nbt.getInteger("burnRate")));
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.FLUID);
	}
    @Override public int getProcessTemperatureC() { return 900; }
}
