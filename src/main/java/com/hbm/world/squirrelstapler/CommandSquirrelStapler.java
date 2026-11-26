package com.hbm.world.squirrelstapler;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CommandSquirrelStapler extends CommandBase {
    private static final String[] ACTIONS = {"status", "day", "completeobjective", "event", "spawn", "finale", "reset"};
    private static final String[] ENTITIES = {"squirrel", "large", "ghost", "bear", "silhouette", "arrival"};
    private static final String[] EVENTS = Arrays.stream(StaplerEncounters.Cue.values())
            .map(cue -> cue.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);

    @Override public String getCommandName() { return "squirrelstapler"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public String getCommandUsage(ICommandSender sender) {
        return "/squirrelstapler <status|day <1-5>|completeobjective|event <event>|spawn <entity>|finale|reset>";
    }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length == 0) {
            reply(sender, getCommandUsage(sender));
            reply(sender, "Events: " + String.join(", ", EVENTS) + ". Entities: " + String.join(", ", ENTITIES) + ".");
            return;
        }
        World world = sender.getEntityWorld();
        if(!StaplerWorldType.active(world)) throw new CommandException("This command requires an enabled Squirrel Stapler Overworld.");
        StaplerCampaign data = StaplerCampaign.get(world);
        String action = args[0].toLowerCase(Locale.ROOT);
        int expected = "day".equals(action) || "event".equals(action) || "spawn".equals(action) ? 2 : 1;
        if(args.length != expected) throw new WrongUsageException(getCommandUsage(sender));
        switch(action) {
        case "status":
            reply(sender, "Day " + data.day + "/" + data.days + ". Rack: " + data.normal + "/" + data.normalRequired
                    + " normal and " + data.large + "/" + data.largeRequired + " large. "
                    + (data.stage == StaplerCampaign.COMPLETE ? "Complete." : data.stage == StaplerCampaign.ARRIVAL ? "Arrival in progress."
                    : data.stage == StaplerCampaign.COUNTDOWN ? "Countdown: " + data.secondsLeft() + "s."
                    : data.ready() ? "Sleep at the cabin to continue." : "Hunting."));
            break;
        case "day":
            StaplerMode.setDay(world, data, parseIntBounded(sender, args[1], 1, data.days));
            break;
        case "completeobjective":
            if(data.stage != StaplerCampaign.HUNT) throw new CommandException("No hunting objective is active.");
            data.completeObjective();
            StaplerForest.updateDisplay(world, data);
            if(data.day == data.days) StaplerMode.beginFinale(world, data);
            reply(sender, "Today's attachment objective is complete.");
            break;
        case "event":
            if(!data.encounters.begin(world, data, witness(sender, world), cue(args[1]), true))
                throw new CommandException("The event is unavailable, already in progress, or has no loaded placement.");
            reply(sender, "Event triggered.");
            break;
        case "spawn":
            spawn(sender, world, data, args[1].toLowerCase(Locale.ROOT));
            reply(sender, "Encounter requested.");
            break;
        case "finale":
            if(!StaplerMode.beginFinale(world, data)) throw new CommandException("The finale has already started or completed.");
            break;
        case "reset":
            data.reset();
            StaplerMode.refreshDay(world, data);
            reply(sender, "Campaign reset.");
            break;
        default:
            throw new WrongUsageException(getCommandUsage(sender));
        }
        StaplerMode.sync(world, data);
    }

    private static void spawn(ICommandSender sender, World world, StaplerCampaign data, String type) {
        if("arrival".equals(type)) {
            witness(sender, world);
            if(data.stage == StaplerCampaign.HUNT) StaplerMode.beginFinale(world, data);
            if(data.stage != StaplerCampaign.COUNTDOWN) throw new CommandException("The arrival is already active or completed.");
            data.remainingTicks = 0;
            data.markDirty();
            return;
        }
        if("squirrel".equals(type) || "large".equals(type)) {
            if(data.stage != StaplerCampaign.HUNT || !StaplerEncounters.spawnPrey(world, witness(sender, world), "large".equals(type), true))
                throw new CommandException("No suitable loaded hunting site.");
            return;
        }
        if(!"ghost".equals(type) && !"bear".equals(type) && !"silhouette".equals(type))
            throw new CommandException("Unknown squirrel encounter: " + type);
        if(!data.encounters.begin(world, data, witness(sender, world), cue(type), true))
            throw new CommandException("The creature is disabled, an event is active, or there is no loaded placement.");
    }

    private static EntityPlayer witness(ICommandSender sender, World world) {
        EntityPlayer player = sender instanceof EntityPlayer ? (EntityPlayer)sender : StaplerEncounters.player(world);
        if(player == null || !player.isEntityAlive()) throw new CommandException("An active player is required.");
        return player;
    }

    private static StaplerEncounters.Cue cue(String name) {
        try {
            return StaplerEncounters.Cue.valueOf(name.toUpperCase(Locale.ROOT));
        } catch(IllegalArgumentException failure) {
            throw new CommandException("Unknown paranormal event: " + name);
        }
    }

    private static void reply(ICommandSender sender, String text) { sender.addChatMessage(new ChatComponentText(text)); }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, ACTIONS);
        if(args.length == 2) {
            if("event".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, EVENTS);
            if("spawn".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, ENTITIES);
            if("day".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4", "5");
        }
        return null;
    }
}
