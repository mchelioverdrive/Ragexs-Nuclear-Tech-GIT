package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityTickingBase;
import com.hbm.util.fauxpointtwelve.BlockPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityICFController extends TileEntityTickingBase implements IEnergyReceiverMK2 {
	private static final int TASK_BEAM = 0;
	
	public long energyQuanta;
	public int laserLength;
	
	public int cellCount;
	public int emitterCount;
	public int capacitorCount;
	public int turbochargerCount;

	protected List<BlockPos> ports = new ArrayList();
	
	public boolean assembled;
	
	public void setup(HashSet<BlockPos> ports, HashSet<BlockPos> cells, HashSet<BlockPos> emitters, HashSet<BlockPos> capacitors, HashSet<BlockPos> turbochargers) {
		long previousCapacity = this.getEnergyCapacityQuanta();

		this.cellCount = 0;
		this.emitterCount = 0;
		this.capacitorCount = 0;
		this.turbochargerCount = 0;
		
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata()).getOpposite();
		BlockPos pos = new BlockPos(0, 0, 0);

		HashSet<BlockPos> validCells = new HashSet();
		HashSet<BlockPos> validEmitters = new HashSet();
		HashSet<BlockPos> validCapacitors = new HashSet();
		
		for(int i = 0; i < cells.size(); i++) {
			int j = i + 1;
			
			if(cells.contains(pos.mutate(xCoord + dir.offsetX * j, yCoord, zCoord + dir.offsetZ * j))) {
				this.cellCount++;
				validCells.add(pos.clone());
			} else {
				break;
			}
		}
		
		for(BlockPos emitter : emitters) { for(ForgeDirection offset : ForgeDirection.VALID_DIRECTIONS) {
				pos.mutate(emitter.getX() + offset.offsetX, emitter.getY() + offset.offsetY, emitter.getZ() + offset.offsetZ);
				if(validCells.contains(pos)) { this.emitterCount++; validEmitters.add(emitter.clone()); break; }
			}
		}
		
		for(BlockPos capacitor : capacitors) { for(ForgeDirection offset : ForgeDirection.VALID_DIRECTIONS) {
				pos.mutate(capacitor.getX() + offset.offsetX, capacitor.getY() + offset.offsetY, capacitor.getZ() + offset.offsetZ);
				if(validEmitters.contains(pos)) { this.capacitorCount++; validCapacitors.add(capacitor.clone()); break; }
			}
		}
		
		for(BlockPos turbo : turbochargers) { for(ForgeDirection offset : ForgeDirection.VALID_DIRECTIONS) {
				pos.mutate(turbo.getX() + offset.offsetX, turbo.getY() + offset.offsetY, turbo.getZ() + offset.offsetZ);
				if(validCapacitors.contains(pos)) { this.turbochargerCount++; break; }
			}
		}
		
		this.ports.clear();
		this.ports.addAll(ports);
		if(this.getEnergyCapacityQuanta() != previousCapacity) this.markPowerNetDirty();
		this.markMachineDirty(MachineDirtyCause.TOPOLOGY | MachineDirtyCause.LIFECYCLE);
	}

	public void setAssembled(boolean assembled) {
		if(this.assembled == assembled) return;
		this.assembled = assembled;
		this.markPowerNetDirty();
		if(!assembled) this.laserLength = 0;
		this.markMachineDirty(MachineDirtyCause.TOPOLOGY | MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) updatePowerConnections();
		if(assembled && getStoredEnergyQuanta() > 0) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BEAM, 0);
		else cancelMachineTransition(TASK_BEAM, 0);
		markNetworkDirty();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_BEAM || taskSlot != 0 || worldObj == null || worldObj.isRemote || !assembled || getStoredEnergyQuanta() <= 0) return;
		runBeamStep();
		markNetworkDirty();
		networkPackNTIfDirty(50);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 20) return;
		updatePowerConnections();
		if(!assembled || getStoredEnergyQuanta() <= 0) networkPackNTIfDirty(50);
	}

	private void updatePowerConnections() {
		if(!assembled || getEnergyCapacityQuanta() <= 0) return;
		for(BlockPos pos : ports) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				BlockPos portPos = pos.offset(dir);
				trySubscribe(worldObj, portPos.getX(), portPos.getY(), portPos.getZ(), dir);
			}
		}
	}

	private void runBeamStep() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
		for(int i = 1; i < 50; i++) {
			this.laserLength = i;
			int bx = xCoord + dir.offsetX * i;
			int by = yCoord;
			int bz = zCoord + dir.offsetZ * i;
			Block b = worldObj.getBlock(bx, by, bz);
			if(b == ModBlocks.icf) {
				TileEntity tile = worldObj.getTileEntity(xCoord + dir.offsetX * (i + 8), yCoord - 3, zCoord + dir.offsetZ * (i + 8));
				if(tile instanceof TileEntityICF) {
					TileEntityICF icf = (TileEntityICF) tile;
					icf.receiveLaserBeam(this.getStoredEnergyQuanta(), this.getEnergyCapacityQuanta());
					break;
				}
			}
			if(!b.isAir(worldObj, bx, by, bz)) {
				float hardness = b.getExplosionResistance(null);
				if(hardness < 6000) worldObj.func_147480_a(bx, by, bz, false);
				break;
			}
		}

		double blx = Math.min(xCoord, xCoord + dir.offsetX * laserLength) + 0.2;
		double bux = Math.max(xCoord, xCoord + dir.offsetX * laserLength) + 0.8;
		double bly = Math.min(yCoord, yCoord + dir.offsetY * laserLength) + 0.2;
		double buy = Math.max(yCoord, yCoord + dir.offsetY * laserLength) + 0.8;
		double blz = Math.min(zCoord, zCoord + dir.offsetZ * laserLength) + 0.2;
		double buz = Math.max(zCoord, zCoord + dir.offsetZ * laserLength) + 0.8;
		List<Entity> list = worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(blx, bly, blz, bux, buy, buz));
		for(Entity e : list) {
			e.attackEntityFrom(DamageSource.inFire, 50);
			e.setFire(5);
		}
		this.setStoredEnergyQuanta(0);
	}

	@Override
	public String getInventoryName() {
		return "container.icfController";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			if(this.laserLength > 0 && worldObj.rand.nextInt(5) == 0) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata());
				ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
				double offXZ = worldObj.rand.nextDouble() * 0.25 - 0.125;
				double offY = worldObj.rand.nextDouble() * 0.25 - 0.125;
				double dist = 0.55;
				worldObj.spawnParticle("reddust", xCoord + 0.5 + dir.offsetX * dist + rot.offsetX * offXZ, yCoord + 0.5 + offY, zCoord + 0.5 + dir.offsetZ * dist + rot.offsetZ * offXZ, 0, 0, 0);
			}
		}
	}

	@Override public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(capacitorCount);
		buf.writeInt(turbochargerCount);
		buf.writeInt(laserLength);
	}
	
	@Override public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.energyQuanta = buf.readLong();
		this.capacitorCount = buf.readInt();
		this.turbochargerCount = buf.readInt();
		this.laserLength = buf.readInt();
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		
		this.assembled = nbt.getBoolean("assembled");
		this.cellCount = nbt.getInteger("cellCount");
		this.emitterCount = nbt.getInteger("emitterCount");
		this.capacitorCount = nbt.getInteger("capacitorCount");
		this.turbochargerCount = nbt.getInteger("turbochargerCount");
		
		ports.clear();
		int portCount = nbt.getInteger("portCount");
		for(int i = 0; i < portCount; i++) {
			int[] port = nbt.getIntArray("p" + i);
			ports.add(new BlockPos(port[0], port[1], port[2]));
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		
		nbt.setBoolean("assembled", assembled);
		nbt.setInteger("cellCount", cellCount);
		nbt.setInteger("emitterCount", emitterCount);
		nbt.setInteger("capacitorCount", capacitorCount);
		nbt.setInteger("turbochargerCount", turbochargerCount);
		
		nbt.setInteger("portCount", ports.size());
		for(int i = 0; i < ports.size(); i++) {
			BlockPos pos = ports.get(i);
			nbt.setIntArray("p" + i, new int[] { pos.getX(), pos.getY(), pos.getZ() });
		}
	}

	@Override
	public long getStoredEnergyQuanta() {
		return Math.min(energyQuanta, this.getEnergyCapacityQuanta());
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		this.markMachineEnergyDirty();
		this.markNetworkDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return (long) (Math.sqrt(capacitorCount) * 2_500_000 + Math.sqrt(Math.min(turbochargerCount, capacitorCount)) * 5_000_000);
	}

	@Override
	public long getMaxInputQuantaPerTick() {
		return this.assembled ? this.getEnergyCapacityQuanta() : 0;
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord + 0.5 - 50,
					yCoord,
					zCoord + 0.5 - 50,
					xCoord + 0.5 + 50,
					yCoord + 1,
					zCoord + 0.5 + 50
					);
		}
		
		return bb;
	}
}
