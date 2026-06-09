# Server Administration Guide

This guide focuses on operating RNTM in multiplayer.

## Pre-launch checklist

Before opening a world to players:

- Confirm all players can run Minecraft 1.7.10, Java 8, Forge, and the same RNTM jar.
- Generate configs on a test server.
- Decide whether nuclear warfare is allowed (`2.0RTM` in `hbm.cfg`).
- Decide whether to use 528 mode or Less Bullshit Mode.
- Review ore, structure, meteor, mob, radiation, biome, and dimension settings before the first permanent world generation.
- Reserve dimension IDs if the pack has other dimension mods.
- Test the exact modpack on a copy before public launch.

## Suggested server rules

Because the mod can permanently damage terrain and player bases, publish rules for:

- Nuclear device use.
- Missile and explosive testing areas.
- Reactor construction near claims/spawn.
- Radioactive waste disposal.
- Pollution-heavy industry.
- Space station naming and ownership.
- Use of admin commands.

## Nuclear warfare controls

Startup/default control:

```text
config/hbm.cfg -> 01_general -> 2.0RTM
```

Runtime controls:

```text
/ntmenablenukes false
/ntmenablenukes true
/ntmenablenukes schedule false 2026-06-09 22:00
```

Use runtime toggles for events or emergencies, but keep the config aligned with your intended default after restart.

## Radiation cleanup and incidents

Useful command:

```text
/ntmrad clear
/ntmrad set <amount>
```

Recommended incident workflow:

1. Stop players entering the area.
2. Make a backup if the world is still stable.
3. Inspect the area in creative/admin mode.
4. Decide whether to clean, rollback, or quarantine.
5. Use `/ntmrad clear` only when you intend to clear radiation data broadly for the current world context.

## Recipe and loot changes

- Use `config/hbmRecipes/` for serializable recipe overrides.
- Use `config/hbmConfig/hbmItemPools.json` for item pool overrides.
- Keep template copies under version control for your server pack.
- Use `/ntmreload` on a test server first; malformed JSON can interrupt gameplay.

## Space and station administration

Useful commands:

```text
/ntmstations list
/ntmstations fetch <id|name>
/ntmstations tp
/ntmsatellites list
/ntmsatellites descend <frequency>
```

Operational advice:

- Keep a record of station names and owners.
- Use `fetch` to recover or recreate a programmed station drive.
- Test teleport behavior after dimension ID changes.
- Avoid changing orbit dimension IDs after stations exist.

## Performance notes

High-load systems include explosions, fallout, atmosphere processing, worldgen, reactors, machines, and large item/fluid networks. Useful settings to review:

- `1.42_threadedAtmospheres` for atmosphere performance.
- Explosion lifespan, speed, fallout range, and chunk-loading settings in `06_explosions`.
- Meteor and structure spawn rates for worldgen overhead.
- Mob raid, glyphid, hive, infestation, and swarm options in `12_mobs`.

## Backups

Back up before:

- Updating the mod.
- Changing dimension IDs or biome IDs.
- Enabling new worldgen on an existing world.
- Testing reactors, nukes, or custom machines.
- Activating custom recipe/fallout/fluid/loot JSON files.

Minimum backup set:

```text
world/
config/hbm.cfg
config/hbmConfig/
config/hbmRecipes/
mods/RX-RNTM*.jar
```

## Known administration limitations

- Many commands rely on vanilla/Forge permission behavior unless a permission level is explicitly implemented.
- `/hbmbedrockdrop` edits the runtime excavator drop list; persistence is not clearly documented in code.
- Several JSON systems generate templates with leading underscores; forgetting to remove the underscore is a common cause of changes not applying.
- Legacy 1.7.10 server forks may alter ticking behavior and break machines.
