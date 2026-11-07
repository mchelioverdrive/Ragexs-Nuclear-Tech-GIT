package com.hbm.entity.mob.gemini;

import java.util.UUID;

import com.hbm.entity.mob.ai.EntityAIFakePerson;
import com.hbm.world.gemini.FakePersonSpawning;

import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIMoveIndoors;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

/** A monster wearing a villager's appearance; never an IMerchant, INpc or EntityVillager. */
public final class EntityFakePerson extends EntityMob implements IDeepRootCarrier {

	private static final int DISGUISED = 1, HOSTILE = 2, RECOVERING = 4, RECOVERY_USED = 8;
	private int recoveryTicks, attackCooldown;

	public EntityFakePerson(World world) {
		super(world);
		setSize(0.6F, 1.8F);
		experienceValue = 8;
		getNavigator().setAvoidsWater(true);
		getNavigator().setCanSwim(true);
		getNavigator().setBreakDoors(true); // Native open-door pathing, no block breaking task.
		tasks.addTask(0, new EntityAISwimming(this));
		tasks.addTask(1, new EntityAIFakePerson(this));
		tasks.addTask(2, new EntityAIMoveIndoors(this));
		tasks.addTask(3, new EntityAIOpenDoor(this, true));
		tasks.addTask(9, new EntityAIWander(this, 0.6D));
		tasks.addTask(10, new EntityAIWatchClosest(this, EntityLivingBase.class, 8F));
	}

    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        GeminiSpawning.initialize(this);
        return super.onSpawnWithEgg(data);
    }
	@Override
	protected void entityInit() {
		super.entityInit();
		dataWatcher.addObject(20, Byte.valueOf((byte) DISGUISED));
		dataWatcher.addObject(21, Integer.valueOf(0));
		dataWatcher.addObject(22, Byte.valueOf((byte) 0));
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(30D);
		getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(5D);
		// Matches native villager walking at the 0.6 path speed multiplier.
		getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.5D);
		getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(24D);
	}

	private boolean flag(int flag) { return (dataWatcher.getWatchableObjectByte(20) & flag) != 0; }
	private void flag(int flag, boolean value) {
		int flags = dataWatcher.getWatchableObjectByte(20);
		dataWatcher.updateObject(20, Byte.valueOf((byte) (value ? flags | flag : flags & ~flag)));
	}
	public boolean isDisguised() { return flag(DISGUISED); }
	public boolean isHostile() { return flag(HOSTILE); }
	public boolean isRecovering() { return flag(RECOVERING); }
	public int getProfession() { return dataWatcher.getWatchableObjectInt(21); }
	@Override public boolean isChild() { return dataWatcher.getWatchableObjectByte(22) != 0; }
	@Override public boolean isAIEnabled() { return true; }

	public void imitateVillager(NBTTagCompound victim) {
		dataWatcher.updateObject(21, Integer.valueOf(victim.getInteger("Profession")));
		dataWatcher.updateObject(22, Byte.valueOf((byte) (victim.getInteger("Age") < 0 ? 1 : 0)));
		if(victim.hasKey("UUIDMost", 4) && victim.hasKey("UUIDLeast", 4))
			entityUniqueID = new UUID(victim.getLong("UUIDMost"), victim.getLong("UUIDLeast"));
		if(victim.hasKey("CustomName", 8)) setCustomNameTag(victim.getString("CustomName"));
		setAlwaysRenderNameTag(victim.getBoolean("CustomNameVisible"));
		updateDisguiseSize();
	}

	private void updateDisguiseSize() {
		float scale = isChild() ? 0.5F : 1F;
		if(width != 0.6F * scale || height != 1.8F * scale) setSize(0.6F * scale, 1.8F * scale);
	}

	public boolean canHunt(EntityLivingBase target) {
		if(target == null || target == this || target.worldObj != worldObj || !target.isEntityAlive()
				|| target instanceof IGeminiCreature || isOnSameTeam(target)) return false;
		if(target instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) target;
			return !player.capabilities.disableDamage && !player.capabilities.isCreativeMode;
		}
		return true; // Retaliation against non-Gemini attackers, including hostile mobs.
	}

	public void expose(EntityLivingBase target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || getHealth() <= 0) return;
		boolean disguised = isDisguised();
		flag(DISGUISED, false);
		flag(HOSTILE, true);
		setAttackTarget(target);
		if(disguised) com.hbm.world.gemini.GeminiPopulation.alert(this, target);
		if(disguised) playSound("mob.villager.no", 1F, 0.55F);
	}

	@Override
	public void setAttackTarget(EntityLivingBase target) {
		super.setAttackTarget(canHunt(target) ? target : null);
	}

	@Override
	public boolean interact(EntityPlayer player) {
		if(!worldObj.isRemote && canHunt(player) && !isRecovering()) expose(player);
		return true;
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float damage) {
		if(worldObj.isRemote || source.getEntity() instanceof IGeminiCreature || !GeminiSpawning.canAct(this, 1)) return false;
		boolean hit = super.attackEntityFrom(source, damage);
		if(hit && getHealth() > 0) {
			Entity attacker = source.getEntity();
			expose(attacker instanceof EntityLivingBase && canHunt((EntityLivingBase) attacker)
					? (EntityLivingBase) attacker : null);
		}
		return hit;
	}

	@Override
	public boolean attackEntityAsMob(Entity target) {
		if(worldObj.isRemote || !GeminiSpawning.canAct(this, 1) || !isHostile()
				|| isRecovering() || getHealth() <= 0 || attackCooldown > 0 || !(target instanceof EntityLivingBase)
				|| !canHunt((EntityLivingBase) target) || !canEntityBeSeen(target)) return false;
		double reach = width + target.width + 0.6D;
		if(getDistanceSqToEntity(target) > reach * reach || target.boundingBox.maxY <= boundingBox.minY
				|| target.boundingBox.minY >= boundingBox.maxY) return false;
		Vec3 from = Vec3.createVectorHelper(posX, posY + height * 0.5D, posZ);
		Vec3 to = Vec3.createVectorHelper(target.posX, target.posY + target.height * 0.5D, target.posZ);
		if(worldObj.func_147447_a(from, to, false, true, false) != null) return false;
		attackCooldown = 20;
		swingItem();
		return super.attackEntityAsMob(target);
	}

	@Override
	public void onDeath(DamageSource source) {
		if(!worldObj.isRemote && !flag(RECOVERY_USED) && GeminiSpawning.canAct(this, 1)
				&& !source.isFireDamage() && !isBurning() && source != DamageSource.outOfWorld && rand.nextInt(4) == 0) {
			flag(RECOVERY_USED, true);
			flag(DISGUISED, false);
			flag(HOSTILE, true);
			flag(RECOVERING, true);
			recoveryTicks = 60;
			setHealth(1F);
			setAttackTarget(null);
			getNavigator().clearPathEntity();
			motionX = motionZ = 0;
			return; // No kill callbacks, loot, XP or vanilla death packet for an apparent death.
		}
		flag(RECOVERING, false);
		recoveryTicks = 0;
		super.onDeath(source);
	}

	@Override
	protected boolean isMovementBlocked() { return isRecovering() || super.isMovementBlocked(); }

	@Override
	public void onUpdate() {
		if(!worldObj.isRemote) {
			if(!GeminiSpawning.canAct(this, 1) || worldObj.difficultySetting == EnumDifficulty.PEACEFUL) {
				setAttackTarget(null);
				getNavigator().clearPathEntity();
				motionX = motionY = motionZ = 0;
				return;
			}
			if(attackCooldown > 0) attackCooldown--;
			if(isRecovering() && getHealth() > 0) {
				getNavigator().clearPathEntity();
				motionX = motionZ = 0;
				if(--recoveryTicks <= 0) {
					flag(RECOVERING, false);
					setHealth(getMaxHealth() * 0.45F);
					playSound("mob.villager.death", 1F, 0.5F);
				}
			}
		}
		updateDisguiseSize();
		super.onUpdate();
	}

	@Override public boolean getCanSpawnHere() { return FakePersonSpawning.canSpawn(worldObj, this) && super.getCanSpawnHere(); }
	@Override public int getMaxSpawnedInChunk() { return 1; }
	@Override public float getBlockPathWeight(int x, int y, int z) { return 0F; }
	@Override protected String getLivingSound() { return isRecovering() ? null : "mob.villager.idle"; }
	@Override protected String getHurtSound() { return "mob.villager.hit"; }
	@Override protected String getDeathSound() { return "mob.villager.death"; }
	@Override protected float getSoundPitch() { return rand.nextInt(4) == 0 || !isDisguised() ? 0.5F + rand.nextFloat() * 0.15F : super.getSoundPitch(); }
	@Override public int getTalkInterval() { return 240; }
	@Override public float getEyeHeight() { return height * 0.9F; }
	@Override protected boolean canDespawn() { return !isRecovering() && !hasCustomNameTag(); }

	@Override
	public void writeEntityToNBT(NBTTagCompound nbt) {
		super.writeEntityToNBT(nbt);
		nbt.setBoolean("Disguised", isDisguised());
		nbt.setBoolean("Hostile", isHostile());
		nbt.setBoolean("RecoveryUsed", flag(RECOVERY_USED));
		nbt.setInteger("RecoveryTicks", recoveryTicks);
		nbt.setInteger("Profession", getProfession());
		nbt.setBoolean("ChildDisguise", isChild());
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		recoveryTicks = Math.max(0, Math.min(60, nbt.getInteger("RecoveryTicks")));
		flag(HOSTILE, nbt.getBoolean("Hostile") || recoveryTicks > 0);
		flag(DISGUISED, (!nbt.hasKey("Disguised") || nbt.getBoolean("Disguised")) && !isHostile());
		flag(RECOVERY_USED, nbt.getBoolean("RecoveryUsed") || recoveryTicks > 0);
		flag(RECOVERING, recoveryTicks > 0 && getHealth() > 0);
		dataWatcher.updateObject(21, Integer.valueOf(nbt.getInteger("Profession")));
		dataWatcher.updateObject(22, Byte.valueOf((byte) (nbt.getBoolean("ChildDisguise") ? 1 : 0)));
		updateDisguiseSize();
	}
}
