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

Dark adaptation uses two deliberately separate signals. Rendered-scene exposure is metered from the completed world immediately before the HUD with a 16×12 GPU-downsampled image; its lower 85% trimmed mean drives physiological cone and rod adaptation without letting isolated torches or particles dominate. Environmental scotopic illumination instead combines saved sky-light availability around the eye with direct sky access, the vanilla night and moon phase, a nonzero starlight floor, and rain/thunder attenuation. This second signal gates recovery only when outdoor photons can plausibly reach the player, so sealed rooms and deep caves remain effectively black.

With Hardcore Darkness installed, the compatibility hook changes only the lightmap's original `0.95`/`0.05` sky-factor pair. The resulting factor is `sunBrightness * (1 - activeCarrier) + activeCarrier`. The configured value is its base; current eye adaptation and perceived outdoor ambient light can gradually raise it, up to the fixed 0.25 bound. This carrier preserves raw low-level outdoor RGB and texture information rather than prescribing final screen brightness. It does not restore the later global RGB floor or affect block light: zero-skylight sealed caves therefore remain black, while RTM's post-process still controls perception. Minecraft's nighttime lightmap is strongly blue-biased, so the retained RGB ratio is deliberately not treated as directly perceived dark color and compatibility does not depend on Hardcore Darkness's `removeBlue` option.

Environmental illumination is converted with a bounded `ambient^0.30` perceptual curve, then combined with 25% cone and 75% rod recovery and a target display luminance. RTM first extracts luminance from nonzero framebuffer RGB and applies bounded scotopic recovery to that luminance, separately doing the same for the existing blurred neighborhood. It then reconstructs texture at the recovered luminance and decides how much source chroma remains perceptible. Deep rod-dominated vision is neutral and nearly monochrome (0–5% chroma at full rod effect), twilight retains progressively more limited color, and locally bright pixels remain colored and sharp even while the eye is otherwise dark-adapted. Only effectively black RGB (the 0.00005–0.00015 transition) uses world depth for broad achromatic shape fallback.

Linearized world depth also provides a subtle player-only near-field perception effect between 2.5 and 4 blocks. It adds at most 30% to achromatic coarse-shape recovery, fades smoothly, and requires both environmental ambient light and active eye recovery. This is a screen-space perception effect, not a Minecraft light source: it does not alter block or sky values, preserve extra chroma, pass through walls, affect spawning, or operate in a sealed zero-light cave. Zero ambient input maps both ordinary gain and the near-field effect to exactly zero. Cleared-depth sky and void receive no synthetic floor, and depth cannot recreate texture information that Hardcore Darkness already discarded.

The compact debug overlay reports `sceneExposure`, cone/rod/effective adaptation, requested/used strength, ambient/perceived ambient, eye recovery, sky/night/moon/weather factors, expected broad/shape black luminance, `depthCopySucceeded`, `worldDepthCurrent`, `worldDepthFrameAge`, `worldDepthSource`, `worldDepthFramebuffer`, `geometryCoverage`, `depthAvailableToShader`, shader state, and failure reason. `geometryCoverage` is a debug-only GPU downsample of the exact geometry mask to 16×12; it is the fraction of samples that are not exact cleared depth, rather than a full-resolution CPU read.

Previously, `depthAvailable == true` meant only that OpenGL accepted the copy. Because color and depth were both copied at the pre-HUD event, the copied depth could be cleared or replaced and no longer represent world geometry. Depth is now copied at highest-priority `RenderWorldLastEvent`, after vanilla terrain, entities, and tile entities but before the first-person hand and HUD. Final color remains captured at `RenderGameOverlayEvent.Pre(ALL)`. The renderer records the render generation, display size, and actually bound source framebuffer, then exposes depth to the shader only when they match the color frame. It restores that framebuffer after capture and labels an active Angelica framebuffer without modifying Angelica-owned attachments.

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
