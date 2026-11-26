# Command Reference

This reference was verified against `src/main/java/com/hbm/commands` and the server command registration in `MainRegistry`.

## Server commands

These commands are registered during server startup.

### `/gemini`

Administrator invasion controls for Forge 1.7.10. Every subcommand requires operator permission level 2. Changes use the shared Overworld `GeminiInvasion` save; status also identifies the sender's dimension. Console senders can inspect and control progression, locate records relative to the console position, and list camps/nests. Structure spawning requires a player and uses that player's dimension; natural structures remain Overworld-only.

```text
/gemini help
/gemini status
/gemini phase set <1|2|3|4>
/gemini phase next
/gemini phase reset
/gemini time add <ticks>
/gemini time set <ticks>
/gemini nest spawn [x y z] [force]
/gemini nest locate
/gemini nest list [page]
/gemini camp spawn [wretch] [x y z] [force]
/gemini camp locate
/gemini camp list [page]
/gemini debug <true|false>
```

`status` reports the activation mode, whether its condition is satisfied, the Mars milestone, and any reason progression is paused or has yet to start. It also reports `geminionly`, the saved phase, elapsed invasion ticks/days, remaining phase delay, encounter countdown, exact camp/nest generation gates, registered structure counts, and world context. All living Creative, Survival and Adventure players are eligible anchors; eligible counts are shown by mode. Each scheduler reports recent server callback activity, session callback count, saved active-time countdown, last search's candidates examined, unloaded/spacing rejections, placement plans attempted, fallback chunk requests, search mode, failed searches since success, most common rejection reason, and last successful coordinates/dimension. These are actual search results, not random diagnostic terrain samples. Countdown time pauses while a generation gate is closed. Recent callback activity means the server entered the scheduler within two seconds of world ticks; the separate gate identifies whether its countdown can advance.

`phase set` enters the selected phase at its beginning, using the configured delays for elapsed time. It also sets the milestone in `MARS` mode. Forward changes apply phase unlocks in order. Selecting the current phase preserves its elapsed time. `phase next` starts at Phase 1 and stops at Phase 4. `phase reset` clears phase-local time, total elapsed time, and the encounter countdown. It returns a started invasion to Phase 1, or Phase 0 if the invasion has yet to start or the milestone is locked in `MARS`. Reset retains the milestone, gamerules, configuration, creatures, and structure records. In `DISABLED`, phase and time commands edit saved progress while natural activity stays paused. In `MARS`, use `/visitedmars false` to pause activity and retain the saved phase.

Timer arguments are nonnegative signed 64-bit tick counts; overflowing additions are rejected. `time add` advances from retained phase-local progress through at most three phase transitions. `time set` recalculates the phase from the configured cumulative delays and supports rewinding. Neither changes the day/night clock. Skipped encounters, chunk population, incremental construction, AI, infections and other live cooldowns are not simulated or replayed; the encounter countdown is retained on forward jumps and rescheduled on rewinds to an earlier phase. Natural advancement retains its existing loaded, enabled, player-present Overworld timing and one-transition/full-delay rule. Existing saves retain their phase and phase-local time; their initial cumulative prefix uses the currently configured delays because historical durations were not saved. Subsequent elapsed time is persisted, including in Phase 4.

Nest and camp spawning use forced debug placement. Both queue saved, bounded construction on server ticks. Camp plans retain their exact graded block edits before placement; retries and reloads reuse the plan and resident identities. Regenerating an existing nest requires its exact saved origin and a trailing `force`; without confirmation no full regeneration is performed. Older nests that still lack registered access receive a bounded entrance conversion. Saved pits that already work retain their geometry. An explicit nest upgrade keeps its living resident UUIDs and defeat states, adds missing slots up to the configured capacity, and clamps the upgraded origin to air Y=9-12 (floor Y=8-11). Living residents retain health and AI and receive updated homes after completion. Unloaded survivors rebind when loaded; they are never duplicated. `wretch` is the only camp type and is selected when omitted. Natural phase, probability, biome, residence, spacing, natural height range and configured natural structure-capacity requirements do not apply to debug placement. Debug structures do not start the invasion or change its progression. Debug placement also works with `mobGriefing=false`.

Without coordinates, camps try the nearest candidate first, then up to seven nearby alternatives, and derive elevation from the surface independently of the player's flying height. Nests use the player's horizontal position, choose floor Y=8-11 independently of player height, and accept solid terrain as an excavation target. No search for an empty cave or untouched clearing is required.

Optional coordinates accept vanilla absolute/relative `~` notation and select one exact origin without relocation. Fractional coordinates round down to blocks. **Camp Y is the foundation elevation**; the first layout layer and resident stand one block above it. Existing saved camps retain their original physical positions, and camp locate/list now report foundation Y. Nest Y retains the existing floor-air-level meaning, with organic floor at Y-1. In a 256-block world, camp foundations remain Y=0..235. Explicit new nest origins may override depth at air Y=6..229, with floor at Y-1; actual bedrock is always protected. Camps require their preparation/read area loaded. Nests request at most one missing construction/neighbor chunk per server tick, with no tickets or full-footprint loaded requirement. Before excavation, nests check world bounds, bedrock, natural cover above the chamber, and a dry opening with a walkable surface approach. A site can fail those checks even when its coordinates are in range.

