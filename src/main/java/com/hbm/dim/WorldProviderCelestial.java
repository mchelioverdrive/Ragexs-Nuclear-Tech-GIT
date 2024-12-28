package com.hbm.dim;

import java.util.ArrayList;
import java.util.Random;

import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.dim.trait.CBT_Atmosphere.FluidEntry;
import com.hbm.dim.trait.CelestialBodyTrait.CBT_Destroyed;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.config.GeneralConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.util.WeightedRandomFishable;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.IRenderHandler;

public abstract class WorldProviderCelestial extends WorldProvider {

	private long localTime = -1;

	@Override
	public abstract void registerWorldChunkManager();

	// Ore gen will attempt to replace this block with ores
	public Block getStone() {
		return Blocks.stone;
	}

	public boolean hasLife() {
		return false;
	}

	public int getWaterOpacity() {
		return 3;
	}

	// Runs every tick, use it to decrement timers and run effects
	@Override
	public void updateWeather() {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
		if(atmosphere != null && atmosphere.getPressure() > 0.5F) {
			super.updateWeather();
			return;
		}

		this.worldObj.getWorldInfo().setRainTime(0);
		this.worldObj.getWorldInfo().setRaining(false);
		this.worldObj.getWorldInfo().setThunderTime(0);
		this.worldObj.getWorldInfo().setThundering(false);
		this.worldObj.rainingStrength = 0.0F;
		this.worldObj.thunderingStrength = 0.0F;

		//is this where atmospheres are handled?
		//if (event.item.getItem() instanceof ItemFood) {
		//	event.setCanceled(true);
		//	event.entityPlayer.addChatMessage(new ChatComponentText("You cannot eat here without an atmosphere!"));
		//}

	}

	// Can be overridden to provide fog changing events based on weather
	public float fogDensity() {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
		if(atmosphere == null) return 0;

		float pressure = (float)atmosphere.getPressure();

		if(pressure <= 2F) return 0;

		return pressure * pressure * 0.002F;
	}

	/**
	 * Read/write for weather data and anything else you wanna store that is per planet and not for every body
	 * the serialization function synchronizes weather data to the player
	 *
	 * also we don't need to mark the WorldSavedData as dirty because the world time is updated every tick and marks it as such
	 */
	public void writeToNBT(NBTTagCompound nbt) {

	}

	public void readFromNBT(NBTTagCompound nbt) {

	}

	public void serialize(ByteBuf buf) {
		buf.writeLong(getWorldTime());
	}

	public void deserialize(ByteBuf buf) {
		long time = buf.readLong();

		// Allow a half second desync for smoothness
		if(Math.abs(time - getWorldTime()) > 10) {
			setWorldTime(time);
		}
	}


	/**
	 * Override to modify the lightmap, return true if the lightmap is actually modified
	 * @param lightmap a 16x16 lightmap stored in a 256 value buffer
	 * @return whether or not the dynamic lightmap texture needs to be updated
	 */
	public boolean updateLightmap(int[] lightmap) {
		return false;
	}

	protected final int packColor(final int[] colors) {
		return packColor(colors[0], colors[1], colors[2]);
	}

	protected final int packColor(final int r, final int g, final int b) {
		return 255 << 24 | r << 16 | g << 8 | b;
	}

	protected final int[] unpackColor(final int color) {
		final int[] colors = new int[3];
		colors[0] = color >> 16 & 255;
		colors[1] = color >> 8 & 255;
		colors[2] = color & 255;
		return colors;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getFogColor(float celestialAngle, float y) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);

		// The cold hard vacuum of space
		if(atmosphere == null) return Vec3.createVectorHelper(0, 0, 0);

		float sun = MathHelper.clamp_float(MathHelper.cos(celestialAngle * (float)Math.PI * 2.0F) * 2.0F + 0.5F, 0.0F, 1.0F);

		float sunR = sun;
		float sunG = sun;
		float sunB = sun;

		if(!GeneralConfig.enableHardcoreDarkness) {
			sunR *= 0.94F;
			sunG *= 0.94F;
			sunB *= 0.91F;
		}
		float totalPressure = (float)atmosphere.getPressure();
		Vec3 color = Vec3.createVectorHelper(0, 0, 0);

