package com.hbm.commands;

import com.hbm.config.GeneralConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import java.util.Locale;

public class CommandEnableNukes extends CommandBase {
	@Override
	public String getCommandName() {
		return "ntmenablenukes";
	}

	@Override
	public String getCommandUsage(ICommandSender iCommandSender) {
		return "true/false";
	}



	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		switch(args[0]) {
			case "true":
				GeneralConfig.enableNuking = true;
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(String.format(Locale.US, "%sNukes enabled.", EnumChatFormatting.GREEN)));
				break;
			case "false":
				GeneralConfig.enableNuking = false;
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(String.format(Locale.US, "%sNukes disabled.", EnumChatFormatting.RED)));
				break;
			default:
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(String.format(Locale.US, "%sInvalid argument. Use true/false.", EnumChatFormatting.RED)));
				break;
		}
	}


}
