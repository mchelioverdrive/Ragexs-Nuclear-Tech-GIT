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
	public final NuclearEffectsProfile effects;

	public NuclearBurstContext(int legacyRadius, double yieldKt, BurstType burstType, double surfaceY, double burstHeight, double fireballRadius, double groundCoupling, NuclearEffectsProfile effects) {
		this.legacyRadius = legacyRadius; this.yieldKt = yieldKt; this.burstType = burstType; this.surfaceY = surfaceY;
		this.burstHeight = burstHeight; this.fireballRadius = fireballRadius; this.groundCoupling = groundCoupling; this.effects = effects;
	}
}
