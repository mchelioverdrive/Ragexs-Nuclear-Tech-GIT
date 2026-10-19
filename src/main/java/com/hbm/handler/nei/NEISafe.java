package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.hbm.main.MainRegistry;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.item.ItemStack;

/** Shared null-safety helpers for HBM NEI recipe handlers. */
public final class NEISafe {

	private static final Set<String> LOGGED = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

	private NEISafe() { }

	public static boolean isValid(ItemStack stack) {
		return stack != null && stack.getItem() != null && stack.stackSize > 0;
	}

	/** Fluid recipe navigation follows identity, not preview quantity/NBT or the selected container. */
	public static boolean matchesRecipeStack(ItemStack a, ItemStack b) {
		if(!isValid(a) || !isValid(b)) return false;
		if(a.getItem() == com.hbm.items.ModItems.icf_pellet || b.getItem() == com.hbm.items.ModItems.icf_pellet) {
			if(a.getItem() != b.getItem()) return false;
			return com.hbm.items.machine.ItemICFPellet.getType(a, true) == com.hbm.items.machine.ItemICFPellet.getType(b, true)
					&& com.hbm.items.machine.ItemICFPellet.getType(a, false) == com.hbm.items.machine.ItemICFPellet.getType(b, false)
					&& (a.hasTagCompound() && a.getTagCompound().getBoolean("muon")) == (b.hasTagCompound() && b.getTagCompound().getBoolean("muon"));
		}
		String pseudoA = a.hasTagCompound() ? a.getTagCompound().getString("neiPseudoFluid") : "";
		String pseudoB = b.hasTagCompound() ? b.getTagCompound().getString("neiPseudoFluid") : "";
		if(!pseudoA.isEmpty() || !pseudoB.isEmpty()) return !pseudoA.isEmpty() && pseudoA.equals(pseudoB);
		if(a.getItem() == com.hbm.items.ModItems.fluid_icon || b.getItem() == com.hbm.items.ModItems.fluid_icon) {
			com.hbm.inventory.fluid.FluidType left = fluidIdentity(a), right = fluidIdentity(b);
			return left != com.hbm.inventory.fluid.Fluids.NONE && left == right;
		}
		return codechicken.nei.NEIServerUtils.areStacksSameTypeCrafting(a, b);
	}

	static com.hbm.inventory.fluid.FluidType fluidIdentity(ItemStack stack) {
		if(!isValid(stack)) return com.hbm.inventory.fluid.Fluids.NONE;
		return stack.getItem() == com.hbm.items.ModItems.fluid_icon
				? com.hbm.inventory.fluid.Fluids.fromID(stack.getItemDamage())
				: com.hbm.inventory.FluidContainerRegistry.getFluidType(stack);
	}

	public static ItemStack copy(ItemStack stack) {
		return isValid(stack) ? stack.copy() : null;
	}

	/** Enumerates declared fuel aliases once per process snapshot, never a live inventory or world. */
	public static List<ItemStack> solidFuelCandidates() {
		List<ItemStack> fuels = new com.hbm.inventory.recipes.MachineRecipes().getAlloyFuels();
		for(String name : net.minecraftforge.oredict.OreDictionary.getOreNames())
			for(ItemStack stack : net.minecraftforge.oredict.OreDictionary.getOres(name))
				if(com.hbm.realsim.SolidFuel.resolve(stack) != null) fuels.add(stack.copy());
		List<ItemStack> unique = new ArrayList<>();
		outer: for(ItemStack stack : fuels) {
			if(!isValid(stack)) continue;
			for(ItemStack existing : unique) if(matchesRecipeStack(existing, stack)) continue outer;
			ItemStack copy = stack.copy(); copy.stackSize = 1; unique.add(copy);
		}
		return unique;
	}

	public static ItemStack[] copyValid(ItemStack[] stacks) {
		if(stacks == null || stacks.length == 0) return null;
		List<ItemStack> valid = new ArrayList<ItemStack>();
		for(ItemStack stack : stacks) {
			ItemStack copy = copy(stack);
			if(copy != null) valid.add(copy);
		}
		return valid.isEmpty() ? null : valid.toArray(new ItemStack[valid.size()]);
	}

