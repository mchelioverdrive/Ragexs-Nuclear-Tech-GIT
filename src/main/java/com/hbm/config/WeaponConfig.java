package com.hbm.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class WeaponConfig {

	public static int radarRange = 1000;
	public static int radarBuffer = 30;
	public static int radarAltitude = 55;
	public static int ciwsHitrate = 50;

	public static boolean dropCell = false;
	public static boolean dropSing = false;
	public static boolean dropStar = false;
	public static boolean dropCrys = false;
	public static boolean dropDead = true;

	public static boolean linearAnimations = false;

	public static void loadFromConfig(Configuration config) {

		final String CATEGORY_MISSILE = CommonConfig.CATEGORY_MISSILE;
		Property propRadarRange = config.get(CATEGORY_MISSILE, "7.00_radarRange", 1000);
		propRadarRange.comment = "Range of the radar, 50 will result in 100x100 block area covered";
		radarRange = propRadarRange.getInt();
		Property propRadarBuffer = config.get(CATEGORY_MISSILE, "7.01_radarBuffer", 30);
		propRadarBuffer.comment = "How high entities have to be above the radar to be detected";
		radarBuffer = propRadarBuffer.getInt();
		Property propRadarAltitude = config.get(CATEGORY_MISSILE, "7.02_radarAltitude", 55);
		propRadarAltitude.comment = "Y height required for the radar to work";
		radarAltitude = propRadarAltitude.getInt();
		Property propCiwsHitrate = config.get(CATEGORY_MISSILE, "7.03_ciwsAccuracy", 50);
		propCiwsHitrate.comment = "Additional modifier for CIWS accuracy";
		ciwsHitrate = propRadarAltitude.getInt();

		final String CATEGORY_DROPS = CommonConfig.CATEGORY_DROPS;
		dropCell = CommonConfig.createConfigBool(config, CATEGORY_DROPS, "10.00_dropCell", "Whether antimatter cells should explode when dropped", false);
		dropSing = CommonConfig.createConfigBool(config, CATEGORY_DROPS, "10.01_dropBHole", "Whether singularities and black holes should spawn when dropped", false);
		dropStar = CommonConfig.createConfigBool(config, CATEGORY_DROPS, "10.02_dropStar", "Whether rigged star blaster cells should explode when dropped", false);
		dropCrys = CommonConfig.createConfigBool(config, CATEGORY_DROPS, "10.04_dropCrys", "Whether xen crystals should move blocks when dropped", false);
		dropDead = CommonConfig.createConfigBool(config, CATEGORY_DROPS, "10.05_dropDead", "Whether dead man's explosives should explode when dropped", true);

		final String CATEGORY_WEAPONS = CommonConfig.CATEGORY_WEAPONS;
		linearAnimations = CommonConfig.createConfigBool(config, CATEGORY_WEAPONS, "18.00_linearAnimations", "Should heavily stylised weapon animations be replaced with more conventional ones?", false);

	}
}
