package com.hbm.config;

import java.util.ArrayList;
import java.util.List;

public class MiningConfig {

	/**
	 * Format:
	 * modid:item meta min max
	 *
	 * Example:
	 * minecraft:diamond 0 1 2
	 * needs to have:
	 * ModItems.chunk_ironoxide
	 * chunk_coppersulfide
	 * chunk_leadzincsulfide
	 * chunk_nickelsulfide
	 * chunk_tintungsten
	 * ModBlocks.ore_aluminium
	 * chunk_ore.rare (ENUM rare specifically)
	 * chunk_lithiumpegmatite
	 * ModBlocks.ore_uranium
	 * ModItems.chunk_heavymineralsand
	 * chunk_chromite
	 * chunk_evaporite
	 * ModBlocks.ore_fire
	 * ModItems.chunk_carbon
	 * ModBlocks.ore_beryllium
	 * ore_pollucite
	 * Items.quartz
	 */
	public static List<String> excavatorBedrockDrops = new ArrayList<String>();
}