Natural and debug generation deliberately replace terrain, ores (including modded resource stone), vegetation, player construction, tile entities and liquids wherever their layouts/preparation require edits. Nest generation preserves actual bedrock. Both builders identify obstructing entities and relocate one riding group at a time to supported, collision-free loaded terrain outside construction volumes. Players receive native position updates. When no safe destination exists, the occupied cell remains unchanged and is retried; debug output names the entity class and UUID. There is no terrain whitelist, mining-hardness check, player construction protection or player-mining event veto for these structure edits. Existing world APIs detach replaced tile entities before block callbacks, discarding their inventories without an inventory spill. Ordinary ongoing mob attacks and burrowing retain their existing protection and gameplay rules.

New camps use 80x80, 112x112 or 144x144 settlement envelopes (112 is most common), with 6-12 main components and 8-16 smaller components. These bounds are not a square excavation: only separate building pads, road corridors, entrance alcoves and broken perimeter segments are prepared. Each pad samples nearby ground and can sit at a different elevation; bounded road routing connects every doorway to the central gathering area with walkable one-block grades, wider main routes and narrower branches. Foundations fill to support, and edited columns are cleared through obstructing trees/buildings with 20 blocks of clearance along the main circulation areas. The original central camp is retained, surrounded by rotated canvas halls/shelters, assimilated houses, organic patches, stores, four lookouts and two descending, lined underground alcoves. These access points do not generate or register Gardener nests. Nests use an irregular cavern approximately 105x99 blocks across with 20-24 blocks of vertical interior clearance, a broad central hall and three connected living lobes. Flesh seals the complete floor, walls and roof before excavation; organic growth follows the floor. A body-sized passage leads to a rounded opening cut through the local terrain. Gardeners climb the lined side of the opening and use a short notch in the natural rim to reach existing ground. Construction and verification use bounded saved passes (at most 1,024 cell examinations and 128 edits per tick). Residents crawl along the pit wall at night and return by the same route during daytime after active pursuit or capture finishes; registered residents do not excavate separate commute tunnels. Existing chambers, resident identities, and registered pits are retained. Legacy ladders awaiting conversion receive the recessed opening. Edits stay inside the calculated room, passage, shaft or camp preparation regions.

Natural nests remain eligible in phases 3 and 4; camps in phase 4. Independent saved schedules attempt discovery within 20 active seconds of unlocking/changing phase, then use the configured interval after successful completion (nests 120 seconds, camps 300). Nest discovery considers up to 24 centers near living Creative, Survival or Adventure players, preferring established homes. Centers default to 64-160 blocks from the anchor, with closer loaded origins allowed, three completed/active nests per area and 32 completed/active natural reservations; failed construction jobs retain their footprints without consuming those limits. Sites rejected during the terrain survey retain only the exact saved origin, so the scheduler can try neighboring terrain. Expanded footprints cannot overlap. A loaded origin is sufficient: the saved job stages its actual excavation and neighboring chunks with at most one missing chunk requested per tick. No new-chunk event or empty cave is required. Camps retain their loaded-footprint selection, 80/112/144 layouts, 80-block minimum footprint, configured spacing and saved fallback loading at one missing chunk per active second, without chunk tickets.
Natural structures require an active invasion, species enablement, suitable difficulty, and `mobGriefing`. Camps also require `doMobSpawning`. `geminionly` does not block GHE structures or residents. Nests excavate solid underground terrain and connect to the surface; camps grade their pads and roads across uneven terrain. New structures register only after complete placement verification. Unfinished nest jobs retain their construction pass and cursor. Jobs with no cursor/pass progress for 1,200 construction opportunities are marked failed, retry with a ten-second backoff, and stop consuming active construction reservations or natural limits while unfinished; their footprints remain reserved to prevent duplicate structures. Completed nests retain their population limits and replenishment rules. Legacy camp reservations and abandoned records without block journals remain visible and are never automatically rebuilt or discarded. Dimension-specific records, completed structures, resident identities, failure summaries and countdowns survive saves/reloads. Phase/reset commands retain structures and shorten pending countdowns; `/rntconfig reload` clamps countdowns to the new interval or retry delay when the corresponding interval changes. Legacy `nestChance`/`campChance` supply initial interval defaults when the new keys are absent; `campChance=0` still disables new camps.

