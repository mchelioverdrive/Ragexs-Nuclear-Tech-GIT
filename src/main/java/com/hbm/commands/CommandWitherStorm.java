package com.hbm.commands;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.hbm.config.WitherStormConfig;
import com.hbm.entity.mob.witherstorm.EntityWitherStorm;
import com.hbm.entity.mob.witherstorm.WitherStormEncounters;
import com.hbm.entity.mob.witherstorm.WitherStormMath;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

public class CommandWitherStorm extends CommandBase {
    @Override public String getCommandName() { return "rntwitherstorm"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntwitherstorm spawn [command|storm] [x y z] | list | inspect <entityID|UUID> | growth <entityID|UUID> <mass> | remove <entityID|UUID> | challenge <true|false|status>"; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        World world = sender.getEntityWorld(); WitherStormEncounters data = WitherStormEncounters.get(world);
        if(args[0].equals("challenge")) {
            if(args.length != 2) throw new WrongUsageException(getCommandUsage(sender));
            World overworld = DimensionManager.getWorld(0);
            if(overworld == null) throw new CommandException("The Overworld is unavailable.");
            if(args[1].equals("true") || args[1].equals("false")) data.setChallenge(overworld, Boolean.parseBoolean(args[1]));
            else if(!args[1].equals("status")) throw new WrongUsageException(getCommandUsage(sender));
            sender.addChatMessage(new ChatComponentText(data.challengeStatus(overworld)));
            return;
        }
        if(args[0].equals("list") && args.length == 1) {
            sender.addChatMessage(new ChatComponentText("Wither Storm encounters (including unloaded): " + data.entries().size()));
            for(Map.Entry<UUID, Integer> entry : data.entries().entrySet()) sender.addChatMessage(new ChatComponentText(entry.getKey() + " dimension=" + entry.getValue()));
            return;
        }
        boolean explicitForm = args.length > 1 && (args[1].equals("command") || args[1].equals("storm"));
        int coordinate = explicitForm ? 2 : 1;
        if(args[0].equals("spawn") && (args.length == coordinate || args.length == coordinate + 3)) {
            if(!data.room()) throw new CommandException("Wither Storm encounter limit reached. Use list/remove for unloaded encounters.");
            ChunkCoordinates p = sender.getPlayerCoordinates();
            double x = args.length > coordinate ? parseDouble(sender, args[coordinate]) : p.posX + 0.5D;
            double y = args.length > coordinate ? parseDouble(sender, args[coordinate + 1]) : Math.min(200, p.posY + 8D);
            double z = args.length > coordinate ? parseDouble(sender, args[coordinate + 2]) : p.posZ + 0.5D;
            if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 29999900 || Math.abs(z) > 29999900 || y < 1 || y > 220 || !WitherStormMath.loaded(world, x, z)) throw new CommandException("Choose loaded coordinates inside world bounds (y=1..220).");
            EntityWitherStorm boss = new EntityWitherStorm(world);
            boss.setLocationAndAngles(x, y, z, 0, 0);
            if(explicitForm && args[1].equals("storm")) boss.setGrowth(WitherStormConfig.transform);
            if(!world.spawnEntityInWorld(boss)) { boss.setDead(); throw new CommandException("Wither Storm spawn was canceled."); }
            sender.addChatMessage(new ChatComponentText("Summoned " + boss.getCommandSenderName() + " ID=" + boss.getEntityId() + " UUID=" + boss.getUniqueID()));
            return;
        }
        if(args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        EntityWitherStorm boss = null; UUID id;
        try { id = UUID.fromString(args[1]); } catch(IllegalArgumentException badUuid) {
            Entity e = world.getEntityByID(parseInt(sender, args[1]));
            if(!(e instanceof EntityWitherStorm)) throw new CommandException("No loaded Wither Storm with that entity ID in this dimension.");
            boss = (EntityWitherStorm)e; id = boss.getUniqueID();
        }
        if(args[0].equals("remove") && args.length == 2) {
            if(!data.remove(id)) throw new CommandException("Unknown encounter UUID.");
            sender.addChatMessage(new ChatComponentText("Removed encounter " + id + "; unloaded parts will retire when loaded.")); return;
        }
        if(boss == null) { Entity e = data.find(id, world); if(e instanceof EntityWitherStorm) boss = (EntityWitherStorm)e; }
        if(boss == null) throw new CommandException("Encounter is unloaded or in another dimension. This command does not load chunks.");
        if(args[0].equals("growth") && args.length == 3) boss.setGrowth(parseIntBounded(sender, args[2], 0, 1000000000));
        else if(!args[0].equals("inspect") || args.length != 2) throw new WrongUsageException(getCommandUsage(sender));
        sender.addChatMessage(new ChatComponentText("ID=" + boss.getEntityId() + " phase=" + boss.getPhase() + " growth=" + boss.getGrowth() + " heads(center/left/right)=" + boss.headTransformed(1) + "/" + boss.headTransformed(0) + "/" + boss.headTransformed(2) + " health=" + boss.getHealth() + "/" + boss.getMaxHealth() + " core=" + boss.hasCore() + " encounter=" + id));
    }
    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, "spawn", "list", "inspect", "growth", "remove", "challenge");
        if(args.length == 2 && args[0].equals("challenge")) return getListOfStringsMatchingLastWord(args, "true", "false", "status");
        if(args.length == 2 && args[0].equals("spawn")) return getListOfStringsMatchingLastWord(args, "command", "storm");
        return null;
    }
}