		for(int i = 0; i < atmosphere.fluids.size(); i++) {
			FluidEntry entry = atmosphere.fluids.get(i);
			Vec3 fluidColor;

			if(entry.fluid == Fluids.EVEAIR) {
				fluidColor = Vec3.createVectorHelper(53F / 255F * sunR, 32F / 255F * sunG, 74F / 255F * sunB);
			} else if(entry.fluid == Fluids.DUNAAIR || entry.fluid == Fluids.CARBONDIOXIDE) {
				fluidColor = Vec3.createVectorHelper(212F / 255F * sunR, 112F / 255F * sunG, 78F / 255F * sunB);
			} else if(entry.fluid == Fluids.AIR || entry.fluid == Fluids.OXYGEN || entry.fluid == Fluids.NITROGEN) {
				// Default to regular ol' overworld
				fluidColor = Vec3.createVectorHelper(0.7529412F * sunR, 0.84705883F * sunG, 1.0F * sunB);
			} else {
				fluidColor = getColorFromHex(entry.fluid.getColor());
				fluidColor.xCoord *= sunR * 1.4F;
				fluidColor.yCoord *= sunG * 1.4F;
				fluidColor.zCoord *= sunB * 1.4F;
			}

			float percentage = (float)entry.pressure / totalPressure;
			color = Vec3.createVectorHelper(
				color.xCoord + fluidColor.xCoord * percentage,
				color.yCoord + fluidColor.yCoord * percentage,
				color.zCoord + fluidColor.zCoord * percentage
			);
		}

		// Add minimum fog colour, for night-time glow
		if(!GeneralConfig.enableHardcoreDarkness) {
			float nightDensity = MathHelper.clamp_float(totalPressure, 0.0F, 1.0F);
			color.xCoord += 0.06F * nightDensity;
			color.yCoord += 0.06F * nightDensity;
			color.zCoord += 0.09F * nightDensity;
		}


		// Fog intensity remains high to simulate a thin looking atmosphere on low pressure planets
		float pressureFactor = MathHelper.clamp_float(totalPressure * 10.0F, 0.0F, 1.0F);
		color.xCoord *= pressureFactor;
		color.yCoord *= pressureFactor;
		color.zCoord *= pressureFactor;

		if(Minecraft.getMinecraft().renderViewEntity.posY > 600) {
			double curvature = MathHelper.clamp_float((1000.0F - (float)Minecraft.getMinecraft().renderViewEntity.posY) / 400.0F, 0.0F, 1.0F);
			color.xCoord *= curvature;
			color.zCoord *= curvature;
			color.yCoord *= curvature;
		}

