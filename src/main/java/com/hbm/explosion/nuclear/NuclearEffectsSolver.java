package com.hbm.explosion.nuclear;

import com.hbm.config.BombConfig;

/** Separates gameplay effects while retaining BombConfig's cube-root baseline. */
public final class NuclearEffectsSolver {
	private NuclearEffectsSolver() { }

	public static NuclearEffectsProfile solve(NuclearDetonationSpec spec) {
		NuclearEffectsProfile profile = new NuclearEffectsProfile();
		double base = BombConfig.radiusFromKt((float)Math.max(spec.yieldKt, 0.001D));
		double atmosphere = spec.burstType == BurstType.VACUUM ? 0.0D : 1.0D;
		profile.fireballRadius = base * 0.35D;
		profile.severeBlastRadius = base * 0.70D * atmosphere;
		profile.moderateBlastRadius = base * (spec.burstType == BurstType.AIR ? 1.75D : 1.35D) * atmosphere;
		profile.lightBlastRadius = base * 2.0D * atmosphere;
		profile.thermalRadius = base * 1.55D * spec.thermalFraction / 0.35D;
		profile.promptRadiationRadius = base * 0.85D * spec.fissionFraction;
		profile.craterRadius = base * 0.42D * spec.groundCoupling;
		profile.craterDepth = profile.craterRadius * 0.30D;
		profile.falloutSourceStrength = spec.createsFallout ? spec.yieldKt * spec.fissionFraction * spec.groundCoupling : 0.0D;
		profile.visualScale = Math.max(1.0D, base / 48.0D);
		profile.cloudTopHeight = base * 2.5D;
		if(spec.burstType == BurstType.AIR) {
			profile.craterRadius = 0;
			profile.craterDepth = 0;
			// Airbursts entrain far less soil than surface bursts, but atmospheric
			// fission products still produce a deliberately small local fallout source.
			profile.falloutSourceStrength = spec.createsFallout ? spec.yieldKt * spec.fissionFraction * 0.08D : 0.0D;
		}
		if(spec.burstType == BurstType.SUBSURFACE) { profile.thermalRadius *= 0.35D; profile.moderateBlastRadius *= 0.55D; }
		if(spec.burstType == BurstType.UNDERWATER) { profile.thermalRadius *= 0.1D; profile.craterRadius *= 0.5D; }
		if(spec.burstType == BurstType.VACUUM) { profile.severeBlastRadius = profile.moderateBlastRadius = profile.lightBlastRadius = 0; profile.falloutSourceStrength = 0; }
		return profile;
	}
}
