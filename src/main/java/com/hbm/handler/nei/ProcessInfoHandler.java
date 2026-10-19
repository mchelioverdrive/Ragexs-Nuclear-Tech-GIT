package com.hbm.handler.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import com.hbm.handler.imc.ICompatNHNEI;
import com.hbm.lib.RefStrings;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Read-only process descriptions. Infrastructure is never indexed as a product or a consumed input. */
public abstract class ProcessInfoHandler extends TemplateRecipeHandler implements ICompatNHNEI {
    private static final Map<Class<?>, Lookup> LOOKUPS = new HashMap<>();
    private static final class Lookup {
        final List<Process> source;
        final Map<Object, LinkedHashSet<Process>> inputs = new HashMap<>(), outputs = new HashMap<>();
        Lookup(List<Process> source) {
            this.source = source;
            for(Process process : source) {
                index(inputs, process.machine, process);
                for(ItemStack[] choices : process.inputs) for(ItemStack stack : choices) index(inputs, stack, process);
                for(ItemStack stack : process.outputs) index(outputs, stack, process);
            }
        }
        private static void index(Map<Object, LinkedHashSet<Process>> map, ItemStack stack, Process process) {
            Object key = stack.getItem() == com.hbm.items.ModItems.fluid_icon ? NEISafe.fluidIdentity(stack) : stack.getItem();
            LinkedHashSet<Process> rows = map.get(key);
            if(rows == null) { rows = new LinkedHashSet<>(); map.put(key, rows); }
            rows.add(process);
        }
    }
    private Lookup lookup() {
        List<Process> source = processes();
        synchronized(LOOKUPS) {
            Lookup lookup = LOOKUPS.get(getClass());
            if(lookup == null || lookup.source != source) { lookup = new Lookup(source); LOOKUPS.put(getClass(), lookup); }
            return lookup;
        }
    }
    private Iterable<Process> candidates(ItemStack stack, boolean usage) {
        if(!NEISafe.isValid(stack)) return Collections.emptyList();
        Lookup lookup = lookup();
        Map<Object, LinkedHashSet<Process>> map = usage ? lookup.inputs : lookup.outputs;
        LinkedHashSet<Process> candidates = new LinkedHashSet<>();
        java.util.Set<Process> items = map.get(stack.getItem());
        if(items != null) candidates.addAll(items);
        java.util.Set<Process> fluids = map.get(NEISafe.fluidIdentity(stack));
        if(fluids != null) candidates.addAll(fluids);
        return candidates;
    }
    public static final class Process {
        public final ItemStack machine;
        public final ItemStack[][] inputs;
        public final ItemStack[] outputs;
        public final String[] notes;
        public Process(Block machine, ItemStack[][] inputs, ItemStack[] outputs, String... notes) {
            this(new ItemStack(machine), inputs, outputs, notes);
        }
        public Process(ItemStack machine, ItemStack[][] inputs, ItemStack[] outputs, String... notes) {
            if(!NEISafe.isValid(machine)) throw new IllegalArgumentException("Missing process machine");
            this.machine = machine.copy();
            this.inputs = new ItemStack[inputs.length][];
            for(int i = 0; i < inputs.length; i++) {
                this.inputs[i] = NEISafe.copyValid(inputs[i]);
                if(this.inputs[i] == null) throw new IllegalArgumentException("Empty required process ingredient");
            }
            ItemStack[] products = NEISafe.copyValid(outputs);
            this.outputs = products == null ? new ItemStack[0] : products;
            this.notes = notes.clone();
        }
    }
    protected abstract List<Process> processes();
    /** Registration runs before post-load recipe initialization; catalysts must not build recipe snapshots. */
    protected abstract Block[] machineBlocks();
    protected static long dataRevision() {
        return com.hbm.inventory.recipes.loader.SerializableRecipe.getRegistryRevision() * 1000003L
                + com.hbm.inventory.fluid.Fluids.getRegistryRevision();
    }
    public static ItemStack[][] inputs(ItemStack... inputs) {
        ItemStack[][] result = new ItemStack[inputs.length][];
        for(int i = 0; i < inputs.length; i++) result[i] = new ItemStack[] {inputs[i]};
        return result;
    }
    @Override public String getGuiTexture() { return RefStrings.MODID + ":textures/gui/nei/gui_nei.png"; }
    @Override public String getRecipeID() { return getOverlayIdentifier(); }
    @Override public int recipiesPerPage() { return 1; }
    @Override public int getNEIMaxRecipesPerPage() { return 1; }
    @Override public int getNEIHandlerHeight() { return 150; }
    @Override public ItemStack[] getMachinesForRecipe() {
        List<ItemStack> machines = new ArrayList<>();
        for(Block block : machineBlocks()) machines.add(new ItemStack(block));
        return NEISafe.copyValid(machines.toArray(new ItemStack[0]));
    }
    public class Page extends CachedRecipe {
        public final Process process;
        private final List<PositionedStack> ingredients = new ArrayList<>(), products = new ArrayList<>();
        private final PositionedStack machine;
        Page(Process process) {
            this.process = process;
            for(int i = 0; i < process.inputs.length; i++) ingredients.add(NEISafe.positionedStack(process.inputs[i], 6 + i % 3 * 18, 4 + i / 3 * 18));
            for(int i = 0; i < process.outputs.length; i++) products.add(NEISafe.positionedStack(process.outputs[i], 106 + i % 3 * 18, 4 + i / 3 * 18));
            machine = NEISafe.positionedStack(process.machine, 75, 22);
        }
        @Override public List<PositionedStack> getIngredients() { return NEISafe.getCycledIngredients(this, cycleticks / 20, ingredients); }
        @Override public PositionedStack getResult() { return products.isEmpty() ? null : products.get(0); }
        @Override public List<PositionedStack> getOtherStacks() {
            List<PositionedStack> other = new ArrayList<>();
            if(products.size() > 1) other.addAll(products.subList(1, products.size()));
            other.add(machine);
            return other;
        }
    }
    @Override public void loadCraftingRecipes(String id, Object... results) {
        if(getOverlayIdentifier().equals(id)) for(Process process : processes()) arecipes.add(new Page(process));
        else super.loadCraftingRecipes(id, results);
    }
    @Override public void loadUsageRecipes(String id, Object... ingredients) {
        if(getOverlayIdentifier().equals(id)) loadCraftingRecipes(id);
        else super.loadUsageRecipes(id, ingredients);
    }
    @Override public void loadCraftingRecipes(ItemStack result) {
        for(Process process : candidates(result, false)) for(ItemStack product : process.outputs) if(NEISafe.matchesRecipeStack(product, result)) {
            arecipes.add(new Page(process)); break;
        }
    }
    @Override public void loadUsageRecipes(ItemStack ingredient) {
        outer: for(Process process : candidates(ingredient, true)) {
            if(NEISafe.matchesRecipeStack(process.machine, ingredient)) { arecipes.add(new Page(process)); continue; }
            for(ItemStack[] choices : process.inputs) for(ItemStack input : choices) if(NEISafe.matchesRecipeStack(input, ingredient)) {
                arecipes.add(new Page(process)); continue outer;
            }
        }
    }
    @Override public void drawBackground(int recipe) {
        Gui.drawRect(0, 0, 166, 148, 0xffc6c6c6);
        Page page = (Page) arecipes.get(recipe);
        List<PositionedStack> slots = new ArrayList<>(page.ingredients); slots.addAll(page.products); slots.add(page.machine);
        for(PositionedStack stack : slots) Gui.drawRect(stack.relx - 1, stack.rely - 1, stack.relx + 17, stack.rely + 17, 0xff8b8b8b);
    }
    @Override public void drawForeground(int recipe) {
        GL11.glColor4f(1, 1, 1, 1); GL11.glDisable(GL11.GL_LIGHTING);
        Minecraft.getMinecraft().fontRenderer.drawString("->", 77, 6, 0x404040);
        int y = 82;
        GL11.glPushMatrix(); GL11.glScalef(0.75F, 0.75F, 1);
        for(String note : ((Page) arecipes.get(recipe)).process.notes) {
            for(Object line : Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(note, 210)) {
                if(y <= 188) Minecraft.getMinecraft().fontRenderer.drawString((String) line, 5, y, 0x404040);
                y += 10;
            }
        }
        GL11.glPopMatrix();
    }
    @Override public List<String> handleItemTooltip(codechicken.nei.recipe.GuiRecipe gui, ItemStack stack, List<String> lines, int recipe) {
        if(recipe >= 0 && recipe < arecipes.size() && NEISafe.isValid(stack)) {
            Page page = (Page) arecipes.get(recipe);
            boolean found = NEISafe.matchesRecipeStack(page.process.machine, stack);
            for(PositionedStack input : page.ingredients) found |= input.contains(stack);
            for(PositionedStack output : page.products) found |= output.contains(stack);
            if(found) Collections.addAll(lines, page.process.notes);
        }
        return super.handleItemTooltip(gui, stack, lines, recipe);
    }
}
