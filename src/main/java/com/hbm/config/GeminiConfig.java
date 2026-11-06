package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GeminiConfig {

	public static final String CATEGORY = "21_gemini";
	public static boolean enabled = true;
	public static int minEventIntervalSeconds = 1200;
	public static int maxEventIntervalSeconds = 2400;
	public static int woodcrawlerDetectionRange = 32;
	public static int woodcrawlerStalkSeconds = 6;
	public static int woodcrawlerMemorySeconds = 20;
	public static int woodcrawlerSpawnWeight = 2;
	public static int woodcrawlerMaxLoaded = 8;

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
		woodcrawlerDetectionRange = config.getInt("woodcrawlerDetectionRange", CATEGORY, 32, 8, 48,
				"Woodcrawler player/villager detection radius in blocks. Also bounds native path searches.");
		woodcrawlerStalkSeconds = config.getInt("woodcrawlerStalkSeconds", CATEGORY, 6, 1, 30,
				"Minimum silent stalking time before a Woodcrawler can sprint and attack.");
		woodcrawlerMemorySeconds = config.getInt("woodcrawlerMemorySeconds", CATEGORY, 20, 2, 60,
				"How long a Woodcrawler remembers the last observed target position after losing sight.");
		woodcrawlerSpawnWeight = config.getInt("woodcrawlerSpawnWeight", CATEGORY, 2, 0, 20,
				"Forest monster spawn weight after Mars. Zero disables both natural and invasion Woodcrawler spawns.");
		woodcrawlerMaxLoaded = config.getInt("woodcrawlerMaxLoaded", CATEGORY, 8, 0, 64,
				"Maximum loaded Woodcrawlers for admitting new spawns in the Overworld. Zero disables new spawns; saved creatures are retained.");
	}
}
