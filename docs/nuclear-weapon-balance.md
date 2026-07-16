# Nuclear weapon balance

Nuclear weapon destructiveness is balanced from kiloton yield instead of arbitrary radius values. A 15 kt Little Boy-class weapon is the calibration point and maps to a 48 block crater radius; other nuclear yields scale by the cube root of yield so larger warheads become more destructive without growing linearly out of control.

Missile nuclear warheads now store their blast radius from a KT-derived value:

| Warhead | Size | Yield | Notes |
| --- | --- | ---: | --- |
| Tater Tot | 1.0 m | 5 kt | Small tactical payload for compact missiles. |
| Chernobyl Boris | 1.0 m | 20 kt | Heavier compact payload with Fat Man-class destructiveness. |
| Auntie Bertha | 1.5 m | 150 kt | Large missile payload for strategic use. |

Inventory tooltips for nuclear missile parts and assembled missiles display `Yield` in kt instead of only a generic strength number. Placeable historical nuclear blocks also display their KT or MT yield in item form.
