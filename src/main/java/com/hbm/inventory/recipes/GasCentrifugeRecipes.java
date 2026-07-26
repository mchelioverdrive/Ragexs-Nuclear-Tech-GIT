package com.hbm.inventory.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.util.I18nUtil;

import net.minecraft.item.ItemStack;

/** Definitions shared by the machine, its GUI and NEI. Amounts are per centrifuge stage. */
public class GasCentrifugeRecipes {

	public enum CampaignGrade { CIVILIAN, STRATEGIC }

	public static class PseudoFluidType {
		public static final Map<String, PseudoFluidType> types = new HashMap<String, PseudoFluidType>();
		public static final PseudoFluidType NONE = new PseudoFluidType("NONE");
		public static final PseudoFluidType HEUF6 = new PseudoFluidType("HEUF6");
		public static final PseudoFluidType MEUF6 = new PseudoFluidType("MEUF6");
		public static final PseudoFluidType LEUF6 = new PseudoFluidType("LEUF6");
		public static final PseudoFluidType NUF6 = new PseudoFluidType("NUF6");
		public static final PseudoFluidType PF6 = new PseudoFluidType("PF6");
		public static final PseudoFluidType MUD_HEAVY = new PseudoFluidType("MUD_HEAVY");
		public static final PseudoFluidType MUD = new PseudoFluidType("MUD");
		public static final PseudoFluidType MINSOLEE = new PseudoFluidType("NONETWO");
		public static final PseudoFluidType MINSOLE = new PseudoFluidType("MINSOL");
		public final String name;
		private PseudoFluidType(String name) { this.name = name; types.put(name, this); }
		public String getName() { return I18nUtil.resolveKey("hbmpseudofluid." + name.toLowerCase(Locale.US)); }
		/* Compatibility accessors; processing values live on StageRecipe. */
		public int getFluidConsumed() { StageRecipe r = getStage(this, CampaignGrade.STRATEGIC); return r == null ? 0 : r.feed; }
		public int getFluidProduced() { StageRecipe r = getStage(this, CampaignGrade.STRATEGIC); return r == null ? 0 : r.productAmount; }
		public PseudoFluidType getOutputType() { StageRecipe r = getStage(this, CampaignGrade.STRATEGIC); return r == null ? NONE : r.product; }
		public ItemStack[] getOutput() { StageRecipe r = getStage(this, CampaignGrade.STRATEGIC); return r == null ? null : r.outputs; }
		public boolean getIfHighSpeed() { StageRecipe r = getStage(this, CampaignGrade.STRATEGIC); return r != null && r.advancedRotor; }
	}

	public static class StageRecipe {
		public final String id;
		public final CampaignGrade grade;
		public final PseudoFluidType input, product;
		public final int feed, productAmount, stages, duration, energyPerTick, tails;
		public final long totalEnergy, energyPerUF6;
		public final boolean advancedRotor;
		public final ItemStack[] outputs;
		public StageRecipe(String id, CampaignGrade grade, PseudoFluidType input, int feed, PseudoFluidType product,
				int productAmount, int tails, int stages, int duration, int energyPerTick, boolean advancedRotor, ItemStack... outputs) {
			this.id=id; this.grade=grade; this.input=input; this.feed=feed; this.product=product;
			this.productAmount=productAmount; this.tails=tails; this.stages=stages; this.duration=duration;
			this.energyPerTick=energyPerTick; this.advancedRotor=advancedRotor; this.outputs=outputs;
			// Includes the charged 100-tick spin-up. Speed doubles this rate and halves only process time.
			this.totalEnergy=(long)(duration + 100) * energyPerTick; this.energyPerUF6=feed == 0 ? 0 : totalEnergy / feed;
		}
		public long getTotalEnergy() { return totalEnergy; }
		public long getEnergyPerUF6() { return energyPerUF6; }
	}

	public static class CampaignRecipe {
		public final String id;
		public final CampaignGrade grade;
		public final FluidStack feed;
		public final int stages;
		public final ItemStack[] products;
		public final int duration, energyPerTick, tails;
		public final long totalEnergy, energyPerUF6;
		public final boolean advancedRotor;
		public CampaignRecipe(String id, CampaignGrade grade, int feed, int stages, int duration, int energyPerTick,
				int tails, boolean advancedRotor, ItemStack... products) {
			this.id=id; this.grade=grade; this.feed=new FluidStack(feed, Fluids.UF6); this.stages=stages;
			this.duration=duration; this.energyPerTick=energyPerTick; this.tails=tails;
			this.advancedRotor=advancedRotor; this.products=products;
			this.totalEnergy=(long)duration * energyPerTick; this.energyPerUF6=totalEnergy / feed;
		}
		public long getTotalEnergy() { return totalEnergy; }
		public long getEnergyPerUF6() { return energyPerUF6; }
		public ItemStack getDisplayInput() { return ItemFluidIcon.make(feed.type, feed.fill); }
	}

	private static final List<StageRecipe> stages = new ArrayList<StageRecipe>();
	private static final List<CampaignRecipe> campaigns = new ArrayList<CampaignRecipe>();
	public static final HashMap<FluidType, PseudoFluidType> fluidConversions = new HashMap<FluidType, PseudoFluidType>();

