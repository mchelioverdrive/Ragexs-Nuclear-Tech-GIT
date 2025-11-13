package com.hbm.blocks.bomb;

import api.hbm.block.IToolable;
import com.hbm.items.ModItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DefuseHandler {
	private static final List<DefuseData> pendingDefuses = new ArrayList<DefuseData>();

	public static void startDefuse(World world, int x, int y, int z, String playerName, IToolable.ToolType tool) {
		pendingDefuses.add(new DefuseData(world, x, y, z, playerName, tool, 100));
	}



	public static void onTick(World world) {
		Iterator<DefuseData> it = pendingDefuses.iterator();
		while (it.hasNext()) {
			DefuseData data = it.next();
			if (data.world == world) {
				data.ticks--;
				if (data.ticks <= 0) {
					EntityPlayer defuser = world.getPlayerEntityByName(data.playerName);
					if (defuser != null && defuser.getHeldItem() != null && defuser.getHeldItem().getItem() == ModItems.defuser) {
						double dist = defuser.getDistanceSq(data.x + 0.5D, data.y + 0.5D, data.z + 0.5D);
						if (dist < 9.0D && world.getBlock(data.x, data.y, data.z) == data.world.getBlock(data.x, data.y, data.z)) {
							world.func_147480_a(data.x, data.y, data.z, false);
							data.world.getBlock(data.x, data.y, data.z).dropBlockAsItem(world, data.x, data.y, data.z, 0, 0);
							defuser.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "[ Bomb Defused! ]"));
						} else {
							defuser.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + "[ Defuse Failed - You Moved! ]"));
						}
					}
					it.remove();
				}
			}
		}
	}

	private static class DefuseData {
		public World world;
		public int x, y, z;
		public String playerName;
		public IToolable.ToolType tool;
		public int ticks;

		public DefuseData(World world, int x, int y, int z, String playerName, IToolable.ToolType tool, int ticks) {
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
			this.playerName = playerName;
			this.tool = tool;
			this.ticks = ticks;
		}
	}
}

