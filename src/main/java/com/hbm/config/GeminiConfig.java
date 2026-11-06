package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GeminiConfig {

	public static final String CATEGORY = "21_gemini";
	public static boolean enabled = true;
	public static int minEventIntervalSeconds = 1200;
	public static int maxEventIntervalSeconds = 2400;

	private GeminiConfig() { }

	public static void loadFromConfig(Configuration config) {
		enabled = config.getBoolean("enabled", CATEGORY, true,
				"Enable Gemini activity after a player visits Mars (Duna). Disabling pauses scheduling; the world keeps its Mars milestone.");
		minEventIntervalSeconds = config.getInt("minEventIntervalSeconds", CATEGORY, 1200, 1, 86400,
				"Minimum active Overworld seconds before the first and each subsequent Gemini event opportunity (20 ticks per second).");
		maxEventIntervalSeconds = config.getInt("maxEventIntervalSeconds", CATEGORY, 2400, 1, 86400,
				"Maximum interval in active Overworld seconds. Raised to the minimum if smaller. Scheduling pauses without Overworld players.");
		if(maxEventIntervalSeconds < minEventIntervalSeconds) {
			maxEventIntervalSeconds = minEventIntervalSeconds;
			config.get(CATEGORY, "maxEventIntervalSeconds", 2400).set(maxEventIntervalSeconds);
		}
	}
}
