package com.hbm.dim.mapgen;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.gen.MapGenRavine;

public class MapGenEuropaFractures extends MapGenRavine {

	public Block crackBlock =
		Blocks.packed_ice;

	public MapGenEuropaFractures() {

		// exact vanilla behavior first
		this.range = 8;
	}

	//@Override
	//protected boolean func_151538_a(
	//	// REMOVE THIS ENTIRE OVERRIDE
	//) {
	//	return false;
	//}
	//you CANNOT do this. cannot override superclass. STOP TRYING THIS.
}
