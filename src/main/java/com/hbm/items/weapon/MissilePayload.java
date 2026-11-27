package com.hbm.items.weapon;

import java.util.Locale;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.explosion.ExplosionChaos;
import com.hbm.explosion.ExplosionLarge;
import com.hbm.explosion.ExplosionNT;
import com.hbm.explosion.ExplosionNT.ExAttrib;
import com.hbm.items.ModItems;
import com.hbm.particle.helper.ExplosionCreator;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** The station fits a standard in-game warhead selected independently of the airframe's identity. */
public enum MissilePayload {
	HE, INCENDIARY, CLUSTER, PENETRATOR, NUCLEAR, THERMONUCLEAR, INTERCEPTOR;

	public void impact(Entity source, int power, boolean extraFallout) {
		World world = source.worldObj;
		if(world.isRemote) return;
		double x = source.posX, y = source.posY, z = source.posZ;
		int blockX = MathHelper.floor_double(x), blockY = MathHelper.floor_double(y), blockZ = MathHelper.floor_double(z);
		switch(this) {
		case HE:
		case INCENDIARY:
			EntityMissileBaseNT.explodeStandard(world, x, y, z, power, power < 25 ? 24 : power < 40 ? 32 : 48, this == INCENDIARY);
			if(power >= 40) ExplosionCreator.composeEffectLarge(world, x, y, z);
			else if(power >= 25) ExplosionCreator.composeEffectStandard(world, x, y, z);
			else ExplosionCreator.composeEffectSmall(world, x, y, z);
			if(this == INCENDIARY && power >= 40) ExplosionChaos.burn(world, blockX, blockY, blockZ, 10);
			break;
		case CLUSTER:
			world.createExplosion(source, x, y, z, power / 5F, true);
			ExplosionChaos.cluster(world, blockX, blockY, blockZ, power, 100);
			break;
		case PENETRATOR:
			for(int depth = 0; depth < power && y - depth >= 0; depth++) {
				ExplosionNT explosion = new ExplosionNT(world, source, x, y - depth, z, power >= 20 ? 10F : 5F);
				explosion.addAllAttrib(ExAttrib.ERRODE);
				explosion.explode();
			}
			ExplosionLarge.spawnParticles(world, x, y, z, power);
			ExplosionLarge.spawnShrapnels(world, x, y, z, power / 2);
			break;
		case NUCLEAR:
		case THERMONUCLEAR:
			int radius = Math.max(1, power);
			EntityNukeExplosionMK5 explosion = EntityNukeExplosionMK5.statFac(world, radius, x, y, z);
			if(extraFallout) explosion.moreFallout(100);
			world.spawnEntityInWorld(explosion);
			EntityNukeTorex.statFac(world, x, y, z, radius);
			break;
		case INTERCEPTOR:
			break;
		}
	}

	public static int separatedPower(int fullPower, int count) {
		return Math.max(1, Math.round(fullPower * BombConfig.mirvWarheadPowerPercent / (100F * (count + 1))));
	}

	public Item component(int size) {
		switch(this) {
		case INCENDIARY:
			return bySize(size, ModItems.warhead_incendiary_small, ModItems.warhead_incendiary_medium, ModItems.warhead_incendiary_large);
		case CLUSTER:
			return bySize(size, ModItems.warhead_cluster_small, ModItems.warhead_cluster_medium, ModItems.warhead_cluster_large);
		case PENETRATOR:
			return bySize(size, ModItems.warhead_buster_small, ModItems.warhead_buster_medium, ModItems.warhead_buster_large);
		case NUCLEAR:
			return ModItems.warhead_nuclear;
		case THERMONUCLEAR:
			return ModItems.warhead_mirv;
		case INTERCEPTOR:
			return ModItems.warhead_generic_small;
		case HE:
			return bySize(size, ModItems.warhead_generic_small, ModItems.warhead_generic_medium, ModItems.warhead_generic_large);
		default:
			throw new AssertionError(this);
		}
	}

	private static Item bySize(int size, Item small, Item medium, Item large) {
		return size == 0 ? small : size == 1 ? medium : large;
	}

	public String translationKey() {
		return "missile.payload." + name().toLowerCase(Locale.ROOT);
	}
}
