package com.hbm.entity.missile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hbm.config.BombConfig;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.MissileDefinition;
import com.hbm.items.weapon.MissileDefinition.Classification;
import com.hbm.items.weapon.MissilePayload;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Fixed-roster carriers share guidance, impacts and payload separation. */
public class EntityRosterMissile extends EntityMissileBaseNT {
	private static final int WATCHER_MISSILE_ID = 4;
	private static final int WATCHER_COLD_LAUNCH = 5;
	private static final byte SEPARATION_EFFECT = 19;
	private static final int COLD_LAUNCH_TICKS = 8;

	private boolean siloPayload;
	private int warheadCount;
	private boolean payloadReleased;
	private boolean plannedFlight;
	private boolean skipLaunch;
	private int flightTick;
	private double launchX, launchY, launchZ;
	private int travelTicks;
	private double arrivalY, arcHeight;

	public EntityRosterMissile(World world) {
		super(world);
	}

	public EntityRosterMissile(World world, float x, float y, float z, int targetX, int targetZ,
			MissileDefinition missile) {
		super(world, x, y, z, targetX, targetZ);
		setDefinition(missile);
		setWarheadCount(missile.warheadCount(world));
		health = 35 + missile.generation.ordinal() * 10;
		beginFlight(missile, false);
		updateColdLaunch(missile.classification);
		// Forge must tick the launch chunk before the first movement can request the next one.
		loadNeighboringChunks(MathHelper.floor_double(posX) >> 4, MathHelper.floor_double(posZ) >> 4);
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(WATCHER_MISSILE_ID, 0);
		this.dataWatcher.addObject(WATCHER_COLD_LAUNCH, (byte) 0);
	}

	public MissileDefinition getDefinition() {
		return MissileDefinition.byId(this.dataWatcher.getWatchableObjectInt(WATCHER_MISSILE_ID));
	}

	private void setDefinition(MissileDefinition missile) {
		this.dataWatcher.updateObject(WATCHER_MISSILE_ID, missile == null ? 0 : missile.id);
	}

	private void setWarheadCount(int count) {
		warheadCount = count;
		MissileDefinition missile = getDefinition();
		isCluster = count == 1 && missile != null && missile.payload == MissilePayload.CLUSTER;
	}

	public void useSiloPayload() {
		this.siloPayload = true;
	}

	@Override
	public void onUpdate() {
		if(!worldObj.isRemote) {
			MissileDefinition missile = getDefinition();
			if(missile == null || payloadReleased || posY < -8) {
				finishTrackedFlight(this);
				return;
			}
			if(plannedFlight) {
				Classification profile = missile.classification;
				int liftTicks = skipLaunch ? 0 : profile.launchTicks;
				if(flightTick >= liftTicks + travelTicks && warheadCount > 1) {
					releasePayload(missile);
					return;
				}
				updateColdLaunch(profile);
				isCluster = warheadCount == 1 && missile.payload == MissilePayload.CLUSTER
						&& flightTick >= liftTicks + travelTicks;
				planNextStep(profile, liftTicks);
				prepareFlightChunk(posX + motionX, posZ + motionZ);
			}
		}
		super.onUpdate();
	}

	private void beginFlight(MissileDefinition missile, boolean resume) {
		plannedFlight = true;
		skipLaunch = resume;
		flightTick = 0;
		launchX = posX;
		launchY = posY;
		launchZ = posZ;
		velocity = 1D;
		accelXZ = decelY = 0D;
		configureRoute(missile);
	}

	private void configureRoute(MissileDefinition missile) {
		Classification profile = missile.classification;
		double distance = Math.hypot(targetX + 0.5D - launchX, targetZ + 0.5D - launchZ);
		travelTicks = Math.max(20, (int) Math.ceil(distance / Math.max(1D, missile.flightSpeed * profile.travelSpeed)));
		arrivalY = skipLaunch ? Math.max(launchY, Math.min(240D, launchY + profile.arrivalLift * 0.5D))
				: Math.max(launchY, Math.min(272D, launchY + profile.arrivalLift));
		arcHeight = profile.arcLift + Math.min(96D, distance * profile.distanceLift);
	}

	private void planNextStep(Classification profile, int liftTicks) {
		int tick = ++flightTick;
		if(tick <= liftTicks) {
			double progress = tick / (double) liftTicks;
			motionX = motionZ = 0D;
			motionY = launchY + profile.launchLift * progress * progress - posY;
		} else if(tick <= liftTicks + travelTicks) {
			double progress = (tick - liftTicks) / (double) travelTicks;
			double liftY = launchY + (skipLaunch ? 0D : profile.launchLift);
			double y = liftY + (arrivalY - liftY) * progress + arcHeight * Math.sin(Math.PI * progress);
			motionX = launchX + (targetX + 0.5D - launchX) * progress - posX;
			motionY = y - posY;
			motionZ = launchZ + (targetZ + 0.5D - launchZ) * progress - posZ;
		} else {
			motionX = motionZ = 0D;
			motionY = -profile.descentSpeed;
		}
	}

	private void updateColdLaunch(Classification profile) {
		byte cold = (byte) (profile == Classification.SLBM && !skipLaunch && flightTick < COLD_LAUNCH_TICKS ? 1 : 0);
		dataWatcher.updateObject(WATCHER_COLD_LAUNCH, cold);
	}

	@Override
	protected void spawnContrail() {
		if(dataWatcher.getWatchableObjectByte(WATCHER_COLD_LAUNCH) != 0) {
			worldObj.spawnParticle("cloud", posX, posY, posZ, 0D, -0.1D, 0D);
		} else {
			super.spawnContrail();
		}
	}

