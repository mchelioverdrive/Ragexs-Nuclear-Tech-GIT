package com.hbm.commands;

import java.util.List;
import java.util.UUID;
import com.hbm.config.SandwormConfig;
import com.hbm.entity.mob.sandworm.*;
import com.hbm.world.sandworm.WormEncounter;
import com.hbm.world.sandworm.WormEncounters;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public final class CommandSandworm extends CommandBase {
    @Override public String getCommandName() { return "rntsandworm"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rntsandworm spawn [worm|brain|heart|tick|orange|blue|green|purple] [x y z] | list | inspect <UUID> | remove <UUID>"; }
    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        World w = sender.getEntityWorld(); WormEncounters data = WormEncounters.get(w);
        if(args[0].equals("list") && args.length == 1) {
            for(WormEncounter e : data.entries()) if(!e.cleaned) describe(sender, e);
            return;
        }
        if((args[0].equals("inspect") || args[0].equals("remove")) && args.length == 2) {
            UUID id;
            try { id = UUID.fromString(args[1]); } catch(IllegalArgumentException e) { throw new CommandException("Expected an encounter UUID from /rntsandworm list"); }
            WormEncounter e = data.find(id); if(e == null) throw new CommandException("Unknown Sandworm encounter");
            if(args[0].equals("remove")) data.cancel(id);
            describe(sender, e); return;
        }
        if(!args[0].equals("spawn")) throw new WrongUsageException(getCommandUsage(sender));
        String kind = args.length > 1 ? args[1] : "worm"; int coordinate = args.length > 1 ? 2 : 1;
        if(args.length != coordinate && args.length != coordinate + 3) throw new WrongUsageException(getCommandUsage(sender));
        ChunkCoordinates pos = sender.getPlayerCoordinates();
        double x = args.length > coordinate ? parseDouble(sender, args[coordinate]) : pos.posX + 0.5;
        double y = args.length > coordinate ? parseDouble(sender, args[coordinate + 1]) : Math.min(220, pos.posY + (kind.equals("worm") ? 12 : 0));
        double z = args.length > coordinate ? parseDouble(sender, args[coordinate + 2]) : pos.posZ + 0.5;
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 29999000 || Math.abs(z) > 29999000 || y < 4 || y > 230
                || !w.checkChunksExist((int)x - 10, 0, (int)z - 10, (int)x + 10, 255, (int)z + 10)) throw new CommandException("Choose loaded coordinates within world bounds and y=4..230");
        Entity entity;
        if(kind.equals("worm")) {
            if(w.provider.dimensionId == SandwormConfig.dimension || !data.room()) throw new CommandException("Cannot summon here, or active Sandworm encounter limit reached");
            entity = new EntitySandworm(w);
        } else if(kind.equals("brain")) entity = new EntityBrain(w);
        else if(kind.equals("heart")) entity = new EntityHeart(w);
        else if(kind.equals("tick")) entity = new EntityTick(w);
        else {
            int v = kind.equals("orange") ? 1 : kind.equals("blue") ? 2 : kind.equals("green") ? 3 : kind.equals("purple") ? 4 : 0;
            if(v == 0) throw new WrongUsageException(getCommandUsage(sender));
            EntitySpark spark = new EntitySpark(w); spark.setVariant(v); entity = spark;
        }
        entity.setLocationAndAngles(x, y, z, 0, 0);
        if(!w.spawnEntityInWorld(entity)) {
            if(entity instanceof EntitySandworm) { data.cancel(entity.getUniqueID()); WormEncounter e = data.find(entity.getUniqueID()); if(e != null) { e.exteriorRetired = true; data.markDirty(); } }
            throw new CommandException("Sandworm entity spawn was canceled");
        }
        sender.addChatMessage(new ChatComponentText("Spawned " + kind + " ID=" + entity.getEntityId() + " UUID=" + entity.getUniqueID()));
    }
    private void describe(ICommandSender sender, WormEncounter e) {
        sender.addChatMessage(new ChatComponentText(e.id + " source=" + e.sourceDimension + " interior=" + e.x() + ",64," + e.z() + " build=" + e.buildCursor + "/" + e.volume()
                + " brain=" + e.brainDead + " heart=" + e.heartDead + " complete=" + e.complete() + " canceled=" + e.canceled + " cleaned=" + e.cleaned));
    }
    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, "spawn", "list", "inspect", "remove");
        if(args.length == 2 && args[0].equals("spawn")) return getListOfStringsMatchingLastWord(args, "worm", "brain", "heart", "tick", "orange", "blue", "green", "purple");
        return null;
    }
}
