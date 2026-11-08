# Restraints and simplified sedation

Handcuffs, straight jackets and tranquilizers support adult villagers, zombies, skeletons and other tall, narrow humanoid creatures. Bosses and oversized entities are excluded. Player restraint and sedation are **disabled by default**; server owners must explicitly enable `allowPlayerRestraint` in `27_restraints` in `hbm.cfg`.

## Capturing and escorting

Use Handcuffs on a supported entity to restrain it. Use a Straight Jacket on an already handcuffed or unconscious entity to prevent ordinary attacks and slow its movement. To remove equipment, sneak-use the entity with an empty hand: the jacket comes off first, then the handcuffs. Each removal returns the corresponding item. These items are consumed when equipped, including in Creative, so removal cannot manufacture extra items.

Hold a vanilla lead and use the restrained or unconscious entity to attach an escort; use it again to detach. Keep the lead held and remain within ten blocks. Handcuffed entities stop when the escort is unavailable; unconscious captives can be dragged over clear ground. Escorting does not teleport a captive through obstacles, across dimensions or up cliffs. Use the lead on an empty device to transfer the nearest eligible captive you are escorting. The transfer removes its escort state.

A Ketamine Syringe works by direct use on an entity and returns an empty syringe. A Ketamine Dart is thrown using RNT's throwable projectile system. The inherited dart guns are unregistered legacy content, so these darts work without requiring one. Ketamine is represented as simplified anesthetic gameplay: both items apply slowing during a configurable onset, then temporary unconsciousness and a recovery interval. The defaults are 60 onset ticks, 1,200 unconscious ticks and 200 recovery ticks. There is no instant unconsciousness on impact. Further hits during onset, unconsciousness or recovery do not extend the duration. The timers count loaded simulation ticks and persist through saves. Existing tranquilizer item registry names are retained for inventory and save compatibility.

## Pharmaceutical crafting

Use the existing Chemical Reactor and Assembler, with their chemistry/assembly templates and normal power supply. All intermediate chemistry below is fictional game progression, without real synthesis or dosage calculations. The existing clean Rag supplies the cloth/filter material; no separate sterile material is required.

| Machine | Inputs per cycle | Output |
| --- | --- | --- |
| Chemical Reactor: Pharmaceutical Base Processing | 2 Biomass, 2 Sugar, 1 Rag, 2 L Fresh Water | 2 Pharmaceutical Base |
| Assembler | 1 Pharmaceutical Base, 1 Empty Syringe, 1 Rag | 1 Ketamine Syringe |
| Crafting grid (shapeless) | 1 Ketamine Syringe, 1 Steel Ingot, 1 Feather | 1 Ketamine Dart |
| Chemical Reactor: Fictional Terminal Reagent | 4 Pharmaceutical Base, 4 Poison Powder, 2 Polymer Ingots, 2 L Fresh Water | 1,000 nominal units Terminal Reagent |
| Chemical Reactor: Terminal Injection Filling | 1 Empty Syringe, 1 Rag, 250 nominal units Terminal Reagent | 1 Terminal Injection |

Base processing takes 60 seconds at 2 kW; terminal reagent takes 120 seconds at 4 kW, and filling takes 20 seconds at 4 kW. One reagent batch fills four injections. These amounts are abstract crafting units. The reagent has no real-world chemical formulation.

Fill an Empty Universal Fluid Tank from the reagent reactor's fluid output, then place that filled tank in the filling reactor's matching fluid input. The existing fluid loader transfers its actual contents and returns the empty tank for reuse. A standard reagent tank holds 1,000 nominal units. Each filling cycle subtracts 250 units from the reactor, so an empty tank supplies nothing. Pipes can transfer the reagent instead. Tanks are fluid containers, not solid recipe ingredients; no crafting-grid fluid shortcut exists. The former slimeball/glowstone syringe and ender-pearl terminal recipes are removed. Server packs with custom `hbmChemplant.json` or `hbmAssembler.json` overrides must add these recipes to their overrides; ordinary default registries include them automatically.

