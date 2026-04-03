package com.hbm.entity.mob;

import com.hbm.dim.laythe.WorldProviderLaythe;
import com.hbm.entity.mob.ai.EntityAIDigToPlayer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

public class EntityFRIEND extends EntityCreature {

	private int despawnTimer = 0;

	private int timer = 0;
	//let him in.
	//(*is actually a god awful thing that will cause you psychological distress)
	//do not go to space, idiot.

	public EntityFRIEND(World world) {
		super(world);

		this.tasks.addTask(0, new EntityAIDigToPlayer(this, 0.1D, 160.0D));
		this.tasks.addTask(1, new EntityAISwimming(this));
		this.tasks.addTask(2, new EntityAIWander(this, 1.0D));
		this.tasks.addTask(3, new EntityAILookIdle(this));
		this.tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 64.0F));

		this.targetTasks.addTask(1, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 0, true));

		this.renderDistanceWeight *= 10;
		this.setSize(0.6F, 1.8F);
	}



	@Override
	public boolean getCanSpawnHere() {

		//todo spawn conditions, spawn should happen once per trigger event
		// (eventually have a trigger event item or block when mined that will allow this to run,
		// once spawned and target acquired it should not spawn again unless the trigger is met again,
		// only one friend should spawn at a time.)

		// Only below Y = 50
		if (this.posY >= 50 && this.worldObj.provider instanceof WorldProviderLaythe ) {

			for (Object obj : this.worldObj.loadedEntityList) {
				if (obj instanceof EntityFRIEND) {
					return false;
				}
			}

			if (this.rand.nextInt(100) != 0) return false; // 1%
			return false;

		}

		return super.getCanSpawnHere();
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(8.0D);
		this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
	}

	@Override
	protected boolean isAIEnabled() {
		return true;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		if(!worldObj.isRemote) {
			//the wholesome
//
			if (this.rand.nextBoolean()) {
//
				double despawnRange = 3;
				List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(despawnRange, despawnRange, despawnRange));
				if (!players.isEmpty())
					timer++;
				if (timer > 3) {
					if (this.rand.nextBoolean()) {
						//teleport away into a safe place, where the player cannot find.
						this.setPosition(this.posX + (rand.nextDouble() - 0.5) * 100, this.posY + (rand.nextDouble() - 0.5) * 100, this.posZ + (rand.nextDouble() - 0.5) * 100);
					} else {
						//runaway
						double runawayRange = 20;
						List<EntityPlayer> players2 = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(runawayRange, runawayRange, runawayRange));
						for (EntityPlayer player : players2) {
							double x = this.posX - player.posX;
							double y = this.posY - player.posY;
							double z = this.posZ - player.posZ;
							this.motionX += x * 0.1;
							this.motionY += y * 0.1;
							this.motionZ += z * 0.1;
						}
						despawnTimer++;
//
						if (despawnTimer >= 5 * 20) { // 5 seconds
							//this.setDead();
							//teleport away into a safe place, where the player cannot find.
							//this.setPosition(this.posX + (rand.nextDouble() - 0.5) * 50, this.posY + (rand.nextDouble() - 0.5) * 50, this.posZ + (rand.nextDouble() - 0.5) * 50);
							this.setPosition(this.posX + (rand.nextDouble() - 0.5) * 100, this.posY + (rand.nextDouble() - 0.5) * 100, this.posZ + (rand.nextDouble() - 0.5) * 100);
						}
					}
				}
			} else {
				//the unwholesome
				double mogrange = 5;
				List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(mogrange, mogrange, mogrange));
				//if there is a player nearby, encircle them in a perfect circle while looking at them, then disappear
				for (EntityPlayer player : players) {
					double x = this.posX - player.posX;
					double y = this.posY - player.posY;
					double z = this.posZ - player.posZ;
					this.motionX += x * 0.1;
					this.motionY += y * 0.1;
					this.motionZ += z * 0.1;
				}
			}
		}
	}

	@Override
	public void setHealth(float health) {
		super.setHealth(this.getMaxHealth());
	}

	@Override
	public boolean isEntityInvulnerable() {
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return distance < 500000;
	}

	//same attrib as ghost, will have a different model and will appear in space when you are ALONE.


}
