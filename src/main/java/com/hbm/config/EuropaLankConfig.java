package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class EuropaLankConfig {

	private static final String CATEGORY = CommonConfig.CATEGORY_MOBS + ".europaLank";
	public static Values settings;

	private EuropaLankConfig() { }

	public static final class Values {
		public final boolean enabled;
		public final boolean requireExcavation;
		public final int excavationThreshold;
		public final int minimumCooldownTicks;
		public final int maxActivePerDimension;
		public final int minimumStalkingDistance;
		public final int maximumStalkingDistance;
		public final int retreatDistance;
		public final boolean passiveStalking;

		public Values(Configuration config) {
			enabled = config.getBoolean("enabled", CATEGORY, true, "Allow new natural Europa Lank encounters.");
			requireExcavation = config.getBoolean("requireExcavation", CATEGORY, true, "Gate natural encounters behind shared Europa excavation.");
			excavationThreshold = config.getInt("excavationThreshold", CATEGORY, 64, 1, Integer.MAX_VALUE,
					"Natural terrain blocks excavated before the permanent unlock.");
			minimumCooldownTicks = config.getInt("minimumCooldownTicks", CATEGORY, 48000, 0, Integer.MAX_VALUE,
					"Minimum game ticks after a natural encounter starts or ends. A running cooldown keeps its deadline.");
			maxActivePerDimension = config.getInt("maxActivePerDimension", CATEGORY, 1, 0, 64,
					"Maximum simultaneous natural encounters. Zero disables natural spawning.");
			minimumStalkingDistance = config.getInt("minimumStalkingDistance", CATEGORY, 32, 8, 95,
					"Nearest preferred observation distance in blocks.");
			maximumStalkingDistance = config.getInt("maximumStalkingDistance", CATEGORY, 64, minimumStalkingDistance + 1, 96,
					"Farthest preferred observation distance in blocks. Must exceed the minimum.");
			retreatDistance = config.getInt("retreatDistance", CATEGORY, 12, 1, minimumStalkingDistance - 1,
					"Player proximity that triggers retreat in blocks. Must be below the minimum stalking distance.");
			passiveStalking = config.getBoolean("passiveStalking", CATEGORY, true,
					"Use passive stalking. Turning this off restores the legacy hunting AI.");
		}
	}
}
