package com.hbm.commands;

import java.util.List;
import com.hbm.config.ColossusConfig;
import com.hbm.entity.mob.colossus.ColossusEncounters;
import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

public final class CommandColossus extends CommandBase {
    @Override public String getCommandName() { return "rntcolossus"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender s) { return "/rntcolossus spawn [active|dormant|admin|glitched] [x y z] | activate <entityID> | remove <entityID>"; }
    @Override public void processCommand(ICommandSender sender,String[] args) {
        if(args.length==0) throw new WrongUsageException(getCommandUsage(sender));
        World w=sender.getEntityWorld();
        if(args.length==2 && (args[0].equals("activate") || args[0].equals("remove"))) {
            Entity e=w.getEntityByID(parseInt(sender,args[1]));
            if(!(e instanceof EntityPrismarineColossus)) throw new CommandException("No loaded Prismarine Colossus with that entity ID in this dimension.");
            if(args[0].equals("activate")) ((EntityPrismarineColossus)e).activate(); else e.setDead();
            return;
        }
        if(!args[0].equals("spawn")) throw new WrongUsageException(getCommandUsage(sender));
        int variant=EntityPrismarineColossus.ACTIVE,offset=1;
        if(args.length>1) {
            String v=args[1];
            if(v.equals("dormant")) { variant=1;offset=2; }
            else if(v.equals("active")) offset=2;
            else if(v.equals("admin")) { variant=2;offset=2; }
            else if(v.equals("glitched")) { variant=3;offset=2; }
        }
        if(args.length!=offset && args.length!=offset+3) throw new WrongUsageException(getCommandUsage(sender));
        if(!ColossusConfig.enabled || !ColossusEncounters.get(w).room()) throw new CommandException("Colossus disabled or this dimension's saved encounter limit has been reached.");
        if(!(sender instanceof EntityPlayer) && args.length==offset) throw new CommandException("Console spawning requires explicit x y z coordinates.");
        ChunkCoordinates p=sender.getPlayerCoordinates();
        double x,z,y;
        if(args.length>offset) {
            x=parseDouble(sender,args[offset]); y=parseDouble(sender,args[offset+1]); z=parseDouble(sender,args[offset+2]);
        } else {
            double angle=Math.toRadians(((EntityPlayer)sender).rotationYaw);
            x=p.posX+0.5-Math.sin(angle)*18; z=p.posZ+0.5+Math.cos(angle)*18;
            if(!EntityPrismarineColossus.loaded(w,x,z,16)) throw new CommandException("Nearby spawn terrain is not loaded. Supply loaded x y z coordinates.");
            y=w.getHeightValue((int)Math.floor(x),(int)Math.floor(z));
        }
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x)>29999800 || Math.abs(z)>29999800 || y<1 || y>245) throw new CommandException("Choose finite coordinates inside world bounds, with room below height 256.");
        EntityPrismarineColossus boss=new EntityPrismarineColossus(w); boss.setVariant(variant);
        boss.setLocationAndAngles(x,y,z,sender instanceof EntityPlayer?((EntityPlayer)sender).rotationYaw:0,0);
        if(!boss.safeSpawn()) throw new CommandException("Unsafe location: the entire Colossus needs loaded, clear terrain and a broad supporting floor.");
        if(!w.spawnEntityInWorld(boss)) { boss.setDead(); throw new CommandException("Colossus spawning was canceled."); }
        sender.addChatMessage(new ChatComponentText("Summoned "+boss.getCommandSenderName()+" ID="+boss.getEntityId()+" UUID="+boss.getUniqueID()));
    }
    @Override public List addTabCompletionOptions(ICommandSender s,String[] args) {
        if(args.length==1) return getListOfStringsMatchingLastWord(args,"spawn","activate","remove");
        if(args.length==2 && args[0].equals("spawn")) return getListOfStringsMatchingLastWord(args,"active","dormant","admin","glitched");
        return null;
    }
}
