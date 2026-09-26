package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Gaseous;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;
import com.hbm.dim.orbit.WorldProviderOrbit;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.fluid.IFluidStandardSender;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityAtmoExtractor extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardSender {
	private static final int TASK_EXTRACT = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(10_000L);
	private DirPos[] cachedConnections;
	private int cachedConnectionMetadata = Integer.MIN_VALUE;

	int consumption = 200;
	public float rot;
	public float prevRot;
	private float rotSpeed;
	public long energyQuanta = 0;
	public FluidTank tank;
	public List<IFluidStandardReceiver> list = new ArrayList<>();

	public TileEntityAtmoExtractor() {
		super(0);
		tank = new FluidTank(Fluids.AIR, 50000);
	}

	@Override
	public String getName() {
		return "container.air";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			float maxSpeed = 30F;

			if(hasPower()) {
				rotSpeed += 0.2;
				if(rotSpeed > maxSpeed) rotSpeed = maxSpeed;
			} else {
				rotSpeed -= 0.1;
				if(rotSpeed < 0) rotSpeed = 0;
			}

			prevRot = rot;

			rot += rotSpeed;

			if(rot >= 360) {
				rot -= 360;
				prevRot -= 360;
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.trackMachineFluidTank(tank);
		this.refreshAtmosphericFluid();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_EXTRACT || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldFill = tank.getFill();
		long oldEnergy = energyQuanta;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.sendOutput();
			if(this.hasPower() && tank.getFill() + 100 <= tank.getMaxFill()) {
				tank.setFill(tank.getFill() + 100);
				this.setStoredEnergyQuanta(energyQuanta - EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts));
				FT_Gaseous.capture(worldObj, tank.getTankType(), 100);
			}
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		if(oldFill != tank.getFill() || oldEnergy != energyQuanta) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.refreshAtmosphericFluid()) this.markMachineDirty(MachineDirtyCause.ENVIRONMENT | MachineDirtyCause.FLUID);
			this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		} else if(cadence == 20) {
			this.updateConnections();
			this.networkPackNTIfDirty(50);
		}
	}

	private boolean refreshAtmosphericFluid() {
		FluidType oldType = tank.getTankType();
		CBT_Atmosphere atmosphere = !(worldObj.provider instanceof WorldProviderOrbit) && !ChunkAtmosphereManager.proxy.hasAtmosphere(worldObj, xCoord, yCoord, zCoord)
			? CelestialBody.getTrait(worldObj, CBT_Atmosphere.class) : null;
		if(atmosphere != null) {
			if(!atmosphere.hasFluid(tank.getTankType())) tank.setTankType(atmosphere.getMainFluid());
		} else tank.setTankType(Fluids.NONE);
		return oldType != tank.getTankType();
	}

	private void sendOutput() {
		for(DirPos pos : this.getCachedConnections()) this.sendFluid(tank, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private DirPos[] getCachedConnections() {
		int metadata = this.getBlockMetadata();
		if(cachedConnections == null || cachedConnectionMetadata != metadata) {
			cachedConnections = this.getConPos();
			cachedConnectionMetadata = metadata;
		}
		return cachedConnections;
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && (this.hasPower() && tank.getFill() + 100 <= tank.getMaxFill() || tank.getFill() > 0)) this.scheduleMachineTransition(now + 1L, TASK_EXTRACT, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_EXTRACT, TASK_SLOT_MAIN);
	}

	public void cycleGas() {
		CBT_Atmosphere atmosphere = !ChunkAtmosphereManager.proxy.hasAtmosphere(worldObj, xCoord, yCoord, zCoord)
			? CelestialBody.getTrait(worldObj, CBT_Atmosphere.class)
			: null;

		if(atmosphere == null) return;

		FluidType currentFluid = tank.getTankType();

		for(int i = 0; i < atmosphere.fluids.size(); i++) {
			if(atmosphere.fluids.get(i).fluid == currentFluid) {
				int targetIndex = i + 1;
				if(targetIndex >= atmosphere.fluids.size()) targetIndex = 0;

				tank.setTankType(atmosphere.fluids.get(targetIndex).fluid);
				this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.FLUID);
				this.markNetworkDirty();
				break;
			}
		}
	}

	protected void updateConnections() {
		for(DirPos pos : this.getCachedConnections()) {
			trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			sendFluid(tank, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		tank.serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		tank.deserialize(buf);
	}

	public boolean hasPower() {
		return energyQuanta >= this.getEnergyCapacityQuanta() / 100;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tank.readFromNBT(nbt, "water");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tank.writeToNBT(nbt, "water");
	}



	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		this.markNetworkDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 1000000;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	private DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		return new DirPos[] {
				new DirPos(this.xCoord - dir.offsetX * 2, this.yCoord, this.zCoord - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(this.xCoord - dir.offsetX * 2 + rot.offsetX, this.yCoord, this.zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),

				new DirPos(this.xCoord + dir.offsetX, this.yCoord, this.zCoord + dir.offsetZ, dir),
				new DirPos(this.xCoord + dir.offsetX + rot.offsetX, this.yCoord, this.zCoord + dir.offsetZ  + rot.offsetZ, dir),

				new DirPos(this.xCoord - rot.offsetX, this.yCoord, this.zCoord - rot.offsetZ, rot.getOpposite()),
				new DirPos(this.xCoord - dir.offsetX - rot.offsetX, this.yCoord, this.zCoord - dir.offsetZ - rot.offsetZ, rot.getOpposite()),

				new DirPos(this.xCoord + rot.offsetX * 2, this.yCoord, this.zCoord + rot.offsetZ * 2, rot),
				new DirPos(this.xCoord - dir.offsetX + rot.offsetX * 2, this.yCoord, this.zCoord - dir.offsetZ + rot.offsetZ * 2, rot),
		};
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
				yCoord + 10,
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

}
