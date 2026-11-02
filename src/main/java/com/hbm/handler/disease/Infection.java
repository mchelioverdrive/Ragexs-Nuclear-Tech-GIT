package com.hbm.handler.disease;

import net.minecraft.nbt.NBTTagCompound;

/** One canonical record per pathogen/host. Disease-specific timeline data stays in details. */
public final class Infection {
	public final String pathogen, source, sourceHost;
	public final long infectedAt;
	public String stage, presentation;
	public final NBTTagCompound details;

	public Infection(String pathogen, String source, String sourceHost, long infectedAt,
			String stage, String presentation, NBTTagCompound details) {
		this.pathogen = pathogen;
		this.source = source;
		this.sourceHost = sourceHost == null ? "unknown" : sourceHost;
		this.infectedAt = infectedAt;
		this.stage = stage;
		this.presentation = presentation;
		this.details = details;
	}

	public NBTTagCompound save() {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setString("pathogen", pathogen);
		tag.setString("source", source);
		tag.setString("sourceHost", sourceHost);
		tag.setLong("infectedAt", infectedAt);
		tag.setString("stage", stage);
		tag.setString("presentation", presentation);
		tag.setTag("details", details.copy());
		return tag;
	}

	public static Infection load(NBTTagCompound tag) {
		return new Infection(tag.getString("pathogen"), tag.getString("source"), tag.getString("sourceHost"),
				tag.getLong("infectedAt"), tag.getString("stage"), tag.getString("presentation"),
				(NBTTagCompound) tag.getCompoundTag("details").copy());
	}
}
