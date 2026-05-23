package com.hbm.commands;

import com.hbm.config.MiningConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class CommandBedrockDrop extends CommandBase {

	@Override
	public String getCommandName() {
		return "hbmaddbedrockdrop";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/hbmaddbedrockdrop <registry> <meta> <min> <max>";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 4; // OP/admin only
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {

		if(args.length != 4) {
			sender.addChatMessage(
				new ChatComponentText(
					"Usage: /hbmaddbedrockdrop <registry> <meta> <min> <max>"
				)
			);
			return;
		}

		try {

			String registry = args[0];
			int meta = Integer.parseInt(args[1]);
			int min = Integer.parseInt(args[2]);
			int max = Integer.parseInt(args[3]);

			String entry = registry + " " + meta + " " + min + " " + max;

			MiningConfig.excavatorBedrockDrops.add(entry);

			sender.addChatMessage(
				new ChatComponentText(
					"Added excavator bedrock drop: " + entry
				)
			);

		} catch(Exception ex) {

			sender.addChatMessage(
				new ChatComponentText(
					"Invalid arguments."
				)
			);
		}
	}
}
