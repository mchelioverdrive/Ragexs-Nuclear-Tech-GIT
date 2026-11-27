package com.hbm.items.weapon;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemMissile.MissileFormFactor;
import com.hbm.items.weapon.ItemMissile.MissileFuel;
import com.hbm.items.weapon.ItemMissile.MissileTier;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** The playable airframes. Numeric IDs are saved as airframe item metadata and sent with entity updates. */
public enum MissileDefinition {
	V2(1, "generic", Country.GERMANY, Classification.SRBM, Generation.PIONEER,
			MissilePayload.HE, 15, 4000, 3.2, MissileFormFactor.V2, MissileFuel.ETHANOL_PEROXIDE, () -> ModItems.plate_iron),
	REDSTONE(2, "incendiary", Country.US, Classification.SRBM, Generation.EARLY,
			MissilePayload.INCENDIARY, 18, 4200, 3.4, MissileFormFactor.V2, MissileFuel.ETHANOL_PEROXIDE, () -> ModItems.plate_steel),
	R12(3, "r12", Country.USSR, Classification.MRBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 55, 6000, 3.2, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_aluminium),
	THOR(4, "thor", Country.US, Classification.IRBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 62, 6600, 3.5, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_titanium),
	JUPITER(5, "jupiter", Country.US, Classification.IRBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 68, 7500, 3.3, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_stainless),
	R7(6, "r7", Country.USSR, Classification.ICBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 75, 9000, 3.0, MissileFormFactor.ATLAS, MissileFuel.KEROSENE_LOXY, () -> ModItems.plate_dura_steel),
	ATLAS_D(7, "nuclear", Country.US, Classification.ICBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 42, 16000, 3.6, MissileFormFactor.ATLAS, MissileFuel.JETFUEL_LOXY, () -> ModItems.plate_nickel),
	ATLAS_F(8, "nuclear_cluster", Country.US, Classification.ICBM, Generation.EARLY,
			MissilePayload.THERMONUCLEAR, 110, 16000, 3.4, MissileFormFactor.ATLAS, MissileFuel.JETFUEL_LOXY, () -> ModItems.plate_weaponsteel),
	SCUD(9, "strong", Country.USSR, Classification.SRBM, Generation.EARLY,
			MissilePayload.HE, 30, 8000, 3.6, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_gunmetal),
	MINUTEMAN_I(10, "minuteman_i", Country.US, Classification.ICBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 64, 1200, 3.7, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_copper),
	TITAN_II(11, "titan_ii", Country.US, Classification.ICBM, Generation.EARLY,
			MissilePayload.THERMONUCLEAR, 96, 13000, 3.7, MissileFormFactor.ATLAS, MissileFuel.KEROSENE_LOXY, () -> ModItems.plate_lead),
	SERGEANT(12, "micro", Country.US, Classification.SRBM, Generation.EARLY,
			MissilePayload.NUCLEAR, 24, 250, 3.5, MissileFormFactor.MICRO, MissileFuel.SOLID, () -> ModItems.plate_iron),
	PERSHING(13, "pershing", Country.US, Classification.MRBM, Generation.LATE_COLD_WAR,
			MissilePayload.NUCLEAR, 50, 1100, 4.4, MissileFormFactor.STRONG, MissileFuel.SOLID, () -> ModItems.plate_steel),
	SPARTAN(14, "anti_ballistic", Country.US, Classification.INTERCEPTOR, Generation.LATE_COLD_WAR,
			MissilePayload.INTERCEPTOR, 0, 250, 0.0, MissileFormFactor.ABM, MissileFuel.SOLID, () -> ModItems.plate_iron),
	MINUTEMAN_II(15, "minuteman_ii", Country.US, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.NUCLEAR, 74, 1550, 4.0, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_aluminium),
	MINUTEMAN_III(16, "minuteman_iii", Country.US, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.THERMONUCLEAR, 90, 2000, 4.2, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_titanium),
	LANCE(17, "incendiary_strong", Country.US, Classification.SRBM, Generation.LATE_COLD_WAR,
			MissilePayload.INCENDIARY, 30, 8000, 4.1, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_copper),
	R36M(18, "r36m", Country.USSR, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.THERMONUCLEAR, 120, 16000, 3.6, MissileFormFactor.ATLAS, MissileFuel.JETFUEL_LOXY, () -> ModItems.plate_dura_steel),
	DF5(19, "df5", Country.CHINA, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.THERMONUCLEAR, 104, 14000, 3.8, MissileFormFactor.ATLAS, MissileFuel.KEROSENE_LOXY, () -> ModItems.plate_stainless),
	TOMAHAWK(20, "buster_strong", Country.US, Classification.CRUISE, Generation.LATE_COLD_WAR,
			MissilePayload.PENETRATOR, 12, 5200, 4.3, MissileFormFactor.STRONG, MissileFuel.KEROSENE_PEROXIDE, () -> ModItems.plate_nickel),
	TOPOL(21, "topol", Country.USSR, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.NUCLEAR, 80, 1350, 4.2, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_weaponsteel),
	PEACEKEEPER(22, "peacekeeper", Country.US, Classification.ICBM, Generation.LATE_COLD_WAR,
			MissilePayload.THERMONUCLEAR, 114, 2400, 4.0, MissileFormFactor.ATLAS, MissileFuel.SOLID, () -> ModItems.plate_gunmetal),
	TRIDENT_II(23, "trident_ii", Country.US, Classification.SLBM, Generation.LATE_COLD_WAR,
			MissilePayload.THERMONUCLEAR, 102, 1850, 4.5, MissileFormFactor.ATLAS, MissileFuel.SOLID, () -> ModItems.plate_lead),
	DF21(24, "cluster_strong", Country.CHINA, Classification.MRBM, Generation.LATE_COLD_WAR,
			MissilePayload.CLUSTER, 55, 1400, 4.2, MissileFormFactor.STRONG, MissileFuel.SOLID, () -> ModItems.ingot_tungsten),
	ATACMS(25, "cluster", Country.US, Classification.SRBM, Generation.MODERN,
			MissilePayload.CLUSTER, 25, 550, 4.6, MissileFormFactor.V2, MissileFuel.SOLID, () -> ModItems.plate_steel),
	TOPOL_M(26, "topol_m", Country.RUSSIA, Classification.ICBM, Generation.MODERN,
			MissilePayload.NUCLEAR, 95, 1900, 4.5, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_aluminium),
	SHAHAB3(27, "inferno", Country.IRAN, Classification.MRBM, Generation.MODERN,
			MissilePayload.INCENDIARY, 50, 12000, 4.0, MissileFormFactor.HUGE, MissileFuel.KEROSENE_LOXY, () -> ModItems.plate_nickel),
	DF31(28, "df31", Country.CHINA, Classification.ICBM, Generation.MODERN,
			MissilePayload.NUCLEAR, 88, 1550, 4.3, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_stainless),
	ISKANDER(29, "buster", Country.RUSSIA, Classification.SRBM, Generation.MODERN,
			MissilePayload.PENETRATOR, 15, 750, 4.8, MissileFormFactor.STRONG, MissileFuel.SOLID, () -> ModItems.plate_iron),
	M51(30, "m51", Country.FRANCE, Classification.SLBM, Generation.MODERN,
			MissilePayload.THERMONUCLEAR, 108, 2150, 4.6, MissileFormFactor.ATLAS, MissileFuel.SOLID, () -> ModItems.plate_titanium),
	YARS(31, "yars", Country.RUSSIA, Classification.ICBM, Generation.MODERN,
			MissilePayload.THERMONUCLEAR, 112, 2100, 4.7, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_weaponsteel),
	AGNI_V(32, "agni_v", Country.INDIA, Classification.IRBM, Generation.MODERN,
			MissilePayload.NUCLEAR, 100, 1800, 4.6, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_gunmetal),
	DF26(33, "burst", Country.CHINA, Classification.IRBM, Generation.MODERN,
			MissilePayload.HE, 50, 1700, 4.5, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_dura_steel),
	SHAHEEN_III(34, "shaheen_iii", Country.PAKISTAN, Classification.MRBM, Generation.MODERN,
			MissilePayload.NUCLEAR, 70, 1100, 4.7, MissileFormFactor.STRONG, MissileFuel.SOLID, () -> ModItems.plate_copper),
	HWASONG_15(35, "hwasong_15", Country.NORTH_KOREA, Classification.ICBM, Generation.MODERN,
			MissilePayload.THERMONUCLEAR, 115, 15000, 4.1, MissileFormFactor.ATLAS, MissileFuel.KEROSENE_LOXY, () -> ModItems.plate_lead),
	DF17(36, "rain", Country.CHINA, Classification.HYPERSONIC, Generation.ADVANCED,
			MissilePayload.CLUSTER, 82, 1850, 5.4, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_titanium),
	DF41(37, "df41", Country.CHINA, Classification.ICBM, Generation.ADVANCED,
			MissilePayload.THERMONUCLEAR, 128, 2600, 5.0, MissileFormFactor.ATLAS, MissileFuel.SOLID, () -> ModItems.plate_dura_steel),
	SARMAT(38, "doomsday", Country.RUSSIA, Classification.ICBM, Generation.ADVANCED,
			MissilePayload.THERMONUCLEAR, 140, 16000, 4.2, MissileFormFactor.ATLAS, MissileFuel.JETFUEL_LOXY, () -> ModItems.plate_stainless),
	SENTINEL(39, "sentinel", Country.US, Classification.ICBM, Generation.ADVANCED,
			MissilePayload.THERMONUCLEAR, 118, 1800, 5.1, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_weaponsteel),
	CPS(40, "drill", Country.US, Classification.HYPERSONIC, Generation.ADVANCED,
			MissilePayload.PENETRATOR, 24, 2200, 5.6, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.plate_nickel),
	ORESHNIK(41, "oreshnik", Country.RUSSIA, Classification.IRBM, Generation.ADVANCED,
			MissilePayload.CLUSTER, 100, 2400, 5.2, MissileFormFactor.HUGE, MissileFuel.SOLID, () -> ModItems.ingot_tungsten);

