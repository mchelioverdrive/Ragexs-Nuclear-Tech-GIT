package com.hbm.commands;

import java.util.List;
import com.hbm.world.progression.BossProgression;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

public final class CommandWitherStormBypass extends CommandBase {
    @Override public String getCommandName() { return "witherstormbypass"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/witherstormbypass [true|false]"; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length > 1 || (args.length == 1 && !args[0].equals("true") && !args[0].equals("false"))) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        BossProgression progression = BossProgression.get(sender.getEntityWorld());
        if(args.length == 1) progression.setWitherStormBypass(Boolean.parseBoolean(args[0]));
        sender.addChatMessage(new ChatComponentText("Wither Storm progression bypass is "
            + (progression.isWitherStormBypassEnabled() ? "enabled." : "disabled.")));
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "true", "false") : null;
    }
}
