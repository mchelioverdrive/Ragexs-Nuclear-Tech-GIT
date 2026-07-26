package com.hbm.explosion.nuclear;

import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;

/** Entities and the single resolved context produced by an ordinary detonation. */
public final class NuclearDetonationResult {
	public final NuclearBurstContext context;
	public final EntityNukeExplosionMK5 explosion;
	public final EntityNukeTorex hypocenterVisual;
	public final EntityNukeTorex surfaceVisual;

	NuclearDetonationResult(NuclearBurstContext context, EntityNukeExplosionMK5 explosion, EntityNukeTorex hypocenterVisual, EntityNukeTorex surfaceVisual) {
		this.context = context; this.explosion = explosion; this.hypocenterVisual = hypocenterVisual; this.surfaceVisual = surfaceVisual;
	}
}
