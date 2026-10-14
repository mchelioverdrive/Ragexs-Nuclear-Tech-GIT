package com.hbm.tileentity.machine;

import java.util.HashSet;

import com.hbm.dim.CelestialBody;
import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;

public class TileEntitySolarBoiler extends TileEntityLoadedBase implements IFluidStandardTransceiver, IBufPacketReceiver, IFluidCopiable, com.hbm.realsim.IMachineInfo {
    public final com.hbm.realsim.ThermalAccount thermal = new com.hbm.realsim.ThermalAccount();
    public long thermalBufferQuanta, startupQuanta;
    public static final long RATED_THERMAL_WATTS = 500000L;

	private static final int TASK_CONVERT = 1;
	private boolean runtimeFluidMutation;
	private final FluidTank.ChangeListener tankListener = new FluidTank.ChangeListener() {
		@Override public void onTankChanged(FluidTank tank) {
			if(worldObj != null && !worldObj.isRemote && !runtimeFluidMutation) markMachineFluidDirty();
		}
	};

	private FluidTank water;
	private FluidTank steam;
	public int heat;

	public HashSet<ChunkCoordinates> primary = new HashSet<>();
	public HashSet<ChunkCoordinates> secondary = new HashSet<>();

	public TileEntitySolarBoiler() {
		water = new FluidTank(Fluids.FRESH_WATER, 100).migrateFrom(Fluids.WATER);
		steam = new FluidTank(Fluids.STEAM, 10_000);
		water.setChangeListener(tankListener);
		steam.setChangeListener(tankListener);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	/** Mirrors are the authoritative external heat source for this collector. */
	public void addMirrorHeat(int amount) {
		if(amount <= 0) return;
		heat = Math.toIntExact(Math.min(RATED_THERMAL_WATTS / 10, Math.addExact(heat, Math.multiplyExact((long) Math.min(amount, 4), 75L)))); // 3 kWth per 4 m2 mirror at full sky
		this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.subscribeToWater();
		if(heat > 0 || thermalBufferQuanta > 0 || thermal.ownedMass() > 0 || steam.getFill() > 0) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_CONVERT, 0);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
		this.subscribeToWater();
	}

	private void subscribeToWater() {
		this.trySubscribe(water.getTankType(), worldObj, xCoord, yCoord + 3, zCoord, Library.POS_Y);
		this.trySubscribe(water.getTankType(), worldObj, xCoord, yCoord - 1, zCoord, Library.NEG_Y);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CONVERT || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		this.convertMirrorHeat();
	}

	private boolean isReceivingSunlight() {

		if(!worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord))
			return false;

		return worldObj.isDaytime();
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			secondary.clear();
			secondary.addAll(primary);
			primary.clear();
		}
	}

	private void convertMirrorHeat() {
		runtimeFluidMutation = true;
		try {
            thermalBufferQuanta = Math.min(16000000L, Math.addExact(thermalBufferQuanta, heat));
            heat = 0;
            if(startupQuanta < 1000000L) {
                long paid = Math.min(1000000L - startupQuanta, thermalBufferQuanta);
                thermalBufferQuanta -= paid; startupQuanta += paid;
            }
            if(startupQuanta == 1000000L) {
                com.hbm.realsim.ThermalAccount.Result r = com.hbm.realsim.ThermalMachine.transfer(thermal, worldObj.getTotalWorldTime(),
                        com.hbm.realsim.SteamLedger.transition(com.hbm.realsim.SteamLedger.State.COLD, com.hbm.realsim.SteamLedger.State.ORDINARY, com.hbm.realsim.SteamLedger.Route.BOILER, 950000),
                        water, steam, 10000L, thermalBufferQuanta, 0, 0, RATED_THERMAL_WATTS / 10, 0, 0, Long.MAX_VALUE);
                thermalBufferQuanta -= r.heatQuanta;
            }
            this.sendFluid(steam, worldObj, xCoord, yCoord + 3, zCoord, Library.POS_Y);
            this.sendFluid(steam, worldObj, xCoord, yCoord - 1, zCoord, Library.NEG_Y);
            if(thermalBufferQuanta > 0 || thermal.ownedMass() > 0 || steam.getFill() > 0) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_CONVERT, 0);
            markDirty();

			networkPackNT(15);
		} finally {
			runtimeFluidMutation = false;
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermal.read(nbt); heat = nbt.getInteger("realSimSolarPending"); thermalBufferQuanta = nbt.getLong("realSimSolarHeat"); startupQuanta = nbt.getLong("realSimSolarStartup");

		this.water.readFromNBT(nbt, "water");
		this.steam.readFromNBT(nbt, "steam");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermal.write(nbt); nbt.setInteger("realSimSolarPending", heat); nbt.setLong("realSimSolarHeat", thermalBufferQuanta); nbt.setLong("realSimSolarStartup", startupQuanta);

		this.water.writeToNBT(nbt, "water");
		this.steam.writeToNBT(nbt, "steam");
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
					yCoord + 3,
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
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { steam };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { water };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { water, steam };
	}

	public void networkPackNT(int range) {
		if(!worldObj.isRemote) PacketDispatcher.wrapper.sendToAllAround(new BufPacket(xCoord, yCoord, zCoord, this), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	@Override
	public void serialize(ByteBuf buf) {
		water.serialize(buf);
		steam.serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		water.deserialize(buf);
		steam.deserialize(buf);
	}

	@Override
	public FluidTank getTankToPaste() {
		return null;
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Thermal ceiling: " + com.hbm.realsim.MachineInfo.thermalWatts(RATED_THERMAL_WATTS));
        lines.add("Actual solar heat depends on illuminated mirrors; feed pump electricity is separate");
    }
}
