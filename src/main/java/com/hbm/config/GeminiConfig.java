package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GeminiConfig {

	public static final String CATEGORY = "21_gemini";
	public static boolean enabled = true;
	public static boolean geminiOnly = false, geminiStageSpawnSuppression = true;
	public static float geminiFinalStageHostileSpawnMultiplier = 0F;
	public static final float[] geminiIntermediateStageHostileSpawnMultipliers = {0.75F, 0.5F, 0.25F};
	public static int phase2DelayDays = 3, phase3DelayDays = 5, phase4DelayDays = 7;
	public static final int[] phaseEncounterPercent = {25, 50, 75, 100};
	public static int maxLoadedCreatures = 32;
	public static int gardenerMaxLoaded = 2, wretchMaxLoaded = 1;
	/** Legacy API aliases for the phase-1 interval. */
	public static int minEventIntervalSeconds = 45;
	public static int maxEventIntervalSeconds = 90;
	public static final int[] woodcrawlerInvasionMinIntervalSeconds = {45, 30, 20, 15};
	public static final int[] woodcrawlerInvasionMaxIntervalSeconds = {90, 60, 45, 30};
	public static final int[] woodcrawlerInvasionMinCount = {2, 2, 3, 4};
	public static final int[] woodcrawlerInvasionMaxCount = {3, 4, 5, 6};
	public static int woodcrawlerInvasionMaxSpawnAttempts = 24;
	public static boolean geminiPreventSleeping = true, woodcrawlerInvasionDiagnostics = false;
	public static int woodcrawlerDetectionRange = 32;
	public static int woodcrawlerStalkSeconds = 6;
	public static int woodcrawlerMemorySeconds = 20;
	public static int woodcrawlerSpawnWeight = 2;
	public static int woodcrawlerMaxLoaded = 16, woodcrawlerMaxPending = 16;
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
	public static int gardenerNestMinDistance = 64, gardenerNestMaxDistance = 160, gardenerNestsPerArea = 3;
	public static int gardenerSupportLimit = 4;
	public static int gardenerBlocksPerSlam = 12;
	public static int gardenerCocoonSeconds = 120, gardenerCocoonMaxLoaded = 16;
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

    /** Allows loaded Woodcrawlers to refresh their native attribute on their next server tick. */
    public static int revision;

	public static void loadFromConfig(Configuration config) {
        apply(parse(config), true);
    }

    /** Parse a complete candidate without changing any runtime fields. */
    public static Values parse(Configuration config) {
        Values values = new Values();
        config.setCategoryComment(CATEGORY, "Gemini runtime settings support /rntconfig reload. woodcrawlerSpawnWeight requires restart (biome registration). Saved infection/cocoon durations, encounter countdowns and existing boss health retain their snapshots. geminiOnly initializes missing world gamerules only.");
        values.geminiOnly = config.getBoolean("geminiOnly", CATEGORY, false,
                "Default for the geminionly gamerule in new worlds, or existing saves missing that rule. /gamerule geminionly changes the saved world value immediately. Reload never overwrites an existing rule; dimensions share vanilla world gamerules.");
        values.geminiStageSpawnSuppression = config.getBoolean("geminiStageSpawnSuppression", CATEGORY, true,
                "Reduce non-GHE automatic hostile spawn attempts in the Overworld using its saved invasion phase, even while invasion scheduling is paused. Excludes passive/neutral creatures. geminionly takes priority in every dimension.");
        for(int i = 0; i < 3; i++) values.geminiIntermediateStageHostileSpawnMultipliers[i] = multiplier(config,
                "geminiPhase" + (i + 1) + "HostileSpawnMultiplier", new float[] {0.75F, 0.5F, 0.25F}[i]);
        values.geminiFinalStageHostileSpawnMultiplier = multiplier(config, "geminiFinalStageHostileSpawnMultiplier", 0F);
		values.enabled = config.getBoolean("enabled", CATEGORY, true,
				"Enable natural Gemini activity after a player visits Mars (Duna). Disabling pauses scheduling; the world keeps its Mars milestone. Manual spawn eggs bypass this setting.");
		values.phase2DelayDays = config.getInt("phase2DelayDays", CATEGORY, 3, 1, 10000,
				"Minecraft days in phase 1 before infection unlocks. One day is 24000 enabled Overworld ticks with players present.");
		values.phase3DelayDays = config.getInt("phase3DelayDays", CATEGORY, 5, 1, 10000,
				"Additional active Minecraft days in phase 2 before underground nests, organic spread and coordination unlock.");
		values.phase4DelayDays = config.getInt("phase4DelayDays", CATEGORY, 7, 1, 10000,
				"Additional active Minecraft days in phase 3 before Wretch camps unlock. Unlocked phases never regress.");
		for(int i = 0; i < 4; i++) values.phaseEncounterPercent[i] = config.getInt("phase" + (i + 1) + "EncounterPercent",
				CATEGORY, new int[] {25, 50, 75, 100}[i], 1, 100,
				"Acceptance percent for ordinary natural Woodcrawler attempts and existing Gardener discovery. Scripted Woodcrawler encounters use their own phase intervals/counts with no percentage roll; saved countdowns are retained.");
		values.maxLoadedCreatures = config.getInt("maxLoadedCreatures", CATEGORY, 32, 0, 256,
				"Combined loaded Gemini creature admission cap, excluding disposable boss parts. Zero stops natural admissions; manual spawn eggs bypass the cap and saved creatures are always retained.");
		parseWoodcrawlerEncounters(config, values);
		values.woodcrawlerDetectionRange = config.getInt("woodcrawlerDetectionRange", CATEGORY, 32, 8, 48,
				"Woodcrawler player/villager detection radius in blocks. Also bounds native path searches.");
		values.woodcrawlerStalkSeconds = config.getInt("woodcrawlerStalkSeconds", CATEGORY, 6, 1, 30,
				"Initial silent stalking duration; noticed, escaping or nearby prey can trigger pursuit sooner.");
		values.woodcrawlerMemorySeconds = config.getInt("woodcrawlerMemorySeconds", CATEGORY, 20, 2, 60,
				"How long a Woodcrawler remembers the last observed target position after losing sight.");
		values.woodcrawlerSpawnWeight = config.getInt("woodcrawlerSpawnWeight", CATEGORY, 2, 0, 20,
				"Forest monster spawn weight after Mars. Zero disables both natural and invasion Woodcrawler spawns.");
		values.woodcrawlerMaxLoaded = config.getInt("woodcrawlerMaxLoaded", CATEGORY, 16, 0, 64,
				"Maximum loaded Woodcrawlers for natural and reproduction admissions. Manual spawn eggs bypass the cap; saved creatures are retained.");
		values.woodcrawlerMaxPending = config.getInt("woodcrawlerMaxPending", CATEGORY, 16, 0, 64,
				"Combined loaded Gardener and villager-processing cocoons. Undecided outputs reserve biomass conservatively. Also bounded by the combined Gemini cap; existing cocoons are retained.");
		values.fakePersonMaxLoaded = config.getInt("fakePersonMaxLoaded", CATEGORY, 16, 0, 128,
				"Maximum loaded Fake People for admitting conversions. Zero disables conversions; manual spawn eggs bypass the cap and saved creatures are retained. No independent natural or invasion spawns are added.");
		values.fakePersonConversionChance = config.getFloat("fakePersonConversionChance", CATEGORY, 0.80F, 0F, 1F,
				"Saved conversion outcome: Fake Person probability; the remainder produces a Woodcrawler (default 80/20). Population-limited cocoons wait without rerolling. Previous defaults 0.35/0.90 migrate once to 0.80; other custom values are retained.");
		int conversionDefaultsVersion = config.getInt("woodcrawlerConversionDefaultsVersion", CATEGORY, 0, 0, 1,
				"Internal one-time conversion-default migration. Saved cocoon decisions are never changed.");
		if(conversionDefaultsVersion == 0) {
			if(values.fakePersonConversionChance == 0.35F || values.fakePersonConversionChance == 0.90F) {
				values.fakePersonConversionChance = 0.80F;
				config.get(CATEGORY, "fakePersonConversionChance", 0.80D).set(0.80D);
			}
			config.get(CATEGORY, "woodcrawlerConversionDefaultsVersion", 0).set(1);
		}
		values.woodcrawlerConversionSeconds = config.getInt("woodcrawlerConversionSeconds", CATEGORY, 30, 1, 3600,
				"Loaded active seconds to process a villager in a Woodcrawler cocoon. Saved per capture; pauses while Gemini activity is disabled, locked or in Peaceful.");
		String mockery = CATEGORY + ".mockery";
		values.mockeryGrowthSeconds = config.getInt("growthSeconds", mockery, 30, 1, 3600,
				"Loaded active seconds per growth point after assimilation. Each converted non-player supplies 20 points; maximum growth is 100.");
		values.mockerySpreadSeconds = config.getInt("spreadSeconds", mockery, 15, 1, 3600,
				"Loaded active seconds between single-candidate growth placements and occasional offshoot attempts. No catch-up.");
		values.mockeryRadius = config.getInt("radius", mockery, 6, 2, 16,
				"Maximum three-dimensional colony radius, shared by the original Mockery and its sterile offshoots. Never loads chunks.");
		values.mockerySpreadLimit = config.getInt("spreadLimit", mockery, 64, 0, 4096,
				"Lifetime successful organic block placements per Mockery, including regrowth after harvesting. Zero disables block spread.");
		values.mockeryMaxActive = config.getInt("maxActive", mockery, 16, 0, 128,
				"Maximum loaded Mockeries for conversions and buds. Manual spawn eggs bypass the cap; saved creatures are retained.");
		values.mockeryOffshootLimit = config.getInt("offshootLimit", mockery, 2, 0, 2,
				"Lifetime buds per original Mockery, requiring growth 40/80. Buds cannot bud again. Zero disables budding; assimilation is separate.");
		String disease = CATEGORY + ".deepRoot";
		String wretch = CATEGORY + ".wretch";
		values.wretchEnabled = config.getBoolean("enabled", wretch, true,
				"Enable natural Wretches and new woodland camps in phase 4. Saved natural bosses become dormant when disabled; manual spawn eggs bypass this setting.");
		values.wretchMaxLoaded = config.getInt("maxLoaded", wretch, 1, 0, 16,
				"Loaded natural Wretch admission cap. Waiting camps pause at the cap without consuming retries; manual spawn eggs bypass the cap and saved bosses are retained.");
		values.wretchHealth = config.getFloat("health", wretch, 900F, 100F, 1024F,
				"Maximum health of newly spawned Wretches. Existing bosses retain their saved health attributes.");
		values.wretchCampChance = config.getInt("campChance", wretch, 256, 0, 100000,
				"One-in-N chance per newly populated phase-4 forest chunk, before clearing and 512-block spacing checks. Zero disables new camps. No retrogen; at most 256 lifetime camps per world.");
		values.wretchTerrainDestruction = config.getBoolean("terrainDestruction", wretch, true,
				"Allow contact-limited, provoked Wretch strikes inside its saved territory. Respects mobGriefing, Forge protection events, tiles and construction permissions, including manual Wretches.");
		values.wretchDetectionRange = config.getInt("detectionRange", wretch, 96, 32, 160, "Visual detection radius in blocks. Sight requires a loaded, unobstructed ray; this is not a path-search radius.");
		values.wretchHearingRange = config.getInt("hearingRange", wretch, 128, 32, 192, "Maximum disturbance hearing radius. Ordinary movement has a much shorter audible range than explosions.");
		values.wretchTerritoryRadius = config.getInt("territoryRadius", wretch, 128, 48, 256, "Patrol/pursuit radius around the saved camp or egg origin. No chunk tickets or forced loading.");
		values.wretchMemorySeconds = config.getInt("memorySeconds", wretch, 120, 20, 600, "Loaded seconds to retain a target's last confirmed position. Hidden players are not tracked through walls.");
		values.wretchSearchSeconds = config.getInt("searchSeconds", wretch, 60, 10, 300, "Maximum loaded seconds per search without a useful new sighting or disturbance.");
		values.wretchQuietSeconds = config.getInt("quietSeconds", wretch, 240, 60, 3600, "Quiet loaded seconds before returning home and sleeping. Disabled activity pauses the controller.");
		values.wretchStalkMinSeconds = config.getInt("stalkMinSeconds", wretch, 20, 5, 300, "Minimum unprovoked stalking time before aggression can turn into hunting.");
		values.wretchStalkMaxSeconds = config.getInt("stalkMaxSeconds", wretch, 60, 5, 600, "Upper bound for randomized unprovoked stalking intervals; raised to the minimum if necessary.");
		values.wretchStalkMaxSeconds = Math.max(values.wretchStalkMinSeconds, values.wretchStalkMaxSeconds);
		values.wretchWakeThreshold = config.getFloat("wakeThreshold", wretch, 12F, 2F, 100F, "Attention needed to awaken. Nearby groups accumulate attention; combat and explosions contribute more than footsteps.");
		values.wretchStalkSpeed = config.getFloat("stalkSpeed", wretch, 0.055F, 0.02F, 0.15F, "Slow stalking speed in blocks per tick before native collision resolution.");
		values.wretchHuntSpeed = config.getFloat("huntSpeed", wretch, 0.24F, 0.15F, 0.4F, "Active pursuit speed in blocks per tick, independent of health phases.");
		values.wretchChargeSpeed = config.getFloat("chargeSpeed", wretch, 0.48F, 0.3F, 0.7F, "Maximum charge speed in blocks per tick. Acceleration, turning and collision checks still apply.");
		values.wretchPlayerStructureDamage = config.getBoolean("playerStructureDamage", wretch, false, "Explicit permission to damage player construction. False protects recorded placement columns and unclassified constructed blocks; claims and tiles are always protected.");
		values.wretchGeneratedStructureDamage = config.getBoolean("generatedStructureDamage", wretch, true, "Permit suitable constructed blocks inside verified vanilla village building components, unless player placements or claims protect them. Unknown/legacy construction remains protected by default.");
		values.wretchBlocksPerStrike = config.getInt("blocksPerStrike", wretch, 4, 0, 16, "Maximum removals per animated strike. Zero disables structural removal.");
		values.wretchBlocksPerTick = config.getInt("blocksPerTick", wretch, 2, 1, 4, "Maximum block removals per Wretch per tick; candidate examination is separately bounded.");
		values.wretchMultipleNatural = config.getBoolean("multipleNaturalWretches", wretch, false, "Gameplay option, not confirmed lore: permit more than one lifetime natural boss in this world. Existing saved encounters are never deleted.");
		values.wretchNaturalLimit = config.getInt("naturalWretchLimit", wretch, 4, 1, 16, "Lifetime natural encounter limit when multipleNaturalWretches is enabled. Otherwise one. Defeated encounters consume a reservation, preventing replacement respawns; eggs bypass this limit.");
		String gardener = CATEGORY + ".gardener";
		values.wretchAssimilation = config.getBoolean("assimilation", wretch, true, "Allow interruptible humanoid assimilation. Successful kills use native death/drop rules; completed visual remains are bounded and saved. Disabled captures retain the legacy throw outcome.");
		values.wretchLivestockFeeding = config.getBoolean("livestockFeeding", wretch, false, "Optional noncanonical livestock feeding/healing. Never interrupts a human encounter; named, juvenile, leashed, breeding and saddled animals remain protected.");
		values.wretchHealthAggression = config.getBoolean("healthBasedAggression", wretch, false, "Optional gameplay aggression/damage modifiers at 65% and 30% health. The mature appearance does not transform; disturbances and hunting drive behavior regardless.");
		values.wretchVoiceSeconds = config.getInt("ambientVoiceSeconds", wretch, 45, 0, 600, "Minimum seconds between rare distorted human-like ambient vocalizations, randomized up to three times this interval. Zero disables ambient voices, not action sounds. No claim about individual victims' agency.");
		values.gardenerEnabled = config.getBoolean("enabled", gardener, true,
				"Enable natural underground Gardeners and incremental cave nests in phase 3. Disabled saved natural bosses remain dormant; manual spawn eggs bypass this setting.");
		values.gardenerMaxLoaded = config.getInt("maxLoaded", gardener, 2, 0, 16,
				"Loaded natural Gardener admission cap. Completed nests wait at the cap without consuming retries; manual spawn eggs bypass the cap and saved bosses are retained.");
		values.gardenerNestChance = config.getInt("nestChance", gardener, 8, 1, 10000,
				"One-in-N roll per active Overworld minute for up to eight loaded cave columns. No chunk loading or offline catch-up.");
		values.gardenerNestLimit = config.getInt("nestLimit", gardener, 32, 0, 64,
				"World lifetime nest limit, including abandoned partial nests and defeated bosses. Zero disables new nests; saved jobs can finish. Nests stay at least 64 blocks apart.");
		values.gardenerNestMinDistance = config.getInt("nestMinDistance", gardener, 64, 48, 512,
				"Minimum nest-center distance from a saved survival anchor. Beds are preferred; bedless anchors require sustained construction activity.");
		values.gardenerNestMaxDistance = config.getInt("nestMaxDistance", gardener, 160, 48, 512,
				"Maximum nest-center distance from a survival anchor. Raised to the minimum if smaller. Only loaded terrain is searched.");
		values.gardenerNestMaxDistance = Math.max(values.gardenerNestMinDistance, values.gardenerNestMaxDistance);
		values.gardenerNestsPerArea = config.getInt("nestsPerArea", gardener, 3, 0, 8,
				"Lifetime nests per shared survival area, distributed in angular sectors. Nearby players share anchors; travel never moves an established anchor.");
		values.gardenerSupportLimit = config.getInt("supportLimit", gardener, 4, 0, 16,
				"Lifetime successful sterile Nature's Mockery summons per Gardener. Also respects Mockery and disease admission settings.");
		values.gardenerBlocksPerSlam = config.getInt("blocksPerSlam", gardener, 12, 0, 32,
				"Maximum natural/tissue block removals per major slam inside the nest. Four coordinates per tick; respects mobGriefing and protection events.");
		values.gardenerCocoonSeconds = config.getInt("cocoonSeconds", gardener, 120, 1, 3600,
				"Loaded active seconds after five-second cocoon growth to produce a Woodcrawler. Saved per cocoon; unrelated to Woodcrawler-to-Fake-Person conversion.");
		values.gardenerCocoonMaxLoaded = config.getInt("cocoonMaxLoaded", gardener, 16, 0, 64,
				"Maximum loaded Gardener cocoons, also bounded by the combined Gemini cap. Zero disables new cocoons. Hatchlings obey normal Woodcrawler conversion admission limits, including for manual parents.");
		int populationVersion = config.getInt("woodcrawlerPopulationDefaultsVersion", CATEGORY, 0, 0, 1,
				"One-time migration of previous default active/biomass limits (8 to 16). Other custom limits and all saved entities are retained.");
		if(populationVersion == 0) {
			if(values.woodcrawlerMaxLoaded == 8) {
				values.woodcrawlerMaxLoaded = 16; config.get(CATEGORY, "woodcrawlerMaxLoaded", 16).set(16);
			}
			if(values.gardenerCocoonMaxLoaded == 8) {
				values.gardenerCocoonMaxLoaded = 16; config.get(gardener, "cocoonMaxLoaded", 16).set(16);
			}
			config.get(CATEGORY, "woodcrawlerPopulationDefaultsVersion", 0).set(1);
		}
		String ocean = CATEGORY + ".oceanGardener";
		values.oceanGardenerEnabled = config.getBoolean("enabled", ocean, true,
				"Enable rare phase-3 Ocean Gardeners in loaded deep Overworld oceans after Mars. Saved residents remain dormant when disabled.");
		values.oceanGardenerMaxLoaded = config.getInt("maxLoaded", ocean, 1, 0, 16,
				"Loaded natural Ocean Gardener cap, separate from underground Gardeners. Manual eggs bypass caps after Mars; saved residents remain.");
		values.oceanGardenerChance = config.getInt("encounterChance", ocean, 32, 1, 10000,
				"One-in-N roll per active minute before phase probability and four loaded deep-water probes. Territories stay 512 blocks apart, with 128 lifetime reservations.");
		values.deepRootEnabled = config.getBoolean("enabled", disease, true,
				"Enable Deep Root Disease from phase 2 in the Overworld. Disabling pauses saved progress and removes its symptoms; no offline or unloaded catch-up.");
		String[] stages = {"exposure", "roots", "contraction", "bulbs", "sprouts"};
		int[] defaults = {300, 600, 600, 600, 600};
		for(int i = 0; i < stages.length; i++)
			values.deepRootStageSeconds[i] = config.getInt(stages[i] + "Seconds", disease, defaults[i], 1, 604800,
					"Loaded, active seconds spent in this stage before advancing (20 ticks per second). Snapshotted for each new infection. Terminal non-player hosts retry conversion; terminal players take gradual disease damage and can leave a Mockery on disease-caused death.");
		values.deepRootPlayerTransmissionChance = config.getFloat("playerTransmissionChance", disease, 0.25F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible player.");
		values.deepRootMobTransmissionChance = config.getFloat("mobTransmissionChance", disease, 0.4F, 0F, 1F,
				"One establishment roll per confirmed Gemini carrier melee wound against a susceptible villager or animal.");
        return values;
	}

    /** Commit only after all supported categories have parsed successfully, on the server thread. */
    public static void apply(Values values, boolean startup) {
        enabled = values.enabled;
        geminiOnly = values.geminiOnly;
        geminiStageSpawnSuppression = values.geminiStageSpawnSuppression;
        geminiFinalStageHostileSpawnMultiplier = values.geminiFinalStageHostileSpawnMultiplier;
        System.arraycopy(values.geminiIntermediateStageHostileSpawnMultipliers, 0, geminiIntermediateStageHostileSpawnMultipliers, 0, geminiIntermediateStageHostileSpawnMultipliers.length);
        phase2DelayDays = values.phase2DelayDays;
        phase3DelayDays = values.phase3DelayDays;
        phase4DelayDays = values.phase4DelayDays;
        System.arraycopy(values.phaseEncounterPercent, 0, phaseEncounterPercent, 0, phaseEncounterPercent.length);
        maxLoadedCreatures = values.maxLoadedCreatures;
        gardenerMaxLoaded = values.gardenerMaxLoaded;
        wretchMaxLoaded = values.wretchMaxLoaded;
        minEventIntervalSeconds = values.minEventIntervalSeconds;
        maxEventIntervalSeconds = values.maxEventIntervalSeconds;
        System.arraycopy(values.woodcrawlerInvasionMinIntervalSeconds, 0, woodcrawlerInvasionMinIntervalSeconds, 0, 4);
        System.arraycopy(values.woodcrawlerInvasionMaxIntervalSeconds, 0, woodcrawlerInvasionMaxIntervalSeconds, 0, 4);
        System.arraycopy(values.woodcrawlerInvasionMinCount, 0, woodcrawlerInvasionMinCount, 0, 4);
        System.arraycopy(values.woodcrawlerInvasionMaxCount, 0, woodcrawlerInvasionMaxCount, 0, 4);
        woodcrawlerInvasionMaxSpawnAttempts = values.woodcrawlerInvasionMaxSpawnAttempts;
        geminiPreventSleeping = values.geminiPreventSleeping;
        woodcrawlerInvasionDiagnostics = values.woodcrawlerInvasionDiagnostics;
        woodcrawlerDetectionRange = values.woodcrawlerDetectionRange;
        woodcrawlerStalkSeconds = values.woodcrawlerStalkSeconds;
        woodcrawlerMemorySeconds = values.woodcrawlerMemorySeconds;
        if(startup) woodcrawlerSpawnWeight = values.woodcrawlerSpawnWeight;
        woodcrawlerMaxLoaded = values.woodcrawlerMaxLoaded;
        woodcrawlerMaxPending = values.woodcrawlerMaxPending;
        fakePersonMaxLoaded = values.fakePersonMaxLoaded;
        fakePersonConversionChance = values.fakePersonConversionChance;
        woodcrawlerConversionSeconds = values.woodcrawlerConversionSeconds;
        deepRootEnabled = values.deepRootEnabled;
        System.arraycopy(values.deepRootStageSeconds, 0, deepRootStageSeconds, 0, deepRootStageSeconds.length);
        deepRootPlayerTransmissionChance = values.deepRootPlayerTransmissionChance;
        deepRootMobTransmissionChance = values.deepRootMobTransmissionChance;
        mockeryGrowthSeconds = values.mockeryGrowthSeconds;
        mockerySpreadSeconds = values.mockerySpreadSeconds;
        mockeryRadius = values.mockeryRadius;
        mockerySpreadLimit = values.mockerySpreadLimit;
        mockeryMaxActive = values.mockeryMaxActive;
        mockeryOffshootLimit = values.mockeryOffshootLimit;
        gardenerEnabled = values.gardenerEnabled;
        gardenerNestChance = values.gardenerNestChance;
        gardenerNestLimit = values.gardenerNestLimit;
        gardenerNestMinDistance = values.gardenerNestMinDistance;
        gardenerNestMaxDistance = values.gardenerNestMaxDistance;
        gardenerNestsPerArea = values.gardenerNestsPerArea;
        gardenerSupportLimit = values.gardenerSupportLimit;
        gardenerBlocksPerSlam = values.gardenerBlocksPerSlam;
        gardenerCocoonSeconds = values.gardenerCocoonSeconds;
        gardenerCocoonMaxLoaded = values.gardenerCocoonMaxLoaded;
        oceanGardenerEnabled = values.oceanGardenerEnabled;
        oceanGardenerMaxLoaded = values.oceanGardenerMaxLoaded;
        oceanGardenerChance = values.oceanGardenerChance;
        wretchEnabled = values.wretchEnabled;
        wretchHealth = values.wretchHealth;
        wretchCampChance = values.wretchCampChance;
        wretchTerrainDestruction = values.wretchTerrainDestruction;
        wretchDetectionRange = values.wretchDetectionRange;
        wretchHearingRange = values.wretchHearingRange;
        wretchTerritoryRadius = values.wretchTerritoryRadius;
        wretchMemorySeconds = values.wretchMemorySeconds;
        wretchSearchSeconds = values.wretchSearchSeconds;
        wretchQuietSeconds = values.wretchQuietSeconds;
        wretchStalkMinSeconds = values.wretchStalkMinSeconds;
        wretchStalkMaxSeconds = values.wretchStalkMaxSeconds;
        wretchWakeThreshold = values.wretchWakeThreshold;
        wretchStalkSpeed = values.wretchStalkSpeed;
        wretchHuntSpeed = values.wretchHuntSpeed;
        wretchChargeSpeed = values.wretchChargeSpeed;
        wretchPlayerStructureDamage = values.wretchPlayerStructureDamage;
        wretchGeneratedStructureDamage = values.wretchGeneratedStructureDamage;
        wretchMultipleNatural = values.wretchMultipleNatural;
        wretchNaturalLimit = values.wretchNaturalLimit;
        wretchBlocksPerStrike = values.wretchBlocksPerStrike;
        wretchBlocksPerTick = values.wretchBlocksPerTick;
        wretchAssimilation = values.wretchAssimilation;
        wretchLivestockFeeding = values.wretchLivestockFeeding;
        wretchHealthAggression = values.wretchHealthAggression;
        wretchVoiceSeconds = values.wretchVoiceSeconds;
        revision++;
    }

    public static final class Values {
        public boolean enabled = true;
        public boolean geminiOnly = false, geminiStageSpawnSuppression = true;
        public float geminiFinalStageHostileSpawnMultiplier = 0F;
        public final float[] geminiIntermediateStageHostileSpawnMultipliers = {0.75F, 0.5F, 0.25F};
        public int phase2DelayDays = 3, phase3DelayDays = 5, phase4DelayDays = 7;
        public final int[] phaseEncounterPercent = {25, 50, 75, 100};
        public int maxLoadedCreatures = 32;
        public int gardenerMaxLoaded = 2, wretchMaxLoaded = 1;
        public int minEventIntervalSeconds = 45;
        public int maxEventIntervalSeconds = 90;
        public final int[] woodcrawlerInvasionMinIntervalSeconds = {45, 30, 20, 15};
        public final int[] woodcrawlerInvasionMaxIntervalSeconds = {90, 60, 45, 30};
        public final int[] woodcrawlerInvasionMinCount = {2, 2, 3, 4};
        public final int[] woodcrawlerInvasionMaxCount = {3, 4, 5, 6};
        public int woodcrawlerInvasionMaxSpawnAttempts = 24;
        public boolean geminiPreventSleeping = true, woodcrawlerInvasionDiagnostics = false;
        public int woodcrawlerDetectionRange = 32;
        public int woodcrawlerStalkSeconds = 6;
        public int woodcrawlerMemorySeconds = 20;
        public int woodcrawlerSpawnWeight = 2;
        public int woodcrawlerMaxLoaded = 16, woodcrawlerMaxPending = 16;
        public int fakePersonMaxLoaded = 16;
        public float fakePersonConversionChance = 0.80F;
        public int woodcrawlerConversionSeconds = 30;
        public boolean deepRootEnabled = true;
        public final int[] deepRootStageSeconds = {300, 600, 600, 600, 600};
        public float deepRootPlayerTransmissionChance = 0.25F;
        public float deepRootMobTransmissionChance = 0.4F;
        public int mockeryGrowthSeconds = 30;
        public int mockerySpreadSeconds = 15;
        public int mockeryRadius = 6;
        public int mockerySpreadLimit = 64;
        public int mockeryMaxActive = 16;
        public int mockeryOffshootLimit = 2;
        public boolean gardenerEnabled = true;
        public int gardenerNestChance = 8;
        public int gardenerNestLimit = 32;
        public int gardenerNestMinDistance = 64, gardenerNestMaxDistance = 160, gardenerNestsPerArea = 3;
        public int gardenerSupportLimit = 4;
        public int gardenerBlocksPerSlam = 12;
        public int gardenerCocoonSeconds = 120, gardenerCocoonMaxLoaded = 16;
        public boolean oceanGardenerEnabled = true;
        public int oceanGardenerMaxLoaded = 1, oceanGardenerChance = 32;
        public boolean wretchEnabled = true;
        public float wretchHealth = 900F;
        public int wretchCampChance = 256;
        public boolean wretchTerrainDestruction = true;
        public int wretchDetectionRange = 96, wretchHearingRange = 128, wretchTerritoryRadius = 128;
        public int wretchMemorySeconds = 120, wretchSearchSeconds = 60, wretchQuietSeconds = 240;
        public int wretchStalkMinSeconds = 20, wretchStalkMaxSeconds = 60;
        public float wretchWakeThreshold = 12F, wretchStalkSpeed = 0.055F, wretchHuntSpeed = 0.24F, wretchChargeSpeed = 0.48F;
        public boolean wretchPlayerStructureDamage = false, wretchGeneratedStructureDamage = true, wretchMultipleNatural = false;
        public int wretchNaturalLimit = 4, wretchBlocksPerStrike = 4, wretchBlocksPerTick = 2;
        public boolean wretchAssimilation = true, wretchLivestockFeeding = false, wretchHealthAggression = false;
        public int wretchVoiceSeconds = 45;
    }

    private static void parseWoodcrawlerEncounters(Configuration config, Values values) {
        // Import old customized pacing only when the new keys are absent. Recognizable
        // old defaults migrate; administrators can retain those exact numbers via new keys.
        int legacyMin = config.hasKey(CATEGORY, "minEventIntervalSeconds")
                ? config.getInt("minEventIntervalSeconds", CATEGORY, 1200, 1, 86400,
                        "Legacy migration input only. Use woodcrawlerInvasionMinIntervalSeconds and phase2/3/4 keys; existing new keys take priority.") : 1200;
        int legacyMax = config.hasKey(CATEGORY, "maxEventIntervalSeconds")
                ? config.getInt("maxEventIntervalSeconds", CATEGORY, 2400, 1, 86400,
                        "Legacy migration input only. Use woodcrawlerInvasionMaxIntervalSeconds and phase2/3/4 keys; saved countdowns are never reset.") : 2400;
        boolean legacyDefaults = legacyMin == 1200 && legacyMax == 2400;
        for(int i = 0; i < 4; i++) {
            String prefix = "woodcrawlerInvasion" + (i == 0 ? "" : "Phase" + (i + 1));
            int minDefault = legacyDefaults ? values.woodcrawlerInvasionMinIntervalSeconds[i]
                    : Math.max(1, legacyMin * values.phaseEncounterPercent[0] / values.phaseEncounterPercent[i]);
            int maxDefault = legacyDefaults ? values.woodcrawlerInvasionMaxIntervalSeconds[i]
                    : Math.max(1, legacyMax * values.phaseEncounterPercent[0] / values.phaseEncounterPercent[i]);
            values.woodcrawlerInvasionMinIntervalSeconds[i] = config.getInt(prefix + "MinIntervalSeconds", CATEGORY,
                    minDefault, 1, 86400, "Minimum active Overworld seconds between phase-" + (i + 1)
                            + " Woodcrawler encounters. No acceptance roll. Empty/disabled/locked worlds pause scheduling. Live reload preserves the current countdown.");
            values.woodcrawlerInvasionMaxIntervalSeconds[i] = Math.max(values.woodcrawlerInvasionMinIntervalSeconds[i],
                    config.getInt(prefix + "MaxIntervalSeconds", CATEGORY, maxDefault, 1, 86400,
                            "Maximum active seconds between encounters; raised to the minimum if smaller. Legacy default 1200/2400 intervals migrate when new keys are absent; other legacy intervals are imported."));
            values.woodcrawlerInvasionMinCount[i] = config.getInt(prefix + "MinCount", CATEGORY,
                    values.woodcrawlerInvasionMinCount[i], 0, 16, "Minimum Woodcrawler target count per encounter. Zero allows empty events. Placement, difficulty, biome and population gates still apply.");
            values.woodcrawlerInvasionMaxCount[i] = Math.max(values.woodcrawlerInvasionMinCount[i],
                    config.getInt(prefix + "MaxCount", CATEGORY, values.woodcrawlerInvasionMaxCount[i], 0, 16,
                            "Maximum target count, raised to the minimum if smaller. Targets are not guaranteed spawns; all attempts share one bounded budget."));
        }
        values.minEventIntervalSeconds = values.woodcrawlerInvasionMinIntervalSeconds[0];
        values.maxEventIntervalSeconds = values.woodcrawlerInvasionMaxIntervalSeconds[0];
        values.woodcrawlerInvasionMaxSpawnAttempts = config.getInt("woodcrawlerInvasionMaxSpawnAttempts", CATEGORY,
                24, 1, 24, "Maximum loaded placement probes per entire encounter, including failed probes; never forces chunks or scans surrounding biomes.");
        values.geminiPreventSleeping = config.getBoolean("geminiPreventSleeping", CATEGORY, true,
                "Prevent survival/adventure sleeping in the Overworld throughout enabled, Mars-unlocked invasion phases 1-4. Disabled/locked invasions restore normal sleeping immediately.");
        values.woodcrawlerInvasionDiagnostics = config.getBoolean("woodcrawlerInvasionDiagnostics", CATEGORY, false,
                "Log encounter results/rejection totals and paused scheduling reasons. Repeated failures are throttled to once per reason per minute per world; no per-position logging.");
    }

    private static float multiplier(Configuration config, String key, float fallback) {
        float value = config.getFloat(key, CATEGORY, fallback, 0F, 1F,
                "Non-GHE hostile spawn-attempt acceptance probability for this invasion phase, clamped to [0,1]. Phase 0 always uses 1. Default phases 1/2/3/4: 0.75/0.5/0.25/0. No mob-cap or existing-entity changes; commands and eggs bypass this policy.");
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value;
    }
}
