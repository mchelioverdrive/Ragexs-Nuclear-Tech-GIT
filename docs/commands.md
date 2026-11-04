# Command Reference

This reference was verified against `src/main/java/com/hbm/commands` and the server command registration in `MainRegistry`.

## Server commands

These commands are registered during server startup.

### `/ntmdisease inspect|clear <player|entityId>`

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

Challenge mode starts a 24,000-tick countdown in the Overworld. The command saves a world-specific override. Running `challenge true` during a countdown preserves its progress, and `challenge false` cancels a pending spawn. Once the automatic summon succeeds, the world has used its challenge spawn. The encounter cap applies, so a full cap leaves the spawn pending. The challenge Storm follows sampled trails from eligible Overworld players and continues toward its last recorded position if they leave.

The core remains visible in every form. Damage to heads passes to the boss, and tentacles pass half damage to the same health pool.

Growth comes from consumed terrain (three mass per block), item stack counts, falling blocks and living victims killed during consumption. Default phases are Command Block below 12,500, Destroyer at 12,500, Devourer above 50,000 and ThunderStorm above 250,000. The central head transforms at 12,500, the first side at the midpoint of the transformation and Devourer thresholds (31,250 by default), and the remaining side above 50,000. Only transformed heads use tractor beams, after their transformation animation finishes. Body volume, necks, head proportions, tentacles and client debris develop with mass. ThunderStorm refreshes ordinary rain/thunder for ten seconds while active in a dimension with a sky. Growth, transformed heads, attack timers, component UUIDs and terrain cursors survive saving; older saves derive head progress from their existing mass. Raising growth preserves completed head transformations, while lowering it explicitly recalculates heads from the configured thresholds. The boss uses loaded terrain only and may stop at unloaded boundaries. See [Wither Storm configuration](configuration.md#wither-storm).

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

Bare `spawn` creates an active boss eighteen blocks ahead on the surface. Console execution requires absolute coordinates. All spawn locations require already loaded terrain, a broad supporting floor, and room for the complete anatomy. The Creative inventory also contains a spawn egg; right-click a broad floor. Dormant statues track targets with glowing eyes and awaken through damage, sneak-interaction, the activation command, or the optional proximity trigger. No natural spawning occurs by default.

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

## Permissions notes

Most server command classes do not override Forge's default permission behavior. `/rntwitherstorm` and `/rntachievement` explicitly require permission level 2, and `/hbmbedrockdrop` explicitly requires permission level 4. Server owners should additionally restrict command access with their server management tooling, especially for radiation, stations, satellites, recipe reloads, chunk diagnostics, and nuclear toggles.