## Devices

Place devices on clear ground with room for their full structure. All four retain the original entity; they do not create captive copies. Empty-hand use releases an inactive, unlocked captive when a clear supported exit exists. Sneak-use controls the device. To activate, sneak-use once to arm, **stand up**, then sneak-use again within three seconds. The same player must confirm, and holding use cannot activate it. Ordinary use cancels the pending confirmation. Redstone never activates these devices.

| Device | Operation |
| --- | --- |
| Guillotine | Load a captive, then confirm activation. The blade falls, damages only its captive on impact, and returns automatically. Release is available before activation. |
| Brazen Bull | Insert solid furnace fuel, load a captive and sneak-use to close and lock the hatch. Sneak-use twice more to begin heating. Sneak-use during heating stops it. Empty-hand use unlocks the hatch after cooling; use again to release. Residual heat continues to harm the captive until the chamber cools. Fuel remains saved when heating is stopped. |
| Electric Chair | Connect an RNT power cable directly to the base. At the default setting, activation requires 20,000 J stored and discharge consumes 1,000 J per tick. Confirm to start; sneak-use again to stop. Losing power stops the discharge. Electrical damage affects only the captive. |
| Injection Table | Insert one Terminal Injection, load a captive and confirm. One item is consumed and a fictional damage-over-time effect is committed to the entity; unloading or releasing it afterward does not cancel that effect. Release before confirmation avoids activation. |

Insert fuel or injections by using the held item on the device, or automate the core inventory with RNT inventory tools. Use an empty hand on an empty, inactive device to recover its stored items. Breaking a device clears captivity and drops its inventory; any still-loaded captive is moved to a clear exit when available. Captives whose device chunk is unloaded remain anchored without forcing the chunk to load, then validate their UUID and device token when it returns.

Terminal Injection applies the constant `injectionDamage` once every 20 loaded ticks for `injectionEffectTicks` (defaults: 4 damage per second for 200 ticks). The first damage occurs after 20 ticks. There is no final burst or guaranteed kill, and a partial final second adds no extra hit. Repeated activation cannot stack or extend an active effect. Damage uses Minecraft's normal damage/death path, respecting immunity and event cancellation, and stops at death. Active timers and their damage cadence survive saving and loading.

With `allowPlayerRestraint` enabled, eligible players can also use Terminal Injection on themselves: **sneak-use in air**, stand up while keeping the same syringe held, then sneak-use in air again within three seconds. Holding use while sneaking cannot confirm. Ordinary use cancels confirmation; changing the held stack, expiry, captivity, dimension changes or session changes invalidates it. Successful Survival self-use consumes one injection and returns an empty syringe, including when using the last dose in a stack. Creative self-use retains the injection without producing empty syringes. It starts the same timed effect as the table and preserves normal death, respawn and inventory rules. Player-targeted table injections and self-use are disabled when player restraints are disabled.

## Configuration and compatibility

`27_restraints` controls the subsystem and devices, player support, sedation onset/duration/recovery, injection duration, damage amounts, chair energy use and bull temperature limits. Zero damage values disable that source of damage. Disabling restraints releases affected entities as they tick. Disabling devices releases their captives while leaving equipped restraints in place.

The generic compatibility check uses entity type and physical dimensions. `additionalHumanoidTypes` includes extra registry names; `excludedEntityTypes` excludes false positives. Neither overrides size limits or the boss exclusion. The bull additionally requires a captive no taller than 2.1 blocks.

Visible cuffs, jackets, sleeping poses and device poses support native biped and villager models and compatible subclasses. Unusual modded renderers retain their native appearance. Standard AI task instances are suspended and restored rather than replaced; jackets suspend recognizable combat tasks while preserving ordinary movement. A mod that implements attacks, item use, movement or rendering outside Forge events and native AI tasks may need its own integration. These controls do not alter unrelated entities' AI.
