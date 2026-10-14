package com.hbm.inventory.recipes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.realsim.MassRemainder;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.util.Tuple.Pair;

public class CompressorRecipes extends SerializableRecipe {
	
	public static HashMap<Pair<FluidType, Integer>, CompressorRecipe> recipes = new HashMap();
	public static HashMap<FluidType, CompressorRecipe> decompressions = new HashMap<FluidType, CompressorRecipe>();
	public static boolean canAcceptOutput(FluidTank input, FluidTank output, CompressorRecipe recipe, MassRemainder remainder) {
		if(output.getTankType() != recipe.output.type || output.getPressure() != recipe.output.pressure) return false;
		long amount = recipe.massConversion ? remainder.preview(recipe.output.type.getName(), Math.multiplyExact(input.getTankType().getPhysicalState().massUnits, recipe.inputAmount)) : recipe.output.fill;
		return output.getFill() + amount <= output.getMaxFill();
	}

	@Override
	public void registerDefaults() {
		com.hbm.realsim.PhaseConversions.register();
        for(com.hbm.realsim.SteamLedger.State in : com.hbm.realsim.SteamLedger.State.values()) {
            com.hbm.realsim.SteamLedger.State out = com.hbm.realsim.SteamLedger.compressorOutlet(in);
            if(out == null) continue;
            long work = com.hbm.realsim.SteamLedger.transition(in, out, com.hbm.realsim.SteamLedger.Route.COMPRESSOR, 1000000).workJPerKg;
            CompressorRecipe recipe = new CompressorRecipe(1000, new FluidStack(Fluids.fromName(out.identity), 100));
            recipe.workQuanta = com.hbm.realsim.SteamLedger.ceilDiv(Math.multiplyExact(in.mass(1000), work), 500000L);
            recipe.duration = Math.toIntExact(com.hbm.realsim.SteamLedger.ceilDiv(recipe.workQuanta, 2500));
            recipes.put(new Pair(Fluids.fromName(in.identity), 0), recipe);
        }


        // Refinery/process gas compression is registered with paid physical work above.
		// LPG recovery and blood-to-oil conversion are separation/chemical processes, not compression recipes.

	}
	
	public static class CompressorRecipe {
		
		public FluidStack output;
		public int inputAmount;
		public int duration;
		public long workQuanta;
		public boolean massConversion;
		
		public CompressorRecipe(int input, FluidStack output, int duration) {
			this.output = output;
			this.inputAmount = input;
			this.duration = duration;
		}
		
		public CompressorRecipe(int input, FluidStack output) {
			this(input, output, 100);
		}
	}

	@Override
	public String getFileName() {
		return "hbmCompressor.json";
	}

	@Override
	public Object getRecipeObject() {
		return recipes;
	}

	@Override
	public void deleteRecipes() {
		recipes.clear();
		decompressions.clear();
	}

	@Override
	public void readRecipe(JsonElement recipe) {
		JsonObject obj = recipe.getAsJsonObject();

		FluidStack input = this.readFluidStack(obj.get("input").getAsJsonArray());
		FluidStack output = this.readFluidStack(obj.get("output").getAsJsonArray());
		
		CompressorRecipe result = new CompressorRecipe(input.fill, output, obj.has("duration") ? obj.get("duration").getAsInt() : 100);
		result.workQuanta = obj.has("workQuanta") ? Math.max(0L, obj.get("workQuanta").getAsLong()) : 0L;
		result.massConversion = obj.has("massConversion") && obj.get("massConversion").getAsBoolean();
		if(result.massConversion && (!input.type.getPhysicalState().isResolved() || !output.type.getPhysicalState().isResolved() || !input.type.getPhysicalState().substance.equals(output.type.getPhysicalState().substance))) throw new IllegalArgumentException("Conversion must preserve chemical identity");
        com.hbm.realsim.SteamLedger.State steam = com.hbm.realsim.SteamLedger.state(input.type.getName());
        if(steam != null) {
            com.hbm.realsim.SteamLedger.State out = com.hbm.realsim.SteamLedger.state(output.type.getName());
            com.hbm.realsim.SteamLedger.Transition t = com.hbm.realsim.SteamLedger.transition(steam, out, com.hbm.realsim.SteamLedger.Route.COMPRESSOR, 1000000);
            if(steam.mass(input.fill) != out.mass(output.fill)) throw new IllegalArgumentException("Steam compression must preserve mass");
            result.workQuanta = com.hbm.realsim.SteamLedger.ceilDiv(Math.multiplyExact(steam.mass(input.fill), t.workJPerKg), 500000);
        }

		recipes.put(new Pair(input.type, input.pressure), result);
	}

	@Override
	public void writeRecipe(Object recipe, JsonWriter writer) throws IOException {
		Entry<Pair<FluidType, Integer>, CompressorRecipe> entry = (Entry) recipe;
		
		writer.name("input");
		this.writeFluidStack(new FluidStack(entry.getKey().getKey(), entry.getValue().inputAmount, entry.getKey().getValue()), writer);
		writer.name("output");
		this.writeFluidStack(entry.getValue().output, writer);
		writer.name("duration").value(entry.getValue().duration);
		writer.name("workQuanta").value(entry.getValue().workQuanta);
		writer.name("massConversion").value(entry.getValue().massConversion);
	}
}