	@Override
	protected float getContrailScale() {
		MissileDefinition missile = getDefinition();
		if(missile == null) return 1F;
		switch(missile.classification) {
		case CRUISE: return 0.35F;
		case HYPERSONIC: return 0.60F;
		case ICBM:
		case SLBM: return 1.4F;
		default: return 1F;
		}
	}

	@Override
	protected double getFlightSpeed() {
		if(plannedFlight) return 1D;
		MissileDefinition missile = getDefinition();
		return missile == null ? super.getFlightSpeed() : missile.flightSpeed;
	}

	@Override
	protected float getFlightYaw() {
		if(!plannedFlight) return super.getFlightYaw();
		return (float) (Math.atan2(targetX + 0.5D - launchX, targetZ + 0.5D - launchZ) * 180D / Math.PI);
	}

	@Override
	public ItemStack getMissileItemForInfo() {
		MissileDefinition missile = getDefinition();
		return missile == null ? null : missile.missileStack();
	}

	@Override
	public String getUnlocalizedName() {
		MissileDefinition missile = getDefinition();
		return missile == null ? super.getUnlocalizedName() : missile.translationKey() + ".name";
	}

	@Override
	protected void onImpact(MovingObjectPosition hit) {
		if(hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
			setPosition(hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord);
			onImpact();
		}
	}

	@Override
	public void onImpact() {
		MissileDefinition missile = getDefinition();
		if(worldObj.isRemote || missile == null || isDead || payloadReleased) return;
		finishTrackedFlight(this);
		missile.payload.impact(this, missile.impactPower(siloPayload), missile.hasExtraFallout(siloPayload));
	}

	private void releasePayload(MissileDefinition missile) {
		payloadReleased = true;
		if(BombConfig.mirvSeparationEffects) worldObj.setEntityState(this, SEPARATION_EFFECT);
		finishTrackedFlight(this);
		int power = MissilePayload.separatedPower(missile.impactPower(siloPayload), warheadCount);
		boolean extraFallout = missile.hasExtraFallout(siloPayload);
		double bearing = rand.nextDouble() * Math.PI * 2D;
		for(int i = 0; i < warheadCount; i++) {
			double angle = bearing + Math.PI * 2D * i / warheadCount;
			double distance = BombConfig.mirvImpactRadius * (i % 2 == 0 ? 0.85D : 0.62D);
			EntityMIRV warhead = new EntityMIRV(worldObj, posX, posY, posZ,
					targetX + 0.5D + Math.cos(angle) * distance, targetZ + 0.5D + Math.sin(angle) * distance,
					missile.payload, power, extraFallout, i);
			if(!worldObj.spawnEntityInWorld(warhead)) warhead.setDead();
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void handleHealthUpdate(byte state) {
		if(state != SEPARATION_EFFECT) {
			super.handleHealthUpdate(state);
			return;
		}
		NBTTagCompound smoke = new NBTTagCompound();
		smoke.setString("type", "smoke");
		smoke.setString("mode", "radial");
		smoke.setInteger("count", 18);
		smoke.setDouble("posX", posX);
		smoke.setDouble("posY", posY);
		smoke.setDouble("posZ", posZ);
		MainRegistry.proxy.effectNT(smoke);
	}

	@Override
	public void cluster() {
		onImpact();
	}

	@Override
	public List<ItemStack> getDebris() {
		MissileDefinition missile = getDefinition();
		if(missile == null) return Collections.emptyList();
		int tier = missile.generation.ordinal() + 1;
		return Arrays.asList(new ItemStack(missile.hullMaterial(), tier), new ItemStack(ModItems.plate_steel, tier * 2));
	}

	@Override
	public ItemStack getDebrisRareDrop() {
		return getDefinition() == null ? null : new ItemStack(getDefinition().warhead());
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		MissileDefinition missile = getDefinition();
		if(missile != null) nbt.setString("missile", missile.key);
		nbt.setBoolean("siloPayload", siloPayload);
		nbt.setInteger("warheadCount", warheadCount);
		nbt.setBoolean("payloadReleased", payloadReleased);
		nbt.setBoolean("plannedFlight", plannedFlight);
		if(plannedFlight) {
			nbt.setBoolean("skipLaunch", skipLaunch);
			nbt.setInteger("flightTick", flightTick);
			nbt.setDouble("launchX", launchX);
			nbt.setDouble("launchY", launchY);
			nbt.setDouble("launchZ", launchZ);
		}
		nbt.setInteger("missileHealth", health);
		nbt.setByte("launchFacing", this.dataWatcher.getWatchableObjectByte(3));
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		MissileDefinition missile = MissileDefinition.byName(nbt.getString("missile"));
		setDefinition(missile);
		siloPayload = nbt.getBoolean("siloPayload");
		if(nbt.hasKey("missileHealth", 99)) health = nbt.getInteger("missileHealth");
		if(nbt.hasKey("launchFacing", 99)) this.dataWatcher.updateObject(3, nbt.getByte("launchFacing"));
		if(missile == null) return;
		setWarheadCount(nbt.hasKey("warheadCount", 99)
				? MathHelper.clamp_int(nbt.getInteger("warheadCount"), Math.min(1, missile.payloadCapacity), missile.payloadCapacity)
				: missile.warheadCount(worldObj));
		payloadReleased = nbt.getBoolean("payloadReleased");
		plannedFlight = nbt.getBoolean("plannedFlight");
		if(plannedFlight) {
			skipLaunch = nbt.getBoolean("skipLaunch");
			flightTick = nbt.getInteger("flightTick");
			launchX = nbt.getDouble("launchX");
			launchY = nbt.getDouble("launchY");
			launchZ = nbt.getDouble("launchZ");
			configureRoute(missile);
		} else if(warheadCount > 1 && !payloadReleased) {
			beginFlight(missile, true);
		}
		if(plannedFlight) updateColdLaunch(missile.classification);
	}
}
