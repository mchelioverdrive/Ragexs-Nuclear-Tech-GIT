package com.hbm.integration.guideapi;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.items.ModItems;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/**
 * Optional Guide-API bridge for the RTM manual.
 *
 * Keep all Guide-API class references behind reflection in this class. RTM must
 * be able to load normally when Guide-API is not installed.
 */
public final class RTMGuideAPIIntegration {

	private static final String GUIDEAPI_MODID = "guideapi";
	private static final String BOOK_ID = "rtm_manual";

	private RTMGuideAPIIntegration() { }

	public static void registerManual() {

		if(!GeneralConfig.enableRTMGuideBook) {
			MainRegistry.logger.info("RTM Guide-API manual disabled by config.");
			return;
		}

		if(!Loader.isModLoaded(GUIDEAPI_MODID)) {
			MainRegistry.logger.info("Guide-API not loaded; skipping RTM manual registration.");
			return;
		}

		try {
			GuideReflection guide = new GuideReflection();
			Object book = buildBook(guide);
			guide.registerBook(book);
			MainRegistry.logger.info("Registered RTM Manual / Railcraft Technical Manual with Guide-API.");
		} catch(Throwable t) {
			MainRegistry.logger.error("Failed to register RTM Manual / Railcraft Technical Manual with Guide-API.", t);
		}
	}

	private static Object buildBook(GuideReflection guide) throws Exception {

		List<Object> categories = new ArrayList<Object>();
		categories.add(category(guide, "getting_started", stack(ModItems.train), entry(guide, "getting_started", stack(ModItems.train), "getting_started")));
		categories.add(category(guide, "track_setup", stack(ModBlocks.rail_large_straight),
				entry(guide, "track_setup", stack(ModBlocks.rail_large_straight), "track_setup"),
				entry(guide, "track_items", stack(ModBlocks.rail_large_switch), "track_items")));
		categories.add(category(guide, "rolling_stock", stack(ModItems.train), entry(guide, "rolling_stock", stack(ModItems.train), "rolling_stock")));
		categories.add(category(guide, "signals_routing", stack(ModBlocks.rail_large_switch), entry(guide, "signals_routing", stack(ModBlocks.rail_large_switch), "signals_routing")));
		categories.add(category(guide, "power", stack(ModBlocks.rail_booster), entry(guide, "power", stack(ModBlocks.rail_booster), "power")));
		categories.add(category(guide, "stations", stack(Blocks.rail), entry(guide, "stations", stack(Blocks.rail), "stations")));
		categories.add(category(guide, "nei", stack(Items.book), entry(guide, "nei", stack(Items.book), "nei")));
		categories.add(category(guide, "compatibility", stack(ModBlocks.rail_highspeed), entry(guide, "compatibility", stack(ModBlocks.rail_highspeed), "compatibility")));
		categories.add(category(guide, "troubleshooting", stack(Items.redstone), entry(guide, "troubleshooting", stack(Items.redstone), "troubleshooting")));
		categories.add(category(guide, "server_admin", stack(Blocks.command_block), entry(guide, "server_admin", stack(Blocks.command_block), "server_admin")));

		Object book = guide.bookClass.newInstance();
		guide.invoke(book, "setTitle", tr("guideapi.rtm.book.title"));
		guide.invoke(book, "setDisplayName", tr("guideapi.rtm.book.name"));
		guide.invoke(book, "setAuthor", tr("guideapi.rtm.book.author"));
		guide.invoke(book, "setColor", new Color(0x2C5A7C));
		guide.invoke(book, "setCategoryList", categories);
		guide.invokeOptional(book, "setRegistryName", new ResourceLocation(RefStrings.MODID, BOOK_ID));
		return book;
	}

	private static Object category(GuideReflection guide, String key, ItemStack icon, Object... entries) throws Exception {

		Map<ResourceLocation, Object> entryMap = new LinkedHashMap<ResourceLocation, Object>();
		for(Object entry : entries) {
			String id = guide.pendingEntryIds.remove(0);
			entryMap.put(new ResourceLocation(RefStrings.MODID, id), entry);
		}
		return guide.newCategory(entryMap, tr("guideapi.rtm.category." + key), icon);
	}

