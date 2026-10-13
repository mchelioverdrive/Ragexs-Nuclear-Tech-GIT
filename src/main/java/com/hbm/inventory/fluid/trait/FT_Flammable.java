package com.hbm.inventory.fluid.trait;


import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import net.minecraft.util.EnumChatFormatting;

public class FT_Flammable extends FluidTrait {
	
	/** Deferred pre-RealSim equipment tuning only; never a physical chemical-energy reference. */
	private long energy;
	
	public FT_Flammable() { }
	
	public FT_Flammable(long energy) {
		this.energy = energy;
	}
	
	public long getLegacyBurnerBudget() {
		return this.energy;
	}
	@Deprecated public long getHeatEnergy() { return getLegacyBurnerBudget(); }
	
	@Override
	public void addInfo(List<String> info) {
		super.addInfo(info);
		
		info.add(EnumChatFormatting.YELLOW + "[Flammable]");
		
	}

	@Override
	public void serializeJSON(JsonWriter writer) throws IOException {
		writer.name("energy").value(energy);
	}
	
	@Override
	public void deserializeJSON(JsonObject obj) {
		this.energy = obj.get("energy").getAsLong();
	}
}
