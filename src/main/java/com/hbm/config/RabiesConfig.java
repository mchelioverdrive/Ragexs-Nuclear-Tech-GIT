package com.hbm.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class RabiesConfig {

	public static final String CATEGORY = "20_rabies";
	public static boolean enabled = true;
	public static double batchance = 0.08D, dogchance = 0.08D;
	public static double batToPlayer = 0.08D, batToMammal = 0.08D;
	public static double wolfToPlayer = 0.08D, wolfToWolf = 0.08D, wolfToMammal = 0.08D;
	public static double mammalToPlayer = 0.08D, mammalToMammal = 0.08D;
	public static double furiousChance = 0.5D;
	public static int incubationTicks = 48000, infectiousTicks = 1200, clinicalTicks = 2400, terminalTicks = 1200;
	public static final Set<String> mammalAllowlist = new HashSet<String>();

	public static void loadFromConfig(Configuration config) {
		config.setCategoryComment(CATEGORY, "Rabies: viral mammal infection transmitted by wounding bites, never proximity. "
				+ "Probabilities and compressed durations are gameplay defaults, not measured biological rates. "
				+ "20 ticks = 1 second at 20 TPS; 24000 ticks = 1 Minecraft day. Restart to apply changes.");
		enabled = CommonConfig.createConfigBool(config, CATEGORY, "enabled", "Enable rabies progression, reservoir seeding and transmission. Disabled infections remain saved.", true);
		// Spawn prevalence and per-bite establishment are independent gameplay probabilities.
		batchance = probability(config, "batchance", 0.08D, "One roll when a bat first enters the simulation: existing infection entering the area. 0.08 means eight percent.");
		dogchance = probability(config, "dogchance", 0.08D, "Per qualifying infectious bat bite against a wolf (wild or tamed). 0.08 means eight percent; not a spawn chance.");
		batToPlayer = probability(config, "batToPlayer", 0.08D, "Per qualifying infectious bat bite against a player.");
		batToMammal = probability(config, "batToOtherMammal", 0.08D, "Per qualifying infectious bat bite against another supported mammal, including bats.");
		wolfToPlayer = probability(config, "wolfToPlayer", 0.08D, "Per qualifying infectious wolf bite against a player.");
		wolfToWolf = probability(config, "wolfToWolf", 0.08D, "Per qualifying infectious wolf bite against another wolf.");
		wolfToMammal = probability(config, "wolfToOtherMammal", 0.08D, "Per qualifying infectious wolf bite against another supported mammal, including bats.");
		mammalToPlayer = probability(config, "otherMammalToPlayer", 0.08D, "Per explicit disease bite from another supported mammal against a player. Ordinary kicks and player attacks are not bites.");
		mammalToMammal = probability(config, "otherMammalToMammal", 0.08D, "Per explicit disease bite between other supported host pairs. Does not confer reservoir status.");
		furiousChance = probability(config, "furiousChance", 0.5D, "Presentation selected once per established infection; otherwise paralytic. Gameplay split, not biological frequency.");
		// Real incubation often lasts weeks/months: deliberately compress time for gameplay.
		incubationTicks = duration(config, "incubationTicks", 48000, "Silent, noninfectious incubation.", 1);
		infectiousTicks = duration(config, "presymptomaticInfectiousTicks", 1200, "Silent saliva shedding before symptoms; shedding continues throughout clinical and terminal illness. Same-host timing is a cross-species gameplay abstraction.", 0);
		clinicalTicks = duration(config, "clinicalTicks", 2400, "First half: behavioral signs; second half: neurological impairment.", 2);
		terminalTicks = duration(config, "terminalTicks", 1200, "Terminal paralysis/illness, followed by fatal disease damage.", 1);
		Property hosts = config.get(CATEGORY, "mammalAllowlist", new String[0]);
		hosts.comment = "Additional exact EntityList registry names of living mammals from other mods. Operator asserts mammalian biology; no class-wide EntityLivingBase rule. Does not seed reservoir infections. Undead, arthropods, chickens, aquatic mobs and golems remain excluded.";
		mammalAllowlist.clear();
		mammalAllowlist.addAll(Arrays.asList(hosts.getStringList()));
	}

	private static double probability(Configuration config, String key, double fallback, String scope) {
		Property property = config.get(CATEGORY, key, fallback);
		property.comment = scope + " Gameplay probability in inclusive range [0, 1], independent of attack damage. Invalid values reset to default.";
		property.setMinValue(0D).setMaxValue(1D);
		double value = property.getDouble(fallback);
		if(Double.isNaN(value) || Double.isInfinite(value) || value < 0D || value > 1D) {
			value = fallback;
			property.set(value);
		}
		return value;
	}

	private static int duration(Configuration config, String key, int fallback, String scope, int min) {
		Property property = config.get(CATEGORY, key, fallback);
		property.comment = scope + " Compressed Minecraft ticks (20 ticks/second at 20 TPS); saved infections retain their original timeline.";
		property.setMinValue(min).setMaxValue(Integer.MAX_VALUE);
		int value = property.getInt(fallback);
		if(value < min) {
			value = fallback;
			property.set(value);
		}
		return value;
	}
}
