package com.hbm.inventory.fluid.trait;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import api.hbm.energymk2.EnergyUnits;

import net.minecraft.util.EnumChatFormatting;

public class FT_Combustible extends FluidTrait {
	
	protected FuelGrade fuelGrade;
	protected long combustionEnergyQuanta;
	
	public FT_Combustible() { }
	
	public FT_Combustible(FuelGrade grade, long energyQuanta) {
		this.fuelGrade = grade;
		this.combustionEnergyQuanta = energyQuanta;
	}
	
	@Override
	public void addInfo(List<String> info) {
		super.addInfo(info);

		info.add(EnumChatFormatting.GOLD + "[Combustible]");
		
		if(combustionEnergyQuanta > 0) {
			info.add(EnumChatFormatting.GOLD + "Provides " + EnumChatFormatting.RED + EnergyUnits.formatJoules(combustionEnergyQuanta) + " " + EnumChatFormatting.GOLD + "per bucket");
			info.add(EnumChatFormatting.GOLD + "Fuel grade: " + EnumChatFormatting.RED + this.fuelGrade.getGrade());
		}
	}
	
	public long getCombustionEnergyQuanta() {
		return this.combustionEnergyQuanta;
	}

	@Deprecated public long getCombustionEnergy() { return EnergyUnits.quantaToLegacyHe(getCombustionEnergyQuanta()); }
	
	public FuelGrade getGrade() {
		return this.fuelGrade;
	}
	
	public static enum FuelGrade {
		LOW("Low"),			//heating and industrial oil				< star engine, iGen
		MEDIUM("Medium"),	//petroil									< diesel generator
		HIGH("High"),		//diesel, gasoline							< HP engine
		AERO("Aviation"),	//kerosene and other light aviation fuels	< turbofan
		GAS("Gaseous");		//fuel gasses like NG, PG and syngas		< gas turbine
		
		private String grade;
		
		private FuelGrade(String grade) {
			this.grade = grade;
		}
		
		public String getGrade() {
			return this.grade;
		}
	}

	@Override
	public void serializeJSON(JsonWriter writer) throws IOException {
		writer.name("energyQuanta").value(combustionEnergyQuanta);
		writer.name("grade").value(fuelGrade.name());
	}
	
	@Override
	public void deserializeJSON(JsonObject obj) {
		this.combustionEnergyQuanta = obj.has("energyQuanta") ? obj.get("energyQuanta").getAsLong() : EnergyUnits.legacyHeToQuanta(obj.get("energy").getAsLong());
		this.fuelGrade = FuelGrade.valueOf(obj.get("grade").getAsString());
	}
}
