package com.hbm.entity.mob;

import com.hbm.entity.mob.ai.EntityAIDigToPlayer;
import com.hbm.entity.mob.ai.EntityAILankStalk;
import com.hbm.entity.mob.ai.LankTerrain;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class EntityFRIEND extends EntityCreature {

	private final EntityAILankStalk stalking;
	private boolean naturalEncounter;
	private int departureTick;
	private int teleportCooldown;
	private EntityPlayer projectileAttacker;
	private int defensiveUntil;

	public EntityFRIEND(World world) {
		super(world);
		stalking = new EntityAILankStalk(this);
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(1, new EntityAIBase() {
			{
				setMutexBits(3);
			}

			@Override
			public boolean shouldExecute() {
				return isDefending();
			}

			@Override
			public void updateTask() {
				if(projectileAttacker == null) return;
				getLookHelper().setLookPositionWithEntity(projectileAttacker, 30F, 30F);
				if(getDistanceSqToEntity(projectileAttacker) < 4D && getEntitySenses().canSee(projectileAttacker)) {
					attackEntityAsMob(projectileAttacker);
					projectileAttacker = null;
				} else if(ticksExisted % 12 == 0 && LankTerrain.canUseNavigator(EntityFRIEND.this)) {
					getNavigator().tryMoveToEntityLiving(projectileAttacker, 1.2D);
				}
			}

			@Override
			public void resetTask() {
				getNavigator().clearPathEntity();
			}
		});
		this.tasks.addTask(2, stalking);
		this.tasks.addTask(3, new EntityAIDigToPlayer(this, 0.1D, 160.0D));
		this.tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 64.0F));
		this.tasks.addTask(5, new EntityAIWander(this, 1.0D) {
			@Override
			public boolean shouldExecute() {
				return LankTerrain.canUseNavigator(EntityFRIEND.this) && super.shouldExecute();
			}
		});
		this.tasks.addTask(6, new EntityAILookIdle(this));

		this.renderDistanceWeight *= 10;
		this.setSize(0.6F, 3.3F);
	}

	public void beginNaturalEncounter(EntityPlayer target) {
		naturalEncounter = true;
		departureTick = 2400 + rand.nextInt(3601);
		setObservedPlayer(target);
	}

	public EntityPlayer getObservedPlayer() {
		return stalking.getTarget();
	}

	public void setObservedPlayer(EntityPlayer player) {
		stalking.setTarget(player);
	}

	public boolean isNaturalEncounter() {
		return naturalEncounter;
	}

	public static boolean isUnclaimed(EntityPlayer player, EntityFRIEND requester) {
		for(Object entry : player.worldObj.loadedEntityList) {
			if(entry instanceof EntityFRIEND && entry != requester) {
				EntityFRIEND other = (EntityFRIEND) entry;
				if(other.isEntityAlive() && other.getObservedPlayer() == player) return false;
			}
		}
		return true;
	}

	@Override
	public boolean getCanSpawnHere() {
		return naturalEncounter && super.getCanSpawnHere();
	}

	// Natural visitors end on unload. Summoned and existing saved entities keep normal persistence.
	@Override
	public boolean writeToNBTOptional(NBTTagCompound tag) {
		return !naturalEncounter && super.writeToNBTOptional(tag);
	}

	@Override
	public boolean writeMountToNBT(NBTTagCompound tag) {
		return !naturalEncounter && super.writeMountToNBT(tag);
	}

	@Override
	protected void despawnEntity() {
		// Distance or player camera direction alone must never remove a Lank.
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(50.0D);
		this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
		this.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1.0D);
	}

	@Override
	protected boolean isAIEnabled() {
		return true;
	}

	@Override
	protected void updateAITick() {
		if(teleportCooldown > 0) teleportCooldown--;
		if(!isDefending()) projectileAttacker = null;
		if(naturalEncounter && !isDefending() && ticksExisted >= departureTick && ticksExisted % 100 == 0
				&& LankTerrain.hiddenFromObservers(worldObj, posX, boundingBox.minY, posZ, height, false)) {
			setDead();
		}
	}

	@Override
	public boolean attackEntityAsMob(Entity entity) {
		boolean hit = entity.attackEntityFrom(DamageSource.causeMobDamage(this), 6.0F);
		if(hit && entity instanceof EntityPlayer && !worldObj.isRemote && teleportCooldown == 0) {
			teleportUndergroundNearPlayer((EntityPlayer) entity);
			teleportCooldown = 40;
		}
		return hit;
	}

	public boolean isDefending() {
		return projectileAttacker != null && ticksExisted < defensiveUntil && projectileAttacker.worldObj == worldObj
				&& LankTerrain.isTarget(projectileAttacker);
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float amount) {
		Entity projectile = source.getSourceOfDamage();
		if(!(projectile instanceof EntityArrow) && !(projectile instanceof EntityThrowable)) {
			return super.attackEntityFrom(source, amount);
		}

		if(!worldObj.isRemote && teleportCooldown == 0) {
			Entity shooter = projectile instanceof EntityArrow ? ((EntityArrow) projectile).shootingEntity
					: ((EntityThrowable) projectile).getThrower();
			EntityPlayer player = shooter instanceof EntityPlayer ? (EntityPlayer) shooter : null;
			if(rand.nextBoolean()) {
				if(player == null || !teleportUndergroundNearPlayer(player)) tryDefensiveTeleport(posX, posY, posZ, 8, true);
				teleportCooldown = 40;
			} else if(player != null && LankTerrain.isTarget(player)) {
				getNavigator().clearPathEntity();
				projectileAttacker = player;
				defensiveUntil = ticksExisted + 60;
				teleportCooldown = 40;
			}
		}
		return false;
	}

	public boolean teleportUndergroundNearPlayer(EntityPlayer player) {
		double y = Math.min(boundingBox.minY, player.boundingBox.minY - 6D);
		return player.worldObj == worldObj && tryDefensiveTeleport(player.posX, y, player.posZ, 16, false);
	}

	private boolean tryDefensiveTeleport(double centerX, double nearY, double centerZ, int radius, boolean surface) {
		for(int i = 0; i < 12; i++) {
			int x = MathHelper.floor_double(centerX + (rand.nextDouble() * 2D - 1D) * radius);
			int z = MathHelper.floor_double(centerZ + (rand.nextDouble() * 2D - 1D) * radius);
			ChunkCoordinates spot = LankTerrain.findFooting(this, x, MathHelper.floor_double(nearY), z, 5, surface);
			if(spot == null || getDistanceSq(x + 0.5D, spot.posY, z + 0.5D) < 16D) continue;
			getNavigator().clearPathEntity();
			setPositionAndUpdate(x + 0.5D, spot.posY, z + 0.5D);
			return true;
		}
		return false;
	}

	@Override
	public boolean isEntityInvulnerable() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return distance < 500000;
	}
}
