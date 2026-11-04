package com.hbm.commands;

import java.util.List;

import com.hbm.entity.mob.giantenderman.EntityGiantEnderman;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class CommandGiantEnderman extends CommandBase {
    @Override public String getCommandName() { return "rntgiantenderman"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntgiantenderman spawn [x y z]"; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if ((args.length != 1 && args.length != 4) || !"spawn".equals(args[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        World world = sender.getEntityWorld();
        EntityGiantEnderman boss = new EntityGiantEnderman(world);
        Vec3 point = null;
        if (args.length == 4) {
            double x = parseDouble(sender, args[1]), y = parseDouble(sender, args[2]), z = parseDouble(sender, args[3]);
            if (boss.safePosition(x, y, z)) point = Vec3.createVectorHelper(x, y, z);
        } else {
            ChunkCoordinates at = sender instanceof MinecraftServer ? world.getSpawnPoint() : sender.getPlayerCoordinates();
            for (int i = 0; i < 12 && point == null; i++) {
                double angle = i * Math.PI / 4;
                double radius = i < 8 ? 13 : 22;
                point = boss.findGround(at.posX + 0.5 + Math.cos(angle) * radius, at.posZ + 0.5 + Math.sin(angle) * radius,
                    at.posY + 5, 12);
            }
        }
        if (point == null) throw new CommandException("giant_enderman.spawn.unsafe");
        boss.setLocationAndAngles(point.xCoord, point.yCoord, point.zCoord, 0, 0);
        if (!world.spawnEntityInWorld(boss)) throw new CommandException("giant_enderman.spawn.canceled");
        boss.activateEncounter();
        sender.addChatMessage(new ChatComponentTranslation("giant_enderman.spawn.success"));
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "spawn") : null;
    }
}
