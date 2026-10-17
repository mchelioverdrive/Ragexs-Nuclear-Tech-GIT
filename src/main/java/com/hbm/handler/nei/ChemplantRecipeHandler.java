package com.hbm.handler.nei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.handler.imc.ICompatNHNEI;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.gui.GUIMachineChemplant;
import com.hbm.inventory.gui.GUIChemfac;
import com.hbm.inventory.container.ContainerChemfac;
import com.hbm.inventory.container.ContainerMachineChemplant;
import com.hbm.inventory.recipes.ChemplantRecipes;
import com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIcon;
import com.hbm.lib.RefStrings;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public class ChemplantRecipeHandler extends SafeTemplateRecipeHandler implements ICompatNHNEI {

	public LinkedList<RecipeTransferRect> transferRectsRec = new LinkedList<RecipeTransferRect>();
	public LinkedList<RecipeTransferRect> transferRectsGui = new LinkedList<RecipeTransferRect>();
	public LinkedList<Class<? extends GuiContainer>> guiRec = new LinkedList<Class<? extends GuiContainer>>();
	public LinkedList<Class<? extends GuiContainer>> guiGui = new LinkedList<Class<? extends GuiContainer>>();

	@Override
	public ItemStack[] getMachinesForRecipe() {
		return new ItemStack[]{
				new ItemStack(ModBlocks.machine_chemplant),
				new ItemStack(ModBlocks.machine_chemfac)};
	}

	@Override
	public String getRecipeID() {
		return "chemistry";
	}
	public class RecipeSet extends TemplateRecipeHandler.CachedRecipe {

		PositionedStack[] itemIn = new PositionedStack[4];
		PositionedStack[] fluidIn = new PositionedStack[4];
		PositionedStack[] itemOut = new PositionedStack[4];
		PositionedStack[] fluidOut = new PositionedStack[4];
		PositionedStack template;
        final ChemRecipe bill;

		public RecipeSet(ChemRecipe recipe) {
            this.bill = recipe;

			for(int i = 0; i < recipe.inputs.length; i++) {
				AStack in = recipe.inputs[i];
				if(in == null) continue;
				this.itemIn[i] = NEISafe.positionedStack(in.extractForNEI(), i * 18, 36);
			}

			for(int i = 0; i < recipe.inputFluids.length; i++) {
				FluidStack in = recipe.inputFluids[i];
				if(in == null || in.fill <= 0 || in.type == com.hbm.inventory.fluid.Fluids.NONE) continue;
				ItemStack drop = ItemFluidIcon.make(in);
				this.fluidIn[i] = NEISafe.positionedStack(drop, i * 18, 16);
			}

			for(int i = 0; i < recipe.outputs.length; i++) {
				ItemStack out = recipe.outputItemDisplay(i);
				if(out == null) continue;
				this.itemOut[i] = NEISafe.positionedStack(out, 96 + i * 18, 36);
			}

			for(int i = 0; i < recipe.outputFluids.length; i++) {
				FluidStack out = recipe.outputFluids[i];
				if(out == null) continue;
				ItemStack drop = recipe.outputFluidDisplay(i);
				this.fluidOut[i] = NEISafe.positionedStack(drop, 96 + i * 18, 16);
			}

			this.template = NEISafe.positionedStack(new ItemStack(ModItems.chemistry_template, 1, recipe.getId()), 76, 16);
		}

		@Override
		public List<PositionedStack> getIngredients() {
			List<PositionedStack> stacks = new ArrayList<PositionedStack>();

			for(PositionedStack stack : itemIn) if(stack != null) stacks.add(stack);
			for(PositionedStack stack : fluidIn) if(stack != null) stacks.add(stack);
			stacks.add(template);

			return NEISafe.getCycledIngredients(this, cycleticks / 20, stacks);
		}

		@Override
		public List<PositionedStack> getOtherStacks() {
			List<PositionedStack> stacks = new ArrayList<PositionedStack>();

			for(PositionedStack stack : itemOut) if(stack != null) stacks.add(stack);
			for(PositionedStack stack : fluidOut) if(stack != null) stacks.add(stack);
			stacks.add(template);

			return NEISafe.cleanPositionedList(stacks);
		}

		@Override
		public PositionedStack getResult() {
			for(PositionedStack stack : itemOut) if(NEISafe.isValid(stack)) return stack;
			for(PositionedStack stack : fluidOut) if(NEISafe.isValid(stack)) return stack;
			return null;
		}
	}

	@Override
	public String getRecipeName() {
		return "Chemical Reactor";
	}

	@Override
	public String getGuiTexture() {
		return RefStrings.MODID + ":textures/gui/nei/gui_nei_chemplant.png";
	}

	@Override
	public void loadCraftingRecipes(String outputId, Object... results) {

		if((outputId.equals("chemistry")) && getClass() == ChemplantRecipeHandler.class) {

			for(ChemRecipe recipe : ChemplantRecipes.recipes) {
				this.arecipes.add(new RecipeSet(recipe));
			}
		} else {
			super.loadCraftingRecipes(outputId, results);
		}
	}

	@Override
	public void loadCraftingRecipes(ItemStack result) {

		outer:
		for(ChemRecipe recipe : ChemplantRecipes.recipes) {

			for(ItemStack out : recipe.outputs) {

				if(out != null && NEIServerUtils.areStacksSameTypeCrafting(result, out)) {
					this.arecipes.add(new RecipeSet(recipe));
					continue outer;
				}
			}

			for(FluidStack out : recipe.outputFluids) {

				if(out != null) {
					ItemStack drop = ItemFluidIcon.make(out.type, out.fill);

					if(compareFluidStacks(result, drop)) {
						this.arecipes.add(new RecipeSet(recipe));
						continue outer;
					}
				}
			}
		}
	}

	@Override
	public void loadUsageRecipes(String inputId, Object... ingredients) {
		if((inputId.equals("chemistry")) && getClass() == ChemplantRecipeHandler.class) {
			loadCraftingRecipes("chemistry", new Object[0]);
		} else {
			super.loadUsageRecipes(inputId, ingredients);
		}
	}

	@Override
	public void loadUsageRecipes(ItemStack ingredient) {

		outer:
		for(ChemRecipe recipe : ChemplantRecipes.recipes) {

			for(AStack in : recipe.inputs) {

				if(in != null) {
					List<ItemStack> stacks = in.extractForNEI();

					for(ItemStack stack : stacks) {
						if(NEIServerUtils.areStacksSameTypeCrafting(ingredient, stack)) {
							this.arecipes.add(new RecipeSet(recipe));
							continue outer;
						}
					}
				}
			}

			for(FluidStack in : recipe.inputFluids) {

				if(in != null) {
					ItemStack drop = ItemFluidIcon.make(in.type, in.fill);

					if(compareFluidStacks(ingredient, drop)) {
						this.arecipes.add(new RecipeSet(recipe));
						continue outer;
					}
				}
			}
		}
	}

	private boolean compareFluidStacks(ItemStack sta1, ItemStack sta2) {
		return NEISafe.matchesRecipeStack(sta1, sta2);
	}

	@Override
	public Class<? extends GuiContainer> getGuiClass() {
		return null;
	}

	@Override
	public void loadTransferRects() {
		transferRectsGui = new LinkedList<RecipeTransferRect>();
		guiGui = new LinkedList<Class<? extends GuiContainer>>();

		transferRects.add(new RecipeTransferRect(new Rectangle(74, 36, 20, 16), "chemistry"));
		transferRectsGui.add(new RecipeTransferRect(new Rectangle(ContainerMachineChemplant.PROCESS_X, ContainerMachineChemplant.PROCESS_Y,
				ContainerMachineChemplant.PROCESS_WIDTH, ContainerMachineChemplant.PROCESS_HEIGHT), "chemistry"));
		guiGui.add(GUIMachineChemplant.class);
		RecipeTransferRectHandler.registerRectsToGuis(getRecipeTransferRectGuis(), transferRects);
		RecipeTransferRectHandler.registerRectsToGuis(guiGui, transferRectsGui);
		LinkedList<RecipeTransferRect> industrialRects = new LinkedList<RecipeTransferRect>();
		for(int lane = 0; lane < 8; lane++) industrialRects.add(new RecipeTransferRect(new Rectangle(
				66 + ContainerChemfac.LANE_PITCH_X * (lane % 2), 16 + ContainerChemfac.LANE_PITCH_Y * (lane / 2), 18, 10), "chemistry"));
		LinkedList<Class<? extends GuiContainer>> industrialGuis = new LinkedList<Class<? extends GuiContainer>>();
		industrialGuis.add(GUIChemfac.class);
		RecipeTransferRectHandler.registerRectsToGuis(industrialGuis, industrialRects);
	}

    @Override public int recipiesPerPage() { return 1; }
    @Override public int getNEIHandlerHeight() { return 112; }
    @Override public int getNEIMaxRecipesPerPage() { return 1; }
    @Override public void drawBackground(int recipe) {
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        net.minecraft.client.gui.Gui.drawRect(0, 0, 166, 112, 0xffc6c6c6);
        for(int i = 0; i < 4; i++) for(int x : new int[] {i * 18, 96 + i * 18})
            for(int y : new int[] {16, 36}) net.minecraft.client.gui.Gui.drawRect(x, y, x + 16, y + 16, 0xff8b8b8b);
    }
    @Override public void drawExtras(int recipe) {
        ChemRecipe bill = ((RecipeSet) arecipes.get(recipe)).bill;
        net.minecraft.client.gui.FontRenderer font = net.minecraft.client.Minecraft.getMinecraft().fontRenderer;
        font.drawString("Feeds", 0, 4, 0x404040); font.drawString("Products", 96, 4, 0x404040);
        font.drawString("->", 77, 40, 0x404040);
        font.drawString(String.format(java.util.Locale.US, "Time: %.2f s", bill.getDuration() / 20D), 0, 58, 0x404040);
        long cost = bill.costPerTick(com.hbm.realsim.IndustrialDemand.CHEMICAL_W / 10);
        font.drawString("Process: " + com.hbm.realsim.MachineInfo.watts(cost * 10), 0, 70, 0x404040);
        font.drawString("Work: " + api.hbm.energymk2.EnergyUnits.formatJoules(bill.physicalBill == null ? cost * bill.getDuration() : bill.physicalBill.workQuanta), 0, 82, 0x404040);
        if(bill.oxygenConsumption > 0) font.drawString("Atmospheric oxygen: " + bill.oxygenConsumption + "/tick", 0, 94, 0x404040);
    }

	@Override
	public TemplateRecipeHandler newInstance() {
		return super.newInstance();
	}
}
