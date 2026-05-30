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

		//TODO test
		//pitch *= 0.35F;
		//verticalScale *= 0.7D;

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

	@Override
	public void func_151539_a(
		net.minecraft.world.chunk.IChunkProvider provider,
		net.minecraft.world.World world,
		int chunkX,
		int chunkZ,
		Block[] blocks) {

		/*
		 * Stable regional clustering.
		 *
		 * Large fracture belts where
		 * ravines become extremely
		 * common and bunched.
		 */

		double fractureField =
			Math.sin(chunkX * 0.045D)
				+ Math.cos(chunkZ * 0.045D);

		/*
		 * Normal Europa terrain
		 */
		int passes = 1;

		/*
		 * Fracture zone
		 */
		if(fractureField > 0.6D) {

			passes = 18;
		}

		/*
		 * Dense core region
		 */
		if(fractureField > 1.2D) {

			passes = 35;
		}

		for(int i = 0; i < passes; i++) {

			super.func_151539_a(
				provider,
				world,
				chunkX,
				chunkZ,
				blocks
			);
		}
	}

}
