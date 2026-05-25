package com.hbm.commands;

import com.hbm.config.GeneralConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import java.util.Locale;

public class CommandEnableNukes extends CommandBase {

	//TODO this command but you can set a custom DATE and TIME for when nukes will be enabled,
	// and it will automatically enable/disable them at the specified time.
	// ALSO fix the fact this command says an unknown error occurred when you run it without true or false
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

		if(args.length == 0) {
			sender.addChatMessage(new net.minecraft.util.ChatComponentText(
				EnumChatFormatting.RED + "Usage: /ntmenablenukes true/false"
			));
			return;
		}

		switch(args[0].toLowerCase(Locale.US)) {

			case "true":
				GeneralConfig.enableNuking = true;
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(
					EnumChatFormatting.GREEN + "Nukes enabled."
				));
				break;

			case "false":
				GeneralConfig.enableNuking = false;
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(
					EnumChatFormatting.RED + "Nukes disabled."
				));
				break;

			default:
				sender.addChatMessage(new net.minecraft.util.ChatComponentText(
					EnumChatFormatting.RED + "Invalid argument. Use true/false."
				));
				break;
		}
	}


}
