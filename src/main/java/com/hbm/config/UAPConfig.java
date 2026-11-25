package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class UAPConfig {

	private static final String CATEGORY = CommonConfig.CATEGORY_MOBS + ".uap";
	public static Values settings;

	private UAPConfig() { }

	public static final class Values {
		public final boolean enabled;
		public final boolean requireMoonExploration;
		public final boolean requireNuclearDetonation;
		public final int initialDelayTicks;
		public final int minimumCooldownTicks;
		public final int maxActivePerDimension;
		public final boolean preferNighttime;

		public Values(Configuration config) {
			enabled = config.getBoolean("enabled", CATEGORY, true, "Allow new natural UAP encounters in the Overworld.");
			requireMoonExploration = config.getBoolean("requireMoonExploration", CATEGORY, true,
					"Require a player to visit the Moon (Mun) before natural encounters.");
			requireNuclearDetonation = config.getBoolean("requireNuclearDetonation", CATEGORY, true,
					"Require a nuclear weapon to detonate in the Overworld before natural encounters.");
			initialDelayTicks = config.getInt("initialDelayTicks", CATEGORY, 24000, 0, Integer.MAX_VALUE,
					"Game ticks after the configured prerequisites are first met. A pending delay keeps its original value.");
			minimumCooldownTicks = config.getInt("minimumCooldownTicks", CATEGORY, 72000, 0, Integer.MAX_VALUE,
					"Minimum Overworld game ticks between successful natural encounters.");
			maxActivePerDimension = config.getInt("maxActivePerDimension", CATEGORY, 1, 0, Integer.MAX_VALUE,
					"Maximum loaded UAPs for natural spawn admission. Zero disables natural spawning. Manual spawns bypass admission.");
			preferNighttime = config.getBoolean("preferNighttime", CATEGORY, true,
					"Reduce the chance of a natural encounter outside nighttime.");
		}
	}
}
