# Nuclear weapon balance

Nuclear weapon destructiveness is balanced from kiloton yield instead of arbitrary radius values. A 15 kt Little Boy-class weapon is the calibration point and maps to a 48 block crater radius; other nuclear yields scale by the cube root of yield so larger warheads become more destructive without growing linearly out of control.

Missile nuclear warheads now store their blast radius from a KT-derived value:

| Warhead | Size | Yield | Notes |
| --- | --- | ---: | --- |
| Tater Tot | 1.0 m | 5 kt | Small tactical payload for compact missiles. |
| Chernobyl Boris | 1.0 m | 20 kt | Heavier compact payload with Fat Man-class destructiveness. |
| Auntie Bertha | 1.5 m | 150 kt | Large missile payload for strategic use. |

Inventory tooltips for nuclear missile parts and assembled missiles display `Yield` in kt instead of only a generic strength number. Placeable historical nuclear blocks also display their KT or MT yield in item form.

## Blast effects on entities

Nuclear entity damage is evaluated as a ground-facing blast zone rather than a full sphere. Its horizontal radius remains the weapon's lethal radius, while its upper edge reaches half that radius above the detonation and its lower edge reaches one full radius below it. This keeps aircraft that are well above a detonation outside the lethal volume, but ensures an airburst still delivers its full horizontal blast effect to exposed targets beneath it.

Exposed entities inside that volume take damage based on horizontal distance from the burst and are set on fire for 15 seconds. Terrain and other solid obstructions still shield entities from the direct blast calculation. This deliberately prioritizes playable blast and thermal effects over a literal simulation of every nuclear effect.
