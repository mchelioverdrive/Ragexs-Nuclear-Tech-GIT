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
	public static int fakePersonMaxLoaded = 16;
	public static float fakePersonConversionChance = 0.35F;
	public static boolean deepRootEnabled = true;
	public static final int[] deepRootStageSeconds = {300, 600, 600, 600, 600};
	public static float deepRootPlayerTransmissionChance = 0.25F;
	public static float deepRootMobTransmissionChance = 0.4F;

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
		fakePersonMaxLoaded = config.getInt("fakePersonMaxLoaded", CATEGORY, 16, 0, 128,
				"Maximum loaded Fake People for admitting new spawns/conversions. Zero disables new ones; saved creatures are retained. No natural or invasion spawns are added.");
		fakePersonConversionChance = config.getFloat("fakePersonConversionChance", CATEGORY, 0.35F, 0F, 1F,
				"One saved probability roll per Woodcrawler captive after 30 seconds. Failed rolls keep the original captive; successful rolls wait for population space and a valid spawn.");
		String disease = CATEGORY + ".deepRoot";
		deepRootEnabled = config.getBoolean("enabled", disease, true,
				"Enable Deep Root Disease after Mars in the Overworld. Disabling pauses saved progress and removes its symptoms; no offline or unloaded catch-up.");
		String[] stages = {"exposure", "roots", "contraction", "bulbs", "sprouts"};
		int[] defaults = {300, 600, 600, 600, 600};
		for(int i = 0; i < stages.length; i++)
			deepRootStageSeconds[i] = config.getInt(stages[i] + "Seconds", disease, defaults[i], 1, 604800,
					"Loaded, active seconds spent in this stage before advancing (20 ticks per second). Snapshotted for each new infection. Terminal lasts indefinitely without conversion.");
		deepRootPlayerTransmissionChance = config.getFloat("playerTransmissionChance", disease, 0.25F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible player.");
		deepRootMobTransmissionChance = config.getFloat("mobTransmissionChance", disease, 0.4F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible villager or animal.");
	}
}
