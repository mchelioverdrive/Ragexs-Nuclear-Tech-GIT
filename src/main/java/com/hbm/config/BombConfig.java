package com.hbm.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class BombConfig {

	public static final float KT_RADIUS_BASE_YIELD = 15F;
	public static final float KT_RADIUS_BASE_RADIUS = 48F;

	// Nuclear radii are balanced from kiloton yield by cube-root destructiveness.
	public static int gadgetRadius = radiusFromKt(22F);
	public static int boyRadius = radiusFromKt(15F);
	public static int manRadius = radiusFromKt(21F);
	public static int mikeRadius = radiusFromKt(10_400F);
	public static int shrimpRadius = radiusFromKt(15_000F);
	public static int tsarlegitrad = radiusFromKt(50_000F);
	public static int tsarRadius = radiusFromKt(100_000F);
	//note: most fake bullshit anime bronie nukes are FUCKING REMOVED FROM THIS FORK. GET THAT SHIT OUT OF THE NUCLEAR TECH MOD
	public static int prototypeRadius = 150;
	public static int fleijaRadius = 50;
	public static int soliniumRadius = 150;


	//ammonium nitrate drum, was heckin evangelon unemployed thing, soon to be just literally an ammonium nitrate canister/ beirut explosion.
	public static int n2Radius = 25;

	public static int missileRadius = radiusFromKt(100F);
	public static int mirvRadius = radiusFromKt(150F);

	public static int fatmanRadius = radiusFromKt(0.25F);
	public static int nukaRadius = radiusFromKt(0.05F);
	public static int aSchrabRadius = 20;

	public static int mk5 = 50;
	public static int blastSpeed = 1024;
	public static int falloutRange = 100;
	public static int fDelay = 4;
	public static int limitExplosionLifespan = 0;
	public static int rain = 0;
	public static int cont = 0;
	public static boolean chunkloading = true;

	public static int radiusFromKt(float kilotons) {
		return Math.max(1, Math.round(KT_RADIUS_BASE_RADIUS * (float) Math.cbrt(kilotons / KT_RADIUS_BASE_YIELD)));
	}

	public static float ktFromRadius(float radius) {
		return KT_RADIUS_BASE_YIELD * (float) Math.pow(radius / KT_RADIUS_BASE_RADIUS, 3);
	}

	public static void loadFromConfig(Configuration config) {

		final String CATEGORY_NUKES = CommonConfig.CATEGORY_NUKES;
		Property propGadget = config.get(CATEGORY_NUKES, "3.00_gadgetRadius", radiusFromKt(22F));
		propGadget.comment = "Radius of the Gadget";
		gadgetRadius = propGadget.getInt();
		Property propBoy = config.get(CATEGORY_NUKES, "3.01_boyRadius", radiusFromKt(15F));
		propBoy.comment = "Radius of Little Boy";
		boyRadius = propBoy.getInt();
		Property propMan = config.get(CATEGORY_NUKES, "3.02_manRadius", radiusFromKt(21F));
		propMan.comment = "Radius of Fat Man";
		manRadius = propMan.getInt();
		Property propMike = config.get(CATEGORY_NUKES, "3.03_mikeRadius", radiusFromKt(10_400F));
		propMike.comment = "Radius of Ivy Mike";
		mikeRadius = propMike.getInt();
		Property propTsar = config.get(CATEGORY_NUKES, "3.04_tsarRadius", radiusFromKt(100_000F));
		propTsar.comment = "Radius of the FULL Tsar Bomba";
		tsarRadius = propTsar.getInt();



		Property propPrototype = config.get(CATEGORY_NUKES, "3.05_prototypeRadius", 150);
		propPrototype.comment = "Radius of the Prototype";
		prototypeRadius = propPrototype.getInt();
		Property propFleija = config.get(CATEGORY_NUKES, "3.06_fleijaRadius", 50);
		propFleija.comment = "Radius of F.L.E.I.J.A.";
		fleijaRadius = propFleija.getInt();
		Property propMissile = config.get(CATEGORY_NUKES, "3.07_missileRadius", radiusFromKt(100F));
		propMissile.comment = "Radius of the nuclear missile";
		missileRadius = propMissile.getInt();
		Property propMirv = config.get(CATEGORY_NUKES, "3.08_mirvRadius", radiusFromKt(150F));
		propMirv.comment = "Radius of a MIRV";
		mirvRadius = propMirv.getInt();
		Property propFatman = config.get(CATEGORY_NUKES, "3.09_fatmanRadius", radiusFromKt(0.25F));
		propFatman.comment = "Radius of the Fatman Launcher";
		fatmanRadius = propFatman.getInt();
		Property propNuka = config.get(CATEGORY_NUKES, "3.10_nukaRadius", radiusFromKt(0.05F));
		propNuka.comment = "Radius of the nuka grenade";
		nukaRadius = propNuka.getInt();
		Property propASchrab = config.get(CATEGORY_NUKES, "3.11_aSchrabRadius", 20);
		propASchrab.comment = "Radius of dropped anti schrabidium";
		aSchrabRadius = propASchrab.getInt();
		Property propSolinium = config.get(CATEGORY_NUKES, "3.12_soliniumRadius", 150);
		propSolinium.comment = "Radius of the blue rinse";
		soliniumRadius = propSolinium.getInt();
		Property propN2 = config.get(CATEGORY_NUKES, "3.13_n2Radius", 25);
		propN2.comment = "Radius of the Ammonium Nitrate Bomb";
		n2Radius = propN2.getInt();

		//FUN
		Property propTsar2 = config.get(CATEGORY_NUKES, "3.14_tsarlegitRadius", radiusFromKt(50_000F));
		propTsar2.comment = "Radius of the 50MT Tsar Bomba";
		tsarlegitrad = propTsar2.getInt();

		final String CATEGORY_NUKE = CommonConfig.CATEGORY_EXPLOSIONS;
		Property propLimitExplosionLifespan = config.get(CATEGORY_NUKE, "6.00_limitExplosionLifespan", 0);
		propLimitExplosionLifespan.comment = "How long an explosion can be unloaded until it dies in seconds. Based of system time. 0 disables the effect";
		limitExplosionLifespan = propLimitExplosionLifespan.getInt();
		// explosion speed
		Property propBlastSpeed = config.get(CATEGORY_NUKE, "6.01_blastSpeed", 1024);
		propBlastSpeed.comment = "Base speed of MK3 system (old and schrabidium) detonations (Blocks / tick)";
		blastSpeed = propBlastSpeed.getInt();
		// new explosion speed
		Property propFalloutRange = config.get(CATEGORY_NUKE, "6.02_mk5BlastTime", 50);
		propFalloutRange.comment = "Minimum amount of milliseconds per tick allocated for mk5 chunk processing";
		mk5 = propFalloutRange.getInt();
		// fallout range
		Property falloutRangeProp = config.get(CATEGORY_NUKE, "6.03_falloutRange", 100);
		falloutRangeProp.comment = "Radius of fallout area (base radius * value in percent)";
		falloutRange = falloutRangeProp.getInt();
		Property falloutDelayProp = config.get(CATEGORY_NUKE, "6.04_falloutDelay", 4);
		falloutDelayProp.comment = "How many ticks to wait for the next fallout chunk computation";
		fDelay = falloutDelayProp.getInt();

		Property radRain = config.get(CATEGORY_NUKE, "6.05_falloutRainDuration", 0);
		radRain.comment = "Duration of the thunderstorm after fallout in ticks (only large explosions)";
		rain = radRain.getInt();
		Property rainCont = config.get(CATEGORY_NUKE, "6.06_falloutRainRadiation", 0);
		rainCont.comment = "Radiation in 100th RADs created by fallout rain";
		cont = rainCont.getInt();

		chunkloading = CommonConfig.createConfigBool(config, CATEGORY_NUKE, "6.XX_enableChunkLoading", "Allows all types of procedural explosions to keep the central chunk loaded.", true);
	}
}
