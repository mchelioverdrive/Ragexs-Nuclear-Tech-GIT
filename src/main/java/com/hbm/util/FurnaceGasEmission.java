package com.hbm.util;

import com.hbm.blocks.ModBlocks;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class FurnaceGasEmission {

	private static final ForgeDirection[] OUTPUT_DIRECTIONS = {
		ForgeDirection.UP,
		ForgeDirection.NORTH,
		ForgeDirection.SOUTH,
		ForgeDirection.WEST,
		ForgeDirection.EAST
	};

	private FurnaceGasEmission() { }

	/**
	 * Attempts to release carbon monoxide into an open block next to a burning furnace.
	 * Calls are intentionally probabilistic so gas accumulates slowly rather than every tick.
	 */
	public static void emitCarbonMonoxide(World world, int x, int y, int z, int chance) {
		if(world == null || world.isRemote || chance <= 0 || world.rand.nextInt(chance) != 0) return;

		int start = world.rand.nextInt(OUTPUT_DIRECTIONS.length);
		for(int i = 0; i < OUTPUT_DIRECTIONS.length; i++) {
			ForgeDirection direction = OUTPUT_DIRECTIONS[(start + i) % OUTPUT_DIRECTIONS.length];
			int gasX = x + direction.offsetX;
			int gasY = y + direction.offsetY;
			int gasZ = z + direction.offsetZ;

			if(world.isAirBlock(gasX, gasY, gasZ)) {
				world.setBlock(gasX, gasY, gasZ, ModBlocks.gas_monoxide);
				return;
			}
		}
	}
}
