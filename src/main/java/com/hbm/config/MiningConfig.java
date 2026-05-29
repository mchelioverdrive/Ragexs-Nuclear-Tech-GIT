package com.hbm.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MiningConfig {

	/**
	 * Format:
	 * modid:item meta min max
	 */
	public static List<String> excavatorBedrockDrops =
		new ArrayList<String>(Arrays.asList(

			// Iron oxide
			"hbm:item.chunk_ironoxide 0 2 6",

			// Copper sulfide
			"hbm:item.chunk_coppersulfide 0 2 5",

			// Lead / zinc sulfide
			"hbm:item.chunk_leadzincsulfide 0 1 4",

			// Nickel sulfide
			"hbm:item.chunk_nickelsulfide 0 1 4",

			// Tin / tungsten
			"hbm:item.chunk_tintungsten 0 1 3",

			// Aluminum ore block
			"hbm:tile.ore_aluminium 0 1 3",

			// Rare ore chunk
			"hbm:item.chunk_ore 0 0 2", // replace meta with actual rare enum meta

			// Lithium pegmatite
			"hbm:item.chunk_lithiumpegmatite 0 1 2",

			// Uranium ore
			"hbm:tile.ore_uranium 0 0 2",

			// Heavy mineral sand
			"hbm:item.chunk_heavymineralsand 0 1 4",

			// Chromite
			"hbm:item.chunk_chromite 0 1 3",

			// Evaporite
			"hbm:item.chunk_evaporite 0 1 4",

			// Fire ore
			"hbm:tile.ore_fire 0 0 1",

			// Carbon
			"hbm:item.chunk_carbon 0 2 8",

			// Beryllium ore
			"hbm:tile.ore_beryllium 0 0 2",

			// Pollucite
			"hbm:tile.ore_pollucite 0 0 2",

			// Quartz
			"minecraft:quartz 0 1 5",
			//lapis
			"minecraft:lapis_ore 0 0 4"
		));
}
