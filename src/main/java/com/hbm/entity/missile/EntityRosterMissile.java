package com.hbm.entity.missile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.explosion.ExplosionChaos;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.explosion.ExplosionNT;
import com.hbm.explosion.ExplosionNT.ExAttrib;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.MissileDefinition;
import com.hbm.items.weapon.MissilePayload;
import com.hbm.particle.helper.ExplosionCreator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Fixed-roster missiles share the existing block-coordinate guidance and chunk lifecycle. */
public class EntityRosterMissile extends EntityMissileBaseNT {
	private static final int WATCHER_MISSILE_ID = 4;
	private boolean siloPayload;

	public EntityRosterMissile(World world) {
		super(world);
	}

	public EntityRosterMissile(World world, float x, float y, float z, int targetX, int targetZ,
			MissileDefinition missile) {
		super(world, x, y, z, targetX, targetZ);
		setDefinition(missile);
		startX = MathHelper.floor_float(x);
		startZ = MathHelper.floor_float(z);
		double distance = Math.max(8D, Math.hypot((double) targetX - startX, (double) targetZ - startZ));
		// Lower arcs trade altitude for horizontal acceleration while using the same block target.
		switch(missile.classification) {
		case CRUISE: motionY = 1D; break;
		case HYPERSONIC: motionY = 1.35D; break;
		default: break;
		}
		decelY = 2D / distance;
		accelXZ = 4D / (distance * motionY * motionY);
		health = 35 + missile.generation.ordinal() * 10;
	}

	@Override
	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(WATCHER_MISSILE_ID, 0);
	}

	public MissileDefinition getDefinition() {
		return MissileDefinition.byId(this.dataWatcher.getWatchableObjectInt(WATCHER_MISSILE_ID));
	}

	private void setDefinition(MissileDefinition missile) {
		this.dataWatcher.updateObject(WATCHER_MISSILE_ID, missile == null ? 0 : missile.id);
		this.isCluster = missile != null && missile.payload == MissilePayload.CLUSTER;
	}

	public void useSiloPayload() {
		this.siloPayload = true;
	}

	@Override
	public void onUpdate() {
		if(!worldObj.isRemote && getDefinition() == null) {
			setDead();
			return;
		}
		super.onUpdate();
	}

	@Override
	protected double getFlightSpeed() {
		MissileDefinition missile = getDefinition();
		return missile == null ? super.getFlightSpeed() : missile.flightSpeed;
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
	public void onImpact() {
		MissileDefinition missile = getDefinition();
		if(worldObj.isRemote || missile == null) return;
		int power = missile.payloadPower;

		switch(missile.payload) {
		case HE:
		case INCENDIARY:
			explodeStandard(power, power < 25 ? 24 : power < 40 ? 32 : 48, missile.payload == MissilePayload.INCENDIARY);
			if(power >= 40) ExplosionCreator.composeEffectLarge(worldObj, posX, posY, posZ);
			else if(power >= 25) ExplosionCreator.composeEffectStandard(worldObj, posX, posY, posZ);
			else ExplosionCreator.composeEffectSmall(worldObj, posX, posY, posZ);
			if(missile.payload == MissilePayload.INCENDIARY && power >= 40) {
				ExplosionChaos.burn(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ), 10);
			}
			break;
		case CLUSTER:
			worldObj.createExplosion(this, posX, posY, posZ, power / 5F, true);
			ExplosionChaos.cluster(worldObj, MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ), power, 100);
			break;
		case PENETRATOR:
			for(int depth = 0; depth < power && posY - depth >= 0; depth++) {
				ExplosionNT explosion = new ExplosionNT(worldObj, this, posX, posY - depth, posZ, power >= 20 ? 10F : 5F);
				explosion.addAllAttrib(ExAttrib.ERRODE);
				explosion.explode();
			}
			ExplosionLarge.spawnParticles(worldObj, posX, posY, posZ, power);
			ExplosionLarge.spawnShrapnels(worldObj, posX, posY, posZ, power / 2);
			break;
		case NUCLEAR:
		case THERMONUCLEAR:
			int radius = siloPayload ? BombConfig.missileRadius : missile == MissileDefinition.SARMAT
					? Math.round(power * (BombConfig.missileRadius / (float) BombConfig.radiusFromKt(100F))) : power;
			EntityNukeExplosionMK5 explosion = EntityNukeExplosionMK5.statFac(worldObj, Math.max(1, radius), posX, posY, posZ);
			if(siloPayload || missile == MissileDefinition.SARMAT) explosion.moreFallout(100);
			worldObj.spawnEntityInWorld(explosion);
			EntityNukeTorex.statFac(worldObj, posX, posY, posZ, Math.max(1, radius));
			break;
		default:
			break;
		}
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
		nbt.setInteger("missileHealth", health);
		nbt.setByte("launchFacing", this.dataWatcher.getWatchableObjectByte(3));
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound nbt) {
		super.readEntityFromNBT(nbt);
		setDefinition(MissileDefinition.byName(nbt.getString("missile")));
		siloPayload = nbt.getBoolean("siloPayload");
		if(nbt.hasKey("missileHealth", 99)) health = nbt.getInteger("missileHealth");
		if(nbt.hasKey("launchFacing", 99)) this.dataWatcher.updateObject(3, nbt.getByte("launchFacing"));
	}
}
