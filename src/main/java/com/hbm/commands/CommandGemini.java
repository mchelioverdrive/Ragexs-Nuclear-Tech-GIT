package com.hbm.commands;

import java.util.List;
import java.util.Locale;

import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiHostileSpawning;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiInvasionManager.StructureInfo;
import com.hbm.world.gemini.WretchCamps;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** Server-only administrator controls for the existing shared invasion and structure records. */
public final class CommandGemini extends CommandBase {
    private static final int PAGE_SIZE = 8;
    @Override public String getCommandName() { return "gemini"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/gemini help|status|phase|time|nest|camp|debug"; }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText("[Gemini] " + message));
    }
    private static WorldServer world(ICommandSender sender) {
        World world = sender.getEntityWorld();
        if(world == null) world = DimensionManager.getWorld(0);
        if(!(world instanceof WorldServer) || world.isRemote || DimensionManager.getWorld(0) == null)
            throw new CommandException("The server world/Overworld is not available.");
        return (WorldServer) world;
    }
    private static EntityPlayerMP player(ICommandSender sender) {
        if(!(sender instanceof EntityPlayerMP)) throw new CommandException("Structure spawning requires a player sender.");
        return (EntityPlayerMP) sender;
    }
    private static long ticks(String value) {
        try {
            long ticks = Long.parseLong(value);
            if(ticks >= 0) return ticks;
        } catch(NumberFormatException ignored) { }
        throw new CommandException("Ticks must be a nonnegative integer no larger than " + Long.MAX_VALUE + ".");
    }
    private static ChunkCoordinates origin(ICommandSender sender, EntityPlayerMP player, String[] args, int offset) {
        if(args.length == offset) return null;
        double x, y, z;
        try {
            x = func_110666_a(sender, player.posX, args[offset]);
            y = func_110666_a(sender, player.boundingBox.minY, args[offset + 1]);
            z = func_110666_a(sender, player.posZ, args[offset + 2]);
        } catch(CommandException invalid) {
            throw new CommandException("Invalid coordinates: use numbers or ~relative offsets within the world boundaries.");
        }
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < -29999984 || x >= 29999984 || z < -29999984 || z >= 29999984
                || y < 1 || y >= player.worldObj.getActualHeight())
            throw new CommandException("Invalid coordinates: origin must be inside world X/Z boundaries and Y=1.." + (player.worldObj.getActualHeight() - 1) + ".");
        return new ChunkCoordinates(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }
    private static void generated(ICommandSender sender, StructureInfo result, boolean exact, boolean camp) {
        reply(sender, "Generated " + result + ".");
        if(exact) reply(sender, "Exact origin retained (coordinates rounded down to blocks); "
                + (camp ? "origin is the first camp layer, with foundation at Y-1." : "origin is the nest floor's air level, with organic floor at Y-1."));
    }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length == 0 || args.length == 1 && "help".equals(args[0])) {
            reply(sender, "/gemini help | status | debug <true|false>");
            reply(sender, "/gemini phase set <1|2|3|4> | phase next | phase reset");
            reply(sender, "/gemini time <add|set> <ticks>");
            reply(sender, "/gemini nest spawn [x y z] | nest locate");
            reply(sender, "/gemini camp spawn [" + WretchCamps.TYPE + "] [x y z] | camp locate | camp list [page]");
            return;
        }
        WorldServer world = world(sender);
        if("status".equals(args[0]) && args.length == 1) {
            status(sender, world); return;
        }
        if("phase".equals(args[0])) {
            if(args.length == 3 && "set".equals(args[1])) {
                int phase = parseIntBounded(sender, args[2], 1, 4);
                GeminiInvasionManager.setPhase(world, phase);
            } else if(args.length == 2 && "next".equals(args[1])) {
                int phase = GeminiInvasionManager.getPhase(world);
                if(phase >= 4) throw new CommandException("Already in the final invasion phase (4).");
                GeminiInvasionManager.setPhase(world, phase + 1);
            } else if(args.length == 2 && "reset".equals(args[1])) {
                GeminiInvasionManager.reset(world);
                reply(sender, "Progression reset. Mars milestone, gamerules, entities and structures retained.");
            } else throw new WrongUsageException("/gemini phase set <1|2|3|4> | /gemini phase next | /gemini phase reset");
            reply(sender, "Phase=" + GeminiInvasionManager.getPhase(world) + "; elapsed ticks=" + GeminiInvasionManager.getElapsedTicks(world)
                    + "; progression active=" + GeminiInvasionManager.progressionActive(world) + ".");
            return;
        }
        if("time".equals(args[0])) {
            if(args.length != 3 || !("add".equals(args[1]) || "set".equals(args[1])))
                throw new WrongUsageException("/gemini time <add|set> <nonnegative ticks>");
            long target = ticks(args[2]);
            if("add".equals(args[1])) {
                GeminiInvasionManager.addElapsedTicks(world, target);
            } else GeminiInvasionManager.setElapsedTicks(world, target);
            target = GeminiInvasionManager.getElapsedTicks(world);
            reply(sender, "Elapsed ticks=" + target + "; phase=" + GeminiInvasionManager.getPhase(world) + ". World clock unchanged.");
            reply(sender, "Skipped encounters, chunk population, nest construction, mob AI, infections and cooldowns require live simulation; none were replayed.");
            return;
        }
        if("nest".equals(args[0])) {
            if((args.length == 2 || args.length == 5) && "spawn".equals(args[1])) {
                EntityPlayerMP player = player(sender);
                ChunkCoordinates requested = origin(sender, player, args, 2);
                generated(sender, GardenerNests.debugSpawn(player, requested), requested != null, false); return;
            }
            if(args.length == 2 && "locate".equals(args[1])) {
                locate(sender, GardenerNests.known(world), "nest"); return;
            }
            throw new WrongUsageException("/gemini nest spawn [x y z] | /gemini nest locate");
        }
        if("camp".equals(args[0])) {
            if((args.length == 2 || args.length == 3 || args.length == 5 || args.length == 6) && "spawn".equals(args[1])) {
                boolean typed = args.length == 3 || args.length == 6;
                if(typed && !WretchCamps.TYPE.equals(args[2]))
                    throw new CommandException("Unknown camp type '" + args[2] + "'. Available: " + WretchCamps.TYPE + ".");
                EntityPlayerMP player = player(sender);
                ChunkCoordinates requested = origin(sender, player, args, typed ? 3 : 2);
                generated(sender, WretchCamps.debugSpawn(player, requested), requested != null, true); return;
            }
            if(args.length == 2 && "locate".equals(args[1])) {
                locate(sender, WretchCamps.known(world), "camp"); return;
            }
            if((args.length == 2 || args.length == 3) && "list".equals(args[1])) {
                List<StructureInfo> camps = WretchCamps.known(world);
                int pages = Math.max(1, (camps.size() + PAGE_SIZE - 1) / PAGE_SIZE);
                int page = args.length == 3 ? parseIntBounded(sender, args[2], 1, pages) : 1;
                reply(sender, "Known camps in dimension " + world.provider.dimensionId + ": " + camps.size() + "; page " + page + "/" + pages + ".");
                for(int i = (page - 1) * PAGE_SIZE; i < Math.min(camps.size(), page * PAGE_SIZE); i++) reply(sender, camps.get(i).toString());
                if(page < pages) reply(sender, "Next: /gemini camp list " + (page + 1));
                return;
            }
            throw new WrongUsageException("/gemini camp spawn [" + WretchCamps.TYPE + "] [x y z] | /gemini camp locate | /gemini camp list [page]");
        }
        if("debug".equals(args[0])) {
            if(args.length != 2 || !("true".equals(args[1]) || "false".equals(args[1])))
                throw new WrongUsageException("/gemini debug <true|false>");
            GeminiInvasionManager.setDebug(world, Boolean.parseBoolean(args[1]));
            reply(sender, "Invasion debug output=" + args[1] + "; saved server-side, sent to level-2 administrators and the server log.");
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static void status(ICommandSender sender, WorldServer world) {
        boolean visited = GeminiInvasionManager.hasMarsVisited(world);
        int phase = GeminiInvasionManager.getPhase(world);
        long elapsed = GeminiInvasionManager.getElapsedTicks(world), remaining = GeminiInvasionManager.getRemainingTicks(world);
        reply(sender, "visitedMars=" + visited + (visited ? "" : " (invasion has not started; retained progress may exist)")
                + "; configured=" + GeminiConfig.enabled + "; progression active=" + GeminiInvasionManager.progressionActive(world) + ".");
        reply(sender, "geminionly=" + world.getGameRules().getGameRuleBooleanValue(GeminiHostileSpawning.RULE)
                + "; phase=" + phase + "; debug=" + GeminiInvasionManager.isDebug(world) + ".");
        reply(sender, "Elapsed=" + elapsed + " ticks (" + String.format(Locale.ROOT, "%.3f", elapsed / 24000D) + " Minecraft days)"
                + "; next phase=" + (remaining < 0 ? phase == 4 ? "final phase" : "not started" : remaining + " ticks")
                + "; encounter countdown=" + GeminiInvasionManager.getEventSecondsRemaining(world) + " seconds.");
        reply(sender, "Nests unlocked=" + (phase >= 3) + "; generation: " + GardenerNests.eligibility(world) + ".");
        reply(sender, "Camps unlocked=" + (phase >= 4) + "; generation: " + WretchCamps.eligibility(world) + ".");
        reply(sender, "Known generated nests=" + GardenerNests.known(world).size() + "; camp records=" + WretchCamps.known(world).size()
                + " in sender dimension " + world.provider.dimensionId + ". Invasion world=Overworld (0), shared saved progression.");
    }

    private static void locate(ICommandSender sender, List<StructureInfo> structures, String kind) {
        ChunkCoordinates position = sender.getPlayerCoordinates();
        double x = position.posX, y = position.posY, z = position.posZ;
        if(sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender; x = player.posX; y = player.posY; z = player.posZ;
        }
        StructureInfo nearest = null;
        double best = Double.MAX_VALUE;
        for(StructureInfo structure : structures) {
            double distance = structure.distanceSq(x, y, z);
            if(distance < best) { nearest = structure; best = distance; }
        }
        if(nearest == null) { reply(sender, "No known " + kind + " in the current dimension."); return; }
        reply(sender, "Nearest known " + nearest + "; distance=" + String.format(Locale.ROOT, "%.1f", Math.sqrt(best)) + " blocks.");
    }

    @Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, "help", "status", "phase", "time", "nest", "camp", "debug");
        if(args.length == 2) {
            if("phase".equals(args[0])) return getListOfStringsMatchingLastWord(args, "set", "next", "reset");
            if("time".equals(args[0])) return getListOfStringsMatchingLastWord(args, "add", "set");
            if("nest".equals(args[0])) return getListOfStringsMatchingLastWord(args, "spawn", "locate");
            if("camp".equals(args[0])) return getListOfStringsMatchingLastWord(args, "spawn", "locate", "list");
            if("debug".equals(args[0])) return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if(args.length == 3) {
            if("phase".equals(args[0]) && "set".equals(args[1])) return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4");
            if("camp".equals(args[0]) && "spawn".equals(args[1])) return getListOfStringsMatchingLastWord(args, WretchCamps.TYPE, "~");
        }
        if(args.length >= 3 && "spawn".equals(args[1]) && ("camp".equals(args[0]) || "nest".equals(args[0]))) {
            int start = "camp".equals(args[0]) && WretchCamps.TYPE.equals(args[2]) ? 3 : 2;
            if(args.length <= start + 3) return getListOfStringsMatchingLastWord(args, "~");
        }
        return null;
    }
}
