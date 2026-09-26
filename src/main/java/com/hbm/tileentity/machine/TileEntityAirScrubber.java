package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.handler.ThreeInts;
import com.hbm.handler.atmosphere.AtmosphereBlob;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityMachineBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardSender;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityAirScrubber extends TileEntityMachineBase implements IFluidStandardSender, IEnergyReceiverMK2 {

	private TileEntityAirPump pump;
	public FluidTank tank;

	private long energyQuanta;

	public float rot;
	public float prevRot;
	private float rotSpeed;

	public TileEntityAirScrubber() {
		super(0);
		tank = new FluidTank(Fluids.CARBONDIOXIDE, 16_000);
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.airScrubber";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			float maxSpeed = 30F;
			
			if(canOperate()) {
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
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(20);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != 1 || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		int before = tank.getFill();
		if(before > 0) this.sendFluidToAll(tank, this);
		if(before != tank.getFill()) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(20);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(canOperate() && (pump == null || pump.getFluidPressure() == 0 || !pump.registerScrubber(this))) this.findAndRegisterPump();
			this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		} else if(cadence == 20) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			this.networkPackNTIfDirty(20);
		}
	}

	private void findAndRegisterPump() {
		pump = null;
		List<AtmosphereBlob> blobs = ChunkAtmosphereManager.proxy.getBlobs(worldObj, xCoord, yCoord, zCoord);
		for(AtmosphereBlob blob : blobs) {
			if(blob == null) continue;
			ThreeInts pos = blob.getRootPosition();
			TileEntity te = worldObj.getTileEntity(pos.x, pos.y, pos.z);
			if(te instanceof TileEntityAirPump) {
				pump = (TileEntityAirPump) te;
				if(!pump.registerScrubber(this)) pump = null;
				else break;
			}
		}
	}

	private void evaluateAndSchedule(long now) {
		if(tank.getFill() > 0) this.scheduleMachineTransition(now + 1L, 1, 0);
		else this.cancelMachineTransition(1, 0);
	}

	public boolean canOperate() {
		return energyQuanta > 200;
	}

	public void scrub(int amount) {
		if(!canOperate()) return;
		int add = Math.min(tank.getMaxFill() - tank.getFill(), amount);
		tank.setFill(tank.getFill() + add);
		this.setStoredEnergyQuanta(this.energyQuanta - add * 10);
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

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tank.writeToNBT(nbt, "t");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tank.readFromNBT(nbt, "t");
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		this.markNetworkDirty();
		this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 10000;
	}

	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord,
				yCoord,
				zCoord,
				xCoord + 1,
				yCoord + 2,
				zCoord + 1
			);
		}
		
		return bb;
	}
	
}