	private static final Map<Integer, MissileDefinition> BY_ID = new HashMap<>();
	private static final Map<String, MissileDefinition> BY_NAME = new HashMap<>();

	static {
		for(MissileDefinition missile : values()) {
			if(BY_ID.put(missile.id, missile) != null || BY_NAME.put(missile.key, missile) != null) {
				throw new IllegalStateException("Duplicate missile identity: " + missile.key);
			}
		}
	}

	public final int id;
	public final String key;
	public final Country country;
	public final Classification classification;
	public final Generation generation;
	public final MissilePayload payload;
	/** All flight, fuel, and payload values use Minecraft gameplay units. */
	public final int payloadPower;
	public final int fuelCapacity;
	public final double flightSpeed;
	public final MissileFormFactor form;
	public final MissileFuel fuel;
	private final Supplier<Item> hullMaterial;
	private final String registryName;
	private ItemMissile item;

	MissileDefinition(int id, String registrySuffix, Country country, Classification classification, Generation generation,
			MissilePayload payload, int payloadPower, int fuelCapacity, double flightSpeed,
			MissileFormFactor form, MissileFuel fuel, Supplier<Item> hullMaterial) {
		this.id = id;
		this.key = name().toLowerCase(Locale.ROOT);
		this.registryName = "missile_" + registrySuffix;
		this.country = country;
		this.classification = classification;
		this.generation = generation;
		this.payload = payload;
		this.payloadPower = payloadPower;
		this.fuelCapacity = fuelCapacity;
		this.flightSpeed = flightSpeed;
		this.form = form;
		this.fuel = fuel;
		this.hullMaterial = hullMaterial;
	}

