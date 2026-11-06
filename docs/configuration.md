# Configuration Guide

RNTM has three main configuration styles:

1. Forge configuration values in `config/hbm.cfg`.
2. JSON configuration files in `config/hbmConfig/`.
3. JSON recipe templates and overrides in `config/hbmRecipes/`.

Always stop the server before editing startup configuration unless a command explicitly supports runtime reloads.

## Energy, fluid and machine settings

Electrical energy is shown in joules and power in watts. Machine JSON uses exact half-joule counts for migrated capacity keys such as `energyCapacityQuanta`; explicit older settings remain supported where the native loader accepts them. A fresh default does not overwrite a value already saved in the server's configuration.

Fluid quantities depend on the fluid: ordinary liquid litres, standard gas litres, steam water mass and specialist quantities are distinct. Capacity does not increase process throughput or generator power. Reducing capacity preserves stored contents and stops further filling until room exists. Reactor HU is separate from qualified process heat.

See [compatibility and persistence](development/compatibility.md), [generator configuration](development/generator_reference.md#configuration-and-recipe-compatibility) and [fluid quantities](development/fluid_reference.md) for exact keys, units and migration rules. [Water quality](gameplay/chemistry.md#water-quality) explains which water to supply.

## Main Forge config: `config/hbm.cfg`

The main config is generated from these source categories:

| Category | Typical contents |
| --- | --- |
| `01_general` | Debug mode, guide book, MOTD, sound extension, atmosphere threading, nuclear warfare toggle, compatibility toggles, visual/server safety options. |
| `02_ores` | Overworld, nether, end, bedrock, cluster, oil, gas, and random ore generation rates. |
| `03_nukes` | Blast radii for nuclear devices and related explosive devices. |
| `04_dungeons` | Structure spawn frequencies for radio stations, antennas, power plants, factories, capsules, vaults, pyramids, and other generated structures. |
| `05_meteors` | Meteor strike, shower, tail, special-meteor, chance, and duration settings. |
| `06_explosions` | Procedural explosion speed, lifespan, fallout range/delay/rain values, and explosion chunk loading. |
| `07_missile_machines` | Missile machine behavior where applicable. |
| `08_potion_effects` | Potion IDs and potion-related settings. |
| `09_machines` | Machine balance and behavior settings. |
| `10_dangerous_drops` | Dangerous drop behavior where applicable. |
| `11_tools` | Tool balance and tool behavior settings. |
| `12_mobs` | Raid, elemental, duck, mob gear, glyphid hive, infestation, and swarm settings. |
| `13_radiation` | Radiation system tuning. |
| `14_hazard` | Hazard behavior where applicable. |
| `15_structures` | Structure toggles/flags. |
| `16_biomes` | Crater biome IDs and crater radiation values. |
| `17_dims` | Dimension IDs, celestial body enable flags, and space-related generation values. |
| `18_pollution` | Pollution behavior where applicable. |
| `19_weapons` | Weapon balance and behavior settings. |
| `20_rabies` | Rabies enable flag, infected bat entries, per-bite transmission, disease timing, roaming/pursuit, optional water behavior and modded mammal allowlist. |
| `21_gemini` | Mars-gated Gemini invasion enable flag and randomized event opportunity intervals. |
| `528` | 528-mode progression/balance controls. |
| `LESS BULLSHIT MODE` | Easier progression recipe and safety controls; forced off while 528 mode is enabled. |

### High-impact keys

| Key | Default | Meaning |
| --- | ---: | --- |
| `2.0RTM` | `true` | Enables nuclear warfare, primarily for server moderation. |
| `enable528Mode` | `true` | Enables 528 mode. |
| `enableLessBullshitMode` | `false` | Enables easier progression mode when 528 mode is off. |
| `1.42_threadedAtmospheres` | `true` | Runs atmosphere blobbing on a separate thread for performance. |
| `1.43_serverSafety` | `false` | Enables automated entity culling behavior described by the config comment. |
| `1.99_enableExpensiveMode` | `false` | Enables expensive-mode balance. |
| `6.XX_enableChunkLoading` | `true` | Allows procedural explosions to keep the central chunk loaded. |

### Gemini invasion foundation

`21_gemini.enabled` defaults to `true`. All Gemini activity is gated by the world-level `geminiMarsVisited` milestone, recorded only when a server player is actually in Mars's existing Duna dimension. Crafting, launching a rocket, visiting another planet, and orbiting Mars do not qualify. A real arrival by dimension transfer, login or respawn qualifies. The milestone is shared by all players and survives death, disconnection and server restarts, including visits made while invasions are disabled. Worlds without a saved milestone remain locked until a qualifying arrival is observed; earlier visits cannot be inferred from old saves.

`minEventIntervalSeconds=1200` and `maxEventIntervalSeconds=2400` choose an inclusive random delay of 20–40 minutes at 20 TPS before the first and each subsequent event opportunity. Each setting accepts 1–86400 seconds; the maximum is raised to the minimum when necessary. The countdown advances on the server only while enabled and Overworld players are present. It is saved in Overworld `data/GeminiInvasion.dat`, alongside `geminiMarsVisited`, and resumes after restart without catching up offline events. Existing countdowns retain their remaining delay when interval settings change; new intervals use the new values.

Gemini creatures and future producers use `GeminiInvasionManager.canActivityOccur(world)` before spawning, spreading, generating or updating activity; the gate permits only the Overworld. Scheduled integrations can subscribe to `GeminiInvasionManager.InvasionEvent` on `MinecraftForge.EVENT_BUS`, dispatched synchronously on the server thread after the gate and delay. Woodcrawlers supply a bounded forest encounter. Subscribers should select one bounded event per opportunity; deferred work must check the gate again when executed. Setting `enabled=false` prevents event dispatch and activity eligibility and pauses the countdown without removing the milestone.

#### Fake People and Woodcrawler captives

Fake People are separate hostile entities created from captured vanilla villagers, with no independent natural or invasion spawns or breeding. Their melee wounds can transmit Deep Root Disease as described below; infection does not create Fake People. After 600 captive ticks (30 seconds), a Woodcrawler makes one saved roll using `fakePersonConversionChance=0.35` (0–1). A failed roll leaves the original villager captive and recoverable when the Woodcrawler dies. A successful roll waits for a collision-free nearby position and population space, retrying every 200 ticks without rerolling. The original captive is consumed only when spawning succeeds. Other integrations can supply a replacement through `WoodcrawlerConversionEvent` before the default conversion handler runs.

`fakePersonMaxLoaded=16` (0–128) limits newly admitted Fake People in the loaded Overworld, including conversions and spawn eggs. Zero disables new admissions; saved entities are retained when activity is allowed. This is a loaded population limit, not a lifetime world quota. Captured modded villager subclasses retain the existing lossless release path because their appearance data may differ. Converted vanilla villagers preserve UUID, profession skin (including Forge profession skins), child size and custom name; they do not trade or count as villagers. Existing Gemini creatures exclude them from prey selection, and normal monster classification applies to other mob interactions.

Disguised Fake People wander with native villager-style walking and door use, watch and follow survival players from about eight blocks away, and reveal when damaged, interacted with, or approached within 2.5 visible blocks. Revealed creatures pursue with short speed bursts and retaliate against non-Gemini attackers. Losing a target returns them to wandering while remaining exposed. A first non-fire lethal hit has a 25% chance to cause a three-second apparent-death collapse, followed by recovery to 45% health. Recovery can happen only once; fire kills and deaths while burning prevent it, and the collapsed body can be finished off. Apparent death awards no loot or kill callbacks. Profession, disguise, hostility and recovery state synchronize through native entity data; recovery progress and single-use status also survive saving. Paths, observed targets, staring and attack timers are transient. Disabling Gemini activity, closing the Mars gate, or Peaceful difficulty removes active Fake People; Woodcrawlers retain their captive-release safeguards.

#### Deep Root Disease

Deep Root Disease is a separate infection in the existing disease framework. `21_gemini.deepRoot.enabled=true` controls establishment, symptoms and progression, in addition to the shared Gemini enable setting and Overworld Mars gate. Infected entities retain their records while activity is disabled or outside the Overworld, with movement restored and visible growths hidden. Only loaded, living, susceptible hosts advance; unloading, disconnecting, restart downtime and paused activity never cause catch-up.

| Setting in `21_gemini.deepRoot` | Default | Behavior |
| --- | --- | --- |
| `exposureSeconds` | 300 | Exposure has no major symptoms. |
| `rootsSeconds` | 600 | Roots reduce movement speed by 10%, with intermittent visible root traces and player nausea. |
| `contractionSeconds` | 600 | Contraction reduces movement speed by 20% and adds short muscle spasms. |
| `bulbsSeconds` | 600 | Persistent external bulbs and roots appear; movement speed is reduced by 30%. |
| `sproutsSeconds` | 600 | Larger branching growths appear; movement speed is reduced by 55%. |
| `playerTransmissionChance` | 0.25 | Establishment probability per confirmed carrier melee wound against a susceptible player. |
| `mobTransmissionChance` | 0.4 | Establishment probability per confirmed carrier melee wound against a susceptible villager or animal. |

Durations accept 1–604800 seconds at 20 TPS; chances accept 0–1. Each infection snapshots all five durations and saves its stage and elapsed loaded ticks under the existing `RNTM_DISEASES` extended entity properties. Changing durations affects new infections. Terminal follows Sprouts and lasts indefinitely, with 60% movement slowing. From Contraction onward, a six-tick spasm every 200 active ticks briefly reduces movement speed by 95% and interrupts horizontal momentum. Players retain movement between spasms, inventory and combat control, their original entity, and normal death/respawn behavior. Terminal never deals disease damage or causes instant death. Death clears player infection; non-death player cloning preserves the exact record. Milk and potion clearing do not remove the infection. Operators can use `/ntmdisease clear <player|entityId> deep_root` to remove it independently of rabies.

Survival players, vanilla villagers, cows, mooshrooms, pigs, sheep, living horses/donkeys/mules, wolves, ocelots and chickens are susceptible, including children. Creative/invulnerable players, undead equines, Gemini creatures and unknown modded animal/villager subclasses are excluded. Hosts can carry rabies and Deep Root independently. Deep Root patients do not transmit disease to one another; Woodcrawlers and Fake People opt into carrier transmission. Capturing a villager is not a wound and adds no infection roll; its existing disease data remains in the captive snapshot, without progression while stored. Protection-canceled hits, projectiles, fully absorbed attacks, mere proximity and targets already infected do not establish or reset infection.

For integrations, implement `IDeepRootCarrier` on a Gemini creature to opt its native `mob` melee into confirmed-wound transmission. `GeminiInfections.transmitWound(carrier, victim)` is the shared server-thread entry point for confirmed wounding contact; the existing disease event handler calls it after verifying native combat history. Do not call it a second time from an entity's melee callback. Future validated environmental interactions can use deterministic `GeminiInfections.expose(victim, source, sourceHost)`. Both paths retain host rules, duplicate rejection and the canonical Mars/enable/Overworld establishment gate. `GeminiInfections.isConversionEligible(victim)` exposes terminal eligibility only for active, loaded non-player hosts. Nature's Mockery and its conversion handler are not implemented; no replacement is attempted until a future integration supplies that behavior.

Clients receive only presentation stage and spasm state through a tracking packet, including initial tracking and the infected player's login, respawn and dimension lifecycle. Native attribute and potion packets carry movement penalties and nausea. Growths use Forge living-render events, existing flesh texture and the actual native animated torso. Rendering is distance-limited to 48 blocks and supports the known biped, villager and animal models; unknown replacement models omit growth attachments rather than guess their anatomy. Infection processing uses existing loaded-living callbacks and does not scan the world's entity list or load chunks.

### Rabies

`20_rabies.enabled` defaults to `true`. Set it to `false` to disable reservoir seeding, disease behavior and transmission. Records remain saved; their timelines catch up if enabled again. Bats first encountered while disabled are marked checked and are not later rerolled.

Rabid animals show small white saliva flecks at the mouth during **CLINICAL, NEUROLOGICAL and TERMINAL** illness in both furious and paralytic presentations. Incubation, shedding, healthy/dead animals, players and villagers have no froth. Clearing infection or disabling disease stops emission; existing flecks expire naturally. This is cosmetic and cannot transmit infection. Wolves, cows/mooshrooms, pigs, sheep, horses/donkeys/mules, cats and bats use their rendered head/jaw poses, including young animals, sitting cats and hanging bats. Unknown modded head models omit the effect rather than guessing a mouth position. Clients emit at randomized 10–20 tick intervals within the vanilla 16-block particle distance, with a shared four-particle-per-tick cap; Decreased particles reduces emission and Minimal disables it. Only tracking clients receive a visual eligibility bit; infection records remain private and server-owned.

`batchance=0.08` means an eight percent chance that a bat already has rabies when it first enters the simulation, including existing bats first encountered after installing this feature. It is rolled once and saved, never on every chunk load. These entries represent existing infections entering the area, not virus evolution or an assertion about rabies' historical origin. All established infections start in incubation; players have a separate early symptom delay.

`dogchance=0.08` means an eight percent establishment probability **per qualifying infectious bat bite against a wolf**, wild or tamed. It is independent of `batchance`. Separate `batToPlayer`, `batToOtherMammal`, `wolfToPlayer`, `wolfToWolf`, `wolfToOtherMammal`, `otherMammalToPlayer` and `otherMammalToMammal` settings also default to `0.08`. These are gameplay defaults, not measured biological prevalence or transmission rates. All probabilities accept the inclusive range `[0, 1]`; out-of-range/nonfinite values reset to their defaults. A failed establishment roll leaves the host susceptible. Infection of a new species neither boosts these probabilities nor makes that species a reservoir or proves sustained transmission.

Default susceptible hosts are players (except invulnerable/creative players), bats, wolves, cows, mooshrooms, pigs, sheep, living horses/donkeys/mules, ocelots/cats and villagers. Vanilla animal classes are matched exactly to avoid accidentally admitting robotic mod subclasses. `mammalAllowlist` adds exact `EntityList` registry names for modded mammals; the administrator supplies that biological classification. It does not add reservoir seeding. Undead, arthropods, chickens, aquatic mobs and golems remain excluded. Other unlisted mobs are not susceptible. Modded host behavior requires the normal `EntityLiving` task/navigation system; a custom movement implementation may need its own integration.

Villagers remain susceptible to infectious animal bites and the Rabies Virus Syringe, but **villager bites never transmit rabies**, as a gameplay rule. Their attacks still cause normal damage. Furious villagers retain pursuit, initial staring, target switching and repeated attacks, with the same neurological impairment and progression.

Transmission requires an infectious host's close-range bite that causes a confirmed wound after cancellation, shields, armor, resistance and absorption. Attack and wound checks use the same body bounding boxes and require unobstructed physical reach. Transmission probability is independent of damage magnitude. Native vanilla wolf melee counts as a bite; ordinary attacks by players or other mammals, projectiles, kicks, thorns and contact do not. Infectious bats can bite at close range, and furious bats pursue through their native flight controller. Furious ground mammals receive a removable bite task that takes precedence over passive movement, including baby following and sitting, while retaining swimming and door handling. Sitting commands survive saves and are restored when disease control ends; ownership and taming are preserved. Paralytic mammals receive reduced movement and later neurological impairment without forced pursuit. Normal wolf bites can transmit in the silent shedding phase as well as clinical illness. No proximity, breathing, water, intact-skin, carcass, scratch or unloaded contact transmission is simulated.

Furious CLINICAL animals roam restlessly when no target is available, choosing varied, safe local destinations from their current position at randomized 40–100 tick (two to five second) intervals. Occasional 10–40 tick pauses interrupt roaming with a change of attention. Ground movement uses segments of up to 12 blocks and native paths, with heading changes at decisions rather than every tick; it is not tethered to the infection location. Failed or finished roaming paths retry after a bounded 10–30 tick delay. Active encounters use one creep phase followed directly by sustained sprint pursuit, without recurring pursuit hesitations. NEUROLOGICAL animals retain aggression and existing Slowness/flight impairment; pursuit multipliers are capped at 1 before impairment so the clinical sprint boost cannot restore healthy coordination. Their untargeted roaming still pauses more often. TERMINAL animals stop active pursuit and continue their decline. Paralytic illness remains quieter, without forced aggression or restless roaming. Bats use separate air destinations and native flight, with approach/pursuit multipliers applied to horizontal steering acceleration; vertical lift, collision handling and neurological damping remain intact. They never use ground navigation.

A furious animal acquiring a visible, eligible target starts an approach lasting a uniformly chosen `creepMinTicks`–`creepMaxTicks` inclusive (default 60–200 ticks, or 3–10 seconds at 20 TPS). The duration is selected once per encounter. Within `stareDistance`, the existing initial stare uses `stareMinTicks`–`stareMaxTicks`, capped by the approach deadline: it is included in the approach period, never added afterward. Ground animals initially stop and face the target, then advance at `creepMovementMultiplier=0.5` times normal movement; bats face and slowly fly toward the target throughout. Targets outside stare distance begin slow movement immediately. At the deadline, active navigation switches directly to `sprintMovementMultiplier=1.5` times normal movement during CLINICAL illness, including paths already in progress. These phases are **gameplay choices**, not biological claims.

Entering unobstructed bite reach or being attacked by the current target ends the approach immediately and allows retaliation, retaining the independent 40-tick bite cooldown. Sprint pursuit remains active through repeated bites and target switches. Searches, path refreshes, failed-route retries and temporary sight loss within valid target memory never restart the deadline. Invalid, unreachable or sufficiently long-unseen targets end the encounter; a later encounter may choose a new approach. Acquisition ranges still apply, so the default bat detection range remains six blocks. During furious illness, the wolf's native melee task is temporarily suspended and native melee damage is gated so it cannot bypass the disease bite cooldown; the original task is restored afterward. Ownership, taming, saved sitting commands and silent-stage wolf behavior are preserved. All encounter and attention timers remain temporary, outside saved infection data. Phase speeds use navigation requests/native flight steering, never permanent base-speed changes or removal of unrelated attribute modifiers and potion effects; clearing infection releases movement control and restores normal navigation speed.

Target searches are local and staggered every 20 loaded ticks. Animals favor nearer visible, reachable hosts without player priority or knowledge of infection records. A target can remain beyond initial detection distance, but loss of sight permits only a brief pursuit toward its **last observed location**. Animals do not know village locations, track hidden hosts indefinitely, or search unloaded terrain. Failed/stalled pursuit releases the target. Ground navigation uses the configured pursuit range rather than the animal's usually smaller native follow range; all native path-cache chunks must already be loaded. Requests refresh at most every 20 ticks, with longer failure delays. Searches check reachability for at most three candidates; roaming tries at most two paths per retry. Dry-route attempts may each use one water-capable fallback. Dead, invulnerable, Creative and unsupported targets are rejected. Incubation and presymptomatic shedding retain normal movement. Mounted, saddled horses retain rider movement control; player infections never force movement or attacks.

Repeated bite attempts retain the 40-tick cooldown even if the victim is already infected. After a confirmed wounding bite, `targetSwitchChance` may request another local host; failed or fully protected attacks do not trigger it. An arriving host within three blocks can also draw aggression if substantially closer. Switching checks sight and reachability, retains the current target when no suitable alternative exists, and uses `targetSwitchCooldownTicks` to prevent flicker. Recent-target memory holds only three temporary entity IDs for 100 ticks. Switching never adds a transmission roll; the existing confirmed-wound path remains the sole transmission owner.

Supported animals can recognize a nearby **visible CLINICAL or NEUROLOGICAL animal** within `avoidanceDetectionDistance=12` blocks. `recognitionChance=0.7` gives a 70 percent gameplay chance once per animal/threat encounter. Recognition starts avoidance; failure leaves ordinary movement in place until a qualifying bite or a distinct later encounter. Silent incubation and shedding are not recognizable. Players and villagers receive no avoidance movement and are not visual animal threats. No threat is detected through walls or unloaded terrain.

A confirmed infectious animal bite first completes the existing transmission attempt, then triggers escape for `biteEscapeTicks=160` (eight seconds at 20 TPS), even when establishment fails or the attacker was silently shedding. Each further bite refreshes that duration and identifies its actual attacker. A newly infected victim can continue escaping during incubation and silent shedding; escape never removes infection. Furious disease aggression and neurological/terminal impairment take precedence over the victim response. Villager behavior, bite damage, transmission chances and saved infection timelines are unchanged.

Avoidance and escape choose local destinations 6–12 blocks away, favoring paths that increase separation. Ground animals use native navigation and movement; bats use their existing flight controller. Swimming and obstacle handling remain available. Escape interrupts ordinary wandering, following, mating and temptation while respecting rider control; commanded sitting survives saves. Recognized encounters end beyond the detection radius plus four blocks, or after 60 ticks without sight, preventing rapid movement switching at the boundary. Bite escape lasts its configured duration while the attacker remains alive and loaded; only its last observed location guides movement when hidden. Failed routes retry after 20 ticks; successful movement decisions are spaced 40 ticks apart, with at most two ground path requests per decision. Searches are staggered every 20 ticks and check at most 16 new candidates plus eight remembered encounters. Recognition decisions persist while the threat remains visible in the encounter area; 100 ticks of absence ends that memory. A full memory defers additional decisions instead of evicting nearby threats and rerolling them. Encounter decisions, threats and escape timers are temporary and are not saved in infection data.

| Behavior setting | Default | Meaning |
| --- | ---: | --- |
| `groundDetectionDistance` | `32` | Visible ground-host acquisition radius, in blocks; range 1–64. |
| `groundPursuitDistance` | `48` | Ground retention/navigation range, in blocks; range 1–64, raised to detection distance if smaller. |
| `batDetectionDistance` | `6` | Visible bat-host acquisition radius, in blocks; range 1–12. |
| `batPursuitDistance` | `10` | Bat retention radius, in blocks; range 1–16, raised to bat detection distance if smaller. |
| `lostSightTicks` | `60` | Maximum ticks following the last observed position; range 1–200. |
| `targetSwitchChance` | `0.5` | Post-wound chance to seek another visible, reachable host, subject to switching cooldown. |
| `targetSwitchCooldownTicks` | `40` | Cooldown for target changes and post-wound switching attempts; range 1–1200. |
| `stareDistance` | `20` | Gameplay distance for an initial visible-target stare, in blocks; range 1–64, subject to acquisition range. |
| `stareMinTicks` | `40` | Minimum initial stare duration within the approach period; range 1–1200 ticks. |
| `stareMaxTicks` | `60` | Maximum initial stare duration, capped by approach deadline; range 1–1200 ticks, raised to the stare minimum if smaller. |
| `creepMinTicks` | `60` | Minimum total approach duration, including stare; range 1–1200 ticks. |
| `creepMaxTicks` | `200` | Maximum total approach duration; range 1–1200 ticks, raised to the creep minimum if smaller. |
| `creepMovementMultiplier` | `0.5` | Slow approach relative to normal movement; finite and positive. |
| `sprintMovementMultiplier` | `1.5` | Sustained CLINICAL pursuit relative to normal movement; finite and positive, capped at 1 during neurological illness before existing impairment. |
| `avoidanceDetectionDistance` | `12` | Gameplay radius for recognizing visible clinical/neurological animals, in blocks; range 1–32. |
| `recognitionChance` | `0.7` | Gameplay recognition chance per animal/threat encounter; range 0–1, independent of transmission. |
| `biteEscapeTicks` | `160` | Gameplay escape duration after a confirmed infectious animal bite; range 1–1200 ticks, refreshed by another bite. |
| `playerWaterContactPenalty` | `true` | Optional gameplay penalty during symptomatic water contact in either presentation. |
| `animalWaterAvoidance` | `true` | Optional symptomatic preference for dry routes/destinations, including bat air destinations. |

These distances, probabilities and avoidance rules are gameplay defaults. Invalid stare/creep durations reset to their defaults before each maximum-duration ordering check. Invalid movement multipliers (nonpositive, NaN or infinite) reset to their defaults. Water avoidance prefers dry routes but preserves swimming, obstacle handling and escape to safe land; a safe native water route can be used when a dry route fails. Terrain probes are bounded and use loaded chunks. Temporary navigation settings, look control, flight destinations and task changes are released when infection clears.

During symptomatic **furious** illness (CLINICAL, NEUROLOGICAL and TERMINAL), players cannot drink vanilla water bottles (`minecraft:potion`, metadata `0`). This is a simplified swallowing-spasm symptom, not a claim that every paralytic infection causes hydrophobia. Drinking remains possible during INCUBATING and SHEDDING. Server-side start/tick checks prevent consumption, including symptoms beginning mid-drink; blocked use leaves the bottle intact and produces no empty bottle. Other potions, milk, splash potions and bottle filling remain available. A brief swallowing-spasm message is limited to once per 100 ticks and does not automatically disclose an exact diagnosis or control the camera/input.

With `playerWaterContactPenalty=true`, symptomatic players actually **in water** receive short Weakness I and Slowness I refreshes every 10 ticks. Rain and nearby water do not trigger them. Water-specific effects last at most 30 ticks after the last refresh; stronger disease or unrelated effects are preserved, and no potion effects are explicitly removed. This penalty also applies to paralytic illness as an explicit gameplay choice. Water does not transmit or cure infection and does not directly cause disease damage.

All durations use Minecraft ticks: **20 ticks = one second at 20 TPS; 24,000 ticks = one Minecraft day**. These deliberately compress real disease times; they are not a biological prediction. `clinicalTicks=24` permits only **1.2 seconds** of clinical behavior in total at 20 TPS, with neurological impairment beginning after 12 ticks (0.6 seconds); it does not mean 24 seconds. New infections snapshot the configured timeline, so existing infections retain their saved deadlines after configuration changes.

Every new player infection guarantees a full **24000-elapsed-tick treatment window**. If configured clinical onset would occur earlier, establishment shifts clinical, neurological, terminal and fatal deadlines forward by the same amount, keeping clinical onset at or after the treatment deadline and preserving all durations between those stages. Longer timelines remain unchanged. Disease damage cannot bypass this window. Animal timelines continue to use the configured durations without this adjustment; global timing defaults are unchanged.

Players develop early Weakness I and Slowness I after `earlySymptomDelayTicks=1200` elapsed ticks from their original saved infection time. This configurable delay is limited to 1–23999 ticks; invalid values reset to 1200. It is independent of saved clinical deadlines, infectiousness and fatal commitment, and never rejects HRIG within the treatment window. Loaded infections use the configured delay without rewriting timestamps or deadlines. Effects last 40 ticks and refresh on the existing once-per-second symptom update, so onset can lag the delay by up to 19 ticks. Stronger and longer same-strength effects remain intact; later clinical and neurological symptoms retain their existing strengths. Treatment stops refreshing symptoms, which expire naturally. Animal symptoms and progression are unchanged.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `earlySymptomDelayTicks` | `1200` | Player-only early Weakness I and Slowness I delay; range 1–23999 ticks, independent of clinical deadlines and HRIG eligibility. |
| `incubationTicks` | `48000` | Noninfectious incubation (two Minecraft days); silent in animals, with separate early player symptoms. |
| `presymptomaticInfectiousTicks` | `1200` | Infectious saliva shedding before clinical illness; silent in animals. Shedding continues through clinical and terminal illness. |
| `clinicalTicks` | `2400` | Behavioral illness, followed halfway through by neurological impairment. |
| `terminalTicks` | `1200` | Terminal decline with paralysis and disease damage; lethal disease damage at the deadline. |
| `furiousChance` | `0.5` | One-time gameplay selection of furious versus paralytic presentation. |

General potion symptoms update once per second per loaded host; disease behavior prepares each loaded tick and refreshes progression before using it. Drinking checks refresh progression before deciding; water-contact effects refresh every ten ticks. Bat contact checks run after native flight each loaded tick; neurological flight impairment applies every five ticks. Disease bites retain their two-second cooldown. Target searches and path refreshes remain bounded as described above. Disease pursuit and contact checks require their native chunk footprints to be loaded already. Presentation and stage are persisted separately from pathogen identity. Shared overworld total ticks advance infection while a chunk is unloaded or a player is logged out, and across dimension travel; no bites or contacts occur while unloaded. Unloading beyond the fatal deadline causes terminal consequences on the next loaded progression update. Time does not advance while the server is stopped, and `/time set` and sleeping do not skip disease time. Death/respawn clears player infections as an explicit gameplay rule, without granting immunity. Dimension cloning preserves them.

**Rabies Virus Syringe** is a separate medical consumable for controlled infection, available in the creative tab, NEI and through `/give <player> hbm:item.syringe_rabies`. It reuses the poison syringe icon. Right-click self-injects immediately, even when symptoms block drinking; the existing syringe hit-injection interaction can infect other eligible living hosts after native attack, PvP and protection checks. With rabies enabled, successful injection deterministically establishes the normal incubation timeline and presentation selection, preserving the full player HRIG window. It records `syringe:<injector UUID>` and the injector's player name for `/ntmdisease inspect`. Disabled rabies, unsupported/dead hosts and existing infections reject injection without consuming a dose or changing the original record, deadlines or fatal commitment. Success consumes one dose and returns an empty syringe using the same hand/inventory/drop rules as HRIG; Creative injectors consume nothing and receive no empty syringe. It is distinct from HRIG and the ordinary antidote.

**DID YOU ALSO EAT A KID** (`achievement.rabiesDeath`, description: "Die from rabies.") uses the virus syringe icon and has no prerequisite. Only a player's uncanceled server-side death caused by rabies disease damage awards it. Infection, Fatal status, unrelated deaths while infected, animal deaths and canceled revivals do not award it. Normal player statistics retain and synchronize the achievement across respawn.

To collect a **Rabies Virus Syringe**, strike an infected animal (including a bat) while holding an **empty syringe**. A successful direct melee wound after protection checks collects one sample during any established stage, including incubation. Misses, canceled or fully blocked damage, projectiles, thorns and indirect damage cannot collect samples. Each qualifying hit consumes one empty syringe and produces one virus syringe: the last empty becomes the filled syringe in hand; otherwise the sample goes into inventory or drops at the player if full. Creative players keep their empty syringes and receive the sample in inventory or as a drop. Collection does not cure, weaken or reset the animal's infection, and adds no exposure or infection roll.

**Human Rabies Immune Globulin (HRIG)** is a separate syringe in the medical consumables tab, crafted shapelessly from **one empty syringe, one milk bucket, one pumpkin seed and one sugar**, yielding one dose and returning the bucket through normal crafting. This crafting-table recipe is available through NEI's recipe lookup (`R` on HRIG). Its registry name remains `hbm:item.syringe_hrig`; it reuses the antidote icon without changing the existing antidote or its recipes. Right-click injects immediately, including when swallowing spasms prevent drinking; the existing syringe hit-injection behavior also permits treating another player or a villager. Right-clicking a living villager with HRIG treats it before trading, without requiring damage; other held items retain ordinary trading interactions. Successful treatment consumes one dose and returns one empty syringe (to the hand for the last self- or right-click villager dose, otherwise to inventory or dropped if full). Creative use consumes nothing and returns no empty syringe. Existing potion-sickness rules apply. Failed treatment consumes nothing and shows a localized reason.

Villager treatment uses the same eligibility rule and **24000-elapsed-tick cutoff**, calculated from the villager's saved infection time. It preserves original timestamps and deadlines; the player-only timeline extension does not apply to villagers. Successful treatment uses normal disease cleanup to release disease-owned aggression, targets, navigation, look control and temporary task changes, restoring ordinary villager behavior without changing profession, trades, reputation or unrelated NBT. Unknown/expired infection times, dead villagers and uninfected villagers retain the dose. Other animals cannot receive HRIG.

HRIG prevents an established player infection before **24000 elapsed ticks** from its original saved `infectedAt`. It is eligible at elapsed ticks 0 and 23999; at exactly 24000 ticks the window is closed. The server repairs legacy timing and refreshes progression before deciding eligibility. Stage alone never rejects early treatment, and shortened settings cannot shorten the window. Repeated bites do not reset it, and failed infection rolls start no deadline. The window uses `DiseaseManager.now`, the same overworld total-tick clock as disease progression, across logout, unloaded chunks and dimensions; sleeping, daylight, `/time set` and wall-clock time do not control it. Successful treatment removes the infection and pending progression through normal disease cleanup without clearing unrelated potion effects. Healthy living players can also receive a valid injection: it consumes one dose and returns an empty syringe under the same potion-sickness and Creative rules, creates no infection and grants no stored protection or immunity. Later exposure may establish a new infection with a new window. Infections beyond the treatment window reject injection and retain the dose.

The separate harmful **RABIES** status displays a living player's saved infection throughout incubation and every later stage. It uses `8.16_rabiesID` in the existing potion category (default `79`), leaving existing potion IDs unchanged. Server ticks restore it after potion clearing, even with disease behavior disabled or the player invulnerable; its curative-item list is empty, so milk cannot cure it. Ordinary medicines cannot remove the saved infection or permanently suppress its indicator. Expiration never removes infection, and manually applying the indicator creates no infection; an indicator without a saved record is removed on the next server tick. Successful HRIG treatment, administrative clearing and death remove the indicator without clearing unrelated effects. Vanilla potion packets synchronize this presentation; private infection records remain server-owned.

Untreated player infections save an irreversible `fatalCommitted` flag in their rabies record at the treatment deadline, using the same authoritative eligibility method as HRIG and administrative diagnostics. Infection establishment does not commit Fatal. The separate harmful **Fatal** status displays that commitment and means neurological illness will eventually cause death. Commitment does not kill immediately or replace the existing final `Rabies.Stage.FATAL`. Rabies progression still owns damage and death, including normal protection hooks. Fatal has no damage callback or second death timer. Its configurable potion ID is `8.15_fatalID` in the existing potion category (default `78`); it is separate from the existing death potion. The disease-owned effect resists milk and is restored on loaded ticks after potion clearing while commitment remains. Milk, antidotes, healing and ordinary treatment cannot erase the flag. Manually applying the visible effect creates no infection or death deadline.

Commitment survives save/load, logout and dimension cloning. A one-time migration applies the player timeline rule to living players' saved infections. Players still within their **original** window have premature commitment, its disease-owned Fatal effect and premature saved stage repaired, without changing `infectedAt`. Missing downstream deadlines are filled from the configured durations before applying the same shift; any later deadline overtaken by a reconstructed predecessor uses its configured duration to preserve stage ordering. Missing commitment defaults to false until the known cutoff. Expired windows remain closed, and dead players are never revived. A missing legacy infection timestamp remains unknown across saves: it is never treated as tick zero or replaced with the current time. HRIG preserves the dose and reports the unknown time; no new commitment is inferred without a known cutoff. Undatable records with incomplete timelines do not progress from absent deadline values. Administrators can inspect and clear such records.

Disabling disease behavior or becoming invulnerable does not reopen the treatment window or erase commitment; the visible status remains, while disease damage follows the existing enable/protection rules. Death/respawn clears player infection and commitment. Admins can inspect treatment eligibility, remaining cutoff ticks and commitment, or remove the record and disease-owned Fatal status with [`/ntmdisease`](commands.md#ntmdisease-inspectclear-playerentityid). No exact diagnosis is automatically disclosed after a bite. Removing infection restores vanilla tasks; brief shared-ID disease symptoms expire naturally without clearing unrelated effects.

The biological model follows [Merck Veterinary Manual](https://www.merckvetmanual.com/nervous-system/rabies/rabies-in-animals) on furious/paralytic presentations and fatal progression, and [CDC veterinary guidance](https://www.cdc.gov/rabies/hcp/veterinarians/index.html) on mammal bites and presymptomatic saliva shedding. The shared shedding schedule across all hosts, deterministic fatal deadline, presentation split and bite behavior are gameplay abstractions; species-specific shedding timing is not established by applying dog/cat/ferret observations to bats. [CDC post-exposure guidance](https://www.cdc.gov/rabies/hcp/clinical-care/post-exposure-prophylaxis.html) describes HRIG as immediate passive antibodies; delivery at the exposure site provides local protection while vaccination develops an immune response. Real post-exposure treatment also requires wound care and vaccination. The mod's single-item prevention and one-Minecraft-day cutoff are **gameplay simplifications**, not a claim that real treatment becomes ineffective exactly one day after exposure. Vaccines and treatment schedules are not simulated.

Merck's description of abnormal reactivity, reduced caution, extensive roaming and attacks against animals and moving objects supports the furious behavior model. It does not establish deliberate village tracking or coordinated infection. The story about one wolf biting over 200 people is unverified and is not used as evidence. The [CDC Yellow Book](https://www.cdc.gov/yellow-book/hcp/travel-associated-infections-diseases/rabies.html) describes human hydrophobia through difficulty swallowing and stimulus-triggered swallowing spasms. [WHO's rabies FAQ, question 8](https://iris.who.int/handle/10665/205089) distinguishes dog signs from human hydrophobia: fear of water is not a typical clinical sign in dogs. Universal animal water avoidance is not established rabies biology; both animal avoidance and player water-contact penalties above are configurable gameplay choices.

### World generation caution

Ore, structure, meteor, biome, and dimension settings affect world generation and world identity. Changing these on an existing save only affects newly generated content unless the setting controls runtime behavior. Dimension ID changes can strand players or break portals/travel if done after a world has been used.

## Client JSON: `config/hbmConfig/hbmClient.json`

This file stores client-only visual and UI preferences. The file is generated and rewritten by the mod. It can also be edited in game with `/ntmclient`.

Known keys include:

| Key | Default | Purpose |
| --- | ---: | --- |
| `GEIGER_OFFSET_HORIZONTAL` | `0` | Horizontal offset for Geiger HUD. |
| `GEIGER_OFFSET_VERTICAL` | `0` | Vertical offset for Geiger HUD. |
| `INFO_OFFSET_HORIZONTAL` | `0` | Horizontal offset for info HUD. |
| `INFO_OFFSET_VERTICAL` | `0` | Vertical offset for info HUD. |
| `INFO_POSITION` | `0` | Info HUD position mode. |
| `GUN_ANIMS_LEGACY` | `false` | Legacy gun animation toggle. |
| `GUN_MODEL_FOV` | `false` | Gun model FOV behavior. |
| `GUN_VISUAL_RECOIL` | `true` | Visual recoil toggle. |
| `ITEM_TOOLTIP_SHOW_OREDICT` | `true` | Ore dictionary tooltip display. |
| `ITEM_TOOLTIP_SHOW_CUSTOM_NUKE` | `true` | Custom nuke tooltip display. |
| `MAIN_MENU_WACKY_SPLASHES` | `true` | Main menu splash behavior. |
| `DODD_RBMK_DIAGNOSTIC` | `true` | RBMK diagnostic display behavior. |
| `RENDER_CABLE_HANG` | `true` | Cable hanging render behavior. |
| `NUKE_HUD_FLASH` | `true` | Nuke HUD flash effect. |
| `NUKE_HUD_SHAKE` | `true` | Nuke HUD shake effect. |
| `RENDER_REEDS` | depends on mod compatibility | Reeds render behavior. |
| `DEBUG_RENDER_GL_ERRORS` | `false` | OpenGL render error debugging. |
| `DARK_ADAPTATION_ENABLED` | `true` | Enables realistic client eye adaptation. |
| `DARK_ADAPTATION_STRENGTH` | `1.0` | Scales the bounded shadow effect (runtime-clamped to 0–2); scotopic recovery saturates at 1 so large values cannot destabilize color math. |
| `DARK_ADAPTATION_SCOTOPIC_FLOOR` | `0.055` | Legacy-compatible control for the bounded perceptual low-light target. The default maps to a 0.140 fully illuminated/adapted target before environmental, near-field, and central-vision factors (runtime-clamped to 0–0.15). |
| `DARK_ADAPTATION_ROD_SECONDS` | `90.0` | Approximate time to reach 95% of the dark rod-adaptation target (runtime-clamped to 10–300 seconds). The exponential time constant is one third of this duration. |
| `DARK_ADAPTATION_NOISE` | `0.012` | Maximum intrinsic shadow-noise amplitude (runtime-clamped to 0–0.05). |
| `DARK_ADAPTATION_CENTER_LOSS` | `0.15` | Weak central scotopic acuity/sensitivity penalty (runtime-clamped to 0–0.20). |
| `DARK_ADAPTATION_QUALITY` | `1` | `0` disables the pass, `1` uses a five-tap blur, and `2` uses nine taps with more frequent exposure updates. |
| `DARK_ADAPTATION_DEBUG` | `false` | Shows every recovery multiplier, current-frame depth copy/source/framebuffer status, debug-only 16×12 geometry coverage, shader status, and the latest failure. |
| `DARK_ADAPTATION_DEBUG_VIEW` | `0` | Debug-only output: `0` normal composite; `1` the exact recovery geometry mask (white terrain/buildings, black cleared sky/void); `2` only the mathematical-black recovery contribution, without scene RGB; `3` logarithmically remapped linear world depth (near geometry brighter, far geometry dimmer, cleared sky/void black). Ignored unless debug is enabled. |
| `DARK_ADAPTATION_HD_LIGHTMAP_COMPAT` | `true` | Preserves a small sky-light carrier before Hardcore Darkness removes nighttime source texture information. Has no effect when Hardcore Darkness or RTM dark adaptation is disabled. |
| `DARK_ADAPTATION_HD_SKY_CARRIER` | `0.06` | Base sky-factor information carrier (runtime-clamped to 0–0.12). Eye adaptation can raise the active carrier with `base + 0.16 * effectiveAdaptation * perceivedAmbient`, bounded to 0–0.25. It affects sky light only and is not a final RGB floor. |

Dark adaptation uses two deliberately separate signals. Rendered-scene exposure is metered from the completed world immediately before the HUD, or at the end of the render tick when F1 hides the HUD, with a 16×12 GPU-downsampled image; its lower 85% trimmed mean drives physiological cone and rod adaptation without letting isolated torches or particles dominate. The hidden-HUD path runs after world rendering and the vanilla shader group without drawing HUD elements. Environmental scotopic illumination instead combines saved sky-light availability around the eye with direct sky access, the vanilla night and moon phase, a nonzero starlight floor, and rain/thunder attenuation. This second signal gates recovery only when outdoor photons can plausibly reach the player, so sealed rooms and deep caves remain effectively black.

With Hardcore Darkness installed, the compatibility hook changes only the lightmap's original `0.95`/`0.05` sky-factor pair. The resulting factor is `sunBrightness * (1 - activeCarrier) + activeCarrier`. The configured value is its base; current eye adaptation and perceived outdoor ambient light can gradually raise it, up to the fixed 0.25 bound. This carrier preserves raw low-level outdoor RGB and texture information rather than prescribing final screen brightness. It does not restore the later global RGB floor or affect block light: zero-skylight sealed caves therefore remain black, while RTM's post-process still controls perception. Minecraft's nighttime lightmap is strongly blue-biased, so the retained RGB ratio is deliberately not treated as directly perceived dark color and compatibility does not depend on Hardcore Darkness's `removeBlue` option.

Environmental illumination is converted with a bounded `ambient^0.30` perceptual curve, then combined with 25% cone and 75% rod recovery and a target display luminance. RTM first extracts luminance from nonzero framebuffer RGB and applies bounded scotopic recovery to that luminance, separately doing the same for the existing blurred neighborhood. It then reconstructs texture at the recovered luminance and decides how much source chroma remains perceptible. Deep rod-dominated vision is neutral and nearly monochrome (0–5% chroma at full rod effect), twilight retains progressively more limited color, and locally bright pixels remain colored and sharp even while the eye is otherwise dark-adapted. Only effectively black RGB (the 0.00005–0.00015 transition) uses world depth for broad achromatic shape fallback.

Linearized world depth provides the forward camera-space Z coordinate. RTM combines it with the horizontal and vertical perspective scales captured from the active world projection matrix to approximate camera-to-surface distance across the whole view. That distance drives continuous, overlapping scotopic perception influences: NEAR is approximately 0–5 blocks, MID approximately 5–20 blocks, and FAR approximately 20–50+ blocks. Nearby terrain retains the most coarse texture, while blur and local-contrast loss increase smoothly through MID until FAR primarily preserves silhouettes and large forms.

Recovered low-light visibility uses a monotonic exponential long-tail distance falloff rather than reaching darkness at a fixed FAR or FADE endpoint. Its useful signal becomes gradually negligible at extreme distance but does not switch off at one configured range. Better environmental ambient light and stronger eye adaptation extend both the start and decay scale of this tail; poor conditions contract them.

This remains a screen-space perception effect, not a Minecraft light source: it does not alter block or sky values, pass through walls, affect spawning, or operate in a sealed zero-light cave. The distance envelope applies only to synthetic low-light recovery, so distant torches, lava, explosions, stars, and other bright framebuffer pixels retain their original photopic signal. Zero ambient input maps ordinary gain, the at-most-30% NEAR benefit, and exact-black recovery to exactly zero. Cleared-depth sky and void receive no synthetic floor, and depth cannot recreate texture information that Hardcore Darkness already discarded.

The compact debug overlay reports `sceneExposure`, cone/rod/effective adaptation, requested/used strength, ambient/perceived ambient, eye recovery, sky/night/moon/weather factors, expected broad/shape black luminance, `depthCopySucceeded`, `worldDepthCurrent`, `worldDepthFrameAge`, `worldDepthSource`, `worldDepthFramebuffer`, `geometryCoverage`, `depthAvailableToShader`, shader state, and failure reason. `geometryCoverage` is a debug-only GPU downsample of the exact geometry mask to 16×12; it is the fraction of samples that are not exact cleared depth, rather than a full-resolution CPU read.

Previously, `depthAvailable == true` meant only that OpenGL accepted the copy. Because color and depth were both copied at the pre-HUD event, the copied depth could be cleared or replaced and no longer represent world geometry. Depth is now copied at highest-priority `RenderWorldLastEvent`, after vanilla terrain, entities, and tile entities but before the first-person hand and HUD. Final color is captured at `RenderGameOverlayEvent.Pre(ALL)` when the overlay runs, or at render-tick end when F1 skips the overlay. The renderer records the render generation, display size, and actually bound source framebuffer, then exposes depth to the shader only when they match the color frame. It restores that framebuffer after capture and labels an active Angelica framebuffer without modifying Angelica-owned attachments.

> Note: the current source maps `ITEM_TOOLTIP_SHOW_CUSTOM_NUKE` to the `ITEM_TOOLTIP_SHOW_OREDICT` key during default registration. Treat custom-nuke tooltip editing as a documentation gap until this is clarified or fixed in code.

### Darkness and eye adaptation

The client tracks a fast cone stage (4.5 seconds in darkness) and a slow rod stage (90 seconds by default). Bright rendered exposure reverses these stages much faster (0.25 and 1.15 seconds). It never changes Minecraft's gamma option. Instead, nonzero shadow pixels supply luminance and spatial detail without forcing their night-lightmap chroma into the result. Rod dominance blends recovered local luminance toward the already sampled neighborhood, reaching 40% away from the center and at most 55% centrally in deep darkness; this is real fine-contrast and spatial-acuity loss rather than a second saturation adjustment. The subtle central penalty contributes up to 15 percentage points of that acuity loss while retaining only a slight sensitivity difference, so it does not form a visible vignette or bright peripheral ring. Animated scarcity noise is monochrome. Mathematical-black geometry receives the sky/moon/starlight-gated grayscale floor described above; depth supplies validity and shape rather than being treated as linear distance.

The final-color pass runs at the `RenderGameOverlayEvent.Pre(ALL)` boundary, after the completed world and hand but before Forge draws HUD elements. Inventory, chat, crosshair, scope/equipment overlays, debug text, and menus therefore remain unprocessed. Its separate world-depth copy occurs before the hand as described above. Vanilla night vision, blindness, and RTM thermal armor suppress the ordinary-eye pass rather than accidentally stacking visual modes.

Exposure is updated every five frames (`quality=1`) or three frames (`quality=2`) from the small rendered-scene target; RTM nuclear flashes are injected explicitly. The shader uses reusable private color and depth copies, queries/restores the previously bound framebuffer, and never samples an Angelica-owned attachment in place. If depth copying is unsupported, nonzero low-light recovery continues without mathematical-black geometry cues. Shader or color-capture failure remains nonfatal and leaves normal rendering intact.

## Dynamic machine JSON: `config/hbmConfig/hbmMachines.json`

This file is generated from tile entities that implement the configurable-machine interface. Unlike recipe templates, it is the active file: edit the values directly, then restart.

Behavior:

- Missing machine values are restored with defaults on startup.
- Unknown custom fields may be deleted when the file is rewritten.
- If an update adds a new machine option, it should be added automatically with its default.

## Custom machines: `config/hbmConfig/hbmCustomMachines.json`

Defines custom multiblock machines. If the file does not exist, the mod writes a default example and reads it.

Because custom machine definitions include blocks, ports, recipe shapes, components, capacities, power, heat, pollution, and IO counts, test all custom definitions in a creative world before deploying to a public server.

## Fluid traits: `config/hbmConfig/hbmFluidTraits.json`

Fluid traits define special fluid behavior. The code supports trait concepts such as:

- combustible or flammable behavior,
- rocket fuel behavior,
- heatable/coolable behavior,
- gaseous behavior,
- corrosive behavior,
- poisonous/toxic behavior,
- PWR moderator behavior,
- radiation venting behavior,
- pollution behavior.

The mod writes `_hbmFluidTraits.json` as a template when an active file is not present. Copy or rename the template to `hbmFluidTraits.json` to make custom trait edits active.

## Fallout JSON: `config/hbmConfig/hbmFallout.json`

Defines block transformations caused by fallout. If no active file exists, `_hbmFallout.json` is generated as a template.

Use this for pack-level tuning of how fallout damages or transforms terrain. Test with backups: fallout rules can permanently alter world blocks.

## Item pools: `config/hbmConfig/hbmItemPools.json`

Defines weighted item pools used by loot systems. If no active file exists, `_hbmItemPools.json` is generated as a template.

Entry format is documented inside the generated template. In short, entries combine an item stack definition, minimum amount, maximum amount, and weight.

## Recipe JSON: `config/hbmRecipes/`

Serializable recipes are generated in `config/hbmRecipes/`.

Foundry material quantities in `hbmCrucibleSmelting.json` use quanta: nugget = 8, ingot = 72, block = 648. The stock stone-bearing ore definitions return 648 quanta of stone per ore, matching the existing stone block mold. Active custom smelting files replace these defaults; update their ordinary ore gangue outputs explicitly if they still use smaller amounts. The large arc furnace derives its molten recipes from these definitions unless an active `hbmArcFurnace.json` supplies its own outputs, which must be updated separately. Do not increase valuable trace byproducts or remelted item contents merely to make them larger.

Important behavior:

- If an active recipe file is missing, defaults are registered and a template named `_<filename>` is written.
- To override recipes, remove the leading underscore from a template file and edit the active file.
- `/ntmreload` reloads serializable recipes and item pools without a full restart, but test carefully.

Armor-modification manufacture uses `hbmAssembler.json`, with ceramic preparation in `hbmPress.json` and `hbmBlastFurnace.json`. An active file replaces that machine's entire default registry; existing custom files are not merged or overwritten by the armor-recipe changes. Custom packs must carry the new outputs/inputs into their own active recipes explicitly. With no active override, startup and `/ntmreload` clear and rebuild these machine defaults and refresh the underscore templates. The removed crafting recipes are startup registrations, so applying this code change requires a restart; `/ntmreload` alone does not rebuild the crafting table. See [armor manufacturing](gameplay/manufacturing.md#armor-modification-manufacture).

Blast-furnace recipe inputs support `item`, `dict` and `dictframe` arrays. Counted OreDictionary inputs use `["dict", "ingotSteel", 6]`; template export and reload retain the count in either input slot. Omitting the count still means one item.

## Bedrock excavator drops

`MiningConfig` defines the default in-memory bedrock excavator drop list. Operators can edit this list during runtime with `/hbmbedrockdrop`, using entries in this format:

```text
modid:item_or_block meta min max
```

Example:

```text
minecraft:diamond 0 1 2
```

The command edits the runtime list; persistence depends on implementation details not fully documented in the current code.

## Wither Storm

The `12_mobs.witherstorm` category in `config/hbm.cfg` controls the hostile boss. Settings load at startup; clients receive phase, growth, physical scale, anatomical development and transformed head states through entity tracking. The boss has no natural spawning. Each loaded Storm requests a Forge entity ticket for its immediate flight area (at most nine chunks, subject to Forge limits). Commands and the survival ritual share the same saved encounter limit and begin with ordinary Wither anatomy unless a later form is explicitly requested.

| Setting | Default | Meaning |
| --- | --- | --- |
| `transformGrowth` / `devourerGrowth` / `thunderStormGrowth` | 12500 / 50000 / 250000 | Consumed mass thresholds, strictly ordered. The center head transforms at `transformGrowth`. Both side heads transform and tentacles emerge at `transformGrowth + max(1, (devourerGrowth - transformGrowth) / 2)` using integer division. Devourer and ThunderStorm begin above their thresholds. See the [appearance stages](commands.md#rntwitherstorm) for the full model progression. |
| `minimumStageBTicks` | 24000 | Active Stage B ticks required before automatic evolution, in addition to the growth requirement. Mass still accumulates, and elapsed ticks are saved when the boss unloads. Zero disables the timer. |
| `maxEncounters` | 1 | Global limit including unloaded saved bosses; zero disables new encounters. Use `/rntwitherstorm list` and `remove UUID` to retire an unloaded encounter. |
| `maxVisibilityDistance` | 1024 | Maximum root render distance in blocks (64–4096), reduced for earlier stages and smaller growth scales. Set identically on client and server. Only Storm/part native tracker ranges are adjusted beyond vanilla's entity-view-distance clamp; observers must still watch their chunks. Loaded chunks, camera far clipping and fog/shaders can limit actual visibility. No chunks are loaded for rendering. |
| `idleWanderingRadius` | 64 | Maximum horizontal distance of a randomly selected idle destination (0–256 blocks); zero disables wandering. Loaded terrain corridors, stage clearance and a 275-block flight ceiling constrain reachable legs. Destinations are checked every 20 ticks, with 20–60 ticks of rest between legs or failed searches. |
| `survivalSummoning` | true | Allow the Fake Command Block structure and legacy euphemium ritual described in the [command guide](commands.md#rntwitherstorm). |
| `challengeMode` | false | Default for worlds without a command override. Starts a 24,000-tick Overworld countdown the first time it is enabled in a world. |
| `fearDetectionRange` | 128 | Detection and release range in blocks for fleeing mobs. |
| `fearEscapeDistance` | 160 | Desired escape distance in blocks used to plan shorter navigation legs. |
| `fearDetectionInterval` | 10 | Ticks between threat scans and escape path attempts. |
| `passiveFleeSpeed` | 1.5 | Flee speed multiplier for nonhostile mobs. |
| `hostileFleeSpeed` | 1.7 | Flee speed multiplier for hostile mobs. |
| `terrainDestruction` | true | Consumption, skull impacts and tentacle terrain damage also require `mobGriefing`. These attacks do not use nuclear crater or fallout systems. |
| `blocksPerTick` | 4096 | Maximum successful block removals per encounter per server tick, shared with skull/tentacle impacts. Stage limits are 32, 64, 256, 1024, 2048, 2048 and 4096 from A through D. Configurable up to 4096; zero disables terrain processing. Existing explicit values remain authoritative and can cap multiple later stages at the same throughput. |
| `terrainChecksPerTick` | 16384 | Separate scan-step allowance (64–65536), including air/protection checks, skipped empty storage sections and queue work. Failed scans never spend the successful-removal allowance. |
| `terrainProcessingMicros` | 2000 | Additional elapsed-time allowance per encounter per tick (100–10000 microseconds). Work resumes on the next tick. A single block callback can exceed this allowance. |
| `maxConsumptionRadius` | 48 | Horizontal feeding radius cap (4–48), scaled from mantle dimensions, heads and intact tentacle endpoints. Existing explicit caps remain authoritative. World-aligned sections grow from 3 to 15 blocks wide, retain progress during movement, and revisit each column to consume lower layers. Vertical reach uses flight clearance and each column’s surface, so altitude does not exclude ground or tall structures. Beam targets and tentacle impacts share the removal path. |
| `consumeTileEntities` | false | Explicitly allow destroying machines/containers and their contents. |
| `protectedBlocks` | chest, ender chest, enchanting table | Registry-name blacklist. |
| `consumeProtectedBlocks` | false | Explicitly override blacklist and block-level `canEntityDestroy` vetoes. Negative hardness, bedrock, command blocks and portal infrastructure always remain protected. |
| `skullAttackTicks` | 80 | Base cooldown after a volley. Each later phase subtracts one eighth of the base, to a minimum of 40 ticks. Up to three heads fire once each, with a 10-tick windup per shot. |
| `skullAttackRange` | 96 | Maximum target distance in blocks from both the Storm and the firing mouth. Accepts 8–96. |
| `skullExplosionStrength` | 2 | Ordinary area attack strength (0–8), increased by phase. Terrain damage shares the normal processing budget and protections. |
| `entityQueryBudget` | 512 | Entries examined per 20-tick head/debris search (64–4096); at most 64 candidates retained. Tentacles use a separate complete census within 96 blocks, refreshed every 20 ticks. Player pursuit uses the dimension’s player list and existing sampled trails without a distance or visibility cutoff. |
| `debrisPerTick` | 4 | Maximum consumed-block visual records per encounter per tick (0–16), batched every five ticks. No physical debris is spawned on the server. |
| `maxClientDebris` | 128 | Client-local active 3D block debris cap across all encounters (0–512). Growth, particle quality and distance control visual density independently of actual consumption. Six metadata-aware faces share the vanilla terrain-atlas particle batch; unsupported block models use textured cube fragments. |

Physical size, damage and reach increase with mass but remain capped. Maximum boss health respects Forge 1.7.10's native 1024 health attribute limit. Heads and tentacles forward damage into that single root health pool. All targeting, pulling, consumption, damage and terrain mutation run on the server thread. Actual-removal debris is sampled independently of terrain throughput and expires after its bounded lift, upper-body hover/corruption and absorption animation (88–112 client ticks, or 180–220 for Stage D). Cleared column ceilings are cached between visits; periodic surface rediscovery catches rebuilt and previously protected structures. Scan cursors restart safely on reload; saved mass and queued impacts retain their existing formats. Skulls have a 160-world-tick lifetime and a hard cap of 24 per encounter; root-owned expiry also removes skulls suspended by vanilla's chunk-boundary tick rules. Skull launches retain a loaded-chunk margin. Flight tickets load only the immediate local area as the Storm advances; trails and terrain consumption never load remote destination chunks. If Forge denies a ticket, the Storm waits at the loaded boundary with its destination retained. Runtime entity IDs are not persisted as ownership; components save parent/encounter UUIDs and resolve them without loading chunks.

Every Storm follows the nearest connected, living Survival/Adventure player in its dimension, using the existing 20-tick sampled movement trails. A challenger must be four blocks or 5% closer to replace a valid target. New locks join the nearest trail segment toward the player instead of replaying the entire old route. Line-of-sight loss and distance beyond attack range do not cancel pursuit. Exhausted or expired routes fall back to the player’s current position. When no eligible player remains, saved trails are retained for reconnection but idle wandering owns flight. Random destinations favor the current heading, stay within loaded routes and use sampled terrain heights to climb before moving into hills. Unknown terrain under an overhanging mantle is not force-loaded. Local mobs remain attack targets without owning navigation; consumption, tentacles and destruction continue while wandering. A valid player immediately interrupts wandering without changing growth or other saved encounter state. Creative, invulnerable, fake and nonphysical players are excluded from pursuit. Without an available flight ticket, idle movement depends on ordinary entity ticking and loaded chunks.

Root and attachment distance checks use the same stage-scaled visibility policy, independent of their gameplay hitboxes. Moved attachments update native chunk membership even when their own boundary tick is suspended. Their native observers are reconciled every 20 ticks, removing watchers when their chunks leave view and allowing fresh spawn packets on re-entry. Conservative render bounds include raised and attacking arms, heads and Stage D's vortex even when the root is outside the view. Existing Stage D mesh/arm detail levels, component culling and distance-reduced vortex batches remain active; nearby particles and beam lighting keep their existing distance limits. Parts outside watched or loaded chunks can still be unavailable under normal Minecraft tracking rules.

Boss sound cues use server-sent positional playback out to 768 blocks with gradual client-side attenuation. Clips are finite and duplicate cues are suppressed. Flaming skulls use the vanilla skull mesh and texture; impacts apply fire damage and six seconds of ignition, with protected terrain removal and nearby ignition processed through the shared budget. Purple beam illumination is a fading client-side surface projection along the beam ray, with no persistent world lighting changes.

Tentacles visibly reach, whip, grab, crush and slam using server-controlled poses. They prioritize intruders inside the mantle to defend the Command Block. Strikes can consume obsidian through the existing terrain queue, subject to the same protections, configuration and work budget as other Storm terrain damage. The big Devourer's three rear curls also have tracked gameplay contacts. Tentacles emerge over 60 ticks; sustained damage can sever an arm and release its grip. After the Storm consumes another 120 mass, that arm regrows over 60 ticks. Arm damage and regrowth survive saves; transient grabs release on reload. Consumed material also repairs the Storm's body health, allowing its existing damaged form to recover.

The implementation uses [The Engender Mod's Wither Storm sources](https://github.com/Mrbt0907/The-Engender-Mod/tree/main/src/main/java/net/minecraft/AgeOfMinecraft/entity/tier6) as a behavior reference. RNT supplies original code, model geometry, procedural texture and synthesized sounds. Recording player waypoints uses positions already available to the server.

## Sandworm

Startup category `12_mobs.sandworm` in `hbm.cfg` isolates the encounter from all existing RNT bosses, fluids, dimensions and packets. Natural spawning is disabled by default; `/rntsandworm spawn` remains available to operators.

| Settings | Defaults | Behavior |
| --- | --- | --- |
| `dimensionID` | -38 | Must be unused. A conflict fails startup with a diagnostic instead of replacing another provider. Keep this ID for the lifetime of a save; the ledger rejects changing it. Interior terrain uses its own `DIM_RNT_Sandworm` folder. |
| `naturalSpawning`, `naturalSpawnInterval` | false, 24000 ticks | One bounded overworld desert attempt near a non-Creative player; loaded terrain only. |
| `maxActiveEncounters` | 8 | Saved unloaded and generating encounters count too; maximum 64. |
| `movementSpeed`, `targetRange`, `mouthRadius`, `swallowCreativePlayers` | 0.7, 128, 6, false | Server-controlled movement, attraction and mouth swallowing. Eleven connected native multipart components share one entity registration. No segment entities or chunk tickets. |
| `returnSwallowCooldownTicks` | 200 | Saved grace after returning or respawning, giving players time to move away from the original mouth. |
| `brainHealth`, `brainDamage`, `brainAttackTicks` | 200, 10, 100 | Stationary Brain, radial bursts of 12–20 energy shots. Allied living Sparks within ten horizontal/five vertical blocks restore its shield. |
| `sparkWaveTicks`, `sparkWaveMin`, `sparkWaveMax`, `maxSparksPerBrain` | 600, 3, 6, 24 | Initial and periodic Brain waves; inclusive wave size and local population cap. A saved encounter-wide cap of `maxSparksPerBrain + room count` also includes unloaded Sparks, preventing wandering waves from accumulating indefinitely. |
| `heartHealth`, `heartInvulnerabilityTicks` | 200, 100 | Wounds accelerate pumping and trigger temporary invulnerability and blood defense. |
| `sparkHealth`, `sparkDamage`, `sparkHealing`, `sparkEffectTicks`, `sparkBlindnessTicks` | 20, 10, 20, 20, 100 | Orange damage, green allied healing, blue knockback and purple blindness are server-owned. |
| `tickHealth`, `tickDamage`, `tickHealthGrowth`, `bloodGrowthTicks` | 40, 10, 0.2, 20 | Five growth increments add a fraction of base max health and heal fully; model and hitbox scale together. Health respects Forge's native 1024 limit. |
| `dungeonRows`, `dungeonBlocksPerTick`, `dungeonChunksPerTick` | 7, 512, 2 | Three rooms across, 21×10×21 per room. Rows and door/rotation data persist per encounter. Global generation/cleanup coordinate budget and construction chunk-load budget are shared across encounters. |
| `bloodDamage`, `acidDamage`, `gasDamage` | 10 each | Contact damage once per second; blood and acid slow players. Tumors release temporary acid and fully grown Ticks leave temporary blood when mobGriefing permits. |
| `cleanupDelayTicks`, `abandonedTicks` | 12000, 1728000 | Delayed bounded cleanup and empty encounter expiration. Saved offline occupants prevent both expiration and deletion. Persistence is always enabled for safe reconnects/restarts. |

The implementation uses [ModularBosses 1.8](https://github.com/gummby8/ModularBosses/tree/1.8) as a gameplay reference. All distributed Java, model geometry, textures, room schematics and synthesized heartbeat sounds are original RNT implementations; no ModularBosses code, models or resources are included and the mod is not a dependency. Bundled classic Alpha schematics use registry-name palettes, validated fixed dimensions, saved rotations and reciprocal door masks. A finite spanning tree connects every room and both organ chambers. Construction resumes from its saved cursor without rebuilding finished rooms. Return records and organ deaths are server-owned overworld saved data; only exterior pose and visual combat state are synchronized to clients.

## Prismarine Colossus

Startup category `12_mobs.prismarineColossus` in `hbm.cfg`. Settings are independent of Wither Storm, Sandworm and nuclear explosions.

| Settings | Defaults | Behavior |
| --- | --- | --- |
| `enabled`, `maxActivePerDimension` | true, 2 | Limit new encounters; saved dormant/unloaded bosses count. Existing bosses remain loadable when disabled. |
| `health`, `attackDamage`, `attackCooldownTicks` | 600, 24, 65 | Native health cap 1024. Attacks have separate windup, contact and recovery windows. Romeo recovers faster; glitched cooldowns vary. |
| `movementSpeed`, `targetRange`, `modelScale` | 0.16, 64, 1 | Blocks per tick, blocks, and anatomy scale. Default height is 20 blocks. Scale (0.5–2) affects geometry, contact, reach and climbing and persists per entity. |
| `weakPointMultiplier`, `gauntletRequired`, `allowBodyDamage` | 8, true, false | Rear-facing animated symbol rectangle is vulnerable. Gauntlet melee finishes it; disable the requirement to use other weapons. Body hits deal 1% damage, capped at 2, and stop at one health unless body kills are enabled. Explosions/magic/fire cannot count as directed symbol hits. Void damage remains an administrative escape hatch. |
| `climbing`, `attacks`, `targetCreativePlayers` | true, true, false | Independent encounter controls. The glitched variant selects any nearby living target (apart from Colossi); normal forms prioritize survival players and attackers. Vanilla Creative invulnerability still applies. |
| `grabEscapePresses` | 5 | Distinct Sneak presses required to escape within 80 game ticks of hand contact. Accepted range is 1–16. |
| `terrainDestruction`, `blocksPerTick` | true, 32 | Swept limb contact, also gated by mobGriefing. The dimension-wide coordinate budget covers obstruction probes and destruction lookups, including air and protected blocks. Hard cap 256. Queued contacts expire after 100 ticks and do not load chunks or persist across reloads. Existing explicit config values are respected. |
| `protectedBlocks` | chests, ender chests, enchanting tables | Additional block registry names. Bedrock, negative-hardness blocks, portals, command blocks, liquids and all tile entities are always protected. Respect canEntityDestroy and Forge BreakEvent with the Minecraft fake player; claim mods must enforce their normal fake-player break rules. Drops are suppressed during removals. |
| `particleIntensity`, `soundVolume` | 12, 2 | Bounded local particles and existing Minecraft stone/golem/impact sounds. Client particle settings also apply. No decorative server fragment entities. |
| `naturalSpawning`, `proximityActivation` | false, false | Optional single loaded-ocean attempt every 24000 ticks near a survival player; no monument edits. Optional dormant activation within eight blocks. |
| `giantClock`, `clockChangesTime`, `permanentClockTime` | true, false, false | Romeo visibly raises a clock once. Time mutation requires explicit enablement. Normally restore the original time plus elapsed daylight progression after ten seconds, death or unload; preserve unrelated time commands. Keeping the shift requires a second explicit option. |

The Colossus can step up one block when its body has clearance and the elevated foot has support. Its gait follows horizontal distance and adjusts each foot to nearby terrain. During blocked pursuit it can punch a breakable obstruction that lies within the animated fist's reach. The server tracks attack timing, aim, arm choice, foot placement, grab escape progress and weak-point flare. Climb and escape inputs are validated on the server tick. Interrupted attacks reload in a stagger. A saved encounter ledger enforces admission across restarts. See [Colossus commands and survival controls](commands.md#rntcolossus).

## Giant Ghast

Startup category `12_mobs.giantGhast` in `config/hbm.cfg`. Each Giant Ghast has its own saved encounter. Its entity state and owned Ghasts survive ordinary saves and reloads. Projectiles expire after 180 world ticks and also count against the encounter cap while unloaded.

| Settings | Defaults | Behavior |
| --- | --- | --- |
| `health`, `fireballDamage` | 2,000 HP, 8 HP | Maximum health for new bosses and base direct projectile damage. Existing bosses retain their saved maximum health. Existing config values are honored. Attack type and difficulty scale projectile damage. |
| `attackRange`, `attackCooldownTicks` | 88 blocks, 54 ticks | Maximum acquisition and attack range. Attacks require line of sight. Recovery gets shorter in later phases. |
| `summonPhaseHealth`, `enragePhaseHealth` | 0.65, 0.30 | Fractions of maximum health that unlock summon waves and allow the boss to target retaliating summons, respectively. |
| `maxActiveFireballs`, `maxSummonedGhasts`, `summonCooldownTicks` | 32, 12, 480 ticks | Per-encounter limits and time between summon waves. Reflected and minion fireballs count toward the same projectile limit. |
| `weakPointStrikes` | 8 | Direct melee hits needed once the boss settles and the top core is exposed. The core accepts one strike per 10 ticks. |
| `bossGriefing`, `fireballExplosionBlockDamage` | false, false | Both settings and vanilla `mobGriefing` must be enabled for the encounter's explosions to break blocks. |
| `naturalSpawning` | false | Allows rare natural spawns in the Nether when a clear area is available. |

Attack phases begin at 75%, 50%, 25%, and 10% health. Ordinary body hits deal 28% of normal damage, while direct reflected fireballs deal at least 12% of maximum health before the final phase. See [Giant Ghast commands](commands.md#rntgiantghast).

## Giant Magma Golem

Startup category `12_mobs.magmaGolem` in `config/hbm.cfg`. The golem saves its health, phase, planted slam state, arm cooling and mining progress, TNT stagger, and final defeat state.

| Settings | Defaults | Behavior |
| --- | --- | --- |
| `health`, `meleeDamage`, `rangedDamage` | 600 HP, 24 HP, 12 HP | Maximum health for new bosses and base attack damage. |
| `movementSpeed`, `attackCooldownTicks` | 0.22 blocks per tick, 38 ticks | Base walking speed and attack recovery. Later phases move and recover faster. |
| `summonCooldownTicks`, `maxSummonedCubes` | 360 ticks, 8 | Base wave delay and per-boss cap. Set the cap to zero to disable summons. |
| `waterCoolingTicks`, `armDurability` | 20 ticks, 24 | Cumulative water contact needed per arm and mining work per cooled arm. Better pickaxes remove more work per hit. |
| `bossGriefing` | true | Terrain damage and ignition also require vanilla `mobGriefing`. |
| `naturalSpawning` | false | Allows rare natural spawns in clear Overworld plains and deserts. |

Combat changes at 70% and below 35% health. Lethal damage or the cooled arms, TNT, and water sequence can end the fight. See [Giant Magma Golem commands](commands.md#rntmagmagolem).

## Recommended server workflow

1. Start a new server once to generate all configs.
2. Stop the server.
3. Configure `hbm.cfg` worldgen, dimension IDs, 528/LBS mode, nukes, radiation, and mob systems.
4. Start a temporary world to generate JSON templates.
5. Edit recipes, fluid traits, fallout, item pools, and custom machines as needed.
6. Test with representative players and machines.
7. Back up the final config set before opening the long-term world.

## Giant Enderman

Startup category `12_mobs.giantenderman` in `config/hbm.cfg`.

| Setting | Default | Effect |
| --- | --- | --- |
| `maximumHealth` | 1000 | Maximum HP for new bosses, up to the Forge 1.7.10 limit of 1024. Saved bosses retain their saved attribute. |
| `attackDamageMultiplier` | 1 | Scales combat damage. A player held in a grab retains at least one HP from the bites. |
| `detectionRange`, `movementSpeed` | 96 blocks, 0.26 blocks/tick | Target range and stalking speed. Pursuit speeds up as health falls. |
| `teleportCooldownTicks` | 160 | Base interval between teleports. Later phases shorten it. |
| `terrainDestruction`, `terrainDestructionRadius` | true, 5 blocks | Control the maximum size of terrain contacts. The boss also observes `mobGriefing`, spawn and Forge break protection, and the Colossus block protection list. |
| `terrainBudgetPerTick` | 24 | Shared limit on terrain coordinates examined per dimension each tick. |
| `naturalSpawning` | false | Allows rare neutral encounters near players in the End. |
| `bossCombat` | true | Enables combat with other hostile bosses and the Prismarine Colossus duel. |
| `playerGrabbing` | true | Allows player grabs. Small mobs remain eligible when disabled. |

Health, phase, target memory and cooldowns survive saving. Reloaded encounters resume from recovery, releasing held victims and discarding temporary terrain formations. See [Giant Enderman commands](commands.md#rntgiantenderman).
