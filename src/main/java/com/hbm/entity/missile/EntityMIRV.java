package com.hbm.entity.missile;

import com.hbm.config.BombConfig;
import com.hbm.entity.logic.IChunkLoader;
import com.hbm.entity.projectile.EntityThrowableInterp;
import com.hbm.items.weapon.MissilePayload;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

/** A separately tracked reentry body. The original entity ID also loads legacy unguided warheads. */
public class EntityMIRV extends EntityThrowableInterp implements IChunkLoader {

	private MissilePayload payload = MissilePayload.THERMONUCLEAR;
	private boolean guided;
	private boolean extraFallout;
	private int power;
	private int spreadTicks;
	private double targetX, targetZ;
	private Ticket loaderTicket;

	public EntityMIRV(World world) {
		super(world);
		ignoreFrustumCheck = true;
	}

	public EntityMIRV(World world, double x, double y, double z, double targetX, double targetZ,
			MissilePayload payload, int power, boolean extraFallout, int index) {
		this(world);
		setPosition(x, y, z);
		guided = true;
		this.targetX = targetX;
		this.targetZ = targetZ;
		this.payload = payload;
		this.power = power;
		this.extraFallout = extraFallout;
		spreadTicks = 18 + index * 4;
		motionX = (targetX - x) / spreadTicks;
		motionY = -0.35D;
		motionZ = (targetZ - z) / spreadTicks;
		updateRotation();
		prevRotationYaw = rotationYaw;
		prevRotationPitch = rotationPitch;
		init(ForgeChunkManager.requestTicket(MainRegistry.instance, world, Type.ENTITY));
	}

	@Override
	public void onUpdate() {
		if(isDead) return;
		if(worldObj.isRemote) {
			super.onUpdate();
			if(ticksExisted % 2 == 0) spawnTrail();
			return;
		}

		if(payload == null || posY < -8) {
			EntityMissileBaseNT.finishTrackedFlight(this);
			return;
		}
		if(spreadTicks > 0) {
			motionX = (targetX - posX) / spreadTicks;
			motionZ = (targetZ - posZ) / spreadTicks;
			spreadTicks--;
		} else if(guided) {
			motionX = motionZ = 0D;
			motionY = Math.max(-3.6D, Math.min(-1.1D, motionY - 0.16D));
		} else {
			motionY -= 0.03D;
		}

		double nextX = posX + motionX, nextY = posY + motionY, nextZ = posZ + motionZ;
		EntityMissileBaseNT.prepareFlightChunk(worldObj, loaderTicket, nextX, nextZ);
		MovingObjectPosition hit = worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY, posZ),
				Vec3.createVectorHelper(nextX, nextY, nextZ), true, false, false);
		if(hit != null) {
			onImpact(hit);
		} else {
			updateRotation();
			setPosition(nextX, nextY, nextZ);
		}
	}

	private void spawnTrail() {
		NBTTagCompound trail = new NBTTagCompound();
		trail.setString("type", "missileContrail");
		trail.setDouble("posX", posX);
		trail.setDouble("posY", posY);
		trail.setDouble("posZ", posZ);
		trail.setFloat("scale", 0.45F);
		trail.setInteger("maxAge", 14);
		MainRegistry.proxy.effectNT(trail);
	}

	private void updateRotation() {
		double horizontal = Math.hypot(motionX, motionZ);
		if(horizontal > 0.0001D) rotationYaw = (float) Math.toDegrees(Math.atan2(motionX, motionZ));
		rotationPitch = (float) Math.toDegrees(Math.atan2(motionY, horizontal)) - 90F;
	}

	@Override
	protected void onImpact(MovingObjectPosition hit) {
		if(isDead || worldObj.isRemote) return;
		setPosition(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
		EntityMissileBaseNT.finishTrackedFlight(this);
		payload.impact(this, guided ? power : BombConfig.mirvRadius, extraFallout);
	}

	@Override
	public void init(Ticket ticket) {
		if(worldObj.isRemote || ticket == null) return;
		if(loaderTicket != null && loaderTicket != ticket) ForgeChunkManager.releaseTicket(loaderTicket);
		loaderTicket = ticket;
		ticket.bindEntity(this);
		EntityMissileBaseNT.prepareFlightChunk(worldObj, ticket, posX, posZ);
	}

	@Override
	public void setDead() {
		super.setDead();
		if(loaderTicket != null) {
			ForgeChunkManager.releaseTicket(loaderTicket);
			loaderTicket = null;
		}
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		if(guided && payload != null) {
			nbt.setString("payload", payload.name());
			nbt.setInteger("power", power);
			nbt.setBoolean("extraFallout", extraFallout);
			nbt.setInteger("spreadTicks", spreadTicks);
			nbt.setDouble("targetX", targetX);
			nbt.setDouble("targetZ", targetZ);
		}
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		guided = nbt.hasKey("payload", 8);
		if(!guided) return;
		try {
			payload = MissilePayload.valueOf(nbt.getString("payload"));
		} catch(IllegalArgumentException e) {
			payload = null;
		}
		power = Math.max(1, nbt.getInteger("power"));
		extraFallout = nbt.getBoolean("extraFallout");
		spreadTicks = nbt.getInteger("spreadTicks");
		targetX = nbt.getDouble("targetX");
		targetZ = nbt.getDouble("targetZ");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return distance < 500000D;
	}
}
