package com.hbm.commands;

import java.util.List;

import com.hbm.main.MainRegistry;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

public final class CommandRNTConfig extends CommandBase {
    @Override public String getCommandName() { return "rntconfig"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntconfig reload"; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if(args.length != 1 || !"reload".equalsIgnoreCase(args[0]))
            throw new WrongUsageException(getCommandUsage(sender));
        try {
            MainRegistry.reloadRuntimeConfig();
        } catch(Exception ex) {
            MainRegistry.logger.error("[RNT] Configuration reload failed; previous runtime settings retained.", ex);
            throw new CommandException("[RNT] Configuration reload failed: " + ex.getMessage() + ". Previous runtime settings retained.");
        }
        sender.addChatMessage(new ChatComponentText("[RNT] Configuration reloaded successfully."));
        sender.addChatMessage(new ChatComponentText("[RNT] Updated Gemini, UAP and Europa Lank encounters, natural villager spawning, tasers, and server logging/diagnostics/login settings."));
        sender.addChatMessage(new ChatComponentText("[RNT] Restart-required settings were not applied (including Woodcrawler biome spawn weight). Existing world gamerules retained. See docs/configuration.md for the supported settings."));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "reload") : null;
    }
}
