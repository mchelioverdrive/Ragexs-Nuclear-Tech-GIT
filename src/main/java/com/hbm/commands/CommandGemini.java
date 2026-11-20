package com.hbm.commands;

import java.util.List;
import java.util.Locale;

import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiHostileSpawning;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiIris;
import com.hbm.world.gemini.GeminiLights;
import com.hbm.world.gemini.GeminiInvasionManager.StructureInfo;
import com.hbm.world.gemini.WretchCamps;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
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
    @Override public String getCommandUsage(ICommandSender sender) { return "/gemini help|status|phase|time|nest|camp|iris|lights|harvest|debug"; }

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
                || y < 0 || y >= player.worldObj.getActualHeight())
            throw new CommandException("Invalid coordinates: origin must be inside world X/Z boundaries and Y=0.." + (player.worldObj.getActualHeight() - 1) + ".");
        return new ChunkCoordinates(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }
    private static void generated(ICommandSender sender, StructureInfo result, boolean exact, boolean camp) {
        reply(sender, (camp ? "Camp job: " : "Nest job: ") + result + ".");
        if(exact) reply(sender, "Exact origin retained (coordinates rounded down to blocks); "
                + (camp ? "origin is the camp foundation elevation." : "origin is the nest floor's air level, with organic floor at Y-1."));
    }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        if(args.length == 0 || args.length == 1 && "help".equals(args[0])) {
            reply(sender, "/gemini help | status | debug <true|false>");
            reply(sender, "/gemini phase set <1|2|3|4> | phase next | phase reset");
            reply(sender, "/gemini time <add|set> <ticks>");
            reply(sender, "/gemini iris status|show|hide|stage <1..4>|observe|reset");
            reply(sender, "/gemini <lights|harvest> <start|stop|status>");
            reply(sender, "/gemini nest spawn [x y z] [force] | nest locate | nest list [page]");
            reply(sender, "/gemini camp spawn [" + WretchCamps.TYPE + "] [x y z] [force] | camp locate | camp list [page]");
            return;
        }
        WorldServer world = world(sender);
        if("lights".equals(args[0]) || "harvest".equals(args[0])) {
            GeminiLights.Kind kind = "harvest".equals(args[0]) ? GeminiLights.Kind.HARVEST : GeminiLights.Kind.LIGHTS;
            if(args.length != 2) throw new WrongUsageException("/gemini " + args[0] + " <start|stop|status>");
            if("start".equals(args[1])) {
                ChunkCoordinates at = sender instanceof MinecraftServer ? world.getSpawnPoint() : sender.getPlayerCoordinates();
                GeminiLights.forceStart(world, kind, at.posX + 0.5D, at.posZ + 0.5D);
            } else if("stop".equals(args[1])) {
                GeminiLights.forceStop(world, kind);
            } else if(!"status".equals(args[1])) {
                throw new WrongUsageException("/gemini " + args[0] + " <start|stop|status>");
            }
            reply(sender, GeminiLights.status(world, kind));
            return;
        }
        if("iris".equals(args[0])) {
            WorldServer overworld = DimensionManager.getWorld(0);
            GeminiIris iris = GeminiIris.get(overworld);
            if(args.length == 2 && "status".equals(args[1])) {
                reply(sender, iris.status(overworld));
                reply(sender, "Visibility is celestial/weather transmission; roofs, terrain, clouds and viewing direction can obscure it locally.");
                return;
            }
            if(args.length == 3 && "stage".equals(args[1])) iris.control(overworld, "stage", parseIntBounded(sender, args[2], 1, 4));
            else if(args.length == 2 && ("show".equals(args[1]) || "hide".equals(args[1]) || "reset".equals(args[1])))
                iris.control(overworld, args[1], 0);
            else if(args.length == 2 && "observe".equals(args[1])) {
                if(!iris.observe(overworld, true)) throw new CommandException("No living survival/adventure player in the Overworld. Debug observation ignores sky, weather and progression restrictions.");
            } else throw new WrongUsageException("/gemini iris status|show|hide|stage <1..4>|observe|reset");
            reply(sender, iris.status(overworld));
            return;
        }
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
            reply(sender, "Structure schedules follow phase changes on live server ticks. Skipped encounters, construction, mob AI, infections and cooldowns were not replayed.");
            return;
        }
        if("nest".equals(args[0])) {
            if(args.length >= 2 && "spawn".equals(args[1])) {
                int end = args.length - ("force".equals(args[args.length - 1]) ? 1 : 0);
                if(end != 2 && end != 5) throw new WrongUsageException("/gemini nest spawn [x y z] [force]");
                EntityPlayerMP player = player(sender);
                ChunkCoordinates requested = end == 2 ? null : origin(sender, player, args, 2);
                generated(sender, GardenerNests.debugSpawn(player, requested, end != args.length), requested != null, false); return;
            }
            if(args.length == 2 && "locate".equals(args[1])) {
                locate(sender, GardenerNests.known(world), "nest"); return;
            }
            if((args.length == 2 || args.length == 3) && "list".equals(args[1])) {
                list(sender, world, GardenerNests.known(world), "nest", args); return;
            }
            throw new WrongUsageException("/gemini nest spawn [x y z] [force] | /gemini nest locate | /gemini nest list [page]");
        }
        if("camp".equals(args[0])) {
            if(args.length >= 2 && "spawn".equals(args[1])) {
                int end = args.length - ("force".equals(args[args.length - 1]) ? 1 : 0);
                boolean typed = end == 3 || end == 6;
                if(end != 2 && end != 3 && end != 5 && end != 6)
                    throw new WrongUsageException("/gemini camp spawn [" + WretchCamps.TYPE + "] [x y z] [force]");
                if(typed && !WretchCamps.TYPE.equals(args[2]))
                    throw new CommandException("Unknown camp type '" + args[2] + "'. Available: " + WretchCamps.TYPE + ".");
                EntityPlayerMP player = player(sender);
                ChunkCoordinates requested = end <= 3 ? null : origin(sender, player, args, typed ? 3 : 2);
                generated(sender, WretchCamps.debugSpawn(player, requested), requested != null, true); return;
            }
            if(args.length == 2 && "locate".equals(args[1])) {
                locate(sender, WretchCamps.known(world), "camp"); return;
            }
            if((args.length == 2 || args.length == 3) && "list".equals(args[1])) {
                list(sender, world, WretchCamps.known(world), "camp", args);
                return;
            }
            throw new WrongUsageException("/gemini camp spawn [" + WretchCamps.TYPE + "] [x y z] [force] | /gemini camp locate | /gemini camp list [page]");
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
        reply(sender, GeminiInvasionManager.structurePlayers(world));
        reply(sender, GardenerNests.diagnostics(world));
        reply(sender, WretchCamps.diagnostics(world));
        reply(sender, "Successfully registered nests=" + GardenerNests.completedCount(world)
                + "; pending/unfinished nests=" + (GardenerNests.known(world).size() - GardenerNests.completedCount(world))
                + "; total nest records=" + GardenerNests.known(world).size() + "; successfully registered camps=" + WretchCamps.completedCount(world)
                + "; pending/unfinished camps=" + (WretchCamps.known(world).size() - WretchCamps.completedCount(world))
                + "; total camp records=" + WretchCamps.known(world).size()
                + " in sender dimension " + world.provider.dimensionId + ". Invasion world=Overworld (0), shared saved progression.");
    }

    private static void list(ICommandSender sender, WorldServer world, List<StructureInfo> structures, String kind, String[] args) {
        int pages = Math.max(1, (structures.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = args.length == 3 ? parseIntBounded(sender, args[2], 1, pages) : 1;
        reply(sender, "Known " + kind + "s in dimension " + world.provider.dimensionId + ": " + structures.size()
                + "; page " + page + "/" + pages + ".");
        for(int i = (page - 1) * PAGE_SIZE; i < Math.min(structures.size(), page * PAGE_SIZE); i++) reply(sender, structures.get(i).toString());
        if(page < pages) reply(sender, "Next: /gemini " + kind + " list " + (page + 1));
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
        if(args.length == 1) return getListOfStringsMatchingLastWord(args, "help", "status", "phase", "time", "nest", "camp", "iris", "lights", "harvest", "debug");
        if(args.length == 2) {
            if("lights".equals(args[0]) || "harvest".equals(args[0])) return getListOfStringsMatchingLastWord(args, "start", "stop", "status");
            if("iris".equals(args[0])) return getListOfStringsMatchingLastWord(args, "status", "show", "hide", "stage", "observe", "reset");
            if("phase".equals(args[0])) return getListOfStringsMatchingLastWord(args, "set", "next", "reset");
            if("time".equals(args[0])) return getListOfStringsMatchingLastWord(args, "add", "set");
            if("nest".equals(args[0])) return getListOfStringsMatchingLastWord(args, "spawn", "locate", "list");
            if("camp".equals(args[0])) return getListOfStringsMatchingLastWord(args, "spawn", "locate", "list");
            if("debug".equals(args[0])) return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if(args.length == 3) {
            if("iris".equals(args[0]) && "stage".equals(args[1])) return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4");
            if("phase".equals(args[0]) && "set".equals(args[1])) return getListOfStringsMatchingLastWord(args, "1", "2", "3", "4");
            if("camp".equals(args[0]) && "spawn".equals(args[1])) return getListOfStringsMatchingLastWord(args, WretchCamps.TYPE, "~", "force");
        }
        if(args.length >= 3 && "spawn".equals(args[1]) && ("camp".equals(args[0]) || "nest".equals(args[0]))) {
            int start = "camp".equals(args[0]) && WretchCamps.TYPE.equals(args[2]) ? 3 : 2;
            if(args.length == start + 1 || args.length == start + 4) return getListOfStringsMatchingLastWord(args, "~", "force");
            if(args.length <= start + 3) return getListOfStringsMatchingLastWord(args, "~");
        }
        return null;
    }
}