Camp and nest spawn commands report saved jobs. Locate/list expose queued, constructing, completed, failed and recovering records, with coordinates, pass/cursor and the last construction or admission reason. Nest status/list include floor Y, chamber dimensions, layout version, saved/configured capacity, spawned (including unloaded), pending and permanently defeated counts, last spawn rejection and complete-footprint chunk availability. A partly loaded footprint does not prevent staged construction or a locally loaded resident admission. Locate/list include the saved settlement dimensions and component counts. Repeating generation at the same origin reuses its saved identity and resident state instead of registering/spawning duplicates. A checked write of the construction record precedes terrain edits, including the original pit survey; completion is saved before reporting successful registration. Disk-write failures pause construction for retry. Debug records use the existing dimension-specific saves without the natural record-count limit. World bounds and required loaded chunks are checked before any edits. An unexpected engine/mod block callback can still fail after edits have begun; the saved plan/progress remains available for recovery, with no claim of rollback or completed registration. Camp work is limited to 1,024 cell scans and 128 edits/notifications per tick; nests retain the same scan/edit budgets. Final verification repeats after a reload to check earlier chunk saves. Roads can use narrow clear routes and up to three layout attempts before rejecting a new camp, always within its original envelope.

Residents retain saved identities, natural anatomy/AI, enabled invasion/species settings, non-Peaceful difficulty, population/reservation capacity, clearance and Forge insertion checks; Wretches also retain `doMobSpawning`. A player elsewhere inside a nest does not block its residents: each slot checks its own body and full limb envelope for terrain, liquids and living entities. Temporary occupancy, unloaded chunks, population limits and Forge insertion vetoes leave slots pending indefinitely with the precise last reason. A new nest attempts all configured initial residents when construction finishes (default three), then retries any blocked initial slots once per active second. Initial population also fills pending slots in existing saved nests. Defeated residents are replaced at most one per `gardener.replenishSeconds` active seconds (default 300); defeat delays the next replacement by a full interval, and replacements receive new UUIDs. Residents do not naturally despawn. Unloaded survivors retain their slots and count toward the saved Overworld limit of 16. Clearance, population caps and Forge insertion checks still apply. The Wretch debug phase and activation bypass persists without altering natural progression. Expanded settlements also reserve 8, 10 or 14 one-shot initial Wood Crawler/Fake Person slots for their respective sizes. These admissions wait for active phase 4 and normal species/combined caps, spawn-weight/settings, difficulty, doMobSpawning, safe supported space and Forge approval. Wood Crawlers retain artificial-light checks and their existing crawling posture; camp admission permits non-forest portions of the settlement without changing ordinary forest spawning. Normal camp ticks attempt at most four admissions per second per world. Admitted slots never replenish on death/despawn; obstruction/veto retries stop after 20 failures. Stable identities, roles and slot states survive reloads. Existing small camp saves remain in place; explicitly regenerating their exact origin expands them while retaining their Wretch identity/defeat state. `/gemini debug true` adds candidate, placement, registration and resident-admission details.

Locate commands read existing saved records in the current dimension without generating structures or loading their chunks. Both locate commands include saved unfinished and failed structures as well as completed structures, including blocked resident spawning. Status separates completed counts from pending/unfinished records. No automatic scan, registration or rebuilding of unrecorded orphaned buildings is performed; historical blocks alone cannot establish a saved layout or resident identity. Camp and nest lists display eight entries per page. Camp listing includes saved tracking status, including legacy abandoned construction. Records describe tracked history, not an inspection of whether every block or resident still exists.

Debug mode is saved server-side and reports activation, transitions, command timer changes, structure attempts/rejections/registration and resident/Woodcrawler admissions to level-2 administrators and the server log. Repeated messages with the same event key are throttled to once per ten seconds of server world ticks. Successful automatic structures always log type, coordinates and dimension. Failed automatic searches log a rejection summary at most once per five minutes of world ticks per structure type; saved status retains the latest reason between logs. The existing independent Woodcrawler diagnostic configuration remains available; no per-tick chat output is added.

### `/gemini iris status|show|hide|stage <1..4>|observe|reset`

Requires operator permission level 2; console and other dimensions control the same saved Overworld Iris. `status` reports the invasion phase, independent visual stage, visibility transmission, observation and disturbance timers. Visibility describes the celestial object; local terrain, roofs, clouds and the viewing direction can still obscure it.

`show` forces full visibility before activation, during daytime, in rain, with Gemini/rendering disabled, or below the configured minimum render-distance setting. `hide` suppresses only the celestial appearance. `stage 1` through `stage 4` override apparent growth without unlocking creatures, advancing the invasion or changing `visitedmars`/`geminionly`. `observe` immediately selects one living real survival/adventure Overworld player, bypassing natural visibility, timing and weather checks; it reports an error if none exists. Use `show` to view an observation before the invasion starts. All clients receive one shared pupil orientation, independent of their cameras. `reset` clears visibility/stage overrides and the current observation, restoring the saved invasion's normal appearance immediately. These overrides persist across saves/restarts separately from invasion progression.

