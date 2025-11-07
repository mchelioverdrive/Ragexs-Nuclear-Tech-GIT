package com.hbm.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class BombConfig {

	//my current formula is just divide by 2 and that's the full crater radius in meters
	public static int gadgetRadius = 53;
	//remathed
	public static int boyRadius = 48;
	public static int manRadius = 53;
	public static int mikeRadius = 418;
	//should be 800 or 1,450 (larger est) bc apparently the radius is fucking 2900 and we div by 2 because bob has autism
	//literal server ending event above apparently mike was just so juicy and fragrant he gerald sigma maxxed servers
	public static int shrimpRadius = 475;
	//I don't even care anymore enjoy your 1400m crater game
	public static int tsarlegitrad = 708;
	//was used irl
	public static int tsarRadius = 893;
	//note: most fake bullshit anime bronie nukes are FUCKING REMOVED FROM THIS FORK. GET THAT SHIT OUT OF THE NUCLEAR TECH MOD
	public static int prototypeRadius = 150;
	public static int fleijaRadius = 50;
	public static int soliniumRadius = 150;


	//ammonium nitrate drum, was heckin evangelon unemployed thing, soon to be just literally an ammonium nitrate canister/ beirut explosion.
	public static int n2Radius = 25;

	public static int missileRadius = 90;
	//150kt 'generic?' nuke
	public static int mirvRadius = 90;

	//duplicate for some reason, probably not needed given I destroyed every last gun from this mod
	//but eh I'm sure it might be used somewhere important.
	public static int fatmanRadius = 53;
	public static int nukaRadius = 25;
	public static int aSchrabRadius = 20;

	public static int mk5 = 50;
	public static int blastSpeed = 1024;
	public static int falloutRange = 100;
	public static int fDelay = 4;
	public static int limitExplosionLifespan = 0;
	public static int rain = 0;
	public static int cont = 0;
	public static boolean chunkloading = true;

	public static void loadFromConfig(Configuration config) {

		final String CATEGORY_NUKES = CommonConfig.CATEGORY_NUKES;
		Property propGadget = config.get(CATEGORY_NUKES, "3.00_gadgetRadius", 53);
		propGadget.comment = "Radius of the Gadget";
		gadgetRadius = propGadget.getInt();
		Property propBoy = config.get(CATEGORY_NUKES, "3.01_boyRadius", 48);
		propBoy.comment = "Radius of Little Boy";
		boyRadius = propBoy.getInt();
		Property propMan = config.get(CATEGORY_NUKES, "3.02_manRadius", 53);
		propMan.comment = "Radius of Fat Man";
		manRadius = propMan.getInt();
		Property propMike = config.get(CATEGORY_NUKES, "3.03_mikeRadius", 418);
		propMike.comment = "Radius of Ivy Mike";
		mikeRadius = propMike.getInt();
		Property propTsar = config.get(CATEGORY_NUKES, "3.04_tsarRadius", 893);
		propTsar.comment = "Radius of the FULL Tsar Bomba";
		tsarRadius = propTsar.getInt();



		Property propPrototype = config.get(CATEGORY_NUKES, "3.05_prototypeRadius", 150);
		propPrototype.comment = "Radius of the Prototype";
		prototypeRadius = propPrototype.getInt();
		Property propFleija = config.get(CATEGORY_NUKES, "3.06_fleijaRadius", 50);
		propFleija.comment = "Radius of F.L.E.I.J.A.";
		fleijaRadius = propFleija.getInt();
		Property propMissile = config.get(CATEGORY_NUKES, "3.07_missileRadius", 90);
		propMissile.comment = "Radius of the nuclear missile";
		missileRadius = propMissile.getInt();
		Property propMirv = config.get(CATEGORY_NUKES, "3.08_mirvRadius", 90);
		propMirv.comment = "Radius of a MIRV";
		mirvRadius = propMirv.getInt();
		Property propFatman = config.get(CATEGORY_NUKES, "3.09_fatmanRadius", 53);
		propFatman.comment = "Radius of the Fatman Launcher";
		fatmanRadius = propFatman.getInt();
		Property propNuka = config.get(CATEGORY_NUKES, "3.10_nukaRadius", 25);
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
		Property propTsar2 = config.get(CATEGORY_NUKES, "3.14_tsarlegitRadius", 708);
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
