package com.hbm.config;

import net.minecraftforge.common.config.Configuration;

public final class GeminiConfig {
    public static boolean irisRendering = true;
    public static boolean irisEffects = true;
    public static float irisSize = 1F;
    public static float irisPupilSpeed = 1F;
    public static int irisObservationSeconds = 900;
    public static int irisObservationDurationSeconds = 60;
    public static int irisAmbientSeconds = 600;
    public static float irisIntensity = 0.6F;
    public static boolean irisProgressionVisibility = true;
    public static int irisMinRenderChunks = 2;
    public static boolean lightsEnabled = true;
    public static int lightsMinPhase = 3, lightsIntervalSeconds = 480, harvestIntervalSeconds = 2400;
    public static int lightsMaxActive = 10, lightsDurationSeconds = 160;
    public static int lightsDetectionRange = 80, lightsHuntingRange = 42;
    public static float lightsHarvestDamage = 10F, lightsMovementSpeed = 0.38F;

	public static final String CATEGORY = "21_gemini";
	public static boolean enabled = true;
	public static boolean geminiOnly = false, geminiStageSpawnSuppression = true;
	public static float geminiFinalStageHostileSpawnMultiplier = 0F;
	public static final float[] geminiIntermediateStageHostileSpawnMultipliers = {0.75F, 0.5F, 0.25F};
	public static int phase2DelayDays = 3, phase3DelayDays = 5, phase4DelayDays = 7;
	public static final int[] phaseEncounterPercent = {25, 50, 75, 100};
	public static int maxLoadedCreatures = 32;
	public static int gardenerMaxLoaded = 16, wretchMaxLoaded = 1;
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
	public static int flashlightRange = 20;
	public static int woodcrawlerStalkSeconds = 6;
	public static int woodcrawlerMemorySeconds = 20;
	public static int woodcrawlerLeapMaxHorizontal = 12, woodcrawlerLeapMaxUp = 6, woodcrawlerLeapMaxDown = 16;
	public static int woodcrawlerLeapCooldownSeconds = 3, woodcrawlerLeapMinDistance = 3;
	public static int woodcrawlerSpawnWeight = 2;
	public static int woodcrawlerMaxLoaded = 16, woodcrawlerMaxPending = 16;
	public static int fakePersonMaxLoaded = 16;
	public static float fakePersonConversionChance = 0.80F;
	public static boolean fakePersonHilltopScreaming = true;
	public static int fakePersonHilltopSearchRadius = 48;
	public static int fakePersonScreamMinIntervalSeconds = 90;
	public static int fakePersonScreamMaxIntervalSeconds = 180;
	public static int fakePersonScreamAudibleDistance = 96;
	public static float fakePersonScreamNightProbability = 0.75F;
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
	public static int gardenerResidents = 3;
    public static int gardenerReplenishSeconds = 300;
	public static int gardenerNestChance = 8;
	public static int gardenerNestLimit = 32;
	public static int gardenerNestMinDistance = 64, gardenerNestMaxDistance = 160, gardenerNestsPerArea = 3;
    public static int gardenerHuntingRadius = 160;
	public static int gardenerSupportLimit = 4;
	public static int gardenerBlocksPerSlam = 12;
	public static int gardenerCocoonSeconds = 120, gardenerCocoonMaxLoaded = 16;
	public static boolean oceanGardenerEnabled = true;
	public static int oceanGardenerMaxLoaded = 1, oceanGardenerChance = 32;
	public static boolean wretchEnabled = true;
	public static float wretchHealth = 900F;
	public static int wretchCampChance = 256;
	public static int wretchCampIntervalSeconds = 300, wretchCampLimit = 256;
	public static int wretchCampMinDistance = 80, wretchCampMaxDistance = 160, wretchCampSpacing = 256;
	public static int gardenerNestIntervalSeconds = 120;
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
        values.irisRendering = config.getBoolean("rendering", CATEGORY + ".iris", true, "Render the Iris in the Overworld sky. Debug show bypasses this switch.");
        values.irisEffects = config.getBoolean("environmentalEffects", CATEGORY + ".iris", true, "Enable bounded atmospheric darkness, red haze, event pulses and distant organic ambience. Never changes blocks or AI.");
        values.irisSize = config.getFloat("apparentSizeMultiplier", CATEGORY + ".iris", 1F, 0.25F, 2F, "Apparent angular size multiplier. Final diameter is about 64 degrees at 1; geometry remains inside the sky clipping volume.");
        if(Float.isNaN(values.irisSize) || Float.isInfinite(values.irisSize)) values.irisSize = 1F;
        values.irisPupilSpeed = config.getFloat("pupilAnimationSpeed", CATEGORY + ".iris", 1F, 0F, 4F, "Slow dilation speed multiplier; zero freezes dilation. Shared observation transitions remain timed independently.");
        if(Float.isNaN(values.irisPupilSpeed) || Float.isInfinite(values.irisPupilSpeed)) values.irisPupilSpeed = 1F;
        values.irisObservationSeconds = config.getInt("observationIntervalSeconds", CATEGORY + ".iris", 900, 30, 86400, "Base random observation interval, divided by invasion phase, randomized from one to two times that interval. Not every opportunity succeeds.");
        values.irisObservationDurationSeconds = config.getInt("observationDurationSeconds", CATEGORY + ".iris", 60, 20, 3600, "Total observation seconds including slow arrival and return; one shared survival/adventure target region.");
        values.irisAmbientSeconds = config.getInt("ambientSoundIntervalSeconds", CATEGORY + ".iris", 600, 30, 86400, "Base random ambient opportunity interval, divided by invasion phase. Nearby infestation increases acceptance; no per-tick sounds.");
        values.irisIntensity = config.getFloat("atmosphericIntensity", CATEGORY + ".iris", 0.6F, 0F, 1F, "Maximum atmospheric effect strength. Local infestations intensify disturbances without changing weather or block light.");
        if(Float.isNaN(values.irisIntensity) || Float.isInfinite(values.irisIntensity)) values.irisIntensity = 0.6F;
        values.irisProgressionVisibility = config.getBoolean("progressionBasedVisibility", CATEGORY + ".iris", true, "Use rare early-night reveals and advanced daytime visibility. False shows all unlocked stages continuously, still attenuated by weather.");
        values.irisMinRenderChunks = config.getInt("minimumRenderDistanceChunks", CATEGORY + ".iris", 2, 2, 16, "Minimum client terrain render-distance setting for automatic Iris rendering. Celestial geometry has no world-distance cutoff or chunk requests. Debug show bypasses this threshold.");
        String lights = CATEGORY + ".lights";
        values.lightsEnabled = config.getBoolean("enabled", lights, true, "Allow natural nighttime Lights in the Sky and Harvest encounters.");
        values.lightsMinPhase = config.getInt("minimumPhase", lights, 3, 1, 4, "Minimum invasion phase for Lights in the Sky. Harvest also requires at least phase 3.");
        values.lightsIntervalSeconds = config.getInt("ordinaryIntervalSeconds", lights, 480, 30, 86400, "Base average eligible seconds between ordinary encounter rolls that succeed. Later phases and nearby infestation improve the odds.");
        values.harvestIntervalSeconds = config.getInt("harvestIntervalSeconds", lights, 2400, 60, 86400, "Base average eligible seconds between Harvest rolls that succeed. Later phases and nearby infestation improve the odds.");
        values.lightsMaxActive = config.getInt("maximumActiveLights", lights, 10, 0, 16, "Maximum Lights admitted to the single active Overworld encounter. Zero also prevents forced admission.");
        values.lightsDurationSeconds = config.getInt("eventDurationSeconds", lights, 160, 30, 600, "Hard lifetime in server seconds for a newly started encounter.");
        values.lightsDetectionRange = config.getInt("detectionRange", lights, 80, 16, 112, "Horizontal detection radius in blocks for exposed prey.");
        values.lightsHuntingRange = config.getInt("huntingRange", lights, 42, 8, 80, "Horizontal distance in blocks at which a Light may commit to hunting, limited by detectionRange.");
        values.lightsHarvestDamage = config.getFloat("harvestDamage", lights, 10F, 0F, 40F, "Damage in health points delivered once per capture.");
        values.lightsMovementSpeed = config.getFloat("movementSpeed", lights, 0.38F, 0.08F, 0.8F, "Base flight speed in blocks per tick. Distant drift is slower than a dive.");
        if(!Float.isFinite(values.lightsHarvestDamage)) values.lightsHarvestDamage = 10F;
        if(!Float.isFinite(values.lightsMovementSpeed)) values.lightsMovementSpeed = 0.38F;
        config.setCategoryComment(CATEGORY, "Gemini runtime settings support /rntconfig reload. woodcrawlerSpawnWeight requires restart (biome registration). Saved infection/cocoon durations, encounter countdowns and existing boss health retain their snapshots. geminiOnly initializes missing world gamerules only.");
		values.flashlightRange = config.getInt("flashlightRange", CATEGORY, 20, 4, 48,
				"Effective range in blocks of held visible/UV flashlights. Both modes require direct aim and loaded, unobstructed line of sight. Right-click toggles power; sneak-right-click changes mode.");
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
				"How long a Woodcrawler remembers the last observed target position after losing sight, before a final five-second search.");
		values.woodcrawlerLeapMaxHorizontal = config.getInt("woodcrawlerLeapMaxHorizontal", CATEGORY, 12, 1, 24,
				"Maximum horizontal leap displacement in blocks. Collision, available launch/landing space and native physics may reduce actual reach.");
		values.woodcrawlerLeapMaxUp = config.getInt("woodcrawlerLeapMaxUp", CATEGORY, 6, 1, 12,
				"Maximum leap apex above takeoff in blocks, also limiting upward landing height.");
		values.woodcrawlerLeapMaxDown = config.getInt("woodcrawlerLeapMaxDown", CATEGORY, 16, 1, 32,
				"Maximum planned downward landing displacement in blocks. Ordinary fall damage is unchanged.");
		values.woodcrawlerLeapCooldownSeconds = config.getInt("woodcrawlerLeapCooldownSeconds", CATEGORY, 3, 1, 60,
				"Minimum active seconds between targeted leaps; failed attempts wait longer.");
		values.woodcrawlerLeapMinDistance = config.getInt("woodcrawlerLeapMinDistance", CATEGORY, 3, 1, 12,
				"Minimum straight-line launch-to-landing distance in blocks; close prey uses ordinary attacks.");
		values.woodcrawlerSpawnWeight = config.getInt("woodcrawlerSpawnWeight", CATEGORY, 2, 0, 20,
				"Forest monster spawn weight after Mars. Zero disables both natural and invasion Woodcrawler spawns.");
		values.woodcrawlerMaxLoaded = config.getInt("woodcrawlerMaxLoaded", CATEGORY, 16, 0, 64,
				"Maximum loaded Woodcrawlers for natural and reproduction admissions. Manual spawn eggs bypass the cap; saved creatures are retained.");
		values.woodcrawlerMaxPending = config.getInt("woodcrawlerMaxPending", CATEGORY, 16, 0, 64,
				"Combined loaded Gardener and villager-processing cocoons. Undecided outputs reserve biomass conservatively. Also bounded by the combined Gemini cap; existing cocoons are retained.");
		values.fakePersonMaxLoaded = config.getInt("fakePersonMaxLoaded", CATEGORY, 16, 0, 128,
				"Maximum loaded Fake People for conversions and initial camp residents. Zero disables these admissions; manual spawn eggs bypass the cap and saved creatures are retained. No independent biome spawn entries are added.");
		values.fakePersonConversionChance = config.getFloat("fakePersonConversionChance", CATEGORY, 0.80F, 0F, 1F,
				"Saved conversion outcome: Fake Person probability; the remainder produces a Woodcrawler (default 80/20). Population-limited cocoons wait without rerolling. Previous defaults 0.35/0.90 migrate once to 0.80; other custom values are retained.");
		String fakePeople = CATEGORY + ".fakePeople";
		values.fakePersonHilltopScreaming = config.getBoolean("hilltopScreaming", fakePeople, true, "Enable occasional hilltop screams from idle Fake People of either disguise.");
		values.fakePersonHilltopSearchRadius = config.getInt("hilltopSearchRadius", fakePeople, 48, 4, 48, "Maximum distance in blocks from the Fake Person to a candidate hilltop.");
		values.fakePersonScreamMinIntervalSeconds = config.getInt("screamMinIntervalSeconds", fakePeople, 90, 10, 86400, "Minimum seconds before another hilltop scream opportunity.");
		values.fakePersonScreamMaxIntervalSeconds = Math.max(values.fakePersonScreamMinIntervalSeconds,
				config.getInt("screamMaxIntervalSeconds", fakePeople, 180, 10, 86400, "Maximum randomized cooldown in seconds. Raised to the minimum interval if entered below it. Missing hilltops or interruptions can delay screams further."));
		values.fakePersonScreamAudibleDistance = config.getInt("screamAudibleDistance", fakePeople, 96, 16, 192, "Nominal audible distance in blocks. Uses native positional sound attenuation.");
		values.fakePersonScreamNightProbability = config.getFloat("nighttimeScreamProbability", fakePeople, 0.75F, 0F, 1F, "Chance to try for a hilltop when an opportunity comes up at night. Other times use one quarter of this chance.");
		if(Float.isNaN(values.fakePersonScreamNightProbability)) values.fakePersonScreamNightProbability = 0.75F;
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
				"Legacy pacing input when campIntervalSeconds is absent (256 maps to 300 seconds). Zero still disables automatic camps. Loaded-area scheduling replaces the old new-chunk roll.");
		values.wretchTerrainDestruction = config.getBoolean("terrainDestruction", wretch, true,
				"Allow provoked animated hand strikes against combat and movement obstructions, including canopy support, inside the saved territory. Respects mobGriefing, Forge protection events, tiles and construction permissions, including manual Wretches.");
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
				"Enable natural underground Gardeners and excavated nests in phases 3 and 4. Disabled saved natural bosses remain dormant; manual spawn eggs bypass this setting.");
		values.gardenerResidents = config.getInt("residentsPerNest", gardener, 3, 2, 3,
                "Living resident target per nest (2-3). Completed nests replenish periodically with new identities; unloaded survivors retain their slots.");
        values.gardenerMaxLoaded = config.getInt("maxLoaded", gardener, 16, 0, 16,
                "Underground Gardener admission cap, including unloaded living identities. Saved bosses are retained; maximum 16. Legacy key name retained.");
        int residentDefaultsVersion = config.getInt("residentDefaultsVersion", gardener, 0, 0, 2,
                "Internal migration: previous default Gardener caps of 2 or 3 become 16 once. Other custom caps remain unchanged.");
        if(residentDefaultsVersion < 2) {
            if(values.gardenerMaxLoaded == 2 || values.gardenerMaxLoaded == 3) {
                values.gardenerMaxLoaded = 16;
                config.get(gardener, "maxLoaded", 16).set(16);
            }
            config.get(gardener, "residentDefaultsVersion", 0).set(2);
        }
        values.gardenerReplenishSeconds = config.getInt("replenishSeconds", gardener, 300, 10, 86400,
                "Active seconds between single resident replacements after death. Initial residents spawn on nest completion and retry blocked admissions each active second. No catch-up after unloading or shutdown.");

		values.gardenerNestChance = config.getInt("nestChance", gardener, 8, 1, 10000,
				"Legacy pacing input when nestIntervalSeconds is absent (8 maps to 120 seconds). Loaded-area scheduling replaces the old discovery roll; saved construction requests at most one missing chunk per tick. No offline catch-up.");
		values.gardenerNestLimit = config.getInt("nestLimit", gardener, 32, 0, 64,
				"World lifetime nest limit, including abandoned partial nests and defeated bosses. Zero disables new nests; saved jobs can finish. Expanded nest footprints may not overlap.");
		values.gardenerNestMinDistance = config.getInt("nestMinDistance", gardener, 64, 48, 512,
				"Preferred minimum nest-center distance from an active area. Living Creative/Survival/Adventure players anchor generation; a nearby established home is preferred. If no footprint fits, closer loaded terrain is used.");
		values.gardenerNestMaxDistance = config.getInt("nestMaxDistance", gardener, 160, 48, 512,
				"Maximum nest-center distance from a living player/home anchor. Raised to the minimum if smaller. Prefer loaded terrain; if none fits, stage the enlarged footprint with at most one missing chunk request per tick.");
		values.gardenerNestMaxDistance = Math.max(values.gardenerNestMinDistance, values.gardenerNestMaxDistance);
        values.gardenerHuntingRadius = config.getInt("huntingRadius", gardener, 160, 64, 512,
                "Nighttime surface roaming radius around each Underground Gardener's nest. Pursuit may extend 48 blocks farther; only loaded terrain is used.");
		values.gardenerNestsPerArea = config.getInt("nestsPerArea", gardener, 3, 0, 8,
				"Lifetime nests per survival area within nestMaxDistance plus 32 blocks. Existing homes remain fixed; active players away from home can seed other areas.");
        values.gardenerNestIntervalSeconds = config.getInt("nestIntervalSeconds", gardener,
                Math.max(60, Math.min(86400, values.gardenerNestChance * 120 / 8)), 60, 86400,
                "Active seconds between successful automatic nests. First eligible attempt within 20 seconds; failures retry after 60 seconds. Saves retain countdowns; phase changes shorten them to 20 seconds; reload clamps to the new interval/retry delay.");
        values.wretchCampIntervalSeconds = config.getInt("campIntervalSeconds", wretch,
                Math.max(60, Math.min(86400, values.wretchCampChance * 300 / 256)), 60, 86400,
                "Active seconds between successful automatic camps near living players of any game mode in phase 4. First attempt within 20 seconds; failed searches retry after 60 seconds. No biome or new-chunk requirement.");
        values.wretchCampLimit = config.getInt("campLimit", wretch, 256, 0, 256,
                "Lifetime registered camp limit per dimension. Zero pauses new automatic camps; existing camps/residents are retained.");
        values.wretchCampMinDistance = config.getInt("campMinDistance", wretch, 80, 64, 512,
                "Preferred minimum camp-center distance from a living Creative/Survival/Adventure player, in blocks. If no loaded footprint fits, closer loaded terrain is used.");
        values.wretchCampMaxDistance = Math.max(values.wretchCampMinDistance,
                config.getInt("campMaxDistance", wretch, 160, 64, 512,
                        "Maximum camp-center distance from a living player; raised to the minimum if smaller. Prefer loaded terrain; if none fits, request one nearby 80-block footprint (at most 49 chunks)."));
        values.wretchCampSpacing = config.getInt("campSpacing", wretch, 256, 208, 1024,
                "Minimum center spacing between camps in blocks, including legacy queued settlements. The minimum prevents overlap of the largest layouts.");
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
        irisRendering = values.irisRendering;
        irisEffects = values.irisEffects;
        irisSize = values.irisSize;
        irisPupilSpeed = values.irisPupilSpeed;
        irisObservationSeconds = values.irisObservationSeconds;
        irisObservationDurationSeconds = values.irisObservationDurationSeconds;
        irisAmbientSeconds = values.irisAmbientSeconds;
        irisIntensity = values.irisIntensity;
        irisProgressionVisibility = values.irisProgressionVisibility;
        irisMinRenderChunks = values.irisMinRenderChunks;
        lightsEnabled = values.lightsEnabled;
        lightsMinPhase = values.lightsMinPhase;
        lightsIntervalSeconds = values.lightsIntervalSeconds;
        harvestIntervalSeconds = values.harvestIntervalSeconds;
        lightsMaxActive = values.lightsMaxActive;
        lightsDurationSeconds = values.lightsDurationSeconds;
        lightsDetectionRange = values.lightsDetectionRange;
        lightsHuntingRange = values.lightsHuntingRange;
        lightsHarvestDamage = values.lightsHarvestDamage;
        lightsMovementSpeed = values.lightsMovementSpeed;
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
        gardenerResidents = values.gardenerResidents;
        gardenerReplenishSeconds = values.gardenerReplenishSeconds;
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
		flashlightRange = values.flashlightRange;
        woodcrawlerStalkSeconds = values.woodcrawlerStalkSeconds;
        woodcrawlerMemorySeconds = values.woodcrawlerMemorySeconds;
        woodcrawlerLeapMaxHorizontal = values.woodcrawlerLeapMaxHorizontal;
        woodcrawlerLeapMaxUp = values.woodcrawlerLeapMaxUp;
        woodcrawlerLeapMaxDown = values.woodcrawlerLeapMaxDown;
        woodcrawlerLeapCooldownSeconds = values.woodcrawlerLeapCooldownSeconds;
        woodcrawlerLeapMinDistance = values.woodcrawlerLeapMinDistance;
        if(startup) woodcrawlerSpawnWeight = values.woodcrawlerSpawnWeight;
        woodcrawlerMaxLoaded = values.woodcrawlerMaxLoaded;
        woodcrawlerMaxPending = values.woodcrawlerMaxPending;
        fakePersonMaxLoaded = values.fakePersonMaxLoaded;
        fakePersonConversionChance = values.fakePersonConversionChance;
        fakePersonHilltopScreaming = values.fakePersonHilltopScreaming;
        fakePersonHilltopSearchRadius = values.fakePersonHilltopSearchRadius;
        fakePersonScreamMinIntervalSeconds = values.fakePersonScreamMinIntervalSeconds;
        fakePersonScreamMaxIntervalSeconds = values.fakePersonScreamMaxIntervalSeconds;
        fakePersonScreamAudibleDistance = values.fakePersonScreamAudibleDistance;
        fakePersonScreamNightProbability = values.fakePersonScreamNightProbability;
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
        gardenerHuntingRadius = values.gardenerHuntingRadius;
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
        wretchCampIntervalSeconds = values.wretchCampIntervalSeconds;
        wretchCampLimit = values.wretchCampLimit;
        wretchCampMinDistance = values.wretchCampMinDistance;
        wretchCampMaxDistance = values.wretchCampMaxDistance;
        wretchCampSpacing = values.wretchCampSpacing;
        gardenerNestIntervalSeconds = values.gardenerNestIntervalSeconds;
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
        public boolean irisRendering = true;
        public boolean irisEffects = true;
        public float irisSize = 1F;
        public float irisPupilSpeed = 1F;
        public int irisObservationSeconds = 900;
        public int irisObservationDurationSeconds = 60;
        public int irisAmbientSeconds = 600;
        public float irisIntensity = 0.6F;
        public boolean irisProgressionVisibility = true;
        public int irisMinRenderChunks = 2;
        public boolean lightsEnabled = true;
        public int lightsMinPhase = 3, lightsIntervalSeconds = 480, harvestIntervalSeconds = 2400;
        public int lightsMaxActive = 10, lightsDurationSeconds = 160;
        public int lightsDetectionRange = 80, lightsHuntingRange = 42;
        public float lightsHarvestDamage = 10F, lightsMovementSpeed = 0.38F;
        public boolean enabled = true;
        public boolean geminiOnly = false, geminiStageSpawnSuppression = true;
        public float geminiFinalStageHostileSpawnMultiplier = 0F;
        public final float[] geminiIntermediateStageHostileSpawnMultipliers = {0.75F, 0.5F, 0.25F};
        public int phase2DelayDays = 3, phase3DelayDays = 5, phase4DelayDays = 7;
        public final int[] phaseEncounterPercent = {25, 50, 75, 100};
        public int maxLoadedCreatures = 32;
        public int gardenerMaxLoaded = 16, wretchMaxLoaded = 1;
        public int minEventIntervalSeconds = 45;
        public int maxEventIntervalSeconds = 90;
        public final int[] woodcrawlerInvasionMinIntervalSeconds = {45, 30, 20, 15};
        public final int[] woodcrawlerInvasionMaxIntervalSeconds = {90, 60, 45, 30};
        public final int[] woodcrawlerInvasionMinCount = {2, 2, 3, 4};
        public final int[] woodcrawlerInvasionMaxCount = {3, 4, 5, 6};
        public int woodcrawlerInvasionMaxSpawnAttempts = 24;
        public boolean geminiPreventSleeping = true, woodcrawlerInvasionDiagnostics = false;
        public int woodcrawlerDetectionRange = 32;
		public int flashlightRange = 20;
        public int woodcrawlerStalkSeconds = 6;
        public int woodcrawlerMemorySeconds = 20;
        public int woodcrawlerLeapMaxHorizontal = 12, woodcrawlerLeapMaxUp = 6, woodcrawlerLeapMaxDown = 16;
        public int woodcrawlerLeapCooldownSeconds = 3, woodcrawlerLeapMinDistance = 3;
        public int woodcrawlerSpawnWeight = 2;
        public int woodcrawlerMaxLoaded = 16, woodcrawlerMaxPending = 16;
        public int fakePersonMaxLoaded = 16;
        public float fakePersonConversionChance = 0.80F;
        public boolean fakePersonHilltopScreaming = true;
        public int fakePersonHilltopSearchRadius = 48;
        public int fakePersonScreamMinIntervalSeconds = 90;
        public int fakePersonScreamMaxIntervalSeconds = 180;
        public int fakePersonScreamAudibleDistance = 96;
        public float fakePersonScreamNightProbability = 0.75F;
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
        public int gardenerResidents = 3;
        public int gardenerReplenishSeconds = 300;
        public int gardenerNestChance = 8;
        public int gardenerNestLimit = 32;
        public int gardenerNestMinDistance = 64, gardenerNestMaxDistance = 160, gardenerNestsPerArea = 3;
        public int gardenerHuntingRadius = 160;
        public int gardenerSupportLimit = 4;
        public int gardenerBlocksPerSlam = 12;
        public int gardenerCocoonSeconds = 120, gardenerCocoonMaxLoaded = 16;
        public boolean oceanGardenerEnabled = true;
        public int oceanGardenerMaxLoaded = 1, oceanGardenerChance = 32;
        public boolean wretchEnabled = true;
        public float wretchHealth = 900F;
        public int wretchCampChance = 256;
        public int wretchCampIntervalSeconds = 300, wretchCampLimit = 256;
        public int wretchCampMinDistance = 80, wretchCampMaxDistance = 160, wretchCampSpacing = 256;
        public int gardenerNestIntervalSeconds = 120;
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
