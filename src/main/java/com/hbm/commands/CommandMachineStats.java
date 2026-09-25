package com.hbm.commands;

import com.hbm.machine.MachineRuntimeManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class CommandMachineStats extends CommandBase {

	@Override
	public String getCommandName() {
		return "ntmmachinestats";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/ntmmachinestats";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		for(String line : MachineRuntimeManager.getReport(sender.getEntityWorld())) sender.addChatMessage(new ChatComponentText(line));
	}
}
