package com.hbm.entity.mob;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

public class EntityFRIEND extends EntityCreature {
	//let him in.
	//(*is actually a god awful thing that will cause you psychological distress)
	//do not go to space, idiot.

	public EntityFRIEND(World world) {
		super(world);
		this.tasks.addTask(0, new EntityAISwimming(this));
		this.tasks.addTask(1, new EntityAIWander(this, 1.0D));
		this.tasks.addTask(2, new EntityAILookIdle(this));
		this.tasks.addTask(3, new EntityAIWatchClosest(this, EntityPlayer.class, 15.0F));

		this.renderDistanceWeight *= 10;
	}

	@Override
	protected void applyEntityAttributes() {
		super.applyEntityAttributes();
		this.getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(8.0D);
		this.getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.2D);
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		if(!worldObj.isRemote) {
			double despawnRange = 50;
			List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(despawnRange, despawnRange, despawnRange));
			if(!players.isEmpty())
				this.setDead();
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

	//same attrib as ghost, will have a different model and will appear in space when you are ALONE.


}
