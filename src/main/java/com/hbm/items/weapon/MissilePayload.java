package com.hbm.items.weapon;

import java.util.Locale;

import com.hbm.items.ModItems;

import net.minecraft.item.Item;

/** The station fits a standard in-game warhead selected independently of the airframe's identity. */
public enum MissilePayload {
	HE, INCENDIARY, CLUSTER, PENETRATOR, NUCLEAR, THERMONUCLEAR, INTERCEPTOR;

	public Item component(int size) {
		switch(this) {
		case INCENDIARY:
			return bySize(size, ModItems.warhead_incendiary_small, ModItems.warhead_incendiary_medium, ModItems.warhead_incendiary_large);
		case CLUSTER:
			return bySize(size, ModItems.warhead_cluster_small, ModItems.warhead_cluster_medium, ModItems.warhead_cluster_large);
		case PENETRATOR:
			return bySize(size, ModItems.warhead_buster_small, ModItems.warhead_buster_medium, ModItems.warhead_buster_large);
		case NUCLEAR:
			return ModItems.warhead_nuclear;
		case THERMONUCLEAR:
			return ModItems.warhead_mirv;
		case INTERCEPTOR:
			return ModItems.warhead_generic_small;
		case HE:
			return bySize(size, ModItems.warhead_generic_small, ModItems.warhead_generic_medium, ModItems.warhead_generic_large);
		default:
			throw new AssertionError(this);
		}
	}

	private static Item bySize(int size, Item small, Item medium, Item large) {
		return size == 0 ? small : size == 1 ? medium : large;
	}

	public String translationKey() {
		return "missile.payload." + name().toLowerCase(Locale.ROOT);
	}
}
