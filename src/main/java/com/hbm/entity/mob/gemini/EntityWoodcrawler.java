package com.hbm.entity.mob.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.ai.EntityAIWoodcrawler;
import com.hbm.world.gemini.WoodcrawlerSpawning;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

public class EntityWoodcrawler extends EntityMob implements IDeepRootCarrier {

	public static final int WANDER = 0, STALK = 1, CHASE = 2, SEARCH = 3, RETREAT = 4;
	public static final float STANDING_HEIGHT = 5.6F, BODY_WIDTH = 0.85F;
	public static final int GRAB_WINDUP = 12, GRAB_HOLD_END = 26, GRAB_END = 36;
	private NBTTagCompound capturedVillager;
	private int captiveTicks;
	public enum CaptureState { IDLE, HUNTING, STALKING, GRABBING, PLACING_CONVERSION_NEST,
		CONVERTING_VICTIM, RECOVERING, RETURN_TO_HUNT }
	private CaptureState captureState = CaptureState.IDLE;
	private int captureStateTicks;
	private final EntityAIWoodcrawler controller;
	private byte fakePersonConversion;
	private boolean loadedFromNBT;
	private boolean checkLoadedClearance;
	private int strikeCooldown, burrowCooldown = 100;
	private EntityLivingBase strikeTarget;
	private boolean grabHit;
	private double burrowX, burrowY, burrowZ;
	public float burrowDepth, prevBurrowDepth;
	public float climbBlend, prevClimbBlend, crouchBlend, prevCrouchBlend, stalkBlend, prevStalkBlend;

