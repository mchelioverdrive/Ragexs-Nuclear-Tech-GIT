package com.hbm.commands;

import java.util.List;

import com.hbm.entity.mob.magmagolem.EntityGiantMagmaGolem;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

public final class CommandMagmaGolem extends CommandBase {
    @Override public String getCommandName() { return "rntmagmagolem"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntmagmagolem spawn [x y z]"; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if ((args.length != 1 && args.length != 4) || !"spawn".equals(args[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        World world = sender.getEntityWorld();
        if (world.difficultySetting == EnumDifficulty.PEACEFUL) throw new CommandException("The Giant Magma Golem requires a hostile difficulty.");
        if (world.provider.isHellWorld) throw new CommandException("Choose a dimension where water can be placed.");

        ChunkCoordinates at = sender.getPlayerCoordinates();
        Entity executor = sender instanceof Entity ? (Entity)sender : null;
        double x = executor == null ? at.posX + 0.5 : executor.posX;
        double y = executor == null ? at.posY : executor.boundingBox.minY;
        double z = executor == null ? at.posZ + 0.5 : executor.posZ;
        if (args.length == 4) {
            x = func_110666_a(sender, x, args[1]);
            y = func_110666_a(sender, y, args[2]);
            z = func_110666_a(sender, z, args[3]);
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) >= 29999980 || Math.abs(z) >= 29999980 || y < 1 || y + EntityGiantMagmaGolem.HEIGHT > world.getActualHeight()) {
            throw new CommandException("Choose coordinates inside the world.");
        }
        EntityGiantMagmaGolem boss = new EntityGiantMagmaGolem(world);
        boss.setLocationAndAngles(x, y, z, executor == null ? 0 : executor.rotationYaw, 0);
        if (!boss.hasSpawnClearance()) throw new CommandException("The Giant Magma Golem needs a clear, loaded area about 10 blocks wide and 12 blocks tall.");
        if (!world.spawnEntityInWorld(boss)) throw new CommandException("Giant Magma Golem spawn was canceled.");
        sender.addChatMessage(new ChatComponentText("Summoned " + boss.getCommandSenderName() + "."));
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "spawn") : null;
    }
}