	public static List<ItemStack> copyValid(Collection<ItemStack> stacks) {
		if(stacks == null || stacks.isEmpty()) return Collections.emptyList();
		List<ItemStack> valid = new ArrayList<ItemStack>();
		for(ItemStack stack : stacks) {
			ItemStack copy = copy(stack);
			if(copy != null) valid.add(copy);
		}
		return valid;
	}

	public static ItemStack[][] copyValid2D(ItemStack[][] stacks) {
		if(stacks == null || stacks.length == 0) return null;
		List<ItemStack[]> valid = new ArrayList<ItemStack[]>();
		for(ItemStack[] stack : stacks) {
			ItemStack[] copy = copyValid(stack);
			if(copy != null) valid.add(copy);
		}
		return valid.isEmpty() ? null : valid.toArray(new ItemStack[valid.size()][]);
	}

	/** Required inputs may lose invalid alternatives, but never a whole ingredient slot. */
	public static ItemStack[][] copyRequired2D(ItemStack[][] stacks) {
		if(stacks == null || stacks.length == 0) return null;
		ItemStack[][] result = new ItemStack[stacks.length][];
		for(int i = 0; i < stacks.length; i++) {
			result[i] = copyValid(stacks[i]);
			if(result[i] == null) return null;
		}
		return result;
	}

	public static PositionedStack positionedStack(Object object, int x, int y) {
		return positionedStack(object, x, y, true);
	}

	public static PositionedStack positionedStack(Object object, int x, int y, boolean genPermutations) {
		Object safe = safeIngredientObject(object);
		if(safe == null) return null;
		try {
			PositionedStack stack = new PositionedStack(safe, x, y, genPermutations);
			return isValid(stack) ? stack : null;
		} catch(Throwable t) {
			logSkip("PositionedStack", "Failed to create positioned stack from " + describe(object), t);
			return null;
		}
	}

	private static Object safeIngredientObject(Object object) {
		if(object instanceof ItemStack) return copy((ItemStack)object);
		if(object instanceof ItemStack[]) return copyValid((ItemStack[])object);
		if(object instanceof Collection) {
			List<ItemStack> choices = copyValid((Collection<ItemStack>)object);
			return choices.isEmpty() ? null : choices;
		}
		return object;
	}

	public static List<PositionedStack> positionedList(PositionedStack... stacks) {
		List<PositionedStack> list = new ArrayList<PositionedStack>();
		if(stacks == null) return list;
		for(PositionedStack stack : stacks) if(isValid(stack)) list.add(stack);
		return list;
	}

	public static List<PositionedStack> cleanPositionedList(Collection<PositionedStack> stacks) {
		List<PositionedStack> list = new ArrayList<PositionedStack>();
		if(stacks == null) return list;
		for(PositionedStack stack : stacks) if(isValid(stack)) list.add(stack);
		return list;
	}

	public static PositionedStack firstValid(Collection<PositionedStack> stacks) {
		if(stacks == null) return null;
		for(PositionedStack stack : stacks) if(isValid(stack)) return stack;
		return null;
	}

	public static List<PositionedStack> getCycledIngredients(TemplateRecipeHandler.CachedRecipe recipe, int cycle, Collection<PositionedStack> stacks) {
		List<PositionedStack> list = cleanPositionedList(stacks);
		int offset = 0;
		for(PositionedStack stack : list) stack.setPermutationToRender(Math.floorMod(cycle + offset++, stack.items.length));
		return list;
	}

	public static boolean isValid(PositionedStack stack) {
		if(stack == null) return false;
		ItemStack[] items = getItems(stack);
		if(items == null || items.length == 0) return false;
		for(ItemStack item : items) if(!isValid(item)) return false;
		return true;
	}

	public static boolean isSafeRecipe(TemplateRecipeHandler handler, TemplateRecipeHandler.CachedRecipe recipe, String source) {
		if(recipe == null) {
			logSkip(handler, source, "null cached recipe", null);
			return false;
		}
		try {
			PositionedStack result = recipe.getResult();
			if(!isValid(result)) {
				logSkip(handler, source, "missing or invalid main output in " + recipe.getClass().getName(), null);
				return false;
			}
			List<PositionedStack> ingredients = cleanPositionedList(recipe.getIngredients());
			if(ingredients.isEmpty()) {
				PositionedStack ingredient = recipe.getIngredient();
				if(isValid(ingredient)) ingredients.add(ingredient);
			}
			if(ingredients.isEmpty()) {
				logSkip(handler, source, "missing or invalid required input in " + recipe.getClass().getName(), null);
				return false;
			}
			recipe.getOtherStacks(); // optional stacks are sanitized by handler return helpers where present
			return true;
		} catch(Throwable t) {
			logSkip(handler, source, "malformed cached recipe " + recipe.getClass().getName(), t);
			return false;
		}
	}

