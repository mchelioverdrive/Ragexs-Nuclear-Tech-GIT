package com.hbm.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Achievement;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.AchievementPage;

public class CommandRNTAchievement extends CommandBase {

	@Override
	public String getCommandName() {
		return "rntachievement";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/rntachievement list | /rntachievement unlock <player> <achievementId>";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		AchievementPage page = AchievementPage.getAchievementPage("Nuclear Tech");
		if(page == null) throw new CommandException("RNT achievements are not registered.");

		if(args.length == 1 && "list".equals(args[0])) {
			List<String> ids = getIds(page);
			sender.addChatMessage(new ChatComponentText("RNT achievement IDs (" + ids.size() + "):"));
			for(String id : ids) sender.addChatMessage(new ChatComponentText(id));
			return;
		}

		if(args.length != 3 || !"unlock".equals(args[0])) throw new CommandException(getCommandUsage(sender));

		EntityPlayerMP target = MinecraftServer.getServer().getConfigurationManager().func_152612_a(args[1]);
		if(target == null) throw new CommandException("Player is not online: " + args[1]);

		Achievement requested = null;
		for(Achievement achievement : page.getAchievements()) {
			if(achievement.statId.equals(args[2])) {
				requested = achievement;
				break;
			}
		}
		if(requested == null) throw new CommandException("Unknown RNT achievement ID: " + args[2]);

		if(target.func_147099_x().hasAchievementUnlocked(requested)) {
			sender.addChatMessage(new ChatComponentText(target.getCommandSenderName() + " already has " + requested.statId + "."));
			return;
		}

		List<Achievement> chain = new ArrayList<Achievement>();
		Set<Achievement> seen = new HashSet<Achievement>();
		for(Achievement current = requested; current != null; current = current.parentAchievement) {
			if(!seen.add(current)) throw new CommandException("Achievement parent cycle: " + current.statId);
			chain.add(current);
		}
		Collections.reverse(chain);

		for(Achievement achievement : chain) {
			if(!target.func_147099_x().hasAchievementUnlocked(achievement)) {
				target.triggerAchievement(achievement);
				if(!target.func_147099_x().hasAchievementUnlocked(achievement)) {
					throw new CommandException("Could not unlock achievement: " + achievement.statId);
				}
			}
		}
		target.func_147099_x().func_150884_b(target);
		sender.addChatMessage(new ChatComponentText("Unlocked " + requested.statId + " for " + target.getCommandSenderName() + "."));
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 1) return getListOfStringsMatchingLastWord(args, "list", "unlock");
		if(args.length == 2 && "unlock".equals(args[0])) {
			return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getConfigurationManager().getAllUsernames());
		}
		if(args.length == 3 && "unlock".equals(args[0])) {
			AchievementPage page = AchievementPage.getAchievementPage("Nuclear Tech");
			if(page != null) {
				List<String> ids = getIds(page);
				return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
			}
		}
		return null;
	}

	private static List<String> getIds(AchievementPage page) {
		List<String> ids = new ArrayList<String>();
		for(Achievement achievement : page.getAchievements()) ids.add(achievement.statId);
		Collections.sort(ids);
		return ids;
	}
}
