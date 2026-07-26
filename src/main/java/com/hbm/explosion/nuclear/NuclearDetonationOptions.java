package com.hbm.explosion.nuclear;

/** Optional policy knobs for the one ordinary MK5/Torex detonation path. */
public final class NuclearDetonationOptions {
	public boolean fallout = true;
	public boolean salted;
	public boolean radiation = true;
	public int additionalFallout;
	public boolean spawnExplosion = true;
	public boolean spawnVisual = true;
	public boolean spawnSurfacePlume = true;

	public static NuclearDetonationOptions standard() { return new NuclearDetonationOptions(); }
	public NuclearDetonationOptions fallout(boolean enabled) { fallout = enabled; return this; }
	public NuclearDetonationOptions salted(boolean enabled) { salted = enabled; return this; }
	public NuclearDetonationOptions radiation(boolean enabled) { radiation = enabled; return this; }
	public NuclearDetonationOptions additionalFallout(int amount) { additionalFallout = amount; return this; }
	public NuclearDetonationOptions explosion(boolean enabled) { spawnExplosion = enabled; return this; }
	public NuclearDetonationOptions visual(boolean enabled) { spawnVisual = enabled; return this; }
	public NuclearDetonationOptions surfacePlume(boolean enabled) { spawnSurfacePlume = enabled; return this; }
}
