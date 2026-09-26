package com.hbm.tileentity;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import api.hbm.energymk2.EnergyUnits;

public interface IConfigurableMachine {

	/* the name for the JOSN object for this machine */
	public String getConfigName();
	/* reads the JSON object and sets the machine's parameters, use defaults and ignore if a value is not yet present */
	public void readIfPresent(JsonObject obj);
	/* writes the entire config for this machine using the relevant values */
	public void writeConfig(JsonWriter writer) throws IOException;
	
	public static boolean grab(JsonObject obj, String name, boolean def) {
		return obj.has(name) ? obj.get(name).getAsBoolean() : def;
	}
	
	public static int grab(JsonObject obj, String name, int def) {
		return obj.has(name) ? obj.get(name).getAsInt() : def;
	}
	
	public static long grab(JsonObject obj, String name, long def) {
		return obj.has(name) ? obj.get(name).getAsLong() : def;
	}

	/** New values are exact half-joule quanta; old HE values map one-to-one. */
	public static long grabEnergyQuanta(JsonObject obj, String siName, String legacyName, long def) {
		if(obj.has(siName)) return obj.get(siName).getAsLong();
		return obj.has(legacyName) ? EnergyUnits.legacyHeToQuanta(obj.get(legacyName).getAsLong()) : def;
	}

	public static int grabEnergyQuanta(JsonObject obj, String siName, String legacyName, int def) {
		return Math.toIntExact(grabEnergyQuanta(obj, siName, legacyName, (long) def));
	}
	
	public static double grab(JsonObject obj, String name, double def) {
		return obj.has(name) ? obj.get(name).getAsDouble() : def;
	}
}
