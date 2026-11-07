package com.hbm.entity.mob.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.ai.EntityAIWoodcrawler;
import com.hbm.world.gemini.WoodcrawlerConversionEvent;
import com.hbm.world.gemini.WoodcrawlerSpawning;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
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
import net.minecraftforge.common.MinecraftForge;

public class EntityWoodcrawler extends EntityMob implements IDeepRootCarrier {

	public static final int WANDER = 0, STALK = 1, CHASE = 2, SEARCH = 3, RETREAT = 4;
	private NBTTagCompound capturedVillager;
	private int captiveTicks;
	private byte fakePersonConversion;
	private boolean loadedFromNBT;
	private int strikeDelay, strikeCooldown;
	private EntityLivingBase strikeTarget;
	public int attackAnimationTicks;
	public float climbBlend, prevClimbBlend, crouchBlend, prevCrouchBlend, stalkBlend, prevStalkBlend;

	public EntityWoodcrawler(World world) {
		super(world);
		setSize(0.85F, 3.1F);
		stepHeight = 1.0F;
		experienceValue = 15;
		getNavigator().setAvoidsWater(true);
		getNavigator().setCanSwim(true);
		getNavigator().setEnterDoors(true);
		getNavigator().setBreakDoors(true);
		tasks.addTask(0, new EntityAISwimming(this));
		tasks.addTask(1, new EntityAIWoodcrawler(this));
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
	public void setHuntState(int state) { dataWatcher.updateObject(20, Byte.valueOf((byte) state)); }
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
						boundingBox.maxX, boundingBox.minY + 3.1D, boundingBox.maxZ)).isEmpty()) return;
		dataWatcher.updateObject(23, Byte.valueOf((byte) (crouch ? 1 : 0)));
		setSize(0.85F, crouch ? 1.8F : 3.1F);
	}

	public boolean isPrey(Entity entity) {
		if(entity == this || entity instanceof IGeminiCreature || !(entity instanceof EntityLivingBase)
				|| !entity.isEntityAlive() || entity.worldObj != worldObj) return false;
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

	/** Short, body-level rays as well as eye visibility prevent over-wall/through-wall strikes. */
	public boolean canStrike(EntityLivingBase target) {
		if(!isPrey(target)) return false;
		AxisAlignedBB a = boundingBox, b = target.boundingBox;
		double dx = Math.max(0, Math.max(a.minX - b.maxX, b.minX - a.maxX));
		double dy = Math.max(0, Math.max(a.minY - b.maxY, b.minY - a.maxY));
		double dz = Math.max(0, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
		if(dx * dx + dy * dy + dz * dz > 1.15D * 1.15D || dy > 0.5D || !canEntityBeSeen(target)) return false;
		Vec3 from = Vec3.createVectorHelper(posX, boundingBox.minY + Math.min(1.25D, height * 0.65D), posZ);
		Vec3 to = Vec3.createVectorHelper(target.posX, b.minY + target.height * 0.5D, target.posZ);
		return worldObj.func_147447_a(from, to, false, true, false) == null;
	}

	public void beginStrike(EntityLivingBase target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || getHuntState() != CHASE
				|| strikeDelay > 0 || strikeCooldown > 0 || !canStrike(target)) return;
		strikeTarget = target;
		strikeDelay = 7;
		attackAnimationTicks = 14;
		worldObj.setEntityState(this, (byte) 40);
	}

	@Override
	public boolean attackEntityAsMob(Entity target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || strikeCooldown > 0
				|| hasCapturedVillager() || !(target instanceof EntityLivingBase) || !canStrike((EntityLivingBase) target)) return false;
		strikeCooldown = 32;
		if(target instanceof EntityVillager && ((EntityVillager) target).getHealth() <= ((EntityVillager) target).getMaxHealth() * 0.5F) {
			EntityVillager villager = (EntityVillager) target;
			NBTTagCompound snapshot = new NBTTagCompound();
			if(!villager.writeToNBTOptional(snapshot)) return false;
			capturedVillager = snapshot;
			captiveTicks = 0;
			fakePersonConversion = 0;
			dataWatcher.updateObject(22, Byte.valueOf((byte) 1));
			villager.setDead();
			if(!villager.isDead) { clearCapture(); return false; }
			setAttackTarget(null);
			getNavigator().clearPathEntity();
			return true;
		}
		return super.attackEntityAsMob(target);
	}

	@Override
	public void onUpdate() {
		if(!worldObj.isRemote) {
			// Natural creatures pause with invasions; manual spawn initialization is saved per creature.
			if(!GeminiSpawning.canAct(this, 1) || worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
				getNavigator().clearPathEntity();
				setAttackTarget(null);
				strikeDelay = strikeCooldown = attackAnimationTicks = 0;
				strikeTarget = null;
				setHuntState(WANDER);
				setClimbing(false);
				motionX = motionY = motionZ = 0;
				// Preserve the creature and lossless captive snapshot while dormant.
				return;
			}
			if(strikeCooldown > 0) strikeCooldown--;
			if(strikeDelay > 0 && --strikeDelay == 0) {
				if(getHealth() > 0 && getHuntState() == CHASE && strikeTarget == getAttackTarget()) attackEntityAsMob(strikeTarget);
				strikeTarget = null;
			}
			if(getHealth() <= 0 && hasCapturedVillager()) releaseCapturedVillager();
			if(getHealth() > 0 && hasCapturedVillager() && ++captiveTicks >= 600 && captiveTicks % 200 == 0) {
				WoodcrawlerConversionEvent event = new WoodcrawlerConversionEvent(this, capturedVillager);
				MinecraftForge.EVENT_BUS.post(event);
				EntityLiving replacement = event.getReplacement();
				if(hasCapturedVillager() && !isDead && GeminiSpawning.canAct(this, 1)
						&& replacement != null && replacement != this && !replacement.isDead && replacement.worldObj == worldObj
						&& !worldObj.loadedEntityList.contains(replacement)) {
					replacement.setLocationAndAngles(posX, posY, posZ, rotationYaw, 0);
					if(spawnReplacement(replacement)) clearCapture();
				}
			}
		}
		prevClimbBlend = climbBlend;
		prevCrouchBlend = crouchBlend;
		prevStalkBlend = stalkBlend;
		climbBlend += ((isOnLadder() ? 1F : 0F) - climbBlend) * 0.2F;
		crouchBlend += ((isCrouching() ? 1F : 0F) - crouchBlend) * 0.25F;
		stalkBlend += ((getHuntState() == STALK ? 1F : 0F) - stalkBlend) * 0.15F;
		if(attackAnimationTicks > 0) attackAnimationTicks--;
		if(worldObj.isRemote) setSize(0.85F, isCrouching() ? 1.8F : 3.1F);
		super.onUpdate();
	}

	private boolean spawnReplacement(EntityLiving replacement) {
		if(!(replacement instanceof EntityFakePerson)) return worldObj.spawnEntityInWorld(replacement);
		// Do not materialize inside the carrier, a wall, water, or another captive.
		for(int i = 0; i < 25; i++) {
			double x = posX + (i % 5 - 2);
			double z = posZ + (i / 5 - 2);
			replacement.setLocationAndAngles(x, posY, z, rotationYaw, 0);
			if(!worldObj.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(posY), MathHelper.floor_double(z))
					|| !worldObj.getCollidingBoundingBoxes(replacement, replacement.boundingBox).isEmpty()
					|| !worldObj.checkNoEntityCollision(replacement.boundingBox)
					|| worldObj.isAnyLiquid(replacement.boundingBox)) continue;
			// A canceled spawn keeps the captive and its already-rolled decision for the next retry.
			return worldObj.spawnEntityInWorld(replacement);
		}
		return false;
	}

	@Override
	public void handleHealthUpdate(byte status) {
		if(status == 40) attackAnimationTicks = 14;
		else super.handleHealthUpdate(status);
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
		if(worldObj.isRemote || !hasCapturedVillager() || captiveTicks < 600
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
		return !worldObj.isRemote && !(source.getEntity() instanceof IGeminiCreature)
				&& GeminiSpawning.canAct(this, 1) && super.attackEntityFrom(source, damage);
	}
	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		if(capturedVillager != null) nbt.setTag("WoodcrawlerCaptive", capturedVillager.copy());
		nbt.setInteger("WoodcrawlerCaptiveTicks", captiveTicks);
		nbt.setByte("FakePersonConversion", fakePersonConversion);
	}
	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		loadedFromNBT = true;
		capturedVillager = nbt.hasKey("WoodcrawlerCaptive", 10) ? nbt.getCompoundTag("WoodcrawlerCaptive") : null;
		captiveTicks = Math.max(0, Math.min(600, nbt.getInteger("WoodcrawlerCaptiveTicks")));
		fakePersonConversion = (byte) Math.max(0, Math.min(2, nbt.getByte("FakePersonConversion")));
		dataWatcher.updateObject(22, Byte.valueOf((byte) (hasCapturedVillager() ? 1 : 0)));
		// Paths, awareness and attack windups are intentionally transient.
	}
}
