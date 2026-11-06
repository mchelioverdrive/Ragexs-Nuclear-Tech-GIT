# Restraints and fictional sedation

Handcuffs, straight jackets and tranquilizers support adult villagers, zombies, skeletons and other tall, narrow humanoid creatures. Bosses and oversized entities are excluded. Player restraint and sedation are **disabled by default**; server owners must explicitly enable `allowPlayerRestraint` in `27_restraints` in `hbm.cfg`.

## Capturing and escorting

Use Handcuffs on a supported entity to restrain it. Use a Straight Jacket on an already handcuffed or unconscious entity to prevent ordinary attacks and slow its movement. To remove equipment, sneak-use the entity with an empty hand: the jacket comes off first, then the handcuffs. Each removal returns the corresponding item. These items are consumed when equipped, including in Creative, so removal cannot manufacture extra items.

Hold a vanilla lead and use the restrained or unconscious entity to attach an escort; use it again to detach. Keep the lead held and remain within ten blocks. Handcuffed entities stop when the escort is unavailable; unconscious captives can be dragged over clear ground. Escorting does not teleport a captive through obstacles, across dimensions or up cliffs. Use the lead on an empty device to transfer the nearest eligible captive you are escorting. The transfer removes its escort state.

A Tranquilizer Syringe works by direct use on an entity and returns an empty syringe. A Tranquilizer Dart is thrown using RNT's throwable projectile system. The inherited dart guns are unregistered legacy content, so these darts work without requiring one. Both apply fictional slowing followed by temporary unconsciousness, with a recovery interval afterward. Further hits during onset, unconsciousness or recovery do not extend the duration. The timers count loaded simulation ticks and persist through saves.

## Devices

Place devices on clear ground with room for their full structure. All four retain the original entity; they do not create captive copies. Empty-hand use releases an inactive, unlocked captive when a clear supported exit exists. Sneak-use controls the device. To activate, sneak-use once to arm, **stand up**, then sneak-use again within three seconds. The same player must confirm, and holding use cannot activate it. Ordinary use cancels the pending confirmation. Redstone never activates these devices.

| Device | Operation |
| --- | --- |
| Guillotine | Load a captive, then confirm activation. The blade falls, damages only its captive on impact, and returns automatically. Release is available before activation. |
| Brazen Bull | Insert solid furnace fuel, load a captive and sneak-use to close and lock the hatch. Sneak-use twice more to begin heating. Sneak-use during heating stops it. Empty-hand use unlocks the hatch after cooling; use again to release. Residual heat continues to harm the captive until the chamber cools. Fuel remains saved when heating is stopped. |
| Electric Chair | Connect an RNT power cable directly to the base. At the default setting, activation requires 20,000 J stored and discharge consumes 1,000 J per tick. Confirm to start; sneak-use again to stop. Losing power stops the discharge. Electrical damage affects only the captive. |
| Injection Table | Insert one Terminal Injection, load a captive and confirm. One item is consumed and a progressive fictional terminal effect is committed to the entity; unloading or releasing it afterward does not cancel that effect. Release before confirmation avoids activation. |

Insert fuel or injections by using the held item on the device, or automate the core inventory with RNT inventory tools. Use an empty hand on an empty, inactive device to recover its stored items. Breaking a device clears captivity and drops its inventory; any still-loaded captive is moved to a clear exit when available. Captives whose device chunk is unloaded remain anchored without forcing the chunk to load, then validate their UUID and device token when it returns.

## Configuration and compatibility

`27_restraints` controls the subsystem and devices, player support, sedation onset/duration/recovery, injection duration, damage amounts, chair energy use and bull temperature limits. Zero damage values disable that source of damage. Disabling restraints releases affected entities as they tick. Disabling devices releases their captives while leaving equipped restraints in place.

The generic compatibility check uses entity type and physical dimensions. `additionalHumanoidTypes` includes extra registry names; `excludedEntityTypes` excludes false positives. Neither overrides size limits or the boss exclusion. The bull additionally requires a captive no taller than 2.1 blocks.

Visible cuffs, jackets, sleeping poses and device poses support native biped and villager models and compatible subclasses. Unusual modded renderers retain their native appearance. Standard AI task instances are suspended and restored rather than replaced; jackets suspend recognizable combat tasks while preserving ordinary movement. A mod that implements attacks, item use, movement or rendering outside Forge events and native AI tasks may need its own integration. These controls do not alter unrelated entities' AI.
