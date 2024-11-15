package com.hbm.world.generator.room;

import java.util.ArrayList;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.MetaBlock;
import com.hbm.world.generator.CellularDungeon;
import com.hbm.world.generator.CellularDungeonRoom;
import com.hbm.world.generator.DungeonToolbox;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TestDungeonRoom4 extends CellularDungeonRoom {

	public TestDungeonRoom4(CellularDungeon parent, CellularDungeonRoom daisyChain, ForgeDirection dir) {
		super(parent);
		this.daisyChain = daisyChain;
		this.daisyDirection = dir;
	}


}