	public static void register() {
		stages.clear(); campaigns.clear(); fluidConversions.clear();
		fluidConversions.put(Fluids.UF6, PseudoFluidType.NUF6);
		fluidConversions.put(Fluids.PUF6, PseudoFluidType.PF6);
		fluidConversions.put(Fluids.WATZ, PseudoFluidType.MUD);
		fluidConversions.put(Fluids.MINSOL, PseudoFluidType.MINSOLE);

		// Civilian: two modest stages, with terminal deconversion defined here rather than in the tile.
		add(new StageRecipe("civilian-separation", CampaignGrade.CIVILIAN, PseudoFluidType.NUF6, 400, PseudoFluidType.LEUF6, 200, 4, 2, 300, 200, false, new ItemStack(ModItems.nugget_u238, 4)));
		add(new StageRecipe("civilian-deconversion", CampaignGrade.CIVILIAN, PseudoFluidType.LEUF6, 600, PseudoFluidType.NONE, 0, 0, 2, 450, 200, false, new ItemStack(ModItems.nugget_uranium_fuel, 6), new ItemStack(ModItems.fluorite, 2)));
		// Strategic: four physical machines/stages and 1,200 mB total feed for a U-235 batch.
		add(new StageRecipe("strategic-low", CampaignGrade.STRATEGIC, PseudoFluidType.NUF6, 1200, PseudoFluidType.LEUF6, 600, 6, 4, 600, 300, true, new ItemStack(ModItems.nugget_u238, 6)));
		add(new StageRecipe("strategic-medium", CampaignGrade.STRATEGIC, PseudoFluidType.LEUF6, 600, PseudoFluidType.MEUF6, 300, 3, 4, 750, 300, true, new ItemStack(ModItems.nugget_u238, 3)));
		add(new StageRecipe("strategic-high", CampaignGrade.STRATEGIC, PseudoFluidType.MEUF6, 300, PseudoFluidType.HEUF6, 150, 1, 4, 900, 300, true, new ItemStack(ModItems.nugget_u238, 1)));
		add(new StageRecipe("strategic-deconversion", CampaignGrade.STRATEGIC, PseudoFluidType.HEUF6, 150, PseudoFluidType.NONE, 0, 1, 4, 1200, 300, true, new ItemStack(ModItems.nugget_u235, 1), new ItemStack(ModItems.nugget_u238, 1), new ItemStack(ModItems.fluorite, 4)));
		// Existing non-uranium chains remain one-definition stage recipes.
		add(new StageRecipe("plutonium", CampaignGrade.CIVILIAN, PseudoFluidType.PF6, 300, PseudoFluidType.NONE, 0, 0, 1, 300, 200, false, new ItemStack(ModItems.nugget_pu238), new ItemStack(ModItems.nugget_pu_mix, 2), new ItemStack(ModItems.fluorite)));
		add(new StageRecipe("watz-mud", CampaignGrade.CIVILIAN, PseudoFluidType.MUD, 1000, PseudoFluidType.MUD_HEAVY, 500, 0, 2, 300, 200, false, new ItemStack(ModItems.powder_lead), new ItemStack(ModItems.dust)));
		add(new StageRecipe("watz-heavy", CampaignGrade.CIVILIAN, PseudoFluidType.MUD_HEAVY, 500, PseudoFluidType.NONE, 0, 0, 2, 300, 200, false, new ItemStack(ModItems.powder_iron), new ItemStack(ModItems.dust), new ItemStack(ModItems.nuclear_waste_tiny)));
		add(new StageRecipe("mineral-solution", CampaignGrade.CIVILIAN, PseudoFluidType.MINSOLE, 1000, PseudoFluidType.MINSOLEE, 1000, 0, 2, 300, 200, false, new ItemStack(ModItems.powder_iron)));
		add(new StageRecipe("mineral-crystals", CampaignGrade.CIVILIAN, PseudoFluidType.MINSOLEE, 1000, PseudoFluidType.NONE, 0, 0, 2, 300, 200, false, new ItemStack(ModItems.crystal_cleaned)));

		campaigns.add(new CampaignRecipe("civilian-leu", CampaignGrade.CIVILIAN, 1200, 2, 1550, 200, 12, false, new ItemStack(ModItems.nugget_uranium_fuel, 6), new ItemStack(ModItems.nugget_u238, 12), new ItemStack(ModItems.fluorite, 2)));
		campaigns.add(new CampaignRecipe("strategic-heu", CampaignGrade.STRATEGIC, 1200, 4, 2125, 600, 11, true, new ItemStack(ModItems.nugget_u235), new ItemStack(ModItems.nugget_u238, 11), new ItemStack(ModItems.fluorite, 4)));
	}
	private static void add(StageRecipe recipe) { stages.add(recipe); }
	public static List<CampaignRecipe> getCampaigns() { return campaigns; }
	public static StageRecipe getStage(PseudoFluidType input, CampaignGrade grade) {
		for(StageRecipe r : stages) if(r.input == input && (r.grade == grade || input != PseudoFluidType.NUF6 && input != PseudoFluidType.LEUF6 && input != PseudoFluidType.MEUF6 && input != PseudoFluidType.HEUF6)) return r;
		return null;
	}
	public static PseudoFluidType migrateType(String name, PseudoFluidType fallback) {
		if(name == null || name.length() == 0) return fallback;
		PseudoFluidType type = PseudoFluidType.types.get(name.toUpperCase(Locale.US));
		return type == null ? fallback : type;
	}
}
