package com.hbm.inventory.fluid.trait;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingStep;
import com.hbm.main.MainRegistry;

/** Checks the built-in steam trait graph after defaults or JSON overrides are loaded. */
public final class SteamLedgerValidator {

	private static final long FEEDWATER_BATCH = 10L;
	private static final long[] STORED_AMOUNTS = {1_000L, 100L, 10L, 1L};

	private SteamLedgerValidator() { }

	public static void validate() {
		FluidType[] grades = {Fluids.STEAM, Fluids.HOTSTEAM, Fluids.SUPERHOTSTEAM, Fluids.ULTRAHOTSTEAM};
		if(Fluids.FRESH_WATER == null || Fluids.SPENTSTEAM == null) {
			warn("Fresh water or spent steam is missing");
			return;
		}
		for(FluidType grade : grades) {
			if(grade == null) {
				warn("A stock steam grade is missing");
				return;
			}
		}

		FT_Heatable water = Fluids.FRESH_WATER.getTrait(FT_Heatable.class);
		if(water == null) {
			warn("Fresh water has no heating trait");
			return;
		}

		long[] idealWork = new long[grades.length];
		boolean[] coolingValid = new boolean[grades.length];
		for(int gradeIndex = 0; gradeIndex < grades.length; gradeIndex++) {
			long amount = STORED_AMOUNTS[gradeIndex];
			coolingValid[gradeIndex] = true;
			for(int coolingIndex = gradeIndex; coolingIndex >= 0; coolingIndex--) {
				FT_Coolable cooling = grades[coolingIndex].getTrait(FT_Coolable.class);
				FluidType expectedOutput = coolingIndex == 0 ? Fluids.SPENTSTEAM : grades[coolingIndex - 1];
				long expectedAmount = coolingIndex == 0 ? FEEDWATER_BATCH : STORED_AMOUNTS[coolingIndex - 1];
				if(cooling == null || cooling.coolsTo != expectedOutput || cooling.amountReq <= 0 || cooling.amountProduced <= 0 || cooling.heatEnergy <= 0 || amount % cooling.amountReq != 0 || amount / cooling.amountReq * cooling.amountProduced != expectedAmount) {
					warn(grades[coolingIndex].getName() + " has an invalid stock cooling transition or water-equivalent ratio");
					coolingValid[gradeIndex] = false;
					break;
				}
				idealWork[gradeIndex] += amount / cooling.amountReq * cooling.heatEnergy;
				amount = expectedAmount;
			}
		}

		long stagedHeat = 0L;
		for(int gradeIndex = 0; gradeIndex < grades.length; gradeIndex++) {
			FluidType input = gradeIndex == 0 ? Fluids.FRESH_WATER : grades[gradeIndex - 1];
			long inputAmount = gradeIndex == 0 ? FEEDWATER_BATCH : STORED_AMOUNTS[gradeIndex - 1];
			FT_Heatable heating = input.getTrait(FT_Heatable.class);
			if(heating == null || heating.steps.isEmpty() || heating.steps.get(0) == null) {
				warn(input.getName() + " has no first heating step");
				return;
			}
			HeatingStep step = heating.getFirstStep();
			if(!validHeatingStep(input, step, grades[gradeIndex], inputAmount, STORED_AMOUNTS[gradeIndex])) return;
			stagedHeat += inputAmount / step.amountReq * step.heatReq;
			if(coolingValid[gradeIndex] && stagedHeat < idealWork[gradeIndex]) warn(grades[gradeIndex].getName() + " staged heat " + stagedHeat + " TU is below downstream ideal work " + idealWork[gradeIndex] + " quanta per 10 feedwater L");

			HeatingStep direct = null;
			for(HeatingStep candidate : water.steps) {
				if(candidate != null && candidate.typeProduced == grades[gradeIndex]) {
					if(direct != null) warn("Duplicate direct fresh-water route to " + grades[gradeIndex].getName());
					direct = candidate;
				}
			}
			if(direct == null) {
				warn("No direct fresh-water route to " + grades[gradeIndex].getName());
				continue;
			}
			if(!validHeatingStep(Fluids.FRESH_WATER, direct, grades[gradeIndex], FEEDWATER_BATCH, STORED_AMOUNTS[gradeIndex])) continue;
			long directHeat = FEEDWATER_BATCH / direct.amountReq * direct.heatReq;
			if(directHeat != stagedHeat) warn(grades[gradeIndex].getName() + " direct heat " + directHeat + " TU differs from staged heat " + stagedHeat + " TU per 10 feedwater L");
			if(coolingValid[gradeIndex] && directHeat < idealWork[gradeIndex]) warn(grades[gradeIndex].getName() + " direct heat " + directHeat + " TU is below downstream ideal work " + idealWork[gradeIndex] + " quanta per 10 feedwater L");
		}
	}

	private static boolean validHeatingStep(FluidType input, HeatingStep step, FluidType output, long inputAmount, long expectedOutputAmount) {
		if(step.typeProduced != output || step.amountReq <= 0 || step.amountProduced <= 0 || step.heatReq < 0 || inputAmount % step.amountReq != 0 || inputAmount / step.amountReq * step.amountProduced != expectedOutputAmount) {
			warn(input.getName() + " -> " + output.getName() + " has an invalid stock heating transition or water-equivalent ratio");
			return false;
		}
		return true;
	}

	private static void warn(String issue) {
		MainRegistry.logger.warn("Stock steam ledger: " + issue);
	}
}
