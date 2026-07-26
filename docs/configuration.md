# Configuration Guide

RNTM has three main configuration styles:

1. Forge configuration values in `config/hbm.cfg`.
2. JSON configuration files in `config/hbmConfig/`.
3. JSON recipe templates and overrides in `config/hbmRecipes/`.

Always stop the server before editing startup configuration unless a command explicitly supports runtime reloads.

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
| `DARK_ADAPTATION_STRENGTH` | `1.0` | Scales shadow-only sensitivity loss/recovery (runtime-clamped to 0–2). |
| `DARK_ADAPTATION_ROD_SECONDS` | `90.0` | Slow rod recovery time (runtime-clamped to 10–300 seconds). |
| `DARK_ADAPTATION_NOISE` | `0.012` | Maximum intrinsic shadow-noise amplitude (runtime-clamped to 0–0.05). |
| `DARK_ADAPTATION_CENTER_LOSS` | `0.15` | Weak central scotopic acuity/sensitivity penalty (runtime-clamped to 0–0.35). |
| `DARK_ADAPTATION_QUALITY` | `1` | `0` disables the pass, `1` uses a five-tap blur, and `2` uses nine taps with more frequent exposure updates. |
| `DARK_ADAPTATION_DEBUG` | `false` | Shows exposure, cone/rod state, path, FBO/shader status, and Angelica detection. |

> Note: the current source maps `ITEM_TOOLTIP_SHOW_CUSTOM_NUKE` to the `ITEM_TOOLTIP_SHOW_OREDICT` key during default registration. Treat custom-nuke tooltip editing as a documentation gap until this is clarified or fixed in code.

### Darkness and eye adaptation

The client tracks a fast cone stage (4.5 seconds in darkness) and a slow rod stage (90 seconds by default). Bright exposure reverses these stages much faster (0.25 and 1.15 seconds). It never changes Minecraft's gamma option. Instead, only shadow pixels receive curved silhouette recovery, desaturation, local-contrast compression, spatial acuity loss, weak central-vision loss, and intrinsic noise. Mathematical black is not lifted into readable scenery.

The pass runs at the `RenderGameOverlayEvent.Pre(ALL)` boundary, after the completed world and hand but before Forge draws HUD elements. Inventory, chat, crosshair, scope/equipment overlays, debug text, and menus therefore remain unprocessed. Vanilla night vision, blindness, and RTM thermal armor suppress the ordinary-eye pass rather than accidentally stacking visual modes.

Exposure is updated every five frames (`quality=1`) or three frames (`quality=2`). The legacy/Angelica-safe path samples block and sky light around the eye, celestial brightness, and weather without any framebuffer CPU readback; RTM nuclear flashes are injected explicitly. The shader uses a reusable private framebuffer copy, queries/restores the previously bound framebuffer, and never samples an Angelica-owned attachment in place. Unsupported framebuffer hardware or shader compilation failure disables only this effect and leaves normal rendering intact.

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

Important behavior:

- If an active recipe file is missing, defaults are registered and a template named `_<filename>` is written.
- To override recipes, remove the leading underscore from a template file and edit the active file.
- `/ntmreload` reloads serializable recipes and item pools without a full restart, but test carefully.

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

## Recommended server workflow

1. Start a new server once to generate all configs.
2. Stop the server.
3. Configure `hbm.cfg` worldgen, dimension IDs, 528/LBS mode, nukes, radiation, and mob systems.
4. Start a temporary world to generate JSON templates.
5. Edit recipes, fluid traits, fallout, item pools, and custom machines as needed.
6. Test with representative players and machines.
7. Back up the final config set before opening the long-term world.
