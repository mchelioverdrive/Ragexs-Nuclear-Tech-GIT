package com.hbm.render.util;

import java.util.HashMap;
import com.hbm.items.ModItems;
import com.hbm.items.weapon.ItemCustomMissilePart;
import com.hbm.items.weapon.ItemCustomMissilePart.PartType;
import com.hbm.main.ResourceManager;
import com.hbm.render.entity.rocket.part.RenderDropPod;
import com.hbm.render.entity.rocket.part.RenderRocketPart;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModelCustom;

public class MissilePart {

	public static HashMap<Integer, MissilePart> parts = new HashMap<Integer, MissilePart>();

	public ItemCustomMissilePart part;
	public PartType type;
	public double height;
	public double guiheight;
	public IModelCustom model;
	public IModelCustom deployedModel;
	public IModelCustom shroudModel;
	public ResourceLocation texture;
	public boolean renderInventoryModel = true;
	public RenderRocketPart renderer;

	private MissilePart(Item item, PartType type, double height, double guiheight, IModelCustom model, ResourceLocation texture) {
		this.part = (ItemCustomMissilePart)item;
		this.type = type;
		this.height = height;
		this.guiheight = guiheight;
		this.model = model;
		this.texture = texture;
	}

	private MissilePart withDeployed(IModelCustom deployedModel) {
		this.deployedModel = deployedModel;
		return this;
	}

	private MissilePart withShroud(IModelCustom shroudModel) {
		this.shroudModel = shroudModel;
		return this;
	}

	private MissilePart withRenderer(RenderRocketPart renderer) {
		this.renderer = renderer;
		return this;
	}

	private MissilePart hideInventoryModel() {
		this.renderInventoryModel = false;
		return this;
	}

	public IModelCustom getModel(boolean deployed) {
		if(deployed && deployedModel != null) return deployedModel;
		return model;
	}

	public IModelCustom getShroud() {
		if(shroudModel != null) return shroudModel;
		return model;
	}