		return color;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getSkyColor(Entity camera, float partialTicks) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);

		// The cold hard vacuum of space
		if(atmosphere == null) return Vec3.createVectorHelper(0, 0, 0);

		float sun = this.getSunBrightnessFactor(1.0F);
		float totalPressure = (float)atmosphere.getPressure();
		Vec3 color = Vec3.createVectorHelper(0, 0, 0);

		for(int i = 0; i < atmosphere.fluids.size(); i++) {
			FluidEntry entry = atmosphere.fluids.get(i);
			Vec3 fluidColor;

			if(entry.fluid == Fluids.EVEAIR) {
				fluidColor = Vec3.createVectorHelper(53F / 255F * sun, 32F / 255F * sun, 74F / 255F * sun);
			} else if(entry.fluid == Fluids.DUNAAIR || entry.fluid == Fluids.CARBONDIOXIDE) {
				fluidColor = Vec3.createVectorHelper(212F / 255F * sun, 112F / 255F * sun, 78F / 255F * sun);
			} else if(entry.fluid == Fluids.AIR || entry.fluid == Fluids.OXYGEN || entry.fluid == Fluids.NITROGEN) {
				// Default to regular ol' overworld
				fluidColor = super.getSkyColor(camera, partialTicks);
			} else {
				fluidColor = getColorFromHex(entry.fluid.getColor());
				fluidColor.xCoord *= sun;
				fluidColor.yCoord *= sun;
				fluidColor.zCoord *= sun;
			}

			float percentage = (float)entry.pressure / totalPressure;
			color = Vec3.createVectorHelper(
				color.xCoord + fluidColor.xCoord * percentage,
				color.yCoord + fluidColor.yCoord * percentage,
				color.zCoord + fluidColor.zCoord * percentage
			);
		}

		// Lower pressure sky renders thinner
		float pressureFactor = MathHelper.clamp_float(totalPressure, 0.0F, 1.0F);
		color.xCoord *= pressureFactor;
		color.yCoord *= pressureFactor;
		color.zCoord *= pressureFactor;

		return color;
	}

	private Vec3 getColorFromHex(int hexColor) {
		float red = ((hexColor >> 16) & 0xFF) / 255.0F;
		float green = ((hexColor >> 8) & 0xFF) / 255.0F;
		float blue = (hexColor & 0xFF) / 255.0F;
		return Vec3.createVectorHelper(red, green, blue);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
		if(atmosphere == null || atmosphere.getPressure() < 0.05F) return null;

		float[] colors = super.calcSunriseSunsetColors(celestialAngle, partialTicks);
		if(colors == null) return null;

		// Mars IRL has inverted blue sunsets, which look cool as
		// So carbon dioxide rich atmospheres will do the same
		// for now, it's just a swizzle between red and blue
		if(atmosphere.hasFluid(Fluids.DUNAAIR) || atmosphere.hasFluid(Fluids.CARBONDIOXIDE)) {
			float tmp = colors[0];
			colors[0] = colors[2];
			colors[2] = tmp;
		} else if (atmosphere.hasFluid(Fluids.EVEAIR)) {
			float f2 = 0.4F;
			float f3 = MathHelper.cos((celestialAngle) * (float)Math.PI * 2.0F) - 0.0F;
			float f4 = -0.0F;

			if (f3 >= f4 - f2 && f3 <= f4 + f2) {
				float f5 = (f3 - f4) / f2 * 0.5F + 0.5F;
				float f6 = 1.0F - (1.0F - MathHelper.sin(f5 * (float)Math.PI)) * 0.99F;
				f6 *= f6;
				colors[0] = f5 * 0.01F;
				colors[1] = f5 * f5 * 0.9F + 0.3F;
				colors[2] = f5 * f5;
				colors[3] = f6;
			}
		}

		return colors;
	}

	@Override
	public boolean canDoLightning(Chunk chunk) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);

		if(atmosphere != null && atmosphere.getPressure() > 0.2)
			return super.canDoLightning(chunk);

		return false;
	}

	@Override
	public boolean canDoRainSnowIce(Chunk chunk) {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);

		if(atmosphere != null && atmosphere.getPressure() > 0.2)
			return super.canDoRainSnowIce(chunk);

		return false;
	}

	// Stars do not show up during the day in a vacuum, common misconception:
	// The reason stars aren't visible during the day on Earth isn't because of the sky,
	// the sky is ALWAYS there. The reason they aren't visible is because the Sun is too bright!
	@Override
	@SideOnly(Side.CLIENT)
	public float getStarBrightness(float par1) {
		// Stars become visible during the day beyond the orbit of Duna
		// And are fully visible during the day beyond the orbit of Jool
		float distanceStart = 20_000_000;
		float distanceEnd = 80_000_000;

		float semiMajorAxisKm = CelestialBody.getPlanet(worldObj).semiMajorAxisKm;
		float distanceFactor = MathHelper.clamp_float((semiMajorAxisKm - distanceStart) / (distanceEnd - distanceStart), 0F, 1F);

		float starBrightness = super.getStarBrightness(par1);

		return MathHelper.clamp_float(starBrightness, distanceFactor, 1F);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getSunBrightness(float par1) {
		if(CelestialBody.getStar(worldObj).hasTrait(CBT_Destroyed.class))
			return 0;

		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);
		float sunBrightness = super.getSunBrightness(par1);

		if(atmosphere == null) return sunBrightness;

		return sunBrightness * MathHelper.clamp_float(1.0F - ((float)atmosphere.getPressure() - 1.5F) * 0.2F, 0.25F, 1.0F);
	}

	@Override
	public int getRespawnDimension(EntityPlayerMP player) {
		ChunkCoordinates coords = player.getBedLocation(dimensionId);

		// If no bed, respawn in overworld
		if(coords == null)
			return 0;

		// If the bed location has no breathable atmosphere, respawn in overworld
		CBT_Atmosphere atmosphere = ChunkAtmosphereManager.proxy.getAtmosphere(worldObj, coords.posX, coords.posY, coords.posZ);
		if(!ChunkAtmosphereManager.proxy.canBreathe(atmosphere))
			return 0;

		return dimensionId;
	}

	// We want spawning to check for breathable, and getRespawnDimension() only runs if this is FALSE
	// BUT this also makes beds blow up (Mojang I swear), so we hook into the sleep event and set a flag
	public static boolean attemptingSleep = false;

	@Override
	public boolean canRespawnHere() {
		if(attemptingSleep) {
			attemptingSleep = false;
			return true;
		}

		return false;
	}

	// Another AWFULLY named deobfuscation function, this one is called when players have all slept,
	// which means we can set the time of day to local morning safely here!
	@Override
	public void resetRainAndThunder() {
		super.resetRainAndThunder();

		if(dimensionId == 0) return;
		if(!worldObj.getGameRules().getGameRuleBooleanValue("doDaylightCycle")) return;

		long dayLength = (long)getDayLength();
		long i = getWorldTime() % dayLength;
		setWorldTime(i - i % dayLength);
	}

	@Override
	public long getWorldTime() {
		if(dimensionId == 0) {
			return super.getWorldTime();
		}

		if(!worldObj.isRemote) {
			localTime = CelestialBodyWorldSavedData.get(this).getLocalTime();
		}

		return localTime;
	}

	@Override
	public void setWorldTime(long time) {
		if(dimensionId == 0) {
			super.setWorldTime(time);
			return;
		}

		if(!worldObj.isRemote) {
			CelestialBodyWorldSavedData.get(this).setLocalTime(time);
		}

		localTime = time;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getCloudHeight() {
		CBT_Atmosphere atmosphere = CelestialBody.getTrait(worldObj, CBT_Atmosphere.class);

		if(atmosphere == null || atmosphere.getPressure() < 0.5F) return -99999;

		return super.getCloudHeight();
	}
	private IRenderHandler skyProvider;


	@Override
	@SideOnly(Side.CLIENT)
	public IRenderHandler getSkyRenderer() {
		// I do not condone this because it WILL confuse your players, but if you absolutely must,
		// you can uncomment this line below in your fork to get default skybox rendering on Earth.

		// if(dimensionId == 0) return super.getSkyRenderer();

		// Make sure you also uncomment the relevant line in getMoonPhase below too.

		// This is not in a config because it is not a decision you should make lightly, as it will break:
		//  * certain atmosphere/terraforming modifications
		//  * Dyson swarm rendering
		//  * seeing weapons platforms in orbit (the big cannon from the trailer will NOT be visible)
		//  * weapon effects on the atmosphere (burning holes in the atmosphere, hitting planetary defense shields)
		//  * accurate celestial body rendering (you won't be able to see ANY other planets)
		//     * this also breaks future plans to modify orbits via huge mass drivers, if someone decides to yeet the moon at you, you won't know
		//  * sun extinction/modification events (the sun will appear normal even if it has been turned into a black hole)
		//  * player launched satellites won't be visible
		//  * artificial moons/rings (once implemented) won't be visible

		if(skyProvider == null) skyProvider = new SkyProviderCelestial();
		return skyProvider;
	}

	protected double getDayLength() {
		CelestialBody body = CelestialBody.getBody(worldObj);
		return body.getRotationalPeriod() / (1 - (1 / body.getPlanet().getOrbitalPeriod()));
	}

	@Override
	public float calculateCelestialAngle(long worldTime, float partialTicks) {
		worldTime = getWorldTime(); // the worldtime passed in is from the fucking overworld
		double dayLength = getDayLength();
		double j = worldTime % dayLength;
		double f1 = (j + partialTicks) / dayLength - 0.25F;

		if(f1 < 0.0F) {
			++f1;
		}

		if(f1 > 1.0F) {
			--f1;
		}

		double f2 = f1;
		f1 = 0.5F - Math.cos(f1 * Math.PI) / 2.0F;
		return (float)(f2 + (f1 - f2) / 3.0D);
	}

	@Override
	public int getMoonPhase(long worldTime) {
		// Uncomment this line as well to return moon phase difficulty calcs to vanilla
		// if(dimensionId == 0) return super.getMoonPhase(worldTime);

		CelestialBody body = CelestialBody.getBody(worldObj);

		// if no moons, default to half-moon difficulty
		if(body.satellites.size() == 0) return 2;

		// Determine difficulty phase from closest moon
		int phase = Math.round(8 - ((float)SolarSystem.calculateSingleAngle(worldObj, 0, body, body.satellites.get(0)) / 45 + 4));
		if(phase >= 8) return 0;
		return phase;
	}

	// This is the vanilla junk table, for replacing fish on dead worlds
	private static ArrayList<WeightedRandomFishable> junk;

	// you know what that means
	/// FISH ///

	// returning null from any of these methods will revert to overworld loot tables
	public ArrayList<WeightedRandomFishable> getFish() {
		if(junk == null) {
			junk = new ArrayList<>();
			// junk.add((new WeightedRandomFishable(new ItemStack(Items.leather_boots), 10)).func_150709_a(0.9F));
			// junk.add(new WeightedRandomFishable(new ItemStack(Items.leather), 10));
			// junk.add(new WeightedRandomFishable(new ItemStack(Items.bone), 10));
			junk.add(new WeightedRandomFishable(new ItemStack(Items.potionitem), 10));
			junk.add(new WeightedRandomFishable(new ItemStack(Items.string), 5));
			junk.add((new WeightedRandomFishable(new ItemStack(Items.fishing_rod), 2)).func_150709_a(0.9F));
			junk.add(new WeightedRandomFishable(new ItemStack(Items.bowl), 10));
			junk.add(new WeightedRandomFishable(new ItemStack(Items.stick), 5));
			junk.add(new WeightedRandomFishable(new ItemStack(Items.dye, 10, 0), 1));
			junk.add(new WeightedRandomFishable(new ItemStack(Blocks.tripwire_hook), 10));
			// junk.add(new WeightedRandomFishable(new ItemStack(Items.rotten_flesh), 10));
		}

		return junk;
	}

	public ArrayList<WeightedRandomFishable> getJunk() {
		return null;
	}

	public ArrayList<WeightedRandomFishable> getTreasure() {
		return null;
	}
	/// FISH ///

	//public abstract void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity);
//
	//public abstract void updateTick(World world, int x, int y, int z, Random rand);

}
