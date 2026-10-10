package com.hbm.util;

import java.util.HashMap;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.items.ItemAmmoEnums;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;
import com.hbm.main.MainRegistry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;

public class AchievementHandler {

	public static HashMap<ComparableStack, Achievement> craftingAchievements = new HashMap();
	private static HashMap<ComparableStack, Achievement> craftOnlyAchievements = new HashMap();

	public static void register() {

		try {

			//craftingAchievements.put(new ComparableStack(ModItems.gun_ar15), MainRegistry.achFreytag);
			//craftingAchievements.put(new ComparableStack(ModItems.piston_selenium), MainRegistry.achSelenium);
			//craftingAchievements.put(new ComparableStack(ModItems.gun_b92), MainRegistry.achSelenium);
			craftingAchievements.put(new ComparableStack(ModItems.battery_potatos), MainRegistry.achPotato);
			//craftingAchievements.put(new ComparableStack(ModItems.gun_revolver_pip), MainRegistry.achC44);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_press), MainRegistry.achBurnerPress);
			craftingAchievements.put(new ComparableStack(ModItems.rbmk_fuel_empty), MainRegistry.achRBMK);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_chemplant), MainRegistry.achChemplant);
			craftingAchievements.put(new ComparableStack(ModBlocks.concrete_smooth), MainRegistry.achConcrete);
			craftingAchievements.put(new ComparableStack(ModBlocks.concrete_asbestos), MainRegistry.achConcrete);
			craftingAchievements.put(new ComparableStack(ModItems.ingot_polymer), MainRegistry.achPolymer);
			//craftingAchievements.put(new ComparableStack(ModItems.ingot_desh), MainRegistry.achDesh);
			craftingAchievements.put(new ComparableStack(ModItems.gem_tantalium), MainRegistry.achTantalum);
			//craftingAchievements.put(new ComparableStack(ModBlocks.machine_gascent), MainRegistry.achGasCent);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_centrifuge), MainRegistry.achCentrifuge);
			//craftingAchievements.put(new ComparableStack(ModItems.ingot_schrabidium), MainRegistry.achSchrab);
			//craftingAchievements.put(new ComparableStack(ModItems.nugget_schrabidium), MainRegistry.achSchrab);
			//craftingAchievements.put(new ComparableStack(ModBlocks.machine_crystallizer), MainRegistry.achAcidizer);
			//craftingAchievements.put(new ComparableStack(ModBlocks.machine_silex), MainRegistry.achSILEX);
			craftingAchievements.put(new ComparableStack(ModItems.nugget_technetium), MainRegistry.achTechnetium);
			//craftingAchievements.put(new ComparableStack(ModBlocks.struct_watz_core), MainRegistry.achWatz);
			craftingAchievements.put(new ComparableStack(ModItems.nugget_bismuth), MainRegistry.achBismuth);
			craftingAchievements.put(new ComparableStack(ModItems.nugget_am241), MainRegistry.achBreeding);
			craftingAchievements.put(new ComparableStack(ModItems.nugget_am242), MainRegistry.achBreeding);
			craftingAchievements.put(new ComparableStack(ModItems.missile_nuclear), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.missile_nuclear_cluster), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.missile_doomsday), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.mp_warhead_10_nuclear), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.mp_warhead_10_nuclear_large), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.mp_warhead_15_nuclear), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModItems.mp_warhead_15_nuclear_shark), MainRegistry.achRedBalloons);
			//craftingAchievements.put(new ComparableStack(ModItems.mp_warhead_15_boxcar), MainRegistry.achRedBalloons);
			craftingAchievements.put(new ComparableStack(ModBlocks.struct_iter_core), MainRegistry.achFusion);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_difurnace_off), MainRegistry.achBlastFurnace);
			craftingAchievements.put(new ComparableStack(ModBlocks.furnace_iron), MainRegistry.achIronFurnace);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_shredder), MainRegistry.achShredder);
			craftOnlyAchievements.put(new ComparableStack(ModItems.circuit, 1, EnumCircuitType.VACUUM_TUBE), MainRegistry.achVacuumTube);
			craftOnlyAchievements.put(new ComparableStack(ModItems.motor), MainRegistry.achMotor);
			craftOnlyAchievements.put(new ComparableStack(ModItems.detonator), MainRegistry.achDetonator);
			craftingAchievements.put(new ComparableStack(ModBlocks.machine_assembler), MainRegistry.achAssembly);
			craftingAchievements.put(new ComparableStack(ModItems.billet_pu_mix), MainRegistry.achChicagoPile);
			//craftingAchievements.put(new ComparableStack(ModItems.ammo_4gauge, 1, ItemAmmoEnums.Ammo4Gauge.VAMPIRE.ordinal()), MainRegistry.achWitchtaunter);
		} catch (Exception e) {
			System.out.println("[Ragex's Nuclear Tech Mod] Failed to register achievements! (AchievementHandler.java)" + e);
			e.printStackTrace();
			//hopefully we should find the crashing ach's
		}

	}

	public static void fire(EntityPlayer player, ItemStack stack) {
		fire(player, stack, false);
	}

	public static void fireCrafting(EntityPlayer player, ItemStack stack) {
		fire(player, stack, true);
	}

	private static void fire(EntityPlayer player, ItemStack stack, boolean crafted) {

		try {

			if (player.worldObj.isRemote) return;
			ComparableStack comp = new ComparableStack(stack).makeSingular();
			Achievement achievement = crafted ? craftOnlyAchievements.get(comp) : null;
			if (achievement == null) achievement = craftingAchievements.get(comp);
			if(achievement == MainRegistry.achDetonator) award(player, achievement);
			else if(achievement != null) player.triggerAchievement(achievement);

		} catch (Exception e) {
			System.out.println("[Ragex's Nuclear Tech Mod] Failed to fire achievement! (AchievementHandler.java)" + e);
			e.printStackTrace();
		}
	}

	public static void award(EntityPlayer player, Achievement achievement) {
		if(!(player instanceof EntityPlayerMP) || achievement == null || player.worldObj.isRemote) return;
		EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
		if(serverPlayer.func_147099_x().hasAchievementUnlocked(achievement)) return;
		if(achievement.parentAchievement == null || serverPlayer.func_147099_x().hasAchievementUnlocked(achievement.parentAchievement)) {
			serverPlayer.triggerAchievement(achievement);
		}
	}
}
