package com.hbm.config;

import java.util.Locale;

import com.hbm.main.MainRegistry;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public class CommonConfig {

	/** Read an isolated candidate. Forge's normal constructor renames malformed files and
	 * its getters silently replace bad numbers; neither is appropriate for an admin reload. */
	public static Configuration readForReload(java.io.File file, Configuration schema) throws java.io.IOException {
		byte[] contents = java.nio.file.Files.readAllBytes(file.toPath());
		int expected = checkReloadStructure(contents);
		ReloadConfiguration candidate = new ReloadConfiguration(file);
		candidate.read();
		int actual = 0;
		for(String category : candidate.getCategoryNames()) {
			for(Property property : candidate.getCategory(category).values()) {
				actual++;
				if(!schema.hasKey(category, property.getName())) continue;
				Property original = schema.getCategory(category).get(property.getName());
				if(property.getType() != original.getType() || property.isList() != original.isList())
					throw new IllegalArgumentException(category + "." + property.getName() + ": incorrect property type");
				String[] values = property.isList() ? property.getStringList() : new String[] {property.getString()};
				for(String value : values) {
					try {
						if(original.getType() == Property.Type.INTEGER) Integer.parseInt(value);
						// Gemini's STRING settings are getFloat values; Forge also gives
						// ordinary text properties numeric bounds, so bounds cannot identify them.
						if(original.getType() == Property.Type.DOUBLE
								|| original.getType() == Property.Type.STRING && category.startsWith(GeminiConfig.CATEGORY)) {
							double number = Double.parseDouble(value);
							if(Double.isNaN(number) || Double.isInfinite(number))
								throw new NumberFormatException("non-finite number");
						}
						if(original.getType() == Property.Type.BOOLEAN && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
							throw new IllegalArgumentException("expected true or false");
					} catch(IllegalArgumentException ex) {
						throw new IllegalArgumentException(category + "." + property.getName() + ": invalid value '" + value + "'", ex);
					}
				}
			}
		}
		// Detect swallowed Forge I/O errors, incomplete reads and duplicate property definitions.
		if(actual != expected || !java.util.Arrays.equals(contents, java.nio.file.Files.readAllBytes(file.toPath())))
			throw new java.io.IOException("Configuration was incomplete, duplicated, or changed while reading; retry after saving the file.");
		return candidate;
	}

	private static final class ReloadConfiguration extends Configuration {
		ReloadConfiguration(java.io.File file) { super(file); }
		@Override public void load() { } // Suppress constructor recovery/renaming; explicitly parse below.
		void read() { super.load(); }
	}

	/** Supplement Forge's parser: it does not reject unclosed categories/lists at EOF. */
	private static int checkReloadStructure(byte[] contents) throws java.io.IOException {
		int depth = 0, properties = 0;
		boolean list = false;
		try(java.io.BufferedReader reader = new java.io.BufferedReader(new Configuration.UnicodeInputStreamReader(
				new java.io.ByteArrayInputStream(contents), "UTF-8"))) {
			String line;
			while((line = reader.readLine()) != null) {
				if(list) { if(line.trim().startsWith(">")) list = false; continue; }
				boolean quoted = false;
				for(int i = 0; i < line.length(); i++) {
					char c = line.charAt(i);
					if(c == '"') quoted = !quoted;
					if(quoted) continue;
					if(c == '#') break;
					if(c == '=') { properties++; break; } // Values may contain punctuation.
					if(c == '<') { properties++; list = true; break; }
					if(c == '{') depth++;
					if(c == '}' && --depth < 0) throw new IllegalArgumentException("Unexpected closing configuration category");
				}
			}
		}
		if(depth != 0 || list || properties == 0) throw new IllegalArgumentException("Empty or unfinished configuration category/list");
		return properties;
	}
	
	public static final String CATEGORY_GENERAL = "01_general";
	public static final String CATEGORY_ORES = "02_ores";
	public static final String CATEGORY_NUKES = "03_nukes";
	public static final String CATEGORY_DUNGEONS = "04_dungeons";
	public static final String CATEGORY_METEORS = "05_meteors";
	public static final String CATEGORY_EXPLOSIONS = "06_explosions";
	public static final String CATEGORY_MISSILE = "07_missile_machines";
	public static final String CATEGORY_POTION = "08_potion_effects";
	public static final String CATEGORY_MACHINES = "09_machines";
	public static final String CATEGORY_DROPS = "10_dangerous_drops";
	public static final String CATEGORY_TOOLS = "11_tools";
	public static final String CATEGORY_MOBS = "12_mobs";
	public static final String CATEGORY_RADIATION = "13_radiation";
	public static final String CATEGORY_HAZARD = "14_hazard";
	public static final String CATEGORY_STRUCTURES = "15_structures";
	public static final String CATEGORY_BIOMES = "16_biomes";
	public static final String CATEGORY_DIMS = "17_dims";
	public static final String CATEGORY_POLLUTION = "18_pollution";
	public static final String CATEGORY_WEAPONS = "19_weapons";

	public static final String CATEGORY_528 = "528";
	public static final String CATEGORY_LBSM = "LESS BULLSHIT MODE";

	public static int setDefZero(int value, int def) {

		if(value < 0) {
			MainRegistry.logger.error("Fatal error config: Randomizer value has been below zero, despite bound having to be positive integer!");
			MainRegistry.logger.error(String.format(Locale.US, "Errored value will default back to %d, PLEASE REVIEW CONFIGURATION DESCRIPTION BEFORE MEDDLING WITH VALUES!", def));
			return def;
		}

		return value;
	}

	public static int setDef(int value, int def) {

		if(value <= 0) {
			MainRegistry.logger.error("Fatal error config: Randomizer value has been set to zero, despite bound having to be positive integer!");
			MainRegistry.logger.error(String.format(Locale.US, "Errored value will default back to %d, PLEASE REVIEW CONFIGURATION DESCRIPTION BEFORE MEDDLING WITH VALUES!", def));
			return def;
		}

		return value;
	}

	public static int createConfigInt(Configuration config, String category, String name, String comment, int def) {
		Property prop = config.get(category, name, def);
		prop.comment = comment;
		return prop.getInt();
	}

	public static double createConfigDouble(Configuration config, String category, String name, String comment, double def) {
		Property prop = config.get(category, name, def);
		prop.comment = comment;
		return prop.getDouble();
	}

	public static boolean createConfigBool(Configuration config, String category, String name, String comment, boolean def) {
		Property prop = config.get(category, name, def);
		prop.comment = comment;
		return prop.getBoolean();
	}

	public static String createConfigString(Configuration config, String category, String name, String comment, String def) {
		Property prop = config.get(category, name, def);
		prop.comment = comment;
		return prop.getString();
	}
    public static int[] createConfigIntList(Configuration config, String category, String name, String comment, int[] def){
		Property prop = config.get(category, name, def);
		prop.comment = comment;
		return prop.getIntList();
	}
	public static String[] createConfigStringList(Configuration config, String category, String name, String comment) {
		Property prop = config.get(category, name, new String[] { "PLACEHOLDER" });
		prop.comment = comment;
		return prop.getStringList();
	}

	public static int parseStructureFlag(String flag) {
		if(flag == null) flag = "";
		
		switch(flag.toLowerCase(Locale.US)) {
		case "true":
		case "on":
		case "yes":
			return 1;
		case "false":
		case "off":
		case "no":
			return 0;
		default:
			return 2;
		}
	}

}