	public ItemMissile createItem() {
		if(item == null) {
			item = new ItemMissile(this);
			item.setUnlocalizedName(registryName).setTextureName(RefStrings.MODID + ":missile_generic")
					.setMaxStackSize(1).setCreativeTab(MainRegistry.missileTab);
		}
		return item;
	}

	public static void registerNewItems() {
		for(MissileDefinition missile : values()) {
			// The old item registrations in ModItems retain their positions and names.
			if(missile.item == null) {
				Item item = missile.createItem();
				GameRegistry.registerItem(item, item.getUnlocalizedName());
			}
		}
	}

	public ItemStack missileStack() {
		return new ItemStack(item);
	}

	public ItemStack airframeStack() {
		return new ItemStack(ModItems.missile_airframe, 1, id);
	}

	public Item targetingCircuit() {
		return generation.targetingCircuit();
	}

	public Item hullMaterial() {
		return hullMaterial.get();
	}

	public Item warhead() {
		int size = generation.warheadSize;
		return payload.component(size);
	}

	public String translationKey() {
		return "item.missile_roster." + key;
	}

	public static MissileDefinition byId(int id) {
		return BY_ID.get(id);
	}

	public static MissileDefinition byName(String key) {
		return BY_NAME.get(key);
	}

	public enum Country {
		US, USSR, RUSSIA, CHINA, GERMANY, FRANCE, INDIA, PAKISTAN, NORTH_KOREA, IRAN;

		public String translationKey() { return "missile.country." + name().toLowerCase(Locale.ROOT); }
	}

	public enum Classification {
		SRBM, MRBM, IRBM, ICBM, SLBM, CRUISE, HYPERSONIC, INTERCEPTOR;

		public String translationKey() { return "missile.class." + name().toLowerCase(Locale.ROOT); }
	}

	public enum Generation {
		PIONEER(MissileTier.TIER1, 0, 0, 2, 0, () -> ModItems.mp_chip_1),
		EARLY(MissileTier.TIER2, 0, 1, 3, 0, () -> ModItems.mp_chip_2),
		LATE_COLD_WAR(MissileTier.TIER3, 1, 1, 4, 1, () -> ModItems.mp_chip_3),
		MODERN(MissileTier.TIER3, 1, 2, 6, 1, () -> ModItems.mp_chip_4),
		ADVANCED(MissileTier.TIER4, 2, 2, 8, 2, () -> ModItems.mp_chip_5);

		public final MissileTier tier;
		public final int tankSize;
		public final int engineSize;
		public final int hullCost;
		private final int warheadSize;
		private final Supplier<Item> circuit;

		Generation(MissileTier tier, int tankSize, int engineSize, int hullCost, int warheadSize, Supplier<Item> circuit) {
			this.tier = tier;
			this.tankSize = tankSize;
			this.engineSize = engineSize;
			this.hullCost = hullCost;
			this.warheadSize = warheadSize;
			this.circuit = circuit;
		}

		public String translationKey() { return "missile.generation." + name().toLowerCase(Locale.ROOT); }

		public Item targetingCircuit() { return circuit.get(); }
	}
}
