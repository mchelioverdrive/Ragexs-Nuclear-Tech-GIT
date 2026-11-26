package com.hbm.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hbm.config.BossProgressionConfig;
import com.hbm.world.progression.BossDiscovery;
import com.hbm.world.progression.BossProgression;
import com.hbm.world.progression.BossProgression.Boss;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public final class CommandBossProgression extends CommandBase {
    private static final String[] ACTIONS = {"next", "status", "list", "unlock", "complete", "reset", "bypass", "locate"};

    @Override public String getCommandName() { return "bossprogression"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/bossprogression next | status | list | unlock <boss> | complete <boss> | reset <boss> | bypass <true|false> | locate <boss>";
    }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length < 1 || args.length > 2) throw new WrongUsageException(getCommandUsage(sender));
        String action = args[0].toLowerCase(Locale.ROOT);
        World world = sender.getEntityWorld();
        ChunkCoordinates origin = sender.getPlayerCoordinates();
        BossProgression data = BossProgression.get(world);
        if(action.equals("next") && args.length == 1) {
            if(!BossProgressionConfig.settings.enabled) {
                send(sender, "Story Mode progression is disabled.");
                return;
            }
            Boss boss = BossDiscovery.next(data);
            send(sender, boss == null ? "All enabled Story Mode milestones are complete."
                : BossDiscovery.guidance(world, boss, origin.posX, origin.posZ));
            return;
        }
        if(!isOperator(sender)) throw new CommandException("commands.generic.permission");
        if((action.equals("list") || action.equals("status")) && args.length == 1) {
            send(sender, "Story Mode progression " + (BossProgressionConfig.settings.enabled ? "enabled" : "disabled")
                + ". World bypass " + (data.isBypassEnabled() ? "enabled." : "disabled."));
            for(Boss boss : Boss.values()) {
                if(boss == Boss.SANDWORM) break;
                send(sender, describe(data, boss, action.equals("status")));
            }
            return;
        }
        if(args.length != 2) throw new WrongUsageException(getCommandUsage(sender));
        if(action.equals("bypass")) {
            data.setBypass(parseBoolean(sender, args[1]));
            send(sender, "Story Mode prerequisite bypass is " + (data.isBypassEnabled() ? "enabled." : "disabled."));
            return;
        }
        Boss boss = parseBoss(args[1]);
        switch(action) {
            case "unlock":
                data.unlock(boss);
                send(sender, "Prerequisite waived for " + boss.displayName + ".");
                break;
            case "complete":
                Boss missing = data.missingPrerequisite(boss);
                if(missing != null) throw new CommandException("Complete " + missing.displayName + " first, or run unlock for " + boss.displayName + ".");
                data.recordDefeat(boss);
                send(sender, boss.displayName + " marked complete.");
                break;
            case "reset":
                data.reset(boss);
                send(sender, "Reset " + boss.displayName + " and its downstream milestones, unlocks, and giant arena activations.");
                break;
            case "locate":
                String result = BossDiscovery.locateRecorded(world, boss, origin.posX, origin.posZ);
                send(sender, result != null ? result : "No structure has been recorded for " + boss.displayName + ".");
                break;
            default: throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static String describe(BossProgression data, Boss boss, boolean detailed) {
        Boss prerequisite = boss.prerequisite(), missing = data.missingPrerequisite(boss);
        BossProgressionConfig.Values config = BossProgressionConfig.settings;
        String state;
        if(!boss.isEnabled()) state = data.isDefeated(boss) ? "completed, encounter disabled" : "disabled";
        else if(boss.hasArena() && !config.enabled) state = data.isDefeated(boss) ? "completed, arena activation disabled" : "arena activation disabled";
        else if(data.isDefeated(boss)) {
            state = boss.hasArena() && config.repeatEncounters && missing == null && data.activeCount(boss) < config.maximumActive
                ? "completed, arena replay available" : "completed";
        }
        else if(missing != null) state = "locked by " + missing.displayName;
        else if(boss.hasArena() && data.activeCount(boss) >= config.maximumActive) state = "active limit reached";
        else if(data.isUnlocked(boss)) state = "operator-unlocked";
        else state = "available";
        String detail = detailed && boss.hasArena()
            ? ". " + data.activeCount(boss) + "/" + config.maximumActive + " arena fights active" : "";
        return id(boss) + " — " + state + ". Preceding enabled boss "
            + (prerequisite == null ? "none" : prerequisite.displayName) + detail + ".";
    }

    private static Boss parseBoss(String name) {
        for(Boss boss : Boss.values()) if(boss != Boss.SANDWORM && id(boss).equalsIgnoreCase(name)) return boss;
        throw new CommandException("Unknown Story Mode boss " + name + ".");
    }

    private static String id(Boss boss) { return boss.name().toLowerCase(Locale.ROOT); }
    private boolean isOperator(ICommandSender sender) { return sender.canCommandSenderUseCommand(2, getCommandName()); }
    private static void send(ICommandSender sender, String message) { sender.addChatMessage(new ChatComponentText(message)); }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if(!isOperator(sender)) return args.length == 1 ? getListOfStringsMatchingLastWord(args, "next") : null;
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, ACTIONS);
        if(args.length != 2) return null;
        if(args[0].equals("bypass")) return getListOfStringsMatchingLastWord(args, "true", "false");
        if(args[0].equals("unlock") || args[0].equals("complete") || args[0].equals("reset") || args[0].equals("locate")) {
            List<String> names = new ArrayList<>();
            for(Boss boss : Boss.values()) if(boss != Boss.SANDWORM) names.add(id(boss));
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[names.size()]));
        }
        return null;
    }
}
