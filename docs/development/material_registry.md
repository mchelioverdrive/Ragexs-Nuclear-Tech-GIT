# Material declaration census

Current `Mats` declarations, reconstructed from production source. This is an exhaustive declaration inventory, not proof of runtime OreDictionary aliases, obtainability or every emitted form. Requested generation is filtered by native metadata support; `hasForm()` checks actual registered aliases. [Material reference](material_reference.md) defines acceptance, quantities, roles and persistent exceptions. [MaterialRoles](../../src/main/java/com/hbm/inventory/material/MaterialRoles.java) supplies role membership; [OreDictManager](../../src/main/java/com/hbm/inventory/OreDictManager.java) supplies native form registrations, including identities outside `Mats`.

127 active material declarations. `makeSmeltable`, `makeNonSmeltable`, `makeAdditive` and explicit conversions retain distinct native behavior. Shape requests do not imply that ingot/dust/block generators exist for every material.

| Material field | Persistent metadata | Constructor / conversion | Requested generated shapes |
| --- | ---: | --- | --- |
| `MAT_WOOD` | 3 | `makeNonSmeltable` | `STOCK, GRIP` |
| `MAT_IVORY` | 4 | `makeNonSmeltable` | `GRIP` |
| `MAT_STONE` | 0 | `makeSmeltable` | None requested |
| `MAT_CARBON` | 699 | `makeAdditive` | `WIRE, BLOCK` |
| `MAT_COAL` | 600 | `conversion: MAT_CARBON,  2, 1` | `FRAGMENT` |
| `MAT_LIGNITE` | 601 | `conversion: MAT_CARBON,  3, 1` | `FRAGMENT` |
| `MAT_COALCOKE` | 610 | `conversion: MAT_CARBON,  4, 3` | None requested |
| `MAT_PETCOKE` | 611 | `conversion: MAT_CARBON,  4, 3` | None requested |
| `MAT_LIGCOKE` | 612 | `conversion: MAT_CARBON,  4, 3` | None requested |
| `MAT_GRAPHITE` | 620 | `conversion: MAT_CARBON,  1, 1` | None requested |
| `MAT_DIAMOND` | 1430 | `conversion: MAT_CARBON,  1, 1` | `FRAGMENT` |
| `MAT_IRON` | 2600 | `makeSmeltable` | `FRAGMENT, DUST, PIPE, CASTPLATE, WELDEDPLATE, BLOCK` |
| `MAT_GOLD` | 7900 | `makeSmeltable` | `FRAGMENT, WIRE, NUGGET, DUST, DENSEWIRE, CASTPLATE, BLOCK` |
| `MAT_REDSTONE` | 1 | `makeSmeltable` | `FRAGMENT` |
| `MAT_OBSIDIAN` | 2 | `makeSmeltable` | None requested |
| `MAT_HEMATITE` | 2601 | `makeAdditive` | None requested |
| `MAT_WROUGHTIRON` | 2602 | `makeSmeltable` | None requested |
| `MAT_PIGIRON` | 2603 | `makeSmeltable` | None requested |
| `MAT_METEORICIRON` | 2604 | `makeSmeltable` | None requested |
| `MAT_MALACHITE` | 2901 | `makeAdditive` | None requested |
| `MAT_CONGLOMERATE` | 2993 | `makeAdditive` | None requested |
| `MAT_BARIUM` | 5600 | `makeSmeltable` | `INGOT, NUGGET` |
| `MAT_URANIUM` | 9200 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_U233` | 9233 | `makeSmeltable` | `NUGGET, BILLET, DUST, BLOCK` |
| `MAT_U235` | 9235 | `makeSmeltable` | `NUGGET, BILLET, DUST, BLOCK` |
| `MAT_U238` | 9238 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_THORIUM` | 9032 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_PLUTONIUM` | 9400 | `makeSmeltable` | `NUGGET, BILLET, DUST, BLOCK` |
| `MAT_RGP` | 9401 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_PU238` | 9438 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_PU239` | 9439 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_PU240` | 9440 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_PU241` | 9441 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_RGA` | 9501 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_AM241` | 9541 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_AM242` | 9542 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_NEPTUNIUM` | 9337 | `makeSmeltable` | `NUGGET, BILLET, DUST, BLOCK` |
| `MAT_POLONIUM` | 8410 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_TECHNETIUM` | 4399 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, BLOCK` |
| `MAT_RADIUM` | 8826 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_ACTINIUM` | 8927 | `makeSmeltable` | `NUGGET, BILLET` |
| `MAT_CO60` | 2760 | `makeSmeltable` | `NUGGET, BILLET, DUST` |
| `MAT_AU198` | 7998 | `makeSmeltable` | `NUGGET, BILLET, DUST` |
| `MAT_PB209` | 8209 | `makeSmeltable` | `NUGGET, BILLET, DUST` |
| `MAT_SCHRABIDIUM` | 12626 | `makeSmeltable` | `NUGGET, WIRE, BILLET, DUST, DENSEWIRE, PLATE, CASTPLATE, BLOCK` |
| `MAT_SOLINIUM` | 12627 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_SCHRABIDATE` | 12600 | `makeSmeltable` | `DUST, DENSEWIRE, CASTPLATE, BLOCK` |
| `MAT_SCHRARANIUM` | 12601 | `makeSmeltable` | `BLOCK` |
| `MAT_GHIORSIUM` | 12836 | `makeSmeltable` | `NUGGET, BILLET, BLOCK` |
| `MAT_TITANIUM` | 2200 | `makeSmeltable` | `FRAGMENT, DUST, PLATE, CASTPLATE, WELDEDPLATE, SHELL, BLOCK, HEAVY_COMPONENT` |
| `MAT_COPPER` | 2900 | `makeSmeltable` | `FRAGMENT, WIRE, DUST, PLATE, CASTPLATE, WELDEDPLATE, SHELL, PIPE, BLOCK, HEAVY_COMPONENT` |
| `MAT_TUNGSTEN` | 7400 | `makeSmeltable` | `FRAGMENT, WIRE, BOLT, DUST, DENSEWIRE, CASTPLATE, WELDEDPLATE, BLOCK, HEAVY_COMPONENT` |
| `MAT_ALUMINIUM` | 1300 | `makeSmeltable` | `FRAGMENT, WIRE, DUST, PLATE, CASTPLATE, WELDEDPLATE, SHELL, PIPE, BLOCK, HEAVY_COMPONENT` |
| `MAT_LEAD` | 8200 | `makeSmeltable` | `FRAGMENT, NUGGET, WIRE, BOLT, DUST, PLATE, CASTPLATE, PIPE, BLOCK, HEAVY_COMPONENT` |
| `MAT_TIN` | 2300 | `makeSmeltable` | `FRAGMENT, NUGGET, DUST, PLATE, CASTPLATE, WELDEDPLATE, SHELL, PIPE, BLOCK` |
| `MAT_BISMUTH` | 8300 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET, DUST, BLOCK` |
| `MAT_ARSENIC` | 3300 | `makeSmeltable` | `NUGGET` |
| `MAT_TANTALIUM` | 7300 | `makeSmeltable` | `NUGGET, DUST, BLOCK` |
| `MAT_NEODYMIUM` | 6000 | `makeSmeltable` | `FRAGMENT, NUGGET, DUSTTINY, INGOT, DUST, DENSEWIRE, BLOCK` |
| `MAT_NIOBIUM` | 4100 | `makeSmeltable` | `FRAGMENT, NUGGET, DUSTTINY, DUST, BLOCK` |
| `MAT_BERYLLIUM` | 400 | `makeSmeltable` | `FRAGMENT, NUGGET, DUST, BLOCK` |
| `MAT_EMERALD` | 401 | `makeNonSmeltable` | `FRAGMENT, DUST, GEM, BLOCK` |
| `MAT_COBALT` | 2700 | `makeSmeltable` | `FRAGMENT, NUGGET, DUSTTINY, BILLET, DUST, BLOCK` |
| `MAT_BORON` | 500 | `makeSmeltable` | `FRAGMENT, DUSTTINY, DUST, BLOCK` |
| `MAT_RHODIUM` | 6900 | `makeSmeltable` | `NUGGET, DUST, BLOCK, INGOT` |
| `MAT_BORAX` | 501 | `makeSmeltable` | `FRAGMENT, INGOT, DUST` |
| `MAT_LANTHANIUM` | 5700 | `makeSmeltable` | `FRAGMENT, BLOCK` |
| `MAT_ZIRCONIUM` | 4000 | `makeSmeltable` | `FRAGMENT, NUGGET, WIRE, DUSTTINY, BILLET, DUST, CASTPLATE, WELDEDPLATE, BLOCK` |
| `MAT_SODIUM` | 1100 | `makeSmeltable` | `FRAGMENT, INGOT, DUST` |
| `MAT_SODALITE` | 1101 | `makeNonSmeltable` | `FRAGMENT, GEM` |
| `MAT_STRONTIUM` | 3800 | `makeSmeltable` | `FRAGMENT, INGOT, DUST` |
| `MAT_CALCIUM` | 2000 | `makeSmeltable` | `DUST` |
| `MAT_LITHIUM` | 300 | `makeSmeltable` | `FRAGMENT, DUST, BLOCK` |
| `MAT_SULFUR` | 1600 | `makeNonSmeltable` | `FRAGMENT, DUST, BLOCK` |
| `MAT_KNO` | 700 | `makeNonSmeltable` | `FRAGMENT, DUST, BLOCK` |
| `MAT_FLUORITE` | 900 | `makeNonSmeltable` | `FRAGMENT, DUST, BLOCK` |
| `MAT_PHOSPHORUS` | 1531 | `makeNonSmeltable` | `FRAGMENT, DUST, BLOCK` |
| `MAT_CHLOROCALCITE` | 1701 | `makeNonSmeltable` | `FRAGMENT, DUST` |
| `MAT_MOLYSITE` | 1702 | `makeNonSmeltable` | `FRAGMENT, DUST` |
| `MAT_CINNABAR` | 8001 | `makeNonSmeltable` | `FRAGMENT, GEM` |
| `MAT_CADMIUM` | 4800 | `makeSmeltable` | `DUST` |
| `MAT_SILICON` | 1400 | `makeSmeltable` | `FRAGMENT, NUGGET, BILLET` |
| `MAT_MAGNESIUM` | 1500 | `makeSmeltable` | `FRAGMENT, NUGGET, DUST` |
| `MAT_CHROMIUM` | 4500 | `makeSmeltable` | `INGOT` |
| `MAT_ASBESTOS` | 1401 | `makeSmeltable` | `FRAGMENT, BLOCK` |
| `MAT_OSMIRIDIUM` | 7699 | `makeSmeltable` | `NUGGET, CASTPLATE, WELDEDPLATE` |
| `MAT_NICKEL` | 2800 | `makeSmeltable` | `NUGGET, DUST, BLOCK` |
| `MAT_GALLIUM` | 3100 | `makeSmeltable` | `NUGGET, DUST, DUSTTINY` |
| `MAT_ZINC` | 3000 | `makeSmeltable` | `NUGGET, DUST, WIRE` |
| `MAT_IRIDIUM` | 7700 | `makeSmeltable` | `INGOT` |
| `MAT_INDIUM` | 3200 | `makeSmeltable` | `NUGGET, DUST, INGOT` |
| `MAT_SAMARIUM` | 5000 | `makeSmeltable` | `INGOT` |
| `MAT_GADOLINIUM` | 5001 | `makeSmeltable` | `INGOT` |
| `MAT_TERBIUM` | 5002 | `makeSmeltable` | `INGOT` |
| `MAT_THULIUM` | 5003 | `makeSmeltable` | `INGOT` |
| `MAT_PLATNIUM` | 7800 | `makeSmeltable` | `NUGGET, INGOT, DUST` |
| `MAT_STEEL` | 30 | `makeSmeltable` | `DUSTTINY, BOLT, WIRE, DUST, PLATE, CASTPLATE, WELDEDPLATE, SHELL, PIPE, BLOCK, HEAVY_COMPONENT, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, GRIP` |
| `MAT_MINGRADE` | 31 | `makeSmeltable` | `WIRE, DUST, BLOCK` |
| `MAT_ALLOY` | 32 | `makeSmeltable` | `WIRE, DUST, DENSEWIRE, PLATE, CASTPLATE, BLOCK, HEAVY_COMPONENT` |
| `MAT_CALCINED_DOLOMITE` | 44 | `makeAdditive` | None requested |
| `MAT_DURA` | 33 | `makeSmeltable` | `BOLT, DUST, PIPE, BLOCK, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER, GRIP` |
| `MAT_DESH` | 42 | `makeSmeltable` | `DUST, CASTPLATE, BLOCK, HEAVY_COMPONENT, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, STOCK, GRIP` |
| `MAT_STAR` | 35 | `makeSmeltable` | `DUST, DENSEWIRE, BLOCK, PLATE` |
| `MAT_FERRO` | 37 | `makeSmeltable` | `HEAVYBARREL, HEAVYRECEIVER` |
| `MAT_TCALLOY` | 36 | `makeSmeltable` | `DUST, CASTPLATE, WELDEDPLATE, HEAVY_COMPONENT, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER` |
| `MAT_CDALLOY` | 43 | `makeSmeltable` | `CASTPLATE, WELDEDPLATE, HEAVY_COMPONENT, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER` |
| `MAT_BBRONZE` | 46 | `makeSmeltable` | `CASTPLATE, LIGHTBARREL, LIGHTRECEIVER` |
| `MAT_ABRONZE` | 47 | `makeSmeltable` | `CASTPLATE, LIGHTBARREL, LIGHTRECEIVER` |
| `MAT_BSCCO` | 48 | `makeSmeltable` | `DENSEWIRE` |
| `MAT_MAGTUNG` | 38 | `makeSmeltable` | `WIRE, DUST, DENSEWIRE, BLOCK` |
| `MAT_CMB` | 39 | `makeSmeltable` | `DUST, PLATE, CASTPLATE, WELDEDPLATE, BLOCK` |
| `MAT_DNT` | 45 | `makeSmeltable` | `DUST, DENSEWIRE, BLOCK` |
| `MAT_FLUX` | 40 | `makeAdditive` | `DUST` |
| `MAT_SLAG` | 41 | `makeSmeltable` | `BLOCK` |
| `MAT_GUNMETAL` | 49 | `makeSmeltable` | `LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER, MECHANISM, STOCK, GRIP` |
| `MAT_WEAPONSTEEL` | 50 | `makeSmeltable` | `SHELL, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER, MECHANISM, STOCK, GRIP` |
| `MAT_BRONZE` | 51 | `makeSmeltable` | `INGOT, CASTPLATE, LIGHTBARREL, LIGHTRECEIVER` |
| `MAT_SATURN` | 34 | `makeSmeltable` | `PLATE, CASTPLATE, SHELL, BLOCK, LIGHTBARREL, HEAVYBARREL, LIGHTRECEIVER, HEAVYRECEIVER, MECHANISM, STOCK, GRIP` |
| `MAT_GAAS` | 24000 | `makeSmeltable` | `NUGGET, BILLET` |
| `MAT_STAINLESS` | 24001 | `makeSmeltable` | `PLATE` |
| `MAT_SOLDER` | 24002 | `makeSmeltable` | `WIRE` |
| `MAT_RAREEARTH` | 20000 | `makeNonSmeltable` | `FRAGMENT` |
| `MAT_POLYMER` | 20001 | `makeNonSmeltable` | `STOCK, GRIP` |
| `MAT_BAKELITE` | 20002 | `makeNonSmeltable` | `STOCK, GRIP` |
| `MAT_RUBBER` | 20003 | `makeNonSmeltable` | `PIPE, GRIP` |
| `MAT_HARDPLASTIC` | 20004 | `makeNonSmeltable` | `STOCK, GRIP` |
| `MAT_PVC` | 20005 | `makeNonSmeltable` | `STOCK, GRIP` |
