package com.hbm.explosion.nuclear;

import com.hbm.config.BombConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.trait.CBT_Atmosphere;

import net.minecraft.util.MathHelper;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
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
		double burialDepth = type == BurstType.SUBSURFACE ? Math.max(0D, surfaceY - y) : 0D;
		double breakthrough = type == BurstType.SUBSURFACE ? calculateBreakthrough(world, blockX, MathHelper.floor_double(y), blockZ, (int)surfaceY, baseRadius) : (type == BurstType.SURFACE ? 1D : 0D);
		boolean vented = type == BurstType.SUBSURFACE && breakthrough > 0.001D;
		boolean contained = type == BurstType.SUBSURFACE && !vented;
		NuclearDetonationSpec spec = NuclearDetonationSpec.fromLegacyRadius(radius);
		spec.burstType = type; spec.burstHeight = burstHeight; spec.groundCoupling = coupling; spec.burialDepth = burialDepth; spec.surfaceBreakthroughFactor = breakthrough; spec.contained = contained; spec.vented = vented;
		spec.breachX = blockX; spec.breachY = (int)surfaceY; spec.breachZ = blockZ;
		return new NuclearBurstContext(radius, yieldKt, type, surfaceY, burstHeight, fireballRadius, coupling, burialDepth, breakthrough, contained, vented, blockX, (int)surfaceY, blockZ, NuclearEffectsSolver.solve(spec));
	}

	/** Yield-scaled excavation reach reduced by the actual vertical overburden resistance. Open shafts vent readily. */
	private static double calculateBreakthrough(World world, int x, int y, int z, int surfaceY, double baseRadius) {
		double reach = Math.max(1D, baseRadius * 0.55D);
		double cost = 0D;
		boolean openPath = true;
		for(int yy = y + 1; yy < surfaceY; yy++) {
			Block block = world.getBlock(x, yy, z);
			if(block == Blocks.air) continue;
			openPath = false;
			if(block.getMaterial().isLiquid()) cost += 0.25D;
			else cost += 1D + Math.min(8D, Math.max(0D, block.getExplosionResistance(null)) / 12D);
		}
		if(openPath) return 1D;
		return clamp((reach - cost) / Math.max(1D, reach * 0.65D), 0D, 1D);
	}

	private static double clamp(double value, double minimum, double maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