### `/rntconfig reload`

Requires operator permission level 2. Rereads `config/hbm.cfg`, validates it before applying supported runtime settings, and reports errors while retaining previous valid values. Supports dedicated-server console and integrated servers. Reloads Gemini settings (including activation mode, hostile suppression multipliers, and population limits), mace and bear mace gameplay settings, server logging/diagnostics and login options. Woodcrawler biome spawn weight, entity, block and item registration, recipes, and JSON machine files are startup-only for this command. Existing world gamerules, invasion progression, entities and saved conversion/incubation timelines remain intact. See [the supported settings and spawn-hook limitations](configuration.md#live-server-configuration-reload).

Use `/gamerule geminionly true|false` to change the current save's automatic hostile spawn filter immediately. Its initial default is `21_gemini.geminiOnly=false`; configuration reload never replaces an existing gamerule value.

### `/ntmdisease inspect|clear <player|entityId> [rabies|deep_root]`

The optional pathogen defaults to `rabies`, preserving existing command behavior. Select `deep_root` to inspect its saved stage, loaded stage ticks, snapshotted stage duration, active gate and terminal conversion eligibility, or to clear only Deep Root Disease and its movement/visual symptoms. A terminal duration of zero means there is no further timed stage: non-player hosts attempt conversion, while players take gradual disease damage. Deep Root inspection does not advance time or cause conversion. Targets must be loaded living entities in the sender's dimension or online players. Both variants require operator permission level 2.

Requires operator permission level 2. `inspect` reports the authoritative rabies stage, presentation, infection source, infection time and saved stage deadlines to the administrator. Players see the separate RABIES status while their infection remains saved. `clear` removes the rabies record, RABIES indicator and disease AI; short potion symptoms expire naturally. This is an administrative override, not vaccination or medical treatment.

For players, `inspect` also reports `treatmentEligible`, `remainingTreatmentTicks` (ticks until the guaranteed 24000-elapsed-tick cutoff, clamped to zero) and persistent `fatalCommitted`. The same eligibility method governs HRIG and fatal commitment: eligible at elapsed ticks 0 and 23999, closed at 24000; clinical stage never closes the window early. Inspection refreshes authoritative progression and one-time legacy repair, never inferring eligibility from visible potions. A missing legacy timestamp reports `infectedAt=unknown` and `elapsedTicks=unknown`, zero remaining ticks and ineligible treatment without fabricating a new cutoff. `clear` removes fatal commitment and its disease-owned Fatal effect immediately; the existing final disease stage and the separate status are distinct. HRIG use and its gameplay/medical distinctions are described in [rabies configuration](configuration.md#rabies).

`inspect` also reports disease behavior status and the target entity ID, or `target=none`. Status distinguishes silent incubation/shedding, paralytic illness, roaming and attention pauses, creeping, sprinting, neurological impairment, delayed retries, bite reach/cooldown, unavailable navigation chunks and navigation failure. Creeping reports `remainingApproachTicks`; an initial stare within that period also reports `remainingStareTicks`. Sprinting identifies the encounter phase; neurological movement remains impaired. These phases are configurable gameplay choices. Active movement status includes configured detection/retention distances and unseen ticks when pursuing; player status includes furious swallowing spasms and active optional water-contact penalties. These are operator diagnostics, not automatic victim notifications. Players receive symptoms without autonomous movement or attacks. A ridden, saddled horse retains rider control; modded mammals with custom AI may require an integration. Inspection does not start pursuit or load chunks.

Times and cooldowns are **ticks**, with 20 ticks per second at 20 TPS. For example, `clinicalTicks=24` gives only 1.2 seconds of clinical behavior in total, with impairment halfway through. Configuration edits apply new stage durations to new infections; existing records retain their saved deadlines. `clear` also releases disease-owned water-navigation preferences and flight destinations, restores saved sitting commands/task settings, and leaves short water penalties to expire without removing unrelated effects. It does not reroll bat entry infection.

Player names target online players; numeric entity IDs target loaded living entities in the sender's dimension (the overworld for the server console). The command does not load chunks. See [rabies configuration](configuration.md#rabies) for host rules, timing and persistence.

### `/rntachievement list|unlock`

Lists RNT achievement IDs or unlocks one for an online player. Requires operator permission level 2; the server console can use it.

**Usage**

```text
/rntachievement list
/rntachievement unlock <player> <achievementId>
```

Use the full internal ID shown by `list`, such as `achievement.arcWelder` or `achievement.hempDiscovery`, rather than the translated title. `unlock` awards any missing parent achievements first, then the requested achievement through the normal player statistics system. An achievement the player already has is reported as a successful no-op. Only online players can be targeted.

`achievement.rabiesDeath` is **DID YOU ALSO EAT A KID** ("Die from rabies."), with the Rabies Virus Syringe icon and no prerequisite. Normal gameplay awards it only for an uncanceled player death caused by rabies disease damage, after revival handling; infection or Fatal status alone does not qualify. It is included in `list`, `unlock` and achievement ID completion.

### `/ntmreload`

Reloads serializable JSON recipes and item pools.

**Usage**

```text
/ntmreload
```

**Use when**

- You edited active files in `config/hbmRecipes/`.
- You edited `config/hbmConfig/hbmItemPools.json`.

**Caution**

This command catches load errors, prints a short error to chat, and rethrows the exception. Test recipe JSON on a staging server before using it on production.

### `/ntmloadchunk <x> <z>`

Debug command that inspects tile entities in an unloaded chunk using block coordinates.

**Usage**

```text
/ntmloadchunk <x> <z>
```

**Example**

```text
/ntmloadchunk 1024 -320
```

The command converts block coordinates to chunk coordinates internally. It reports tile-entity IDs and positions, highlighting invalid positions.

### `/ntmsatellites orbit|descend|list`

Manages active satellites. Must be run by a player.

**Usage**

```text
/ntmsatellites orbit
/ntmsatellites descend <frequency>
/ntmsatellites list
```

**Examples**

```text
/ntmsatellites orbit
/ntmsatellites list
/ntmsatellites descend 12345
```

Notes:

- `orbit` launches the satellite chip held by the player and consumes one item if valid.
- `descend` removes an active satellite by frequency.
- `list` prints active satellite frequencies and implementation class names.

### `/ntmrad clear|set`

Edits chunk radiation at the sender's location or clears the radiation data system.

**Usage**

```text
/ntmrad clear
/ntmrad set <amount>
```

**Examples**

```text
/ntmrad clear
/ntmrad set 25
```

`set` accepts values from `0` to `100000`.

### `/ntmstations launch|tp|list|fetch`

Manages orbital stations and station drives. Must be run by a player.

**Usage**

```text
/ntmstations launch
/ntmstations tp
/ntmstations list
/ntmstations fetch <id|name>
```

**Examples**

```text
/ntmstations launch
/ntmstations tp
/ntmstations list
/ntmstations fetch 0xA1B2C3D4
/ntmstations fetch My Station Name
```

Notes:

- `launch` creates a station for the held orbit destination drive.
- `tp` teleports the player to the station represented by the held orbit drive and spawns the station structure if needed.
- `list` prints station IDs and station names.
- `fetch` gives the player a programmed drive for a matching station ID or name.

### `/ntmenablenukes true|false`

Enables or disables the runtime nuclear warfare toggle.

**Usage**

```text
/ntmenablenukes true
/ntmenablenukes false
```

**Examples**

```text
/ntmenablenukes false
/ntmenablenukes true
```

This changes the in-memory value of `GeneralConfig.enableNuking`; it is useful for event windows or emergency lockdowns.

Disabling nuclear warfare also prevents redstone from detonating placed nuclear bomb blocks, regardless of the separate redstone toggle below.

### `/ntmenableredstonegroundnukes true|false`

Enables or disables redstone detonation for placed nuclear bomb blocks such as Fat Man and Tsar bombs.

**Usage**

```text
/ntmenableredstonegroundnukes true
/ntmenableredstonegroundnukes false
```

This is an in-memory server setting and defaults to enabled at startup. It applies only to nuclear bomb block classes; ordinary TNT, explosive charges, mines, and other non-nuclear explosives retain their normal redstone behavior. Redstone can detonate a ground nuke only when both this setting and `/ntmenablenukes` are enabled.

### `/ntmenablenukes schedule true|false yyyy-MM-dd HH:mm`

Schedules a runtime nuclear warfare toggle.

**Usage**

```text
/ntmenablenukes schedule <true|false> <yyyy-MM-dd> <HH:mm>
```

**Example**

```text
/ntmenablenukes schedule false 2026-06-09 22:00
```

The date parser uses the server JVM's local time zone and the format `yyyy-MM-dd HH:mm`.

### `/hbmbedrockdrop add|remove|list|clear`

Edits the in-memory excavator bedrock drop list. Requires permission level 4.

**Usage**

```text
/hbmbedrockdrop list
/hbmbedrockdrop clear
/hbmbedrockdrop remove <index>
/hbmbedrockdrop add <registry> <meta> <min> <max>
```

**Examples**

```text
/hbmbedrockdrop list
/hbmbedrockdrop add minecraft:diamond 0 1 2
/hbmbedrockdrop remove 3
/hbmbedrockdrop clear
```

The entry format is `modid:item_or_block meta min max`.

## Client commands

These commands are registered only on clients.

### `/ntmclient help|list|reload|get|set`

Views and edits client JSON variables in `config/hbmConfig/hbmClient.json`.

**Usage**

```text
/ntmclient help
/ntmclient help <command>
/ntmclient list
/ntmclient reload
/ntmclient get <name>
/ntmclient set <name> <value>
```

**Examples**

```text
/ntmclient list
/ntmclient get GUN_VISUAL_RECOIL
/ntmclient set GUN_VISUAL_RECOIL false
/ntmclient reload
```

Available keys are listed by `/ntmclient list`. Values are parsed according to the default value type.

### `/dumpthreadsandcrashgame dump|crash`

Debug-only command that writes a thread dump to the log and optionally exits Java.

**Usage**

```text
/dumpthreadsandcrashgame dump
/dumpthreadsandcrashgame crash
```

Only use this command when debugging hangs or crashes. `crash` intentionally exits the client.

### `/rntwitherstorm`

Operator level 2 controls the hostile Wither Storm encounter.

```text
/rntwitherstorm spawn [x y z]
/rntwitherstorm spawn command [x y z]
/rntwitherstorm spawn storm [x y z]
/rntwitherstorm list
/rntwitherstorm inspect <entityID|UUID>
/rntwitherstorm growth <entityID|UUID> <mass>
/rntwitherstorm remove <entityID|UUID>
/rntwitherstorm challenge true
/rntwitherstorm challenge false
/rntwitherstorm challenge status
```

Omitting the form or choosing `command` starts the Command Block Wither with three ordinary Wither heads and an embedded command block. `storm` starts Destroyer at the configured transformation threshold, beginning the central head's transformation. Coordinates must already be loaded; omitted coordinates use the sender's position plus eight blocks vertically. Inspection reports each head's transformation state. Inspection and growth edits require a loaded boss in the sender's dimension. `list` includes saved unloaded encounters across dimensions. `remove UUID` retires an unloaded encounter without loading its chunks; its boss, projectiles and parts disappear when next loaded. Numeric IDs are local to the current runtime and dimension; UUIDs survive restarts.

For survival summoning, place a **Fake Command Block** above one soul sand block and flank the Fake Command Block with soul sand. Leave both lower corners empty. Place three wither skeleton skulls on the top row. The last skull starts the encounter in either horizontal orientation and consumes the structure if the summon succeeds. Find the Fake Command Block in stronghold libraries, jungle temples, and exposed Ice Plains Spikes formations. Mine it with a diamond-tier pickaxe. The legacy euphemium structure still accepts a sneak-right-click with a nether star.

Both survival rituals require a world-wide Ender Dragon defeat or the operator bypass described below. A blocked activation leaves the structure and any held nether star intact. Place a skull again to retry the Fake Command Block ritual, or activate the euphemium ritual again with the nether star. The `spawn` subcommand remains available to operators at any stage.

Challenge mode starts a 24,000-tick countdown in the Overworld. The command saves a world-specific override. Running `challenge true` during a countdown preserves its progress, and `challenge false` cancels a pending spawn. Once the automatic summon succeeds, the world has used its challenge spawn. The encounter cap applies, so a full cap leaves the spawn pending. The challenge Storm follows sampled trails from eligible Overworld players and continues toward its last recorded position if they leave. The timed spawn is available before the Dragon defeat and grants no boss progression.

The core is visible in Stage A, Stage A with the transformed head, and damaged Stage C. The mantle covers it in the other forms. Damage to heads passes to the boss, and tentacles pass half damage to the same health pool.

Growth comes from consumed terrain (three mass per block), item stack counts, falling blocks and living victims killed during consumption. Default growth thresholds are 12,500 for Destroyer, above 50,000 for Devourer and above 250,000 for ThunderStorm. Stage B must also finish its minimum duration before automatic evolution. When the timer ends, the total accumulated mass determines the next stage. The `growth` command applies the requested mass and phase immediately.

| Appearance | Default progression |
| --- | --- |
| Stage A | Below 12,500. Three ordinary skulls. |
| Stage A (Transformed Head) | At 12,500. The center head transforms, with two ordinary side skulls and early growth behind the exposed core. |
| Stage B | At 31,250, halfway from Destroyer to Devourer. Both side heads transform and tentacles emerge. Absorbed debris spirals inward as mass continues to accumulate. The default minimum duration is 24,000 active ticks. |
| Stage C (Small) | Above 50,000 once the Stage B timer has elapsed. A tall curved mantle rises above three low heads, with one long raised side tentacle and several curled lower arms. |
| Stage C (Big) | At 150,000, halfway from Devourer to ThunderStorm. The crown reaches its full proportions. |
| Stage C (Damaged) | Stage C (Big) at or below 25% health. The crown fractures and the core is exposed. |
| Stage D | Above 250,000. The tall mantle reforms with visible purple seams. |

Only transformed heads use tractor beams, after their transformation animation finishes. ThunderStorm refreshes ordinary rain/thunder for ten seconds while active in a dimension with a sky. Growth, transformed heads, elapsed Stage B ticks, attack timers, component UUIDs and terrain cursors survive saving. Older saves with a transformed side head gain its partner. Raising growth preserves completed head transformations, while lowering it recalculates heads from the configured thresholds. The boss uses loaded terrain only and may stop at unloaded boundaries. See [Wither Storm configuration](configuration.md#wither-storm).

### `/witherstormbypass`

Operator level 2 controls the Ender Dragon requirement for Wither Storm rituals.

```text
/witherstormbypass
/witherstormbypass true
/witherstormbypass false
```

With no argument the command reports the current setting. `true` allows either ritual immediately, and `false` restores the Dragon requirement. The setting is off by default and is saved with the boss milestones in `<world>/data/rntBossProgression.dat`.

The server records Dragon deaths in the End and imports older defeats from the saved vanilla End exit fountain on first startup with this feature. An existing save needs surviving portal blocks and the fountain's central bedrock pillar for automatic recognition. Defeating the Wither Storm after its Stage D transition finishes records the next milestone. In older saves that already used the challenge spawn, previously saved Storms lack the origin data needed to award that milestone.

### `/rntsandworm`

Operator level 2 controls the Sandworm and individual interior monsters:

```text
/rntsandworm spawn
/rntsandworm spawn worm [x y z]
/rntsandworm spawn brain [x y z]
/rntsandworm spawn heart [x y z]
/rntsandworm spawn tick [x y z]
/rntsandworm spawn orange|blue|green|purple [x y z]
/rntsandworm list
/rntsandworm inspect <UUID>
/rntsandworm remove <UUID>
```

Coordinates must already be loaded. Bare `spawn` summons an exterior worm twelve blocks above the sender; individual monsters spawn at the sender's elevation. Manual spawning does not require natural spawning. Stand in front of the mouth after dungeon construction finishes to enter; Creative players are excluded by default. Craft two Worm Bait from rotten flesh and a slimeball, then right-click to throw it or drop it to attract an existing worm.

Each worm owns a separate connected interior. Fight through Ticks and four Spark variants to defeat both stationary organs. Nearby allied Sparks shield the Brain; green Sparks heal only allied interior monsters. The Heart briefly resists further wounds and sprays blood while pumping rapidly. Successful Tick bites and blood contact grow it up to five stages. Blood, acid, tumors and gas are dangerous; saliva is a flowing visual environmental fluid. The glowing floor at the entrance unlocks when both organs die and returns each player to their own original dimension and safe landing coordinates. Exterior worms retire on completion, including when reloaded later.

`list` and `inspect` include unloaded encounters and generation progress. `remove` cancels an encounter and opens its exit without loading the exterior. Offline occupants retain their dungeon and return records. Death returns a participant safely to the original dimension, with a saved ten-second grace period before swallowing is possible again. The same exterior can then swallow them again while the encounter remains active. Disconnected players resume in the saved interior. Empty abandoned encounters eventually expire; completed/canceled interiors are cleared in bounded batches only after no saved occupant remains inside. Ownership records remain until the exterior acknowledges retirement, then are removed after cleanup; saved registered exteriors cannot recreate a missing encounter. See [Sandworm configuration](configuration.md#sandworm).

### `/rntcolossus`

Operator level 2 controls the Prismarine Colossus:

```text
/rntcolossus spawn [active|dormant|admin|glitched] [x y z]
/rntcolossus activate <entityID>
/rntcolossus remove <entityID>
```

Bare `spawn` creates an active boss eighteen blocks ahead on the surface. Console execution requires absolute coordinates. All spawn locations require already loaded terrain, a broad supporting floor, and room for the complete anatomy. The Creative inventory also contains a spawn egg. Right-click a broad floor to use it. Operator spawning and `activate` bypass the Wither Storm prerequisite. A dormant boss entity can also awaken through damage, sneak-interaction or its optional proximity trigger after the prerequisite is met.

In survival, defeat the Ender Dragon, defeat the Wither Storm, then right-click the core beneath the dormant guardian in an Ocean Temple. Explore both entrances and the arena so the temple can finish generating. A treasure chamber contains a Prismarine Gauntlet. Once the core successfully awakens the guardian, that temple's encounter is spent across saves and reloads. A Colossus defeat in combat records the world-wide milestone. Removing the entity with `/rntcolossus remove` or losing it to the void leaves the milestone unchanged.

Craft the Prismarine Gauntlet with `DED / GPG / _G_`: D = diamond, E = emerald, G = gold ingot, P = ender pearl, underscore = empty. Hold it as a weapon. Approach the rear of either leg and right-click the lower body to grip the Colossus, then use W/S to climb between the leg, hip, back, shoulder and rear-head holds. Hold Space when it shakes; Sneak releases. Four players can climb simultaneously. Aim and punch the glowing rear-head symbol with the gauntlet for a finishing strike. Elevated platforms and terrain provide another approach. Heavy body hits can force a temporary kneel; body attacks otherwise do very little damage and cannot finish it by default. Server settings can permit ordinary weapons against the symbol. When grabbed, follow the HUD prompt and tap Sneak (Shift by default) five times within four seconds to escape. Release the key between presses. Escaping makes the Colossus recoil.

The normal and Romeo forms leave one stack of Prismarine Shards after the collapse; the glitched form drops none. Shards and sand craft four quartz. Summon integrations can call `setDropShards(false)`. Death, removal, logout, chunk unload and world unload release attached entities; released players receive a short fall-damage grace period. Attachments never persist across saves.

The rear symbol, gauntlet finish, climbing, grab/eat attempt, clock and berserk Terminal Space appearance follow the [Story Mode Colossus reference](https://minecraftstorymode.fandom.com/wiki/Prismarine_Colossus) and [Episode 2 transcript](https://minecraftstorymode.fandom.com/wiki/Giant_Consequences/Transcript). The repeatable attack cycle, radial shockwaves, gauntlet recipe, W/S climbing route, bracing, stagger threshold and temporary clock reset are RNT survival adaptations. The model, textures and implementation are original; no Story Mode audio or models are redistributed.

### `/rntgiantghast`

Operator level 2 controls the Giant Ghast in the sender's dimension.

```
/rntgiantghast spawn
/rntgiantghast spawn <x> <y> <z>
/rntgiantghast kill
/summon hbm.giant_ghast <x> <y> <z>
```

Use open, loaded air on a hostile difficulty. Allow about 20 blocks of width and 17 blocks of height for the boss to turn. The coordinates place the boss near the tips of its tentacles. `kill` retires all saved encounters in the dimension, removes their loaded Ghasts and fireballs, and discards any remaining encounter entities when their chunks next load.

The opening attack fires four orange shots followed by a blue-tinted shot. Hit the blue shot with melee while aiming at the boss to return it. At 75% health the boss switches to 15-shot bursts, at 50% it fires large radial flowers, and at 25% it alternates triple shots and five-shot flowers. Later patterns also include marked shots you can return. Summoned Ghasts initially attack players and can turn on the boss after a stray hit.

At 10% health the Giant Ghast starts spinning, fires erratically, and descends. Once it settles, build up or jump onto the solid roof and strike down into the central recess to hit the exposed red core with melee. Eight valid strikes defeat the boss by default. See [Giant Ghast configuration](configuration.md#giant-ghast).

The three faces, fireball patterns, retaliation, and exposed top weak point follow the [Giant Ghast reference](https://wiki.ronlab.site/content/minecraftwiki_en_all_maxi_2025-11/Story_Mode%3AGiant_Ghast) and [Jailhouse Block transcript](https://minecraftstorymode.fandom.com/wiki/Jailhouse_Block/Transcript). The timed phases and walkable roof adapt the fight to ordinary survival gameplay.

### `/rntmagmagolem`

Operators with permission level 2 can summon the Giant Magma Golem in a loaded area with about 10 blocks of width and 12 blocks of height. Use a hostile difficulty and a dimension where water can be placed. Coordinates accept vanilla relative offsets.

```
/rntmagmagolem spawn
/rntmagmagolem spawn <x> <y> <z>
/summon hbm.giant_magma_golem <x> <y> <z>
```

After a ground slam, the golem holds its planted arms still for ten seconds. Place water beside a planted fist so the flow touches the arm. Once that arm turns gray, strike it with a pickaxe. Cooling and mining progress carry over to later slams. The golem can still slam with one arm.

Destroy both arms to expose the feet. A primed TNT charge very close to either foot knocks the golem down, and glancing blasts accumulate. Keep a foot or the fallen body wet for about a second to finish the fight. The golem stays down while you fetch water. Depleting its health also defeats it at any stage.

The arm and TNT sequence follows the [Story Mode Giant Magma Golem encounter](https://minecraftstorymode.fandom.com/wiki/Giant_Magma_Golem). See [Giant Magma Golem configuration](configuration.md#giant-magma-golem).

## Permissions notes

Most server command classes do not override Forge's default permission behavior. `/rntwitherstorm`, `/witherstormbypass`, and `/rntachievement` explicitly require permission level 2, and `/hbmbedrockdrop` explicitly requires permission level 4. Server owners should additionally restrict command access with their server management tooling, especially for radiation, stations, satellites, recipe reloads, chunk diagnostics, and nuclear toggles.

## /rntgiantenderman

Operator level 2 can run `/rntgiantenderman spawn [x y z]`. The command starts an active encounter at the specified feet position or searches for open ground near the sender. Use solid ground in loaded terrain with roughly 14 blocks of width and 32 blocks of vertical clearance. The Creative spawn egg places a neutral boss on top of the selected block.

Eye contact or damage provokes a neutral Giant Enderman. At 70% health it adds grabs and two-handed smashes. At 35% health it roars, recovers faster, and adds expanding shockwaves and spread throws. Strike the hand holding you to escape, and aim at the glowing chest when it opens after a heavy attack. See [Giant Enderman configuration](configuration.md#giant-enderman).