	public EntityWoodcrawler(World world) {
		super(world);
		setSize(BODY_WIDTH, STANDING_HEIGHT);
		stepHeight = 1.0F;
		experienceValue = 15;
		getNavigator().setAvoidsWater(true);
		getNavigator().setCanSwim(true);
		getNavigator().setEnterDoors(true);
		getNavigator().setBreakDoors(true);
		tasks.addTask(0, new EntityAISwimming(this));
		controller = new EntityAIWoodcrawler(this);
		tasks.addTask(1, controller);
	}

    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        GeminiSpawning.initialize(this);
        return super.onSpawnWithEgg(data);
    }
	@Override
	protected void entityInit() {
		super.entityInit();
		dataWatcher.addObject(20, Byte.valueOf((byte) WANDER));
		dataWatcher.addObject(21, Byte.valueOf((byte) 0));
		dataWatcher.addObject(22, Byte.valueOf((byte) 0));
		dataWatcher.addObject(23, Byte.valueOf((byte) 0));
		dataWatcher.addObject(24, Byte.valueOf((byte) 0)); // Grab timeline, including recovery.
		dataWatcher.addObject(25, Integer.valueOf(-1)); // Live prey network ID; never persisted.
		dataWatcher.addObject(26, Byte.valueOf((byte) 0)); // Enter / submerged / emerge timeline.
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(48.0D);
		getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(6.0D);
		getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.30D);
		getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(GeminiConfig.woodcrawlerDetectionRange);
		getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.35D);
	}

	@Override
	public boolean isAIEnabled() { return true; }

	public int getHuntState() { return dataWatcher.getWatchableObjectByte(20); }
	public void setHuntState(int state) {
		dataWatcher.updateObject(20, Byte.valueOf((byte) state));
		if(!isCaptureBusy() && !isGrabbing()) captureState = state == WANDER ? CaptureState.IDLE
				: state == STALK ? CaptureState.STALKING : CaptureState.HUNTING;
	}
	public CaptureState getCaptureState() { return captureState; }
	public boolean isCaptureBusy() {
		return captureState == CaptureState.PLACING_CONVERSION_NEST || captureState == CaptureState.CONVERTING_VICTIM
				|| captureState == CaptureState.RECOVERING || captureState == CaptureState.RETURN_TO_HUNT;
	}
	private void captureState(CaptureState state) { captureState = state; captureStateTicks = 0; }
	private void resetHunting() {
		setAttackTarget(null);
		getNavigator().clearPathEntity();
		endGrab();
		setClimbing(false);
		setCrouching(false);
		controller.resetTask();
	}
	@Override
	public boolean isOnLadder() { return dataWatcher.getWatchableObjectByte(21) != 0; }
	public void setClimbing(boolean climbing) { dataWatcher.updateObject(21, Byte.valueOf((byte) (climbing ? 1 : 0))); }
	public boolean hasCapturedVillager() { return capturedVillager != null; }
	public boolean isCarrying() { return dataWatcher.getWatchableObjectByte(22) != 0; }
	public boolean wasLoadedFromNBT() { return loadedFromNBT; }
	public boolean isCrouching() { return dataWatcher.getWatchableObjectByte(23) != 0; }

	public void setCrouching(boolean crouch) {
		if(crouch == isCrouching()) return;
		if(!crouch && !worldObj.getCollidingBoundingBoxes(this,
				AxisAlignedBB.getBoundingBox(boundingBox.minX, boundingBox.minY + 1.8D, boundingBox.minZ,
						boundingBox.maxX, boundingBox.minY + STANDING_HEIGHT, boundingBox.maxZ)).isEmpty()) return;
		dataWatcher.updateObject(23, Byte.valueOf((byte) (crouch ? 1 : 0)));
		setSize(BODY_WIDTH, crouch ? 1.8F : STANDING_HEIGHT);
	}

	public boolean isPrey(Entity entity) {
		if(entity == this || entity instanceof IGeminiCreature || !(entity instanceof EntityLivingBase)
				|| !entity.isEntityAlive() || entity.worldObj != worldObj || !entity.addedToChunk) return false;
		if(entity instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) entity;
			return !player.capabilities.disableDamage && !player.capabilities.isCreativeMode;
		}
		return entity instanceof EntityVillager && entity.ridingEntity == null && entity.riddenByEntity == null;
	}

	@Override
	public void setAttackTarget(EntityLivingBase target) {
		super.setAttackTarget(isPrey(target) ? target : null);
	}

	public int getGrabTick() { return dataWatcher.getWatchableObjectByte(24); }
	public boolean isGrabbing() { return getGrabTick() != 0; }
	public int getBurrowTick() { return dataWatcher.getWatchableObjectByte(26); }
	public boolean isBurrowing() { return getBurrowTick() != 0; }
	public EntityLivingBase getTendrilTarget() {
		Entity target = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(25));
		return isPrey(target) ? (EntityLivingBase) target : null;
	}
	public float getGrabExtension(float partial) {
		float tick = getGrabTick();
		if(tick == 0) return 0;
		tick += partial;
		return MathHelper.clamp_float(tick <= GRAB_WINDUP ? tick / GRAB_WINDUP
				: tick <= GRAB_HOLD_END ? 1 : (GRAB_END - tick) / (GRAB_END - GRAB_HOLD_END), 0, 1);
	}
	/** Model units, shared with the hook sockets so their geometry and server rays agree. */
	public float getBodyModelY(float partial) {
		float crouch = prevCrouchBlend + (crouchBlend - prevCrouchBlend) * partial;
		float stalk = prevStalkBlend + (stalkBlend - prevStalkBlend) * partial;
		return -36 + crouch * 46 + stalk * 3;
	}
	public float getBodyVerticalScale(float partial) {
		return 1 - (prevCrouchBlend + (crouchBlend - prevCrouchBlend) * partial) * 0.5F;
	}
	public Vec3 getTendrilOrigin(int side, float partial) {
		double yaw = (prevRenderYawOffset + MathHelper.wrapAngleTo180_float(renderYawOffset - prevRenderYawOffset) * partial) * Math.PI / 180;
		double x = lastTickPosX + (posX - lastTickPosX) * partial;
		double y = lastTickPosY + (posY - lastTickPosY) * partial;
		double z = lastTickPosZ + (posZ - lastTickPosZ) * partial;
		return Vec3.createVectorHelper(x + Math.cos(yaw) * side * 0.125D - Math.sin(yaw) * 0.125D,
				y + (24 - getBodyModelY(partial) - 7 * getBodyVerticalScale(partial)) / 16D,
				z + Math.sin(yaw) * side * 0.125D + Math.cos(yaw) * 0.125D);
	}
	/** The same articulated centerline is used for drawing and server obstruction checks. */
	public Vec3 getTendrilPoint(EntityLivingBase target, int side, int joint, float partial) {
		Vec3 origin = getTendrilOrigin(side, partial);
		if(joint == 0) return origin;
		float extension = getGrabExtension(partial);
		double tx = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial;
		double ty = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial + target.height * 0.5D;
		double tz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial;
		double t = joint == 1 ? 0.43D : joint == 2 ? 0.78D : 1;
		double feetY = lastTickPosY + (posY - lastTickPosY) * partial;
		double hang = Math.min(1.85D, Math.max(0.2D, origin.yCoord - feetY - 0.15D));
		double x = origin.xCoord + (tx - origin.xCoord) * t * extension;
		double y = origin.yCoord + (ty - origin.yCoord) * t * extension - hang * t * (1 - extension);
		double z = origin.zCoord + (tz - origin.zCoord) * t * extension;
		if(joint >= 4) {
			double dx = tx - origin.xCoord, dz = tz - origin.zCoord;
			double length = Math.max(0.01D, Math.sqrt(dx * dx + dz * dz));
			double curl = joint == 4 ? 0.12D : 0.18D;
			x += -dz / length * side * curl;
			z += dx / length * side * curl;
			y += joint == 4 ? -0.18D : 0.08D;
		}
		return Vec3.createVectorHelper(x, y, z);
	}

	public boolean hasClearTendrils(EntityLivingBase target, float partial) {
		for(int side = -1; side <= 1; side += 2) {
			Vec3 previous = getTendrilPoint(target, side, 0, partial);
			for(int joint = 1; joint <= 5; joint++) {
				Vec3 next = getTendrilPoint(target, side, joint, partial);
				if(worldObj.func_147447_a(previous, next, false, true, false) != null) return false;
				previous = next;
			}
		}
		return true;
	}
	/** Both visible hook sockets and a low body ray must be clear; tall eyes cannot bypass a wall. */
	public boolean canStrike(EntityLivingBase target) {
		if(!isPrey(target) || isBurrowing()) return false;
		AxisAlignedBB a = boundingBox, b = target.boundingBox;
		double dx = Math.max(0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
		double dy = Math.max(0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
		double dz = Math.max(0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
		if(getDistanceSqToEntity(target) > 25 || dx * dx + dy * dy + dz * dz > 25 || dy > 1 || target.ridingEntity != null
				|| !worldObj.checkChunksExist(MathHelper.floor_double(Math.min(posX, target.posX) - 0.5D),
						MathHelper.floor_double(Math.min(a.minY, b.minY)), MathHelper.floor_double(Math.min(posZ, target.posZ) - 0.5D),
						MathHelper.floor_double(Math.max(posX, target.posX) + 0.5D), MathHelper.ceiling_double_int(Math.max(a.maxY, b.maxY)),
						MathHelper.floor_double(Math.max(posZ, target.posZ) + 0.5D)) || !canEntityBeSeen(target)) return false;
		Vec3 from = Vec3.createVectorHelper(posX, boundingBox.minY + Math.min(1.25D, height * 0.65D), posZ);
		Vec3 to = Vec3.createVectorHelper(target.posX, b.minY + target.height * 0.5D, target.posZ);
		return worldObj.func_147447_a(from, to, false, true, false) == null
				&& worldObj.func_147447_a(getTendrilOrigin(-1, 1), to, false, true, false) == null
				&& worldObj.func_147447_a(getTendrilOrigin(1, 1), to, false, true, false) == null
				&& (!isGrabbing() || hasClearTendrils(target, 1));
	}

	public void beginStrike(EntityLivingBase target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || getHuntState() != CHASE
				|| getHealth() <= 0 || isCaptureBusy() || hasCapturedVillager() || isGrabbing() || strikeCooldown > 0 || !canStrike(target)) return;
		strikeTarget = target;
		captureState(CaptureState.GRABBING);
		grabHit = false;
		strikeCooldown = 72;
		dataWatcher.updateObject(25, Integer.valueOf(target.getEntityId()));
		dataWatcher.updateObject(24, Byte.valueOf((byte) 1));
		getNavigator().clearPathEntity();
	}

	private void endGrab() {
		strikeTarget = null;
		grabHit = false;
		dataWatcher.updateObject(24, Byte.valueOf((byte) 0));
		dataWatcher.updateObject(25, Integer.valueOf(-1));
		if(captureState == CaptureState.GRABBING) captureState(CaptureState.HUNTING);
	}

	private void tickGrab() {
		if(!isGrabbing()) return;
		if(getHealth() <= 0 || strikeTarget != getAttackTarget() || !canStrike(strikeTarget)) { endGrab(); return; }
		int tick = getGrabTick();
		getLookHelper().setLookPositionWithEntity(strikeTarget, 30, 30);
		if(tick == GRAB_WINDUP) {
			boolean hit = attackEntityAsMob(strikeTarget);
			if(!isGrabbing()) return; // Cocoon placement transfers ownership and ends the attack.
			grabHit = hit;
		}
		if(grabHit && tick >= GRAB_WINDUP && tick <= GRAB_HOLD_END) {
			// A short soft restraint, applied only on the owning server thread. Native movement
			// still resolves collisions; no teleport, mount, persistent modifier or client hit test.
			double dx = posX - strikeTarget.posX, dz = posZ - strikeTarget.posZ;
			double length = Math.sqrt(dx * dx + dz * dz);
			strikeTarget.motionX *= 0.35D;
			strikeTarget.motionZ *= 0.35D;
			if(length > 1.4D) {
				strikeTarget.motionX += dx / length * 0.12D;
				strikeTarget.motionZ += dz / length * 0.12D;
			}
			strikeTarget.velocityChanged = true;
		}
		if(tick >= GRAB_END) endGrab();
		else dataWatcher.updateObject(24, Byte.valueOf((byte) (tick + 1)));
	}

	@Override
	public boolean attackEntityAsMob(Entity target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || getHealth() <= 0 || grabHit
				|| getGrabTick() != GRAB_WINDUP || target != strikeTarget
				|| isCaptureBusy() || hasCapturedVillager() || !(target instanceof EntityLivingBase) || !canStrike((EntityLivingBase) target)) return false;
		if(target instanceof EntityVillager && ((EntityVillager) target).getHealth() <= ((EntityVillager) target).getMaxHealth() * 0.5F) {
			EntityVillager villager = (EntityVillager) target;
			NBTTagCompound snapshot = new NBTTagCompound();
			if(!villager.writeToNBTOptional(snapshot)) return false;
			EntityWoodcrawlerCocoon cocoon = EntityWoodcrawlerCocoon.place(this, snapshot,
					villager.posX, villager.posY, villager.posZ, 0, (byte) 0);
			if(cocoon == null) {
				resetHunting();
				captureState(CaptureState.RECOVERING);
				return false;
			}
			villager.setDead();
			if(!villager.isDead) { cocoon.cancelPlacement(); return false; }
			resetHunting();
			captureState(CaptureState.PLACING_CONVERSION_NEST);
			return true;
		}
		return super.attackEntityAsMob(target);
	}

	/** Surface-anchored visual submersion: the real body stays collidable and vulnerable.
	 * Reposition only across a clear, loaded corridor of natural soil; never tunnel through builds. */
	public boolean tryBurrow(EntityLivingBase target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || getHealth() <= 0 || burrowCooldown > 0
				|| isBurrowing() || isGrabbing() || isCaptureBusy() || hasCapturedVillager() || !onGround
				|| isCrouching() || isOnLadder() || isInWater() || !isPrey(target) || !canEntityBeSeen(target)) return false;
		double distance = getDistanceSqToEntity(target);
		boolean retreat = getHealth() < getMaxHealth() * 0.5F;
		if(distance < 9 || distance > (retreat ? 49 : 144)) return false;
		burrowCooldown = 40; // Failed terrain probes are bounded too.
		if(!burrowGround(posX, posY, posZ)) return false;
		double angle = Math.atan2(target.posZ - posZ, target.posX - posX) + (retreat ? Math.PI : 0);
		for(int i = 0; i < 8; i++) {
			double direction = angle + (i % 2 == 0 ? -1 : 1) * (0.45D + i * 0.12D);
			double radius = 3 + i % 4;
			double x = MathHelper.floor_double(posX + Math.cos(direction) * radius) + 0.5D;
			double z = MathHelper.floor_double(posZ + Math.sin(direction) * radius) + 0.5D;
			if(target.getDistanceSq(x, posY, z) < 6.25D || !burrowRoute(x, posY, z)) continue;
			burrowX = x; burrowY = posY; burrowZ = z;
			burrowCooldown = 400;
			getNavigator().clearPathEntity();
			setClimbing(false);
			dataWatcher.updateObject(26, Byte.valueOf((byte) 1));
			return true;
		}
		return false;
	}

	private boolean burrowGround(double x, double y, double z) {
		int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y - 0.05D), bz = MathHelper.floor_double(z);
		if(Math.abs(y - Math.rint(y)) > 0.05D || !worldObj.blockExists(bx, by, bz)) return false;
		Block block = worldObj.getBlock(bx, by, bz);
		return !block.hasTileEntity(worldObj.getBlockMetadata(bx, by, bz))
				&& (block == Blocks.dirt || block == Blocks.grass || block == Blocks.mycelium
						|| block == Blocks.sand || block == Blocks.gravel || block == Blocks.clay);
	}

	private boolean burrowRoute(double x, double y, double z) {
		AxisAlignedBB destination = AxisAlignedBB.getBoundingBox(x - BODY_WIDTH / 2, y, z - BODY_WIDTH / 2,
				x + BODY_WIDTH / 2, y + STANDING_HEIGHT, z + BODY_WIDTH / 2);
		AxisAlignedBB corridor = AxisAlignedBB.getBoundingBox(Math.min(boundingBox.minX, destination.minX), y,
				Math.min(boundingBox.minZ, destination.minZ), Math.max(boundingBox.maxX, destination.maxX),
				y + STANDING_HEIGHT, Math.max(boundingBox.maxZ, destination.maxZ));
		if(!worldObj.checkChunksExist(MathHelper.floor_double(corridor.minX), MathHelper.floor_double(y) - 1,
				MathHelper.floor_double(corridor.minZ), MathHelper.floor_double(corridor.maxX),
				MathHelper.ceiling_double_int(corridor.maxY), MathHelper.floor_double(corridor.maxZ))
				|| !worldObj.getCollidingBoundingBoxes(this, corridor).isEmpty() || worldObj.isAnyLiquid(corridor)
				|| !worldObj.checkNoEntityCollision(destination, this)) return false;
		for(int step = 0; step <= 8; step++) {
			double t = step / 8D;
			if(!burrowGround(posX + (x - posX) * t, y, posZ + (z - posZ) * t)) return false;
		}
		return true;
	}

	private void tickBurrow() {
		if(!isBurrowing()) return;
		int tick = getBurrowTick();
		getNavigator().clearPathEntity();
		motionX = motionZ = 0;
		if(getHealth() <= 0 || !onGround || !burrowGround(posX, posY, posZ)) {
			finishBurrow();
			return;
		}
		if(tick == 32 && burrowRoute(burrowX, burrowY, burrowZ)) {
			setPositionAndUpdate(burrowX, burrowY, burrowZ);
		}
		if(tick >= 60) {
			finishBurrow();
		} else dataWatcher.updateObject(26, Byte.valueOf((byte) (tick + 1)));
	}

	private void finishBurrow() {
		dataWatcher.updateObject(26, Byte.valueOf((byte) 0));
		strikeCooldown = Math.max(strikeCooldown, 20); // Readable emergence before any new windup.
		controller.onBurrowFinished();
	}

	@Override
	public void moveEntityWithHeading(float strafe, float forward) {
		if(isBurrowing() || isGrabbing()) {
			motionX = motionZ = 0;
			super.moveEntityWithHeading(0, 0);
		} else super.moveEntityWithHeading(strafe, forward);
	}

	@Override
	public void onUpdate() {
		if(!worldObj.isRemote) {
			if(checkLoadedClearance) {
				checkLoadedClearance = false;
				// Older saves used a shorter standing body. Fold safely when loaded beneath
				// an existing roof; do not relocate the creature or edit the surrounding blocks.
				if(!isCrouching() && !worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty()
						&& worldObj.getCollidingBoundingBoxes(this, AxisAlignedBB.getBoundingBox(boundingBox.minX,
								boundingBox.minY, boundingBox.minZ, boundingBox.maxX, boundingBox.minY + 1.8D, boundingBox.maxZ)).isEmpty()) {
					setCrouching(true);
					crouchBlend = prevCrouchBlend = 1;
				}
			}
			// Natural creatures pause with invasions; manual spawn initialization is saved per creature.
			if(!GeminiSpawning.canAct(this, 1) || worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
				resetHunting();
				dataWatcher.updateObject(26, Byte.valueOf((byte) 0));
				captureState(CaptureState.IDLE);
				setHuntState(WANDER);
				setClimbing(false);
				motionX = motionY = motionZ = 0;
				// Preserve the creature and lossless captive snapshot while dormant.
				return;
			}
			if(strikeCooldown > 0) strikeCooldown--;
			if(burrowCooldown > 0) burrowCooldown--;
			tickGrab();
			tickBurrow();
			if(getHealth() <= 0 && hasCapturedVillager()) releaseCapturedVillager();
			// Migrate snapshots saved by the old carrier pipeline to independent processing.
			if(getHealth() > 0 && hasCapturedVillager() && ++captiveTicks % 20 == 0) {
				if(EntityWoodcrawlerCocoon.place(this, capturedVillager, posX, posY, posZ,
						Math.min(captiveTicks, GeminiConfig.woodcrawlerConversionSeconds * 20), fakePersonConversion) != null) {
					clearCapture();
					resetHunting();
					captureState(CaptureState.RECOVERING);
				} else if(captiveTicks >= 200) releaseCapturedVillager();
			}
			if(isCaptureBusy()) {
				captureStateTicks++;
				if(captureState == CaptureState.PLACING_CONVERSION_NEST && captureStateTicks >= 20)
					captureState(CaptureState.CONVERTING_VICTIM);
				else if(captureState == CaptureState.CONVERTING_VICTIM && captureStateTicks >= 20)
					captureState(CaptureState.RECOVERING);
				else if(captureState == CaptureState.RECOVERING && captureStateTicks >= 40)
					captureState(CaptureState.RETURN_TO_HUNT);
				else if(captureState == CaptureState.RETURN_TO_HUNT) {
					resetHunting();
					captureState(CaptureState.IDLE);
				}
			}
		}
		prevClimbBlend = climbBlend;
		prevCrouchBlend = crouchBlend;
		prevStalkBlend = stalkBlend;
		climbBlend += ((isOnLadder() ? 1F : 0F) - climbBlend) * 0.2F;
		crouchBlend += ((isCrouching() ? 1F : 0F) - crouchBlend) * 0.25F;
		stalkBlend += ((getHuntState() == STALK ? 1F : 0F) - stalkBlend) * 0.15F;
		prevBurrowDepth = burrowDepth;
		int burrowTick = getBurrowTick();
		burrowDepth = burrowTick == 0 ? 0 : MathHelper.clamp_float(burrowTick <= 20 ? burrowTick / 20F
				: burrowTick <= 40 ? 1 : (60 - burrowTick) / 20F, 0, 1);
		if(worldObj.isRemote) setSize(BODY_WIDTH, isCrouching() ? 1.8F : STANDING_HEIGHT);
		if(worldObj.isRemote && isBurrowing() && ticksExisted % 6 == 0) {
			int x = MathHelper.floor_double(posX), y = MathHelper.floor_double(posY - 0.05D), z = MathHelper.floor_double(posZ);
			String dust = "blockcrack_" + Block.getIdFromBlock(worldObj.getBlock(x, y, z)) + "_" + worldObj.getBlockMetadata(x, y, z);
			for(int i = 0; i < 2; i++) worldObj.spawnParticle(dust, posX + (rand.nextDouble() - 0.5D) * 0.8D,
					posY + 0.1D, posZ + (rand.nextDouble() - 0.5D) * 0.8D, 0, 0.04D, 0);
		}
		super.onUpdate();
	}

	/** Restore the original UUID, profession, trades and mod data; never silently discard a captive. */
	public boolean releaseCapturedVillager() {
		if(worldObj.isRemote || capturedVillager == null) return true;
		Entity restored = EntityList.createEntityFromNBT((NBTTagCompound) capturedVillager.copy(), worldObj);
		if(!(restored instanceof EntityVillager)) return false;
		for(int i = 0; i < 25; i++) {
			double x = posX + (i == 0 ? 0 : (i % 5 - 2) * 0.8D);
			double z = posZ + (i == 0 ? 0 : (i / 5 - 2) * 0.8D);
			restored.setLocationAndAngles(x, posY + 0.1D, z, rotationYaw, 0);
			if(!worldObj.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(posY), MathHelper.floor_double(z))
					|| !worldObj.getCollidingBoundingBoxes(restored, restored.boundingBox).isEmpty()) continue;
			if(worldObj.spawnEntityInWorld(restored)) {
				clearCapture();
				resetHunting();
				captureState(CaptureState.RECOVERING);
				return true;
			}
		}
		return false;
	}

	private void clearCapture() {
		capturedVillager = null;
		captiveTicks = 0;
		fakePersonConversion = 0;
		dataWatcher.updateObject(22, Byte.valueOf((byte) 0));
	}

	/** One decision per captive, including across reloads and vetoed/limited spawn retries. */
	public boolean shouldConvertToFakePerson() {
		if(worldObj.isRemote || !hasCapturedVillager() || captiveTicks < GeminiConfig.woodcrawlerConversionSeconds * 20
				|| !GeminiSpawning.canAct(this, 1)) return false;
		if(fakePersonConversion == 0) fakePersonConversion = (byte) (rand.nextFloat() < GeminiConfig.fakePersonConversionChance ? 1 : 2);
		return fakePersonConversion == 1;
	}

	@Override
	public void setDead() {
		// A vetoed/obstructed release retains the saved carrier for a later retry.
		if(!worldObj.isRemote && hasCapturedVillager() && !releaseCapturedVillager()) return;
		super.setDead();
	}

	@Override
	protected boolean canDespawn() { return !hasCapturedVillager(); }
	@Override
	public boolean getCanSpawnHere() {
		return WoodcrawlerSpawning.canSpawn(worldObj, this) && WoodcrawlerSpawning.isForest(worldObj,
				MathHelper.floor_double(posX), MathHelper.floor_double(posZ)) && super.getCanSpawnHere();
	}
	@Override
	public int getMaxSpawnedInChunk() { return 1; }
	@Override
	public EnumCreatureAttribute getCreatureAttribute() { return EnumCreatureAttribute.ARTHROPOD; }
	@Override
	protected String getLivingSound() { return null; }
	@Override
	protected String getHurtSound() { return null; }
	@Override
	protected String getDeathSound() { return null; }
	@Override
	protected void func_145780_a(int x, int y, int z, Block block) { }
	@Override
	public float getEyeHeight() { return height * 0.78F; }
	@Override
	public boolean attackEntityFrom(DamageSource source, float damage) {
		if(worldObj.isRemote || source.getEntity() instanceof IGeminiCreature || !GeminiSpawning.canAct(this, 1)
				|| !super.attackEntityFrom(source, damage)) return false;
		endGrab();
		int tick = getBurrowTick();
		// Damage forces emergence at the current visual depth, including during entry.
		if(tick > 0 && tick <= 40) dataWatcher.updateObject(26, Byte.valueOf((byte) (tick <= 20 ? 60 - tick : 41)));
		return true;
	}
	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		if(capturedVillager != null) nbt.setTag("WoodcrawlerCaptive", capturedVillager.copy());
		nbt.setInteger("WoodcrawlerCaptiveTicks", captiveTicks);
		nbt.setByte("FakePersonConversion", fakePersonConversion);
		nbt.setString("CaptureState", captureState.name());
		nbt.setInteger("WoodcrawlerGrabCooldown", strikeCooldown);
		nbt.setInteger("WoodcrawlerBurrowCooldown", burrowCooldown);
		nbt.setBoolean("WoodcrawlerCrouching", isCrouching());
	}
	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		loadedFromNBT = true;
		checkLoadedClearance = true;
		capturedVillager = nbt.hasKey("WoodcrawlerCaptive", 10) ? nbt.getCompoundTag("WoodcrawlerCaptive") : null;
		captiveTicks = Math.max(0, Math.min(600, nbt.getInteger("WoodcrawlerCaptiveTicks")));
		fakePersonConversion = (byte) Math.max(0, Math.min(2, nbt.getByte("FakePersonConversion")));
		dataWatcher.updateObject(22, Byte.valueOf((byte) (hasCapturedVillager() ? 1 : 0)));
		// Never reload a windup, stale path, target or dependency on a cocoon's loaded chunk.
		captureState = hasCapturedVillager() || nbt.hasKey("CaptureState", 8) ? CaptureState.RECOVERING : CaptureState.IDLE;
		captureStateTicks = 0;
		strikeCooldown = Math.max(20, Math.min(72, nbt.getInteger("WoodcrawlerGrabCooldown")));
		burrowCooldown = Math.max(100, Math.min(400, nbt.getInteger("WoodcrawlerBurrowCooldown")));
		// Submersion is visual: saved position is always above ground. Reload safely surfaces
		// the same creature and discards live targets, never recreating a captive or an attack.
		endGrab();
		dataWatcher.updateObject(26, Byte.valueOf((byte) 0));
		if(nbt.getBoolean("WoodcrawlerCrouching")) {
			setCrouching(true);
			crouchBlend = prevCrouchBlend = 1;
		}
	}
}
