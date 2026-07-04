package com.hbm.handler.pollution;

import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Central server-side entry point for machine exhaust.
 *
 * CO is invisible and tracked separately from SOOT/smog. This helper keeps the
 * realism policy in one place so machines only report actual fuel/recipe
 * activity instead of copy-pasting gas rules.
 */
public final class MachineEmissionHelper {

	private MachineEmissionHelper() { }

	public static final EmissionProfile DIESEL_GENERATOR = new EmissionProfile(3.0F, 1.0F, 1.4F, 1.0F, 1.0F, 6, 0.96F);
	public static final EmissionProfile INDUSTRIAL_COMBUSTION_ENGINE = new EmissionProfile(4.0F, 1.5F, 1.6F, 1.0F, 1.0F, 6, 0.96F);
	public static final EmissionProfile FUEL_FIRED_FURNACE = new EmissionProfile(1.5F, 1.0F, 1.2F, 1.0F, 1.0F, 3, 0.97F);
	public static final EmissionProfile COKER = new EmissionProfile(2.5F, 3.0F, 1.8F, 1.3F, 1.0F, 8, 0.95F);
	public static final EmissionProfile PYROLYSIS = new EmissionProfile(2.0F, 1.5F, 1.6F, 1.2F, 1.0F, 4, 0.96F);

	public static void emitCombustion(World world, int x, int y, int z, EmissionProfile profile, double activity) {
		emitCombustion(world, x, y, z, profile, activity, null, ForgeDirection.UNKNOWN);
	}

	public static void emitCombustion(World world, int x, int y, int z, EmissionProfile profile, double activity, FluidType fuel, ForgeDirection exhaust) {
		if(world == null || world.isRemote || profile == null || activity <= 0) return;

		int[] pos = getExhaustPosition(world, x, y, z, profile.hotExhaustRise, exhaust);
		float fuelMultiplier = getFuelCOFactor(fuel);
		float sootMultiplier = getFuelSootFactor(fuel);
		float enclosure = getEnclosedSpaceMultiplier(world, pos[0], pos[1], pos[2], profile.enclosedSpaceMultiplier);
		double outdoor = isOpenAir(world, pos[0], pos[1], pos[2]) ? 0.45D : 1D;
		double highAir = pos[1] > 100 && world.canBlockSeeTheSky(pos[0], pos[1], pos[2]) ? 0.35D : 1D;

		emitCarbonMonoxide(world, pos[0], pos[1], pos[2], profile.baseCO * activity * profile.incompleteCombustionMultiplier * fuelMultiplier * enclosure * outdoor * highAir);
		emitSmog(world, pos[0], pos[1], pos[2], profile.baseSmog * activity * sootMultiplier * outdoor * highAir);
	}

	public static void emitCarbonMonoxide(World world, int x, int y, int z, double amount) {
		if(world == null || world.isRemote || amount <= 0) return;
		PollutionHandler.incrementPollution(world, x, y, z, PollutionType.CARBON_MONOXIDE, (float) amount * PollutionHandler.CARBON_MONOXIDE_PER_SECOND);
	}

	public static void emitSmog(World world, int x, int y, int z, double amount) {
		if(world == null || world.isRemote || amount <= 0) return;
		PollutionHandler.incrementPollution(world, x, y, z, PollutionType.SOOT, (float) amount * PollutionHandler.SOOT_PER_SECOND);
	}

	public static void emitRecipeOffgas(World world, int x, int y, int z, ItemStack[] inputs, FluidStack[] fluids, double activity) {
		if(world == null || world.isRemote || activity <= 0) return;
		if(containsCarbonaceousInput(inputs) || containsCarbonaceousFluid(fluids)) {
			emitCombustion(world, x, y, z, PYROLYSIS, activity);
		}
	}

	public static void scrubCarbonMonoxide(World world, int x, int y, int z, float amount) {
		if(world == null || world.isRemote || amount <= 0) return;
		PollutionHandler.decrementPollution(world, x, y, z, PollutionType.CARBON_MONOXIDE, amount);
		PollutionHandler.incrementPollution(world, x, y, z, PollutionType.POISON, amount * 0.02F);
	}

