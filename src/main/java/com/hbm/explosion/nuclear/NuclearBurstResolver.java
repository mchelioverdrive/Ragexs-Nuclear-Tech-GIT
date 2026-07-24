package com.hbm.explosion.nuclear;

import com.hbm.config.BombConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Deterministic, lightweight RNT-side classification of a legacy-radius detonation. */
public final class NuclearBurstResolver {
	public static final double FIREBALL_RADIUS_FACTOR = 0.35D;
	private NuclearBurstResolver() { }

	public static NuclearBurstContext resolve(World world, double x, double y, double z, int legacyRadius) {
		int radius = Math.max(1, legacyRadius);
		double yieldKt = BombConfig.ktFromRadius(radius);
		double baseRadius = BombConfig.radiusFromKt((float) yieldKt);
		int blockX = MathHelper.floor_double(x);
		int blockZ = MathHelper.floor_double(z);
		double surfaceY = world.getHeightValue(blockX, blockZ);
		double burstHeight = y - surfaceY;
		double fireballRadius = baseRadius * FIREBALL_RADIUS_FACTOR;
		double bottom = y - fireballRadius;
		double coupling = bottom > surfaceY ? 0D : clamp((surfaceY - bottom) / Math.max(1D, fireballRadius), 0D, 1D);
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(world, CBT_Atmosphere.class);
		BurstType type;
		if(CelestialBody.inOrbit(world) || atmosphere == null || atmosphere.getPressure() < 0.01D) type = BurstType.VACUUM;
		else if(world.getBlock(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)).getMaterial().isLiquid()) type = BurstType.UNDERWATER;
		else if(y < surfaceY) type = BurstType.SUBSURFACE;
		else if(coupling == 0D) type = BurstType.AIR;
		else type = BurstType.SURFACE;
		NuclearDetonationSpec spec = NuclearDetonationSpec.fromLegacyRadius(radius);
		spec.burstType = type; spec.burstHeight = burstHeight; spec.groundCoupling = coupling;
		return new NuclearBurstContext(radius, yieldKt, type, surfaceY, burstHeight, fireballRadius, coupling, NuclearEffectsSolver.solve(spec));
	}

	private static double clamp(double value, double minimum, double maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
