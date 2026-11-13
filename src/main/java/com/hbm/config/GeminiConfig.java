package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GeminiConfig {

	public static final String CATEGORY = "21_gemini";
	public static boolean enabled = true;
	public static int phase2DelayDays = 3, phase3DelayDays = 5, phase4DelayDays = 7;
	public static final int[] phaseEncounterPercent = {25, 50, 75, 100};
	public static int maxLoadedCreatures = 32;
	public static int gardenerMaxLoaded = 2, wretchMaxLoaded = 1;
	public static int minEventIntervalSeconds = 1200;
	public static int maxEventIntervalSeconds = 2400;
	public static int woodcrawlerDetectionRange = 32;
	public static int woodcrawlerStalkSeconds = 6;
	public static int woodcrawlerMemorySeconds = 20;
	public static int woodcrawlerSpawnWeight = 2;
	public static int woodcrawlerMaxLoaded = 8;
	public static int fakePersonMaxLoaded = 16;
	public static float fakePersonConversionChance = 0.80F;
	public static int woodcrawlerConversionSeconds = 30;
	public static boolean deepRootEnabled = true;
	public static final int[] deepRootStageSeconds = {300, 600, 600, 600, 600};
	public static float deepRootPlayerTransmissionChance = 0.25F;
	public static float deepRootMobTransmissionChance = 0.4F;
	public static int mockeryGrowthSeconds = 30;
	public static int mockerySpreadSeconds = 15;
	public static int mockeryRadius = 6;
	public static int mockerySpreadLimit = 64;
	public static int mockeryMaxActive = 16;
	public static int mockeryOffshootLimit = 2;
	public static boolean gardenerEnabled = true;
	public static int gardenerNestChance = 8;
	public static int gardenerNestLimit = 32;
	public static int gardenerSupportLimit = 4;
	public static int gardenerBlocksPerSlam = 12;
	public static boolean oceanGardenerEnabled = true;
	public static int oceanGardenerMaxLoaded = 1, oceanGardenerChance = 32;
	public static boolean wretchEnabled = true;
	public static float wretchHealth = 900F;
	public static int wretchCampChance = 256;
	public static boolean wretchTerrainDestruction = true;
	public static int wretchDetectionRange = 96, wretchHearingRange = 128, wretchTerritoryRadius = 128;
	public static int wretchMemorySeconds = 120, wretchSearchSeconds = 60, wretchQuietSeconds = 240;
	public static int wretchStalkMinSeconds = 20, wretchStalkMaxSeconds = 60;
	public static float wretchWakeThreshold = 12F, wretchStalkSpeed = 0.055F, wretchHuntSpeed = 0.24F, wretchChargeSpeed = 0.48F;
	public static boolean wretchPlayerStructureDamage = false, wretchGeneratedStructureDamage = true, wretchMultipleNatural = false;
	public static int wretchNaturalLimit = 4, wretchBlocksPerStrike = 4, wretchBlocksPerTick = 2;
	public static boolean wretchAssimilation = true, wretchLivestockFeeding = false, wretchHealthAggression = false;
	public static int wretchVoiceSeconds = 45;

	private GeminiConfig() { }

	public static void loadFromConfig(Configuration config) {
		enabled = config.getBoolean("enabled", CATEGORY, true,
				"Enable natural Gemini activity after a player visits Mars (Duna). Disabling pauses scheduling; the world keeps its Mars milestone. Manual spawn eggs bypass this setting.");
		phase2DelayDays = config.getInt("phase2DelayDays", CATEGORY, 3, 1, 10000,
				"Minecraft days in phase 1 before infection unlocks. One day is 24000 enabled Overworld ticks with players present.");
		phase3DelayDays = config.getInt("phase3DelayDays", CATEGORY, 5, 1, 10000,
				"Additional active Minecraft days in phase 2 before underground nests, organic spread and coordination unlock.");
		phase4DelayDays = config.getInt("phase4DelayDays", CATEGORY, 7, 1, 10000,
				"Additional active Minecraft days in phase 3 before Wretch camps unlock. Unlocked phases never regress.");
		for(int i = 0; i < 4; i++) phaseEncounterPercent[i] = config.getInt("phase" + (i + 1) + "EncounterPercent",
				CATEGORY, new int[] {25, 50, 75, 100}[i], 1, 100,
				"Percent of Woodcrawler natural spawn attempts admitted. Event intervals scale by phase-1 percent / current percent; existing countdowns are retained.");
		maxLoadedCreatures = config.getInt("maxLoadedCreatures", CATEGORY, 32, 0, 256,
				"Combined loaded Gemini creature admission cap, excluding disposable boss parts. Zero stops natural admissions; manual spawn eggs bypass the cap and saved creatures are always retained.");
		minEventIntervalSeconds = config.getInt("minEventIntervalSeconds", CATEGORY, 1200, 1, 86400,
				"Minimum phase-1 active Overworld seconds between Gemini event opportunities. Later phases scale by relative encounter percent (20 ticks per second).");
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
				"Maximum loaded Woodcrawlers for admitting natural spawns in the Overworld. Zero disables natural spawns; manual spawn eggs bypass the cap and saved creatures are retained.");
		fakePersonMaxLoaded = config.getInt("fakePersonMaxLoaded", CATEGORY, 16, 0, 128,
				"Maximum loaded Fake People for admitting conversions. Zero disables conversions; manual spawn eggs bypass the cap and saved creatures are retained. No independent natural or invasion spawns are added.");
		fakePersonConversionChance = config.getFloat("fakePersonConversionChance", CATEGORY, 0.80F, 0F, 1F,
				"Saved conversion outcome: Fake Person probability; the remainder produces a Woodcrawler (default 80/20). Population-limited cocoons wait without rerolling. Previous defaults 0.35/0.90 migrate once to 0.80; other custom values are retained.");
		int conversionDefaultsVersion = config.getInt("woodcrawlerConversionDefaultsVersion", CATEGORY, 0, 0, 1,
				"Internal one-time conversion-default migration. Saved cocoon decisions are never changed.");
		if(conversionDefaultsVersion == 0) {
			if(fakePersonConversionChance == 0.35F || fakePersonConversionChance == 0.90F) {
				fakePersonConversionChance = 0.80F;
				config.get(CATEGORY, "fakePersonConversionChance", 0.80D).set(0.80D);
			}
			config.get(CATEGORY, "woodcrawlerConversionDefaultsVersion", 0).set(1);
		}
		woodcrawlerConversionSeconds = config.getInt("woodcrawlerConversionSeconds", CATEGORY, 30, 1, 3600,
				"Loaded active seconds to process a villager in a Woodcrawler cocoon. Saved per capture; pauses while Gemini activity is disabled, locked or in Peaceful.");
		String mockery = CATEGORY + ".mockery";
		mockeryGrowthSeconds = config.getInt("growthSeconds", mockery, 30, 1, 3600,
				"Loaded active seconds per growth point after assimilation. Each converted non-player supplies 20 points; maximum growth is 100.");
		mockerySpreadSeconds = config.getInt("spreadSeconds", mockery, 15, 1, 3600,
				"Loaded active seconds between single-candidate growth placements and occasional offshoot attempts. No catch-up.");
		mockeryRadius = config.getInt("radius", mockery, 6, 2, 16,
				"Maximum three-dimensional colony radius, shared by the original Mockery and its sterile offshoots. Never loads chunks.");
		mockerySpreadLimit = config.getInt("spreadLimit", mockery, 64, 0, 4096,
				"Lifetime successful organic block placements per Mockery, including regrowth after harvesting. Zero disables block spread.");
		mockeryMaxActive = config.getInt("maxActive", mockery, 16, 0, 128,
				"Maximum loaded Mockeries for conversions and buds. Manual spawn eggs bypass the cap; saved creatures are retained.");
		mockeryOffshootLimit = config.getInt("offshootLimit", mockery, 2, 0, 2,
				"Lifetime buds per original Mockery, requiring growth 40/80. Buds cannot bud again. Zero disables budding; assimilation is separate.");
		String disease = CATEGORY + ".deepRoot";
		String wretch = CATEGORY + ".wretch";
		wretchEnabled = config.getBoolean("enabled", wretch, true,
				"Enable natural Wretches and new woodland camps in phase 4. Saved natural bosses become dormant when disabled; manual spawn eggs bypass this setting.");
		wretchMaxLoaded = config.getInt("maxLoaded", wretch, 1, 0, 16,
				"Loaded natural Wretch admission cap. Waiting camps pause at the cap without consuming retries; manual spawn eggs bypass the cap and saved bosses are retained.");
		wretchHealth = config.getFloat("health", wretch, 900F, 100F, 1024F,
				"Maximum health of newly spawned Wretches. Existing bosses retain their saved health attributes.");
		wretchCampChance = config.getInt("campChance", wretch, 256, 0, 100000,
				"One-in-N chance per newly populated phase-4 forest chunk, before clearing and 512-block spacing checks. Zero disables new camps. No retrogen; at most 256 lifetime camps per world.");
		wretchTerrainDestruction = config.getBoolean("terrainDestruction", wretch, true,
				"Allow contact-limited, provoked Wretch strikes inside its saved territory. Respects mobGriefing, Forge protection events, tiles and construction permissions, including manual Wretches.");
		wretchDetectionRange = config.getInt("detectionRange", wretch, 96, 32, 160, "Visual detection radius in blocks. Sight requires a loaded, unobstructed ray; this is not a path-search radius.");
		wretchHearingRange = config.getInt("hearingRange", wretch, 128, 32, 192, "Maximum disturbance hearing radius. Ordinary movement has a much shorter audible range than explosions.");
		wretchTerritoryRadius = config.getInt("territoryRadius", wretch, 128, 48, 256, "Patrol/pursuit radius around the saved camp or egg origin. No chunk tickets or forced loading.");
		wretchMemorySeconds = config.getInt("memorySeconds", wretch, 120, 20, 600, "Loaded seconds to retain a target's last confirmed position. Hidden players are not tracked through walls.");
		wretchSearchSeconds = config.getInt("searchSeconds", wretch, 60, 10, 300, "Maximum loaded seconds per search without a useful new sighting or disturbance.");
		wretchQuietSeconds = config.getInt("quietSeconds", wretch, 240, 60, 3600, "Quiet loaded seconds before returning home and sleeping. Disabled activity pauses the controller.");
		wretchStalkMinSeconds = config.getInt("stalkMinSeconds", wretch, 20, 5, 300, "Minimum unprovoked stalking time before aggression can turn into hunting.");
		wretchStalkMaxSeconds = config.getInt("stalkMaxSeconds", wretch, 60, 5, 600, "Upper bound for randomized unprovoked stalking intervals; raised to the minimum if necessary.");
		wretchStalkMaxSeconds = Math.max(wretchStalkMinSeconds, wretchStalkMaxSeconds);
		wretchWakeThreshold = config.getFloat("wakeThreshold", wretch, 12F, 2F, 100F, "Attention needed to awaken. Nearby groups accumulate attention; combat and explosions contribute more than footsteps.");
		wretchStalkSpeed = config.getFloat("stalkSpeed", wretch, 0.055F, 0.02F, 0.15F, "Slow stalking speed in blocks per tick before native collision resolution.");
		wretchHuntSpeed = config.getFloat("huntSpeed", wretch, 0.24F, 0.15F, 0.4F, "Active pursuit speed in blocks per tick, independent of health phases.");
		wretchChargeSpeed = config.getFloat("chargeSpeed", wretch, 0.48F, 0.3F, 0.7F, "Maximum charge speed in blocks per tick. Acceleration, turning and collision checks still apply.");
		wretchPlayerStructureDamage = config.getBoolean("playerStructureDamage", wretch, false, "Explicit permission to damage player construction. False protects recorded placement columns and unclassified constructed blocks; claims and tiles are always protected.");
		wretchGeneratedStructureDamage = config.getBoolean("generatedStructureDamage", wretch, true, "Permit suitable constructed blocks inside verified vanilla village building components, unless player placements or claims protect them. Unknown/legacy construction remains protected by default.");
		wretchBlocksPerStrike = config.getInt("blocksPerStrike", wretch, 4, 0, 16, "Maximum removals per animated strike. Zero disables structural removal.");
		wretchBlocksPerTick = config.getInt("blocksPerTick", wretch, 2, 1, 4, "Maximum block removals per Wretch per tick; candidate examination is separately bounded.");
		wretchMultipleNatural = config.getBoolean("multipleNaturalWretches", wretch, false, "Gameplay option, not confirmed lore: permit more than one lifetime natural boss in this world. Existing saved encounters are never deleted.");
		wretchNaturalLimit = config.getInt("naturalWretchLimit", wretch, 4, 1, 16, "Lifetime natural encounter limit when multipleNaturalWretches is enabled. Otherwise one. Defeated encounters consume a reservation, preventing replacement respawns; eggs bypass this limit.");
		String gardener = CATEGORY + ".gardener";
		wretchAssimilation = config.getBoolean("assimilation", wretch, true, "Allow interruptible humanoid assimilation. Successful kills use native death/drop rules; completed visual remains are bounded and saved. Disabled captures retain the legacy throw outcome.");
		wretchLivestockFeeding = config.getBoolean("livestockFeeding", wretch, false, "Optional noncanonical livestock feeding/healing. Never interrupts a human encounter; named, juvenile, leashed, breeding and saddled animals remain protected.");
		wretchHealthAggression = config.getBoolean("healthBasedAggression", wretch, false, "Optional gameplay aggression/damage modifiers at 65% and 30% health. The mature appearance does not transform; disturbances and hunting drive behavior regardless.");
		wretchVoiceSeconds = config.getInt("ambientVoiceSeconds", wretch, 45, 0, 600, "Minimum seconds between rare distorted human-like ambient vocalizations, randomized up to three times this interval. Zero disables ambient voices, not action sounds. No claim about individual victims' agency.");
		gardenerEnabled = config.getBoolean("enabled", gardener, true,
				"Enable natural underground Gardeners and incremental cave nests in phase 3. Disabled saved natural bosses remain dormant; manual spawn eggs bypass this setting.");
		gardenerMaxLoaded = config.getInt("maxLoaded", gardener, 2, 0, 16,
				"Loaded natural Gardener admission cap. Completed nests wait at the cap without consuming retries; manual spawn eggs bypass the cap and saved bosses are retained.");
		gardenerNestChance = config.getInt("nestChance", gardener, 8, 1, 10000,
				"One-in-N roll per active Overworld minute for up to eight loaded cave columns. No chunk loading or offline catch-up.");
		gardenerNestLimit = config.getInt("nestLimit", gardener, 32, 0, 64,
				"World lifetime nest limit, including abandoned partial nests and defeated bosses. Zero disables new nests; saved jobs can finish. Nests stay 256 blocks apart.");
		gardenerSupportLimit = config.getInt("supportLimit", gardener, 4, 0, 16,
				"Lifetime successful sterile Nature's Mockery summons per Gardener. Also respects Mockery and disease admission settings.");
		gardenerBlocksPerSlam = config.getInt("blocksPerSlam", gardener, 12, 0, 32,
				"Maximum natural/tissue block removals per major slam inside the nest. Four coordinates per tick; respects mobGriefing and protection events.");
		String ocean = CATEGORY + ".oceanGardener";
		oceanGardenerEnabled = config.getBoolean("enabled", ocean, true,
				"Enable rare phase-3 Ocean Gardeners in loaded deep Overworld oceans after Mars. Saved residents remain dormant when disabled.");
		oceanGardenerMaxLoaded = config.getInt("maxLoaded", ocean, 1, 0, 16,
				"Loaded natural Ocean Gardener cap, separate from underground Gardeners. Manual eggs bypass caps after Mars; saved residents remain.");
		oceanGardenerChance = config.getInt("encounterChance", ocean, 32, 1, 10000,
				"One-in-N roll per active minute before phase probability and four loaded deep-water probes. Territories stay 512 blocks apart, with 128 lifetime reservations.");
		deepRootEnabled = config.getBoolean("enabled", disease, true,
				"Enable Deep Root Disease from phase 2 in the Overworld. Disabling pauses saved progress and removes its symptoms; no offline or unloaded catch-up.");
		String[] stages = {"exposure", "roots", "contraction", "bulbs", "sprouts"};
		int[] defaults = {300, 600, 600, 600, 600};
		for(int i = 0; i < stages.length; i++)
			deepRootStageSeconds[i] = config.getInt(stages[i] + "Seconds", disease, defaults[i], 1, 604800,
					"Loaded, active seconds spent in this stage before advancing (20 ticks per second). Snapshotted for each new infection. Terminal non-player hosts retry conversion; terminal players take gradual disease damage and can leave a Mockery on disease-caused death.");
		deepRootPlayerTransmissionChance = config.getFloat("playerTransmissionChance", disease, 0.25F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible player.");
		deepRootMobTransmissionChance = config.getFloat("mobTransmissionChance", disease, 0.4F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible villager or animal.");
	}
}
