package com.hbm.commands;

import java.util.List;

import com.hbm.entity.mob.giantghast.EntityGiantGhast;
import com.hbm.entity.mob.giantghast.GiantGhastEncounters;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

public final class CommandGiantGhast extends CommandBase {
    @Override public String getCommandName() { return "rntgiantghast"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntgiantghast spawn [x y z] | kill"; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        World world = sender.getEntityWorld();
        if (args.length == 1 && "kill".equals(args[0])) {
            int count = GiantGhastEncounters.get(world).removeAll(world);
            sender.addChatMessage(new ChatComponentText("Removed " + count + " Giant Ghast encounters in this dimension."));
            return;
        }
        if ((args.length != 1 && args.length != 4) || !"spawn".equals(args[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        if (world.difficultySetting == EnumDifficulty.PEACEFUL) throw new CommandException("Giant Ghasts require a hostile difficulty.");

        EntityGiantGhast boss = new EntityGiantGhast(world);
        ChunkCoordinates at = sender.getPlayerCoordinates();
        double x = args.length == 4 ? parseDouble(sender, args[1]) : at.posX + 0.5;
        double y = args.length == 4 ? parseDouble(sender, args[2]) : Math.min(at.posY + 8, world.getActualHeight() - EntityGiantGhast.ROOF - 2);
        double z = args.length == 4 ? parseDouble(sender, args[3]) : at.posZ + 0.5;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) >= 29999980 || Math.abs(z) >= 29999980 || y < 1 || y >= world.getActualHeight()) {
            throw new CommandException("Choose coordinates inside the world.");
        }
        boss.setLocationAndAngles(x, y, z, 0, 0);
        if (args.length == 1) {
            for (int i = 0; i < 6 && !boss.hasSpawnClearance(); i++) {
                y += 4;
                boss.setLocationAndAngles(x, y, z, 0, 0);
            }
        }
        if (!boss.hasSpawnClearance()) throw new CommandException("The Giant Ghast needs clear, loaded space about 18 blocks wide and 14 blocks tall.");
        if (!world.spawnEntityInWorld(boss)) throw new CommandException("Giant Ghast spawn was canceled.");
        sender.addChatMessage(new ChatComponentText("Summoned " + boss.getCommandSenderName() + "."));
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "spawn", "kill") : null;
    }
}
