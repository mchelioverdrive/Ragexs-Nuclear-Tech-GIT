package com.hbm.commands;

import java.util.List;

import com.hbm.config.GeminiConfig;
import com.hbm.config.GeminiConfig.ActivationMode;
import com.hbm.world.gemini.GeminiInvasionManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Inspects and overrides the shared Mars milestone for testing. */
public final class CommandVisitedMars extends CommandBase {
	@Override public String getCommandName() { return "visitedmars"; }
	@Override public int getRequiredPermissionLevel() { return 2; }
	@Override public String getCommandUsage(ICommandSender sender) { return "/visitedmars [true|false]"; }

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		if(args.length > 1 || args.length == 1 && !("true".equalsIgnoreCase(args[0]) || "false".equalsIgnoreCase(args[0])))
			throw new WrongUsageException(getCommandUsage(sender));
		World world = sender.getEntityWorld();
		if(args.length == 1 && !GeminiInvasionManager.setMarsVisited(world, Boolean.parseBoolean(args[0])))
			throw new CommandException("The server Overworld is not available.");
		boolean visited = GeminiInvasionManager.hasMarsVisited(world);
		ActivationMode mode = GeminiConfig.geminiActivationMode;
		String result = "visitedmars=" + visited + ", activation mode=" + mode
				+ ", Gemini invasion phase=" + GeminiInvasionManager.getPhase(world) + ".";
		if(args.length == 1 && mode == ActivationMode.MARS)
			result += visited ? " Mars activation condition and GHE spawn eggs unlocked."
					: " Mars activation condition and GHE spawn eggs locked. Saved progress retained.";
		sender.addChatMessage(new ChatComponentText(result));
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		return args.length == 1 ? getListOfStringsMatchingLastWord(args, "true", "false") : null;
	}
}
