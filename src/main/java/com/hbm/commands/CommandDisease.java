package com.hbm.commands;

import com.hbm.handler.disease.DiseaseHost;
import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.Infection;
import com.hbm.handler.disease.Pathogen;
import com.hbm.handler.disease.Rabies;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

/** Administrative diagnostics, not an automatic bite notification or a medical treatment. */
public final class CommandDisease extends CommandBase {
	@Override public String getCommandName() { return "ntmdisease"; }
	@Override public int getRequiredPermissionLevel() { return 2; }
	@Override public String getCommandUsage(ICommandSender sender) { return "/ntmdisease <inspect|clear> <player|entityId>"; }

	@Override public void processCommand(ICommandSender sender, String[] args) {
		if(args.length != 2 || !("inspect".equals(args[0]) || "clear".equals(args[0])))
			throw new WrongUsageException(getCommandUsage(sender));
		Entity entity;
		if(args[1].matches("[0-9]+")) entity = sender.getEntityWorld().getEntityByID(parseIntWithMin(sender, args[1], 0));
		else entity = getPlayer(sender, args[1]);
		if(!(entity instanceof EntityLivingBase)) throw new WrongUsageException("Target must be a loaded living entity in the sender's dimension, or an online player.");
		EntityLivingBase host = (EntityLivingBase) entity;
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
				+ ", infectedAt=" + infection.infectedAt + ", elapsedTicks=" + Math.max(0L, now - infection.infectedAt)
				+ ", treatmentEligible=" + (host instanceof EntityPlayer && Rabies.treatmentEligible(infection, now))
				+ ", remainingTreatmentTicks=" + Rabies.treatmentTicksRemaining(infection, now)
				+ ", fatalCommitted=" + Rabies.fatalCommitted(infection)
				+ ", source=" + infection.source + " (" + infection.sourceHost + ")"
				+ ", timeline=" + infection.details));
		sender.addChatMessage(new ChatComponentText("behavior: " + Rabies.behaviorStatus(host, infection)));
	}
}