	public static ItemStack firstStack(PositionedStack stack) {
		if(!isValid(stack)) return null;
		ItemStack[] items = getItems(stack);
		return items != null && items.length > 0 ? copy(items[0]) : null;
	}

	/** Development helper: validates currently cached NEI recipes without rendering them. */
	public static void validateCachedRecipes(TemplateRecipeHandler handler) {
		if(handler == null || handler.arecipes == null) return;
		for(Object recipe : new ArrayList<Object>(handler.arecipes)) {
			if(recipe instanceof TemplateRecipeHandler.CachedRecipe) {
				isSafeRecipe(handler, (TemplateRecipeHandler.CachedRecipe) recipe, "dev validation");
			} else {
				logSkip(handler, "dev validation", "non-cached recipe entry " + describe(recipe), null);
			}
		}
	}

    /** Opt-in client checks against the actual installed NEI API; no rendering or world access. */
    public static void validateNavigation(List<TemplateRecipeHandler> handlers) {
        int pages = 0, queries = 0;
        List<String> failures = new ArrayList<>();
        for(TemplateRecipeHandler prototype : handlers) {
            if(!(prototype instanceof com.hbm.handler.imc.ICompatNHNEI) && !(prototype instanceof CustomMachineHandler)) continue;
            try {
            String id = prototype instanceof CustomMachineHandler ? "ntm_" + ((CustomMachineHandler) prototype).conf.unlocalizedName
                    : ((com.hbm.handler.imc.ICompatNHNEI) prototype).getRecipeID();
            TemplateRecipeHandler all = prototype.newInstance();
            all.loadCraftingRecipes(id);
            Set<String> testedInputs = new HashSet<>(), testedOutputs = new HashSet<>();
            for(TemplateRecipeHandler.CachedRecipe page : all.arecipes) {
                all.cycleticks = 200000;
                List<PositionedStack> inputs = page.getIngredients();
                List<PositionedStack> outputs = new ArrayList<>();
                PositionedStack result = page.getResult();
                if(result != null) outputs.add(result);
                // Machine catalysts in other stacks are not products. Only index actual outputs.
                if(page instanceof ProcessInfoHandler.Page) {
                    for(ItemStack product : ((ProcessInfoHandler.Page) page).process.outputs)
                        outputs.add(positionedStack(product, 0, 0));
                } else if(page instanceof NEIUniversalHandler.RecipeSet) {
                    Collections.addAll(outputs, ((NEIUniversalHandler.RecipeSet) page).output);
                } else if(page instanceof CustomMachineHandler.RecipeSet) {
                    outputs.addAll(((CustomMachineHandler.RecipeSet) page).outputs);
                } else if(!(prototype instanceof AlloyFurnaceRecipeHandler) && !(prototype instanceof ShredderRecipeHandler)) {
                    for(PositionedStack other : page.getOtherStacks()) {
                        ItemStack stack = firstStack(other);
                        boolean catalyst = false;
                        ItemStack[] machines = prototype instanceof com.hbm.handler.imc.ICompatNHNEI ? ((com.hbm.handler.imc.ICompatNHNEI) prototype).getMachinesForRecipe() : null;
                        if(machines != null) for(ItemStack machine : machines) catalyst |= matchesRecipeStack(machine, stack);
                        for(PositionedStack input : inputs) if(input != null && input.contains(stack)) catalyst = true;
                        if(stack != null && stack.getItem() == com.hbm.items.ModItems.crucible_template) catalyst = true;
                        if(!catalyst) outputs.add(other);
                    }
                }
                for(PositionedStack input : inputs) {
                    if(!isValid(input)) throw new IllegalStateException(id + ": invalid input");
                    for(ItemStack stack : input.items) if(testedInputs.add(queryKey(stack))) {
                        checkNavigation(prototype, stack, true, failures); queries++;
                        queries += checkFilledContainer(prototype, stack, true, testedInputs, failures);
                    }
                }
                for(PositionedStack output : outputs) {
                    if(!isValid(output)) throw new IllegalStateException(id + ": invalid output");
                    if(output.item.getItem() == com.hbm.items.ModItems.nothing) continue;
                    for(ItemStack stack : output.items) if(testedOutputs.add(queryKey(stack))) {
                        checkNavigation(prototype, stack, false, failures); queries++;
                        queries += checkFilledContainer(prototype, stack, false, testedOutputs, failures);
                    }
                }
                all.handleItemTooltip(null, null, new ArrayList<String>(), all.arecipes.indexOf(page));
                pages++;
            }
            // U dispatches every handler, even when the selected stack is unrelated to that handler.
            TemplateRecipeHandler rotaryQuery = prototype.newInstance();
            rotaryQuery.loadUsageRecipes(new ItemStack(com.hbm.blocks.ModBlocks.machine_rotary_furnace));
            MainRegistry.logger.info("[NEI audit] " + id + ": " + all.arecipes.size() + " pages; "
                    + testedInputs.size() + " input and " + testedOutputs.size() + " output lookups");
            } catch(RuntimeException | LinkageError failure) {
                failures.add(prototype.getClass().getSimpleName() + ": " + failure);
            }
        }
        for(String failure : failures) MainRegistry.logger.error("[NEI audit] " + failure);
        if(!failures.isEmpty()) throw new IllegalStateException("NEI navigation audit failed: " + failures.size() + " failures; see [NEI audit] log entries");
        MainRegistry.logger.info("[NEI audit] PASS: " + handlers.size() + " handlers, " + pages + " pages, " + queries + " R/U queries (GUI rendering not tested)");
    }
    private static String queryKey(ItemStack stack) {
        return net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage()
                + (stack.hasTagCompound() ? stack.getTagCompound().toString() : "");
    }
    private static int checkFilledContainer(TemplateRecipeHandler prototype, ItemStack stack, boolean usage, Set<String> tested, List<String> failures) {
        if(stack.getItem() != com.hbm.items.ModItems.fluid_icon || stack.hasTagCompound() && stack.getTagCompound().hasKey("neiPseudoFluid")) return 0;
        com.hbm.inventory.fluid.FluidType type = fluidIdentity(stack);
        List<com.hbm.inventory.FluidContainer> containers = com.hbm.inventory.FluidContainerRegistry.getContainers(type);
        if(containers != null) for(com.hbm.inventory.FluidContainer container : containers) {
            ItemStack full = copy(container.fullContainer);
            if(full == null || fluidIdentity(full) != type) continue;
            if(tested.add(queryKey(full))) { checkNavigation(prototype, full, usage, failures); return 1; }
            break;
        }
        return 0;
    }
    private static void checkNavigation(TemplateRecipeHandler prototype, ItemStack stack, boolean usage, List<String> failures) {
        try {
        TemplateRecipeHandler query = prototype.newInstance();
        ItemStack before = stack.copy();
        if(usage) query.loadUsageRecipes(stack); else query.loadCraftingRecipes(stack);
        if(query.arecipes.isEmpty()) throw new IllegalStateException(prototype.getClass().getSimpleName() + ": missing " + (usage ? "U" : "R") + " lookup for " + queryKey(stack));
        if(!ItemStack.areItemStacksEqual(before, stack)) throw new IllegalStateException("NEI query mutated " + queryKey(stack));
        } catch(RuntimeException | LinkageError failure) {
            failures.add(failure.toString());
        }
    }

	private static ItemStack[] getItems(PositionedStack stack) {
		return stack.items;
	}

	private static void logSkip(TemplateRecipeHandler handler, String source, String reason, Throwable t) {
		String name = handler == null ? "unknown" : handler.getClass().getSimpleName() + "/" + handler.getRecipeName();
		logSkip(name, source + ": " + reason, t);
	}

	private static void logSkip(String name, String reason, Throwable t) {
		String key = name + "|" + reason;
		if(!LOGGED.add(key)) return;
		if(t == null) {
			MainRegistry.logger.warn("[NEI safety] Skipped broken recipe in " + name + ": " + reason);
		} else {
			MainRegistry.logger.warn("[NEI safety] Skipped broken recipe in " + name + ": " + reason + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
		}
	}

	private static String describe(Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
}
