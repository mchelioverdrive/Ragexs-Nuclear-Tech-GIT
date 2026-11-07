package com.hbm.commands;

import java.util.List;

import com.hbm.world.gemini.GeminiInvasionManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Controls the shared Gemini Mars gate for spawn-egg and invasion testing. */
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
		sender.addChatMessage(new ChatComponentText("visitedmars=" + visited
				+ "; Gemini invasion phase=" + GeminiInvasionManager.getPhase(world) + "."
				+ (args.length == 0 ? "" : visited
						? " GHE spawn eggs unlocked; natural invasions retain their phase/configuration rules."
						: " GHE spawns and activity locked; saved creatures and invasion progress retained.")));
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		return args.length == 1 ? getListOfStringsMatchingLastWord(args, "true", "false") : null;
	}
}
