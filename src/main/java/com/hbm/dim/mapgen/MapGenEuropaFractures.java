package com.hbm.dim.mapgen;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.gen.MapGenRavine;

public class MapGenEuropaFractures extends MapGenRavine {

	public Block crackBlock =
		Blocks.packed_ice;

	public MapGenEuropaFractures() {

		this.range = 8;
	}

	@Override
	protected void func_151540_a(
		long seed,
		int chunkX,
		int chunkZ,
		Block[] blocks,
		double x,
		double y,
		double z,
		float width,
		float yaw,
		float pitch,
		int startStep,
		int endStep,
		double verticalScale) {

		/*
		 * Vanilla ravines work.
		 * Just widen them.
		 */

		width *= 2.2F;

		super.func_151540_a(
			seed,
			chunkX,
			chunkZ,
			blocks,
			x,
			y,
			z,
			width,
			yaw,
			pitch,
			startStep,
			endStep,
			verticalScale
		);
	}
}
