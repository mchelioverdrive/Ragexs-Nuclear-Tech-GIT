package com.hbm.tileentity.bomb;

import api.hbm.energymk2.IEnergyReceiverMK2;
import com.hbm.blocks.turret.TurretBase;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.packet.TETurretPacket;
import com.hbm.lib.Library;
import com.hbm.packet.AuxGaugePacket;
import com.hbm.packet.PacketDispatcher;

import com.hbm.util.fauxpointtwelve.DirPos;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

public class TileEntityTurretCIWS extends TileEntityTurretBase implements IEnergyReceiverMK2 {

	public int spin;
	public int rotation;
	private long power;
	private static final long maxPower = 100_000;
	public static final long POWER_PER_SHOT = 250;
	public static final int consumption = 1000;
	public double turretYaw;
	public double turretPitch;

	public double renderYaw;
	public double renderPitch;

	//trySubscribe(
	//				worldObj,
	//				xCoord,
	//				yCoord - 1,
	//				zCoord,
	//				net.minecraftforge.common.util.ForgeDirection.UP
	//			);

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			return;
		}

		updateConnections();
		consumeIdlePower();
		updateSpin();

		if(!isAI || !hasPower()) {
			use = 0;
			return;
		}

		Entity target = findClosestMissile();
		if(target == null) {
			use = 0;
			return;
		}

		aimAt(target);
		use++;
		if(ammo > 0 && worldObj.getBlock(xCoord, yCoord, zCoord) instanceof TurretBase) {
			if(((TurretBase) worldObj.getBlock(xCoord, yCoord, zCoord)).executeHoldAction(worldObj, use, rotationYaw, rotationPitch, xCoord, yCoord, zCoord)) {
				ammo--;
			}
		}
	}

	private void consumeIdlePower() {
		if(hasPower()) {
			power = Math.max(0, power - consumption);
		}
	}

	private void updateSpin() {
		if(spin > 0) spin--;
		rotation = (rotation + spin) % 360;
		PacketDispatcher.wrapper.sendToAll(new AuxGaugePacket(xCoord, yCoord, zCoord, rotation, 0));
	}

	private Entity findClosestMissile() {
		Object[] iter = worldObj.loadedEntityList.toArray();
		double radius = 100000;
		Entity target = null;

		for(Object obj : iter) {
			Entity e = (Entity) obj;
			if(!(e instanceof EntityMissileBaseNT) || !canSee(e)) continue;
			double distance = e.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
			if(distance < radius) {
				radius = distance;
				target = e;
			}
		}
		return target;
	}

	private boolean canSee(Entity e) {
		Vec3 turret = Vec3.createVectorHelper(xCoord + 0.5, yCoord + 1.5, zCoord + 0.5);
		Vec3 entity = Vec3.createVectorHelper(e.posX, e.posY + e.getEyeHeight(), e.posZ);
		Vec3 side = Vec3.createVectorHelper(entity.xCoord - turret.xCoord, entity.yCoord - turret.yCoord, entity.zCoord - turret.zCoord).normalize();

		turret.xCoord += side.xCoord;
		turret.yCoord += side.yCoord;
		turret.zCoord += side.zCoord;

		return !Library.isObstructed(worldObj, turret.xCoord, turret.yCoord, turret.zCoord, entity.xCoord, entity.yCoord, entity.zCoord);
	}

	private void aimAt(Entity target) {
		Vec3 turret = Vec3.createVectorHelper(
			target.posX - (xCoord + 0.5),
			target.posY + target.getEyeHeight() - (yCoord + 1.5),
			target.posZ - (zCoord + 0.5)
		);

		rotationPitch = -Math.asin(turret.yCoord / turret.lengthVector()) * 180 / Math.PI;
		rotationYaw = -Math.atan2(turret.xCoord, turret.zCoord) * 180 / Math.PI;
		rotationPitch = Math.max(-60, Math.min(30, rotationPitch));

		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		PacketDispatcher.wrapper.sendToAll(new TETurretPacket(xCoord, yCoord, zCoord, rotationYaw, rotationPitch));
	}


	private DirPos[] getConPos() {
		return new DirPos[] {
			new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y)
			//we just want that
			//new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
			//new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
			//new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
			//new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z)
		};
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	public void consumePower(long amount) {
		power = Math.max(0, power - amount);
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("power");
		turretYaw = nbt.getDouble("turretYaw");
		turretPitch = nbt.getDouble("turretPitch");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
		nbt.setDouble("turretYaw", turretYaw);
		nbt.setDouble("turretPitch", turretPitch);
	}

	@Override
	public boolean isLoaded() {
		return true;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	public boolean hasPower() {
		return power > 0;
	}

	@Override
	public void setPower(long i) {
		power = i;
	}
}
