package com.hbm.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockMetallicHydrogen extends Block {

	public BlockMetallicHydrogen() {

		super(Material.iron);

		this.setBlockName(
			"metallic_hydrogen"
		);

		this.setBlockTextureName(
			"hbm:block_lead"
		);

		this.setHardness(250F);
		this.setResistance(
			999999F
		);

		this.setLightLevel(
			0.15F
		);
	}
}
