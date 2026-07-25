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
		int[] opening = type == BurstType.SUBSURFACE ? findExistingOpenPath(world, blockX, MathHelper.floor_double(y), blockZ, (int)surfaceY, Math.min(48, Math.max(8, (int)Math.ceil(baseRadius * 0.6D)))) : null;
		boolean vented = type == BurstType.SUBSURFACE && opening != null;
		boolean contained = type == BurstType.SUBSURFACE && !vented;
		NuclearDetonationSpec spec = NuclearDetonationSpec.fromLegacyRadius(radius);
		spec.burstType = type; spec.burstHeight = burstHeight; spec.groundCoupling = coupling; spec.burialDepth = burialDepth; spec.predictedBreakthroughFactor = breakthrough; spec.actualSurfaceBreach = type != BurstType.SUBSURFACE || vented; spec.atmosphericReleaseFactor = type == BurstType.SUBSURFACE ? (vented ? Math.max(0.15D, breakthrough) : 0D) : 1D; spec.surfaceDeformationFactor = breakthrough; spec.contained = contained; spec.vented = vented;
		spec.breachX = opening == null ? blockX : opening[0]; spec.breachY = opening == null ? (int)surfaceY : opening[1]; spec.breachZ = opening == null ? blockZ : opening[2];
		return new NuclearBurstContext(radius, yieldKt, type, surfaceY, burstHeight, fireballRadius, coupling, burialDepth, breakthrough, spec.actualSurfaceBreach, spec.atmosphericReleaseFactor, spec.surfaceDeformationFactor, contained, vented, spec.breachX, spec.breachY, spec.breachZ, NuclearEffectsSolver.solve(spec));
	}

	/** Bounded horizontal search for a pre-existing air shaft whose column is open to the sky. */
	private static int[] findExistingOpenPath(World world, int x, int y, int z, int surfaceY, int radius) {
		int minY = Math.max(1, y - 2), maxY = Math.min(255, surfaceY + 2), budget = 32768;
		java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<int[]>(); java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
		if(world.isAirBlock(x,y,z)) queue.add(new int[] { x, y, z });
		else { queue.add(new int[] {x+1,y,z}); queue.add(new int[] {x-1,y,z}); queue.add(new int[] {x,y+1,z}); queue.add(new int[] {x,y-1,z}); queue.add(new int[] {x,y,z+1}); queue.add(new int[] {x,y,z-1}); }
		while(!queue.isEmpty() && budget-- > 0) { int[] p = queue.removeFirst(); int dx = p[0] - x, dz = p[2] - z; if(Math.abs(dx) > radius || Math.abs(dz) > radius || p[1] < minY || p[1] > maxY) continue; long key = ((long)(p[0] & 0x3FFFFFF) << 38) | ((long)(p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFF); if(!seen.add(key) || !world.isAirBlock(p[0], p[1], p[2])) continue; if(world.canBlockSeeTheSky(p[0], p[1], p[2])) return new int[] { p[0], p[1], p[2] }; queue.add(new int[] {p[0]+1,p[1],p[2]}); queue.add(new int[] {p[0]-1,p[1],p[2]}); queue.add(new int[] {p[0],p[1]+1,p[2]}); queue.add(new int[] {p[0],p[1]-1,p[2]}); queue.add(new int[] {p[0],p[1],p[2]+1}); queue.add(new int[] {p[0],p[1],p[2]-1}); }
		return null;
	}

	/** Re-runs the same capped connectivity test after incremental terrain removal. */
	public static int[] confirmSurfaceBreach(World world, double x, double y, double z, double surfaceY, double cavityRadius) {
		return findExistingOpenPath(world, MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z), (int)surfaceY, Math.min(96, Math.max(8, (int)Math.ceil(cavityRadius))));
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