	private static Object entry(GuideReflection guide, String id, ItemStack icon, String pageKey) throws Exception {

		List<Object> pages = new ArrayList<Object>();
		pages.add(guide.newPageText(tr("guideapi.rtm.page." + pageKey)));
		String detailsKey = "guideapi.rtm.page." + pageKey + ".details";
		String details = tr(detailsKey);
		if(!detailsKey.equals(details)) pages.add(guide.newPageText(details));
		if("compatibility".equals(pageKey)) pages.add(guide.newPageText(trf("guideapi.rtm.page.compatibility.generated", GeneralConfig.enableFluidContainerCompat, GeneralConfig.enableGuideBook)));
		if("server_admin".equals(pageKey)) pages.add(guide.newPageText(trf("guideapi.rtm.page.server_admin.generated", GeneralConfig.enableRTMGuideBook)));

		guide.pendingEntryIds.add(id);
		return guide.newEntry(pages, tr("guideapi.rtm.entry." + id), icon);
	}

	private static ItemStack stack(Object itemOrBlock) {
		if(itemOrBlock instanceof Item) return new ItemStack((Item) itemOrBlock, 1);
		if(itemOrBlock instanceof Block) return new ItemStack((Block) itemOrBlock, 1);
		return new ItemStack(Items.book, 1);
	}

	private static String tr(String key) {
		return StatCollector.translateToLocal(key);
	}

	private static String trf(String key, Object... args) {
		return StatCollector.translateToLocalFormatted(key, args);
	}

	private static final class GuideReflection {

		private final Class<?> guideAPIClass;
		private final Class<?> bookClass;
		private final Class<?> pageTextClass;
		private final Class<?> categoryClass;
		private final Class<?> entryClass;
		private final List<String> pendingEntryIds = new ArrayList<String>();

		private GuideReflection() throws ClassNotFoundException {
			guideAPIClass = Class.forName("amerifrance.guideapi.api.GuideAPI");
			bookClass = findClass("amerifrance.guideapi.api.impl.Book", "amerifrance.guideapi.api.Book");
			pageTextClass = Class.forName("amerifrance.guideapi.page.PageText");
			categoryClass = findClass("amerifrance.guideapi.category.CategoryItemStack", "amerifrance.guideapi.api.Category");
			entryClass = findClass("amerifrance.guideapi.entry.EntryItemStack", "amerifrance.guideapi.api.Entry");
		}

		private Object newPageText(String text) throws Exception {
			return construct(pageTextClass, text);
		}

		private Object newEntry(List<Object> pages, String name, ItemStack icon) throws Exception {
			return construct(entryClass, pages, name, icon);
		}

		private Object newCategory(Map<ResourceLocation, Object> entries, String name, ItemStack icon) throws Exception {
			return construct(categoryClass, entries, name, icon);
		}

		private void registerBook(Object book) throws Exception {

			for(Method method : guideAPIClass.getMethods()) {
				if(!Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 1) continue;
				if(!method.getParameterTypes()[0].isAssignableFrom(bookClass)) continue;

				String name = method.getName().toLowerCase();
				if(name.contains("register") || name.contains("add")) {
					method.invoke(null, book);
					return;
				}
			}

			for(Field field : guideAPIClass.getDeclaredFields()) {
				if(!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
				field.setAccessible(true);
				Map map = (Map) field.get(null);
				if(map != null) {
					map.put(new ResourceLocation(RefStrings.MODID, BOOK_ID), book);
					return;
				}
			}

			throw new IllegalStateException("No Guide-API book registration hook found");
		}

		private void invokeOptional(Object target, String methodName, Object value) {
			try {
				invoke(target, methodName, value);
			} catch(Exception ignored) {
				// Older Guide-API builds do not expose every modern metadata setter.
			}
		}

		private void invoke(Object target, String methodName, Object value) throws Exception {

			for(Method method : target.getClass().getMethods()) {
				if(!method.getName().equals(methodName) || method.getParameterTypes().length != 1) continue;
				Class<?> param = method.getParameterTypes()[0];
				if(value == null || param.isAssignableFrom(value.getClass())) {
					method.invoke(target, value);
					return;
				}
			}

			throw new NoSuchMethodException(target.getClass().getName() + "." + methodName);
		}

		private static Class<?> findClass(String... names) throws ClassNotFoundException {
			ClassNotFoundException last = null;
			for(String name : names) {
				try { return Class.forName(name); } catch(ClassNotFoundException e) { last = e; }
			}
			throw last;
		}

		private static Object construct(Class<?> clazz, Object... args) throws Exception {

			for(Constructor<?> constructor : clazz.getConstructors()) {
				Class<?>[] params = constructor.getParameterTypes();
				if(params.length != args.length) continue;

				boolean matches = true;
				for(int i = 0; i < params.length; i++) {
					if(args[i] != null && !params[i].isAssignableFrom(args[i].getClass())) {
						matches = false;
						break;
					}
				}

				if(matches) return constructor.newInstance(args);
			}

			throw new NoSuchMethodException("No matching constructor for " + clazz.getName());
		}
	}
}