	public static void registerAllParts() {

		parts.clear();

		MissilePart.registerPart(ModItems.mp_thruster_10_kerosene, PartType.THRUSTER, 1, 1, ResourceManager.mp_t_10_kerosene, ResourceManager.mp_t_10_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_thruster_10_kerosene_tec, PartType.THRUSTER, 1.25, 1.25, ResourceManager.mp_t_10_kerosene_tec, ResourceManager.universal);
		MissilePart.registerPart(ModItems.mp_thruster_10_solid, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_10_solid, ResourceManager.mp_t_10_solid_tex);
		MissilePart.registerPart(ModItems.mp_thruster_10_hydrazine, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_10_solid, ResourceManager.mp_t_10_solid_tex);
		MissilePart.registerPart(ModItems.mp_thruster_10_xenon, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_10_xenon, ResourceManager.mp_t_10_xenon_tex);
		MissilePart.registerPart(ModItems.mp_thruster_10_hydrazine, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_10_solid, ResourceManager.mp_t_10_solid_tex);

		//
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene, PartType.THRUSTER, 1.5, 1.5, ResourceManager.mp_t_15_kerosene, ResourceManager.mp_t_15_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_tec, PartType.THRUSTER, 1.5, 3.25, ResourceManager.mp_t_15_kerosene_tec, ResourceManager.universal);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_dual, PartType.THRUSTER, 1, 1.5, ResourceManager.mp_t_15_kerosene_dual, ResourceManager.mp_t_15_kerosene_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_triple, PartType.THRUSTER, 1, 1.5, ResourceManager.mp_t_15_kerosene_triple, ResourceManager.mp_t_15_kerosene_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_solid, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_15_solid, ResourceManager.mp_t_15_solid_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_solid_hexdecuple, PartType.THRUSTER, 0.5, 1, ResourceManager.mp_t_15_solid_hexdecuple, ResourceManager.mp_t_15_solid_hexdecuple_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_hydrogen, PartType.THRUSTER, 1.5, 1.5, ResourceManager.mp_t_15_kerosene, ResourceManager.mp_t_15_hydrogen_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_hydrogen_dual, PartType.THRUSTER, 1, 1.5, ResourceManager.mp_t_15_kerosene_dual, ResourceManager.mp_t_15_hydrogen_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_short, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_15_balefire_short, ResourceManager.mp_t_15_balefire_short_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire, PartType.THRUSTER, 3, 2.5, ResourceManager.mp_t_15_balefire, ResourceManager.mp_t_15_balefire_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_large, PartType.THRUSTER, 3, 2.5, ResourceManager.mp_t_15_balefire_large, ResourceManager.mp_t_15_balefire_large_tex);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_large_rad, PartType.THRUSTER, 3, 2.5, ResourceManager.mp_t_15_balefire_large, ResourceManager.mp_t_15_balefire_large_rad_tex);
		//
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene, ResourceManager.mp_t_20_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene_dual, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_dual, ResourceManager.mp_t_20_kerosene_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene_triple, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_triple, ResourceManager.mp_t_20_kerosene_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene, ResourceManager.mp_t_20_methalox_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox_dual, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_dual, ResourceManager.mp_t_20_methalox_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox_triple, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_triple, ResourceManager.mp_t_20_methalox_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene, ResourceManager.mp_t_20_hydrogen_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen_dual, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_dual, ResourceManager.mp_t_20_hydrogen_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen_triple, PartType.THRUSTER, 2, 2, ResourceManager.mp_t_20_kerosene_triple, ResourceManager.mp_t_20_hydrogen_dual_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid, PartType.THRUSTER, 1, 1.75, ResourceManager.mp_t_20_solid, ResourceManager.mp_t_20_solid_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid_multi, PartType.THRUSTER, 0.5, 1.5, ResourceManager.mp_t_20_solid_multi, ResourceManager.mp_t_20_solid_multi_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid_multier, PartType.THRUSTER, 0.5, 1.5, ResourceManager.mp_t_20_solid_multi, ResourceManager.mp_t_20_solid_multier_tex);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrazine, PartType.THRUSTER, 3, 2.5, ResourceManager.mp_t_20_azide, ResourceManager.mp_t_20_azide_tex);

		//////

		MissilePart.registerPart(ModItems.mp_stability_10_flat, PartType.FINS, 0, 2, ResourceManager.mp_s_10_flat, ResourceManager.mp_s_10_flat_tex);
		MissilePart.registerPart(ModItems.mp_stability_10_cruise, PartType.FINS, 0, 3, ResourceManager.mp_s_10_cruise, ResourceManager.mp_s_10_cruise_tex);
		MissilePart.registerPart(ModItems.mp_stability_10_space, PartType.FINS, 0, 2, ResourceManager.mp_s_10_space, ResourceManager.mp_s_10_space_tex);
		//
		MissilePart.registerPart(ModItems.mp_stability_15_flat, PartType.FINS, 0, 3, ResourceManager.mp_s_15_flat, ResourceManager.mp_s_15_flat_tex);
		MissilePart.registerPart(ModItems.mp_stability_15_thin, PartType.FINS, 0, 3, ResourceManager.mp_s_15_thin, ResourceManager.mp_s_15_thin_tex);
		MissilePart.registerPart(ModItems.mp_stability_15_soyuz, PartType.FINS, 0, 3, ResourceManager.mp_s_15_soyuz, ResourceManager.mp_s_15_soyuz_tex);
		//
		MissilePart.registerPart(ModItems.mp_stability_20_flat, PartType.FINS, 0, 3, ResourceManager.mp_s_20, ResourceManager.universal);

		MissilePart.registerPart(ModItems.rp_legs_20, PartType.FINS, 2.4, 3, ResourceManager.rp_s_20_leggy, ResourceManager.universal).withDeployed(ResourceManager.rp_s_20_leggy_deployed);

		//////

		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_camo, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_camo_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_desert, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_desert_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_sky, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_sky_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_insulation, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_flames, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_flames_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_sleek, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_sleek_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_metal, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_metal_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_taint, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_kerosene_taint_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_hydrazine, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_hydrazine_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_flames, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_flames_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_insulation, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_sleek, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_sleek_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_soviet_glory, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_soviet_glory_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_cathedral, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_cathedral_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_moonlit, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_moonlit_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_battery, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_battery_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_duracell, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_solid_duracell_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_xenon, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_xenon_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_xenon_bhole, PartType.FUSELAGE, 4, 3, ResourceManager.mp_f_10_kerosene, ResourceManager.mp_f_10_xenon_bhole_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_camo, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_camo_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_desert, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_desert_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_sky, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_sky_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_flames, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_flames_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_insulation, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_sleek, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_sleek_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_metal, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_metal_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_dash, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_dash_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_taint, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_taint_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_vap, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_kerosene_vap_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_flames, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_flames_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_insulation, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_sleek, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_sleek_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_soviet_glory, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_soviet_glory_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_bullet, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_bullet_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_silvermoonlight, PartType.FUSELAGE, 7, 5, ResourceManager.mp_f_10_long_kerosene, ResourceManager.mp_f_10_long_solid_silvermoonlight_tex);
		//
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_kerosene, PartType.FUSELAGE, 9, 5.5, ResourceManager.mp_f_10_15_kerosene, ResourceManager.mp_f_10_15_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_solid, PartType.FUSELAGE, 9, 5.5, ResourceManager.mp_f_10_15_kerosene, ResourceManager.mp_f_10_15_solid_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_hydrogen, PartType.FUSELAGE, 9, 5.5, ResourceManager.mp_f_10_15_kerosene, ResourceManager.mp_f_10_15_hydrogen_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_balefire, PartType.FUSELAGE, 9, 5.5, ResourceManager.mp_f_10_15_kerosene, ResourceManager.mp_f_10_15_balefire_tex);
		//
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_camo, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_camo_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_desert, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_desert_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_sky, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_sky_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_insulation, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_metal, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_metal_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_decorated, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_decorated_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_steampunk, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_steampunk_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_polite, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_polite_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_blackjack, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_blackjack_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_lambda, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_lambda_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_minuteman, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_minuteman_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_pip, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_pip_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_taint, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_taint_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_yuck, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_kerosene_yuck_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_insulation, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_insulation_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_desh, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_desh_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_soviet_glory, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_soviet_glory_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_soviet_stank, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_soviet_stank_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_faust, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_faust_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_silvermoonlight, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_silvermoonlight_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_snowy, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_snowy_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_panorama, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_panorama_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_roses, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_roses_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_mimi, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_kerosene, ResourceManager.mp_f_15_solid_mimi_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_hydrogen, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_hydrogen, ResourceManager.mp_f_15_hydrogen_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_hydrogen_cathedral, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_hydrogen, ResourceManager.mp_f_15_hydrogen_cathedral_tex);
		//MissilePart.registerPart(ModItems.mp_fuselage_15_balefire, PartType.FUSELAGE, 10, 6, ResourceManager.mp_f_15_hydrogen, ResourceManager.mp_f_15_balefire_tex);

		MissilePart.registerPart(ModItems.mp_fuselage_15_20_kerosene, PartType.FUSELAGE, 16, 10, ResourceManager.mp_f_15_20_kerosene, ResourceManager.mp_f_15_20_kerosene_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_20_kerosene_magnusson, PartType.FUSELAGE, 16, 10, ResourceManager.mp_f_15_20_kerosene, ResourceManager.mp_f_15_20_kerosene_magnusson_tex);
		MissilePart.registerPart(ModItems.mp_fuselage_15_20_solid, PartType.FUSELAGE, 16, 10, ResourceManager.mp_f_15_20_kerosene, ResourceManager.mp_f_15_20_solid_tex);
		//
		MissilePart.registerPart(ModItems.rp_fuselage_20_12, PartType.FUSELAGE, 12, 8, ResourceManager.mp_f_20_12_usa, ResourceManager.mp_f_20_kerolox_usa);
		MissilePart.registerPart(ModItems.rp_fuselage_20_6, PartType.FUSELAGE, 6, 4.5, ResourceManager.mp_f_20_6_usa, ResourceManager.mp_f_20_kerolox_usa);
		MissilePart.registerPart(ModItems.rp_fuselage_20_3, PartType.FUSELAGE, 3, 2.5, ResourceManager.mp_f_20_3_usa, ResourceManager.mp_f_20_kerolox).withShroud(ResourceManager.mp_f_20_6_usa);
		MissilePart.registerPart(ModItems.rp_fuselage_20_1, PartType.FUSELAGE, 1, 1.5, ResourceManager.mp_f_20_1_usa, ResourceManager.mp_f_20_kerolox).withShroud(ResourceManager.mp_f_20_6_usa);
		MissilePart.registerPart(ModItems.rp_fuselage_20_12_hydrazine, PartType.FUSELAGE, 10, 8, ResourceManager.mp_f_20_neo, ResourceManager.mp_f_20_hydrazine_tex);


		//////

		MissilePart.registerPart(ModItems.mp_warhead_10_he, PartType.WARHEAD, 2, 1.5, ResourceManager.mp_w_10_he, ResourceManager.mp_w_10_he_tex);
		MissilePart.registerPart(ModItems.mp_warhead_10_incendiary, PartType.WARHEAD, 2.5, 2, ResourceManager.mp_w_10_incendiary, ResourceManager.mp_w_10_incendiary_tex);
		MissilePart.registerPart(ModItems.mp_warhead_10_buster, PartType.WARHEAD, 0.5, 1, ResourceManager.mp_w_10_buster, ResourceManager.mp_w_10_buster_tex);
		MissilePart.registerPart(ModItems.mp_warhead_10_nuclear, PartType.WARHEAD, 2, 1.5, ResourceManager.mp_w_10_nuclear, ResourceManager.mp_w_10_nuclear_tex);
		MissilePart.registerPart(ModItems.mp_warhead_10_nuclear_large, PartType.WARHEAD, 2.5, 1.5, ResourceManager.mp_w_10_nuclear_large, ResourceManager.mp_w_10_nuclear_large_tex);
		//MissilePart.registerPart(ModItems.mp_warhead_10_taint, PartType.WARHEAD, 2.25, 1.5, ResourceManager.mp_w_10_taint, ResourceManager.mp_w_10_taint_tex);
		//MissilePart.registerPart(ModItems.mp_warhead_10_cloud, PartType.WARHEAD, 2.25, 1.5, ResourceManager.mp_w_10_taint, ResourceManager.mp_w_10_cloud_tex);
		//
		MissilePart.registerPart(ModItems.mp_warhead_15_he, PartType.WARHEAD, 2, 1.5, ResourceManager.mp_w_15_he, ResourceManager.mp_w_15_he_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_incendiary, PartType.WARHEAD, 2, 1.5, ResourceManager.mp_w_15_incendiary, ResourceManager.mp_w_15_incendiary_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear, PartType.WARHEAD, 3.5, 2, ResourceManager.mp_w_15_nuclear, ResourceManager.mp_w_15_nuclear_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear_shark, PartType.WARHEAD, 3.5, 2, ResourceManager.mp_w_15_nuclear, ResourceManager.mp_w_15_nuclear_shark_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear_mimi, PartType.WARHEAD, 3.5, 2, ResourceManager.mp_w_15_nuclear, ResourceManager.mp_w_15_nuclear_mimi_tex);
		//MissilePart.registerPart(ModItems.mp_warhead_15_boxcar, PartType.WARHEAD, 2.25, 7.5, ResourceManager.mp_w_15_boxcar, ResourceManager.boxcar_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_n2, PartType.WARHEAD, 3, 2, ResourceManager.mp_w_15_n2, ResourceManager.mp_w_15_n2_tex);
		//MissilePart.registerPart(ModItems.mp_warhead_15_balefire, PartType.WARHEAD, 2.75, 2, ResourceManager.mp_w_15_balefire, ResourceManager.mp_w_15_balefire_tex);
		MissilePart.registerPart(ModItems.mp_warhead_15_turbine, PartType.WARHEAD, 2.25, 2, ResourceManager.mp_w_15_turbine, ResourceManager.mp_w_15_turbine_tex);

		//
		MissilePart.registerPart(ModItems.mp_warhead_20_he, PartType.WARHEAD, 3, 2.25, ResourceManager.mp_w_20, ResourceManager.universal);

		// SPACE
		MissilePart.registerPart(ModItems.rp_capsule_20, PartType.WARHEAD, 3.5, 2.25, ResourceManager.soyuz_lander_neo, ResourceManager.module_lander_tex);
		MissilePart.registerPart(ModItems.rp_station_core_20, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex);
		MissilePart.registerPart(ModItems.rp_pod_20, PartType.WARHEAD, 3.0, 2.25, ResourceManager.drop_pod, ResourceManager.drop_pod_tex).withRenderer(new RenderDropPod());

		MissilePart.registerPart(ModItems.sat_mapper, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_scanner, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_radar, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_laser, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_foeq, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_resonator, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_miner, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		MissilePart.registerPart(ModItems.sat_lunar_miner, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();
		//MissilePart.registerPart(ModItems.sat_gerald, PartType.WARHEAD, 7, 5, ResourceManager.mp_w_fairing, ResourceManager.mp_w_fairing_tex).hideInventoryModel();

	}

	// I'm a huge fucking moron, refactor this later
	public static void registerAllPartsServer() {

		parts.clear();

		MissilePart.registerPart(ModItems.mp_thruster_10_kerosene, PartType.THRUSTER, 1, 1);
		MissilePart.registerPart(ModItems.mp_thruster_10_kerosene_tec, PartType.THRUSTER, 1.25, 1.25);
		MissilePart.registerPart(ModItems.mp_thruster_10_solid, PartType.THRUSTER, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_thruster_10_hydrazine, PartType.THRUSTER, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_thruster_10_xenon, PartType.THRUSTER, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_thruster_10_hydrazine, PartType.THRUSTER, 0.5, 1);

		//
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene, PartType.THRUSTER, 1.5, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_tec, PartType.THRUSTER, 1.5, 3.25);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_dual, PartType.THRUSTER, 1, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_kerosene_triple, PartType.THRUSTER, 1, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_solid, PartType.THRUSTER, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_thruster_15_solid_hexdecuple, PartType.THRUSTER, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_thruster_15_hydrogen, PartType.THRUSTER, 1.5, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_hydrogen_dual, PartType.THRUSTER, 1, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_short, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire, PartType.THRUSTER, 3, 2.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_large, PartType.THRUSTER, 3, 2.5);
		MissilePart.registerPart(ModItems.mp_thruster_15_balefire_large_rad, PartType.THRUSTER, 3, 2.5);
		//
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene_dual, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_kerosene_triple, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox_dual, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_methalox_triple, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen_dual, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrogen_triple, PartType.THRUSTER, 2, 2);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid, PartType.THRUSTER, 1, 1.75);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid_multi, PartType.THRUSTER, 0.5, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_20_solid_multier, PartType.THRUSTER, 0.5, 1.5);
		MissilePart.registerPart(ModItems.mp_thruster_20_hydrazine, PartType.THRUSTER, 3, 2.5);

		//////

		MissilePart.registerPart(ModItems.mp_stability_10_flat, PartType.FINS, 0, 2);
		MissilePart.registerPart(ModItems.mp_stability_10_cruise, PartType.FINS, 0, 3);
		MissilePart.registerPart(ModItems.mp_stability_10_space, PartType.FINS, 0, 2);
		//
		MissilePart.registerPart(ModItems.mp_stability_15_flat, PartType.FINS, 0, 3);
		MissilePart.registerPart(ModItems.mp_stability_15_thin, PartType.FINS, 0, 3);
		MissilePart.registerPart(ModItems.mp_stability_15_soyuz, PartType.FINS, 0, 3);
		//
		MissilePart.registerPart(ModItems.mp_stability_20_flat, PartType.FINS, 0, 3);

		MissilePart.registerPart(ModItems.rp_legs_20, PartType.FINS, 2.4, 3);

		//////

		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_camo, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_desert, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_sky, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_insulation, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_flames, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_sleek, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_metal, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_kerosene_taint, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_hydrazine, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_flames, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_insulation, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_sleek, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_soviet_glory, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_cathedral, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_moonlit, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_battery, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_solid_duracell, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_xenon, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_xenon_bhole, PartType.FUSELAGE, 4, 3);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_camo, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_desert, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_sky, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_flames, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_insulation, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_sleek, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_metal, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_dash, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_taint, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_kerosene_vap, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_flames, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_insulation, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_sleek, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_soviet_glory, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_bullet, PartType.FUSELAGE, 7, 5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_long_solid_silvermoonlight, PartType.FUSELAGE, 7, 5);
		//
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_kerosene, PartType.FUSELAGE, 9, 5.5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_solid, PartType.FUSELAGE, 9, 5.5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_hydrogen, PartType.FUSELAGE, 9, 5.5);
		MissilePart.registerPart(ModItems.mp_fuselage_10_15_balefire, PartType.FUSELAGE, 9, 5.5);
		//
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_camo, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_desert, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_sky, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_insulation, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_metal, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_decorated, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_steampunk, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_polite, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_blackjack, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_lambda, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_minuteman, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_pip, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_taint, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_kerosene_yuck, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_insulation, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_desh, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_soviet_glory, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_soviet_stank, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_faust, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_silvermoonlight, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_snowy, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_panorama, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_roses, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_solid_mimi, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_hydrogen, PartType.FUSELAGE, 10, 6);
		MissilePart.registerPart(ModItems.mp_fuselage_15_hydrogen_cathedral, PartType.FUSELAGE, 10, 6);
		//MissilePart.registerPart(ModItems.mp_fuselage_15_balefire, PartType.FUSELAGE, 10, 6);

		MissilePart.registerPart(ModItems.mp_fuselage_15_20_kerosene, PartType.FUSELAGE, 16, 10);
		MissilePart.registerPart(ModItems.mp_fuselage_15_20_kerosene_magnusson, PartType.FUSELAGE, 16, 10);
		MissilePart.registerPart(ModItems.mp_fuselage_15_20_solid, PartType.FUSELAGE, 16, 10);
		//
		MissilePart.registerPart(ModItems.rp_fuselage_20_12, PartType.FUSELAGE, 12, 8);
		MissilePart.registerPart(ModItems.rp_fuselage_20_6, PartType.FUSELAGE, 6, 4.5);
		MissilePart.registerPart(ModItems.rp_fuselage_20_3, PartType.FUSELAGE, 3, 2.5);
		MissilePart.registerPart(ModItems.rp_fuselage_20_1, PartType.FUSELAGE, 1, 2);
		MissilePart.registerPart(ModItems.rp_fuselage_20_12_hydrazine, PartType.FUSELAGE, 10, 8);


		//////

		MissilePart.registerPart(ModItems.mp_warhead_10_he, PartType.WARHEAD, 2, 1.5);
		MissilePart.registerPart(ModItems.mp_warhead_10_incendiary, PartType.WARHEAD, 2.5, 2);
		MissilePart.registerPart(ModItems.mp_warhead_10_buster, PartType.WARHEAD, 0.5, 1);
		MissilePart.registerPart(ModItems.mp_warhead_10_nuclear, PartType.WARHEAD, 2, 1.5);
		MissilePart.registerPart(ModItems.mp_warhead_10_nuclear_large, PartType.WARHEAD, 2.5, 1.5);
		//MissilePart.registerPart(ModItems.mp_warhead_10_taint, PartType.WARHEAD, 2.25, 1.5);
		//MissilePart.registerPart(ModItems.mp_warhead_10_cloud, PartType.WARHEAD, 2.25, 1.5);
		//
		MissilePart.registerPart(ModItems.mp_warhead_15_he, PartType.WARHEAD, 2, 1.5);
		MissilePart.registerPart(ModItems.mp_warhead_15_incendiary, PartType.WARHEAD, 2, 1.5);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear, PartType.WARHEAD, 3.5, 2);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear_shark, PartType.WARHEAD, 3.5, 2);
		MissilePart.registerPart(ModItems.mp_warhead_15_nuclear_mimi, PartType.WARHEAD, 3.5, 2);
		//MissilePart.registerPart(ModItems.mp_warhead_15_boxcar, PartType.WARHEAD, 2.25, 7.5);
		MissilePart.registerPart(ModItems.mp_warhead_15_n2, PartType.WARHEAD, 3, 2);
		//MissilePart.registerPart(ModItems.mp_warhead_15_balefire, PartType.WARHEAD, 2.75, 2);
		MissilePart.registerPart(ModItems.mp_warhead_15_turbine, PartType.WARHEAD, 2.25, 2);

		//
		MissilePart.registerPart(ModItems.mp_warhead_20_he, PartType.WARHEAD, 3, 2.25);

		// SPACE
		MissilePart.registerPart(ModItems.rp_capsule_20, PartType.WARHEAD, 3.5, 2.25);
		MissilePart.registerPart(ModItems.rp_station_core_20, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.rp_pod_20, PartType.WARHEAD, 3.0, 2.25);

		MissilePart.registerPart(ModItems.sat_mapper, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_scanner, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_radar, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_laser, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_foeq, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_resonator, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_miner, PartType.WARHEAD, 7, 5);
		MissilePart.registerPart(ModItems.sat_lunar_miner, PartType.WARHEAD, 7, 5);
		//MissilePart.registerPart(ModItems.sat_gerald, PartType.WARHEAD, 7, 5);

	}

	public static MissilePart registerPart(Item item, PartType type, double height, double guiheight) {
		MissilePart part = new MissilePart(item, type, height, guiheight, null, null);
		parts.put(item.hashCode(), part);
		return part;
	}

	public static MissilePart registerPart(Item item, PartType type, double height, double guiheight, IModelCustom model, ResourceLocation texture) {
		MissilePart part = new MissilePart(item, type, height, guiheight, model, texture);
		parts.put(item.hashCode(), part);
		return part;
	}

	public static MissilePart getPart(ItemStack stack) {
		if(stack == null)
			return null;

		return getPart(stack.getItem());
	}

	public static MissilePart getPart(Item item) {
		if(item == null)
			return null;

		return parts.get(item.hashCode());
	}

	public static MissilePart getPart(int id) {
		if(id <= 0)
			return null;

		return getPart(Item.getItemById(id));
	}

	public static int getId(MissilePart m) {
		if(m == null) return 0;
		return Item.getIdFromItem(m.part);
	}

}
