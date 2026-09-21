package com.hbm.commands;

import api.hbm.energymk2.PowerNetDiagnostics;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class CommandPowerNetStats extends CommandBase {

	@Override
	public String getCommandName() {
		return "ntmpowerstats";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/ntmpowerstats";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		for(String line : PowerNetDiagnostics.getReport()) sender.addChatMessage(new ChatComponentText(line));
	}
}