	private static int[] getExhaustPosition(World world, int x, int y, int z, int rise, ForgeDirection exhaust) {
		int ex = x, ey = y, ez = z;
		if(exhaust != null && exhaust != ForgeDirection.UNKNOWN) {
			ex += exhaust.offsetX;
			ey += exhaust.offsetY;
			ez += exhaust.offsetZ;
		}
		for(int i = 0; i < rise && ey + 1 < world.getActualHeight() && canGasPass(world, ex, ey + 1, ez); i++) ey++;
		return new int[] { ex, ey, ez };
	}

	public static boolean canGasPass(World world, int x, int y, int z) {
		Block block = world.getBlock(x, y, z);
		return block.isAir(world, x, y, z) || !block.getMaterial().blocksMovement() || block.getUnlocalizedName().toLowerCase().contains("grate") || block.getUnlocalizedName().toLowerCase().contains("vent");
	}

	private static boolean isOpenAir(World world, int x, int y, int z) {
		return world.canBlockSeeTheSky(x, y, z) || y > 100;
	}

	private static float getEnclosedSpaceMultiplier(World world, int x, int y, int z, float profileMultiplier) {
		return isOpenAir(world, x, y, z) ? 1F : profileMultiplier;
	}

	private static float getFuelCOFactor(FluidType fuel) {
		if(fuel == null) return 1F;
		if(fuel == Fluids.HYDROGEN) return 0F;
		String name = fuel.getName().toLowerCase();
		if(name.contains("gas") || name.contains("hydrogen")) return 0.45F;
		if(name.contains("diesel") || name.contains("fuel") || name.contains("gasoline")) return 1.2F;
		if(name.contains("oil") || name.contains("tar") || name.contains("coal") || name.contains("coke")) return 1.5F;
		return 1F;
	}

	private static float getFuelSootFactor(FluidType fuel) {
		if(fuel == null) return 1F;
		if(fuel == Fluids.HYDROGEN) return 0F;
		String name = fuel.getName().toLowerCase();
		if(name.contains("gas") || name.contains("hydrogen")) return 0.35F;
		if(name.contains("oil") || name.contains("tar") || name.contains("coal") || name.contains("coke")) return 1.7F;
		return 1F;
	}

	private static boolean containsCarbonaceousInput(ItemStack[] inputs) {
		if(inputs == null) return false;
		for(ItemStack stack : inputs) {
			if(stack == null) continue;
			String name = stack.getDisplayName().toLowerCase();
			if(name.contains("coal") || name.contains("coke") || name.contains("carbon") || name.contains("graphite") || name.contains("tar") || name.contains("oil") || name.contains("plastic")) return true;
		}
		return false;
	}

	private static boolean containsCarbonaceousFluid(FluidStack[] fluids) {
		if(fluids == null) return false;
		for(FluidStack stack : fluids) {
			if(stack == null || stack.type == null) continue;
			String name = stack.type.getName().toLowerCase();
			if(name.contains("oil") || name.contains("fuel") || name.contains("gas") || name.contains("tar") || name.contains("diesel")) return true;
		}
		return false;
	}

	public static class EmissionProfile {
		public final float baseCO;
		public final float baseSmog;
		public final float incompleteCombustionMultiplier;
		public final float fuelTypeMultiplier;
		public final float enclosedSpaceMultiplier;
		public final int hotExhaustRise;
		public final float decay;

		public EmissionProfile(float baseCO, float baseSmog, float incompleteCombustionMultiplier, float fuelTypeMultiplier, float enclosedSpaceMultiplier, int hotExhaustRise, float decay) {
			this.baseCO = baseCO;
			this.baseSmog = baseSmog;
			this.incompleteCombustionMultiplier = incompleteCombustionMultiplier;
			this.fuelTypeMultiplier = fuelTypeMultiplier;
			this.enclosedSpaceMultiplier = enclosedSpaceMultiplier;
			this.hotExhaustRise = hotExhaustRise;
			this.decay = decay;
		}
	}
}
