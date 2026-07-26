package com.hbm.explosion.nuclear;

/** Immutable RNT-only description shared by the independent legacy factories. */
public final class NuclearBurstContext {
	public final int legacyRadius;
	public final double yieldKt;
	public final BurstType burstType;
	public final double surfaceY;
	public final double burstHeight;
	public final double fireballRadius;
	public final double groundCoupling;
	public final double waterSurfaceY, waterDepth, seabedY, distanceFromSeabed, surfaceInteractionFactor;
	public final double burialDepth;
	public final double predictedBreakthroughFactor;
	/** @deprecated compatibility alias for predictedBreakthroughFactor. */
	public final double surfaceBreakthroughFactor;
	public final boolean actualSurfaceBreach;
	public final double atmosphericReleaseFactor;
	public final double surfaceDeformationFactor;
	public final boolean contained;
	public final boolean vented;
	public final int breachX, breachY, breachZ;
	public final NuclearEffectsProfile effects;

	public NuclearBurstContext(int legacyRadius, double yieldKt, BurstType burstType, double surfaceY, double burstHeight, double fireballRadius, double groundCoupling, double waterSurfaceY, double waterDepth, double seabedY, double distanceFromSeabed, double surfaceInteractionFactor, double burialDepth, double predictedBreakthroughFactor, boolean actualSurfaceBreach, double atmosphericReleaseFactor, double surfaceDeformationFactor, boolean contained, boolean vented, int breachX, int breachY, int breachZ, NuclearEffectsProfile effects) {
		this.legacyRadius = legacyRadius; this.yieldKt = yieldKt; this.burstType = burstType; this.surfaceY = surfaceY;
		this.burstHeight = burstHeight; this.fireballRadius = fireballRadius; this.groundCoupling = groundCoupling; this.effects = effects;
		this.waterSurfaceY = waterSurfaceY; this.waterDepth = waterDepth; this.seabedY = seabedY; this.distanceFromSeabed = distanceFromSeabed; this.surfaceInteractionFactor = surfaceInteractionFactor;
		this.burialDepth = burialDepth; this.predictedBreakthroughFactor = predictedBreakthroughFactor; this.surfaceBreakthroughFactor = predictedBreakthroughFactor;
		this.actualSurfaceBreach = actualSurfaceBreach; this.atmosphericReleaseFactor = atmosphericReleaseFactor; this.surfaceDeformationFactor = surfaceDeformationFactor; this.contained = contained; this.vented = vented;
		this.breachX = breachX; this.breachY = breachY; this.breachZ = breachZ;
	}
}
