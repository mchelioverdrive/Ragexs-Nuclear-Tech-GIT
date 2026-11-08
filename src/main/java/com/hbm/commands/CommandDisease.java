package com.hbm.commands;

import java.util.List;
import java.util.Locale;

import com.hbm.handler.disease.DiseaseHost;
import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.Infection;
import com.hbm.handler.disease.Pathogen;
import com.hbm.handler.disease.Rabies;
import com.hbm.handler.disease.DeepRootDisease;
import com.hbm.world.gemini.GeminiInfections;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

/** Administrative diagnostics, not an automatic bite notification or a medical treatment. */
public final class CommandDisease extends CommandBase {
	@Override public String getCommandName() { return "ntmdisease"; }
	@Override public int getRequiredPermissionLevel() { return 2; }
	@Override public String getCommandUsage(ICommandSender sender) {
		return "/ntmdisease <inspect|clear> <player|entityId> [rabies|deep_root] | "
				+ "/ntmdisease infect <player|entityId> deep_root | /ntmdisease stage <player|entityId> deep_root "
				+ "<EXPOSURE|ROOTS|CONTRACTION|BULBS|SPROUTS|TERMINAL>";
	}

	@Override public void processCommand(ICommandSender sender, String[] args) {
		if(args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
		boolean infect = "infect".equals(args[0]), selectStage = "stage".equals(args[0]);
		if(infect || selectStage) {
			if(args.length != (selectStage ? 4 : 3) || !DeepRootDisease.ID.equals(args[2]))
				throw new WrongUsageException(getCommandUsage(sender));
		} else if((args.length != 2 && args.length != 3) || !("inspect".equals(args[0]) || "clear".equals(args[0]))
				|| args.length == 3 && !(Rabies.ID.equals(args[2]) || DeepRootDisease.ID.equals(args[2])))
			throw new WrongUsageException(getCommandUsage(sender));
		DeepRootDisease.Stage requestedStage = DeepRootDisease.Stage.EXPOSURE;
		if(selectStage) {
			try { requestedStage = DeepRootDisease.Stage.valueOf(args[3].toUpperCase(Locale.ROOT)); }
			catch(IllegalArgumentException ex) {
				throw new WrongUsageException("Unknown Deep Root stage. Use EXPOSURE, ROOTS, CONTRACTION, BULBS, SPROUTS or TERMINAL.");
			}
		}
		Entity entity;
		if(args[1].matches("[0-9]+")) entity = sender.getEntityWorld().getEntityByID(parseIntWithMin(sender, args[1], 0));
		else entity = getPlayer(sender, args[1]);
		if(!(entity instanceof EntityLivingBase)) throw new WrongUsageException("Target must be a loaded living entity in the sender's dimension, or an online player.");
		EntityLivingBase host = (EntityLivingBase) entity;
		if(infect || selectStage) {
			Pathogen pathogen = DiseaseManager.pathogen(DeepRootDisease.ID);
			if(!pathogen.enabled()) throw new CommandException("Deep Root Disease is disabled. Both Gemini and Deep Root Disease settings must be enabled.");
			if(!host.isEntityAlive() || !pathogen.supports(host))
				throw new CommandException("Target must be a living supported Deep Root host; creative/invulnerable players, undead and Gemini creatures are excluded.");
			Infection infection = DiseaseHost.get(host).infections.get(DeepRootDisease.ID);
			if(infect && infection != null) throw new CommandException("Target already has Deep Root Disease. Use /ntmdisease stage to select its stage.");
			boolean created = infection == null;
			if(created && !DeepRootDisease.infectForDebug(host, sender.getCommandSenderName()))
				throw new CommandException("Could not infect the target with Deep Root Disease.");
			if(selectStage) DeepRootDisease.selectStage(host, requestedStage);
			sender.addChatMessage(new ChatComponentText(host.getCommandSenderName() + " (ID=" + host.getEntityId() + "): "
					+ (created ? "Deep Root Disease infection created; " : "existing Deep Root Disease infection updated; ")
					+ "stage=" + requestedStage.name() + ", stageTicks=0."
					+ (DeepRootDisease.active(host) ? "" : " Progression is currently paused by the existing infection's activity rules.")));
			return;
		}
		if(args.length == 3 && DeepRootDisease.ID.equals(args[2])) {
			if("clear".equals(args[0])) {
				sender.addChatMessage(new ChatComponentText(DiseaseManager.remove(host, DeepRootDisease.ID)
						? "Deep Root Disease removed; movement restored and growths cleared. Nausea expires shortly."
						: "No Deep Root Disease record."));
				return;
			}
			Infection deepRoot = DiseaseHost.get(host).infections.get(DeepRootDisease.ID);
			if(deepRoot == null) sender.addChatMessage(new ChatComponentText("No established Deep Root Disease infection recorded."));
			else sender.addChatMessage(new ChatComponentText("deep_root: " + deepRoot.stage
					+ ", active=" + DeepRootDisease.active(host) + ", stageTicks=" + DeepRootDisease.stageTicks(deepRoot)
					+ ", durationTicks=" + DeepRootDisease.durationTicks(deepRoot)
					+ ", conversionEligible=" + GeminiInfections.isConversionEligible(host)
					+ ", conversionImplemented=false, source=" + deepRoot.source + " (" + deepRoot.sourceHost + ")"));
			return;
		}
		if("clear".equals(args[0])) {
			sender.addChatMessage(new ChatComponentText(DiseaseManager.remove(host, Rabies.ID) ? "Rabies record and fatal commitment removed; disease AI restored. Symptoms expire shortly." : "No rabies record."));
			return;
		}
		Infection infection = DiseaseHost.get(host).infections.get(Rabies.ID);
		if(infection == null) {
			sender.addChatMessage(new ChatComponentText("No established rabies infection recorded."));
			return;
		}
		Pathogen pathogen = DiseaseManager.pathogen(Rabies.ID);
		long now = DiseaseManager.now(host);
		if(pathogen.enabled()) pathogen.progress(host, infection, now);
		else Rabies.refreshFatalCommitment(host, infection, now);
		sender.addChatMessage(new ChatComponentText("rabies: " + infection.stage + ", presentation=" + infection.presentation
				+ ", active=" + pathogen.enabled() + ", infectious=" + (pathogen.enabled() && pathogen.infectious(infection))
				+ ", infectedAt=" + (infection.hasInfectionTime() ? Long.toString(infection.infectedAt) : "unknown")
				+ ", elapsedTicks=" + (infection.hasInfectionTime() ? Long.toString(Math.max(0L, now - infection.infectedAt)) : "unknown")
				+ ", treatmentEligible=" + (host instanceof EntityPlayer && Rabies.treatmentEligible(infection, now))
				+ ", remainingTreatmentTicks=" + Rabies.treatmentTicksRemaining(infection, now)
				+ ", fatalCommitted=" + Rabies.fatalCommitted(infection)
				+ ", source=" + infection.source + " (" + infection.sourceHost + ")"
				+ ", timeline=" + infection.details));
		sender.addChatMessage(new ChatComponentText("behavior: " + Rabies.behaviorStatus(host, infection)));
	}

	@Override public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		if(args.length == 1) return getListOfStringsMatchingLastWord(args, "inspect", "clear", "infect", "stage");
		boolean debug = args.length > 0 && ("infect".equals(args[0]) || "stage".equals(args[0]));
		boolean diagnostic = args.length > 0 && ("inspect".equals(args[0]) || "clear".equals(args[0]));
		if(args.length == 2 && (debug || diagnostic))
			return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
		if(args.length == 3) {
			if(debug) return getListOfStringsMatchingLastWord(args, DeepRootDisease.ID);
			if(diagnostic) return getListOfStringsMatchingLastWord(args, Rabies.ID, DeepRootDisease.ID);
		}
		if(args.length == 4 && "stage".equals(args[0]) && DeepRootDisease.ID.equals(args[2])) {
			DeepRootDisease.Stage[] stages = DeepRootDisease.Stage.values();
			String[] names = new String[stages.length];
			for(int i = 0; i < stages.length; i++) names[i] = stages[i].name();
			return getListOfStringsMatchingLastWord(args, names);
		}
		return null;
	}
}
