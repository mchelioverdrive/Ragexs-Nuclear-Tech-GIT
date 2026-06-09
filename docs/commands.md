# Command Reference

This reference was verified against `src/main/java/com/hbm/commands` and the server command registration in `MainRegistry`.

## Server commands

These commands are registered during server startup.

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

## Permissions notes

Most server command classes do not override Forge's default permission behavior. `/hbmbedrockdrop` explicitly requires permission level 4. Server owners should additionally restrict command access with their server management tooling, especially for radiation, stations, satellites, recipe reloads, chunk diagnostics, and nuclear toggles.
