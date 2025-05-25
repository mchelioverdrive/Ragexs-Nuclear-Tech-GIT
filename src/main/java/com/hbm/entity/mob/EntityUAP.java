package com.hbm.entity.mob;

import com.hbm.explosion.ExplosionNukeSmall;
import com.hbm.items.ModItems;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.world.World;

import java.util.List;

public class EntityUAP extends EntityFlying implements IMob {

	public EntityUAP(World p_i1587_1_) {
		super(p_i1587_1_);
		this.setSize(15F, 4F);
		this.isImmuneToFire = true;
		this.experienceValue = 500;
		this.ignoreFrustumCheck = true;
		this.deathTime = -30;
	}

	protected void onDeathUpdate() {

		this.motionY -= 0.05D;

		if(this.deathTime == -10) {
			worldObj.playSoundAtEntity(this, "hbm:entity.chopperDamage", 10.0F, 1.0F);
		}

		if(this.deathTime == 19 && !worldObj.isRemote) {
			//worldObj.newExplosion(this, posX, posY, posZ, 10F, true, true);
			//ExplosionNukeSmall.explode(worldObj, posX, posY, posZ, ExplosionNukeSmall.PARAMS_MEDIUM);

			//List<EntityPlayer> players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, this.boundingBox.expand(200, 200, 200));

			//for(EntityPlayer player : players) {
			//	//player.triggerAchievement(MainRegistry.bossUFO);
			//	//player.inventory.addItemStackToInventory(new ItemStack(ModItems.coin_ufo));
			//}
		}

		//whatever ill do something with this at some point

		super.onDeathUpdate();
	}

	@Override
	protected float getSoundVolume() {
		return 10.0F;
	}

	@Override
	protected String getHurtSound() {
		return "hbm:sounds/block/vaultScrape";
	}
	//dammit how does this stupid fuck get his directories

	@Override
	protected String getDeathSound() {
		return null;
	}

	@Override
	protected void entityInit() {
		this.dataWatcher.addObject(12, 1);
		this.dataWatcher.addObject(13, 0);
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound p_70014_1_) {
		super.writeEntityToNBT(p_70014_1_);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound p_70037_1_) {
		super.readEntityFromNBT(p_70037_1_);
	}

	public void setWaypoint(int x, int y, int z) {
		this.dataWatcher.updateObject(17, x);
		this.dataWatcher.updateObject(18, y);
		this.dataWatcher.updateObject(19, z);
	}

	public int getX() {
		return this.dataWatcher.getWatchableObjectInt(17);
	}

	public int getY() {
		return this.dataWatcher.getWatchableObjectInt(18);
	}

	public int getZ() {
		return this.dataWatcher.getWatchableObjectInt(19);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean isInRangeToRenderDist(double distance) {
		return distance < 500000;
	}

}
