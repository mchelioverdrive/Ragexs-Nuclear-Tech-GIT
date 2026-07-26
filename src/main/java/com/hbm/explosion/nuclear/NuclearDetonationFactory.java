package com.hbm.explosion.nuclear;

import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;

import net.minecraft.world.World;

/** Authoritative creation path for ordinary MK5 physical and Torex visual effects. */
public final class NuclearDetonationFactory {
	private NuclearDetonationFactory() { }

	public static NuclearDetonationResult detonate(World world, double x, double y, double z, int radius, NuclearDetonationOptions options) {
		if(options == null) options = NuclearDetonationOptions.standard();
		NuclearBurstContext context = NuclearBurstResolver.resolve(world, x, y, z, Math.max(1, radius));
		EntityNukeExplosionMK5 explosion = null;
		EntityNukeTorex hypocenter = null;
		EntityNukeTorex surface = null;
		if(options.spawnExplosion) {
			explosion = EntityNukeExplosionMK5.fromBurstContext(world, x, y, z, context)
				.setFallout(options.fallout).setSalted(options.salted).setRadiation(options.radiation).moreFallout(options.additionalFallout);
			world.spawnEntityInWorld(explosion);
		}
		if(options.spawnVisual) {
			hypocenter = EntityNukeTorex.fromBurstContext(world, x, y, z, context, 1F, false);
			if(context.burstType == BurstType.UNDERWATER && options.spawnSurfacePlume && context.surfaceInteractionFactor > 0D) {
				surface = EntityNukeTorex.fromBurstContext(world, x, context.waterSurfaceY + 0.5D, z, context, (float)context.surfaceInteractionFactor, true);
			}
		}
		return new NuclearDetonationResult(context, explosion, hypocenter, surface);
	}
}
