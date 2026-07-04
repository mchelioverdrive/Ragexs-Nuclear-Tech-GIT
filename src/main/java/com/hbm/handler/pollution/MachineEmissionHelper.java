package com.hbm.handler.pollution;

import com.hbm.blocks.ModBlocks;
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
 * Carbon monoxide already exists as {@link ModBlocks#gas_monoxide}; this helper
 * reuses that invisible gas block and keeps visible SOOT/smog in the pollution
 * sector system. Machines only report actual fuel/recipe activity here instead
 * of copy-pasting emission rules.
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

		int attempts = Math.min(3, (int) Math.floor(amount));
		double fractional = amount - attempts;
		if(fractional > 0 && world.rand.nextDouble() < fractional) attempts++;
		if(attempts <= 0 && world.rand.nextInt(Math.max(1, (int) Math.ceil(1D / amount))) == 0) attempts = 1;

		for(int i = 0; i < attempts; i++) {
			placeCarbonMonoxide(world, x, y, z);
		}
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

		int remaining = Math.max(1, (int) amount);
		int range = 4;
		for(int dy = -range; dy <= range && remaining > 0; dy++) {
			for(int dx = -range; dx <= range && remaining > 0; dx++) {
				for(int dz = -range; dz <= range && remaining > 0; dz++) {
					if(world.getBlock(x + dx, y + dy, z + dz) == ModBlocks.gas_monoxide) {
						world.setBlockToAir(x + dx, y + dy, z + dz);
						remaining--;
					}
				}
			}
		}
	}

	private static void placeCarbonMonoxide(World world, int x, int y, int z) {
		ForgeDirection start = ForgeDirection.getOrientation(world.rand.nextInt(ForgeDirection.VALID_DIRECTIONS.length));
		for(int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++) {
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[(start.ordinal() + i) % ForgeDirection.VALID_DIRECTIONS.length];
			int gasX = x + dir.offsetX;
			int gasY = y + dir.offsetY;
			int gasZ = z + dir.offsetZ;
			if(world.isAirBlock(gasX, gasY, gasZ)) {
				world.setBlock(gasX, gasY, gasZ, ModBlocks.gas_monoxide);
				return;
			}
		}
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
