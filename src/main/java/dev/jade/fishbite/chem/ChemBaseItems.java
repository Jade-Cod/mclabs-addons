package dev.jade.fishbite.chem;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base and processed chems on MCLabs are PLAIN vanilla items with no NBT — the
 * server's optional texture pack just renames them (e.g. wheat → "Wheatium",
 * green_dye → "Cactium", sugar_cane → "Canium"). Combo chems, by contrast, carry
 * {@code custom_data}/{@code custom_model_data}. This maps each base/processed
 * vanilla item to its canonical chem key so those chems are detected and tracked
 * exactly like combo chems.
 *
 * <p>Source of truth: the server texture pack's item lang file. Keep these keys
 * consistent with {@link ChemIcons} (which maps key → icon item for the reverse
 * direction).
 */
public final class ChemBaseItems {
	private static final Map<Item, String> ITEM_TO_KEY = new LinkedHashMap<>();

	static {
		// Base chems (the harvested/sold form).
		put(Items.WHEAT, "wheatium");
		put(Items.POTATO, "potatium");
		put(Items.CARROT, "carrotenium");
		put(Items.NETHER_WART, "nethwartium");
		put(Items.COCOA_BEANS, "cocobium");
		put(Items.BEETROOT, "betronium");
		put(Items.CHORUS_FRUIT, "chorufrium");
		put(Items.GREEN_DYE, "cactium");
		put(Items.MELON_SEEDS, "melonium");
		put(Items.PUMPKIN_SEEDS, "pumpkonium");
		put(Items.PAPER, "paprium");
		put(Items.SUGAR, "sugrium");
		put(Items.GLOW_BERRIES, "globerrium");
		put(Items.SWEET_BERRIES, "sweeberrium");
		put(Items.SUGAR_CANE, "canium");
		put(Items.HAY_BLOCK, "nonowheanide");
		put(Items.BREAD, "triwheanide");
		// Processed chems.
		put(Items.POPPED_CHORUS_FRUIT, "chorufrinide");
		put(Items.LIME_DYE, "cactinide");
		put(Items.BAKED_POTATO, "potatinide");
		// Raw forms fold into their base chem.
		put(Items.CACTUS, "cactium");
		put(Items.MELON_SLICE, "melonium");
		put(Items.PUMPKIN, "pumpkonium");
	}

	private ChemBaseItems() {
	}

	private static void put(Item item, String key) {
		ITEM_TO_KEY.put(item, key);
	}

	/** The chem key for a plain vanilla base/processed chem item, or "" if it isn't one. */
	public static String keyFor(Item item) {
		return ITEM_TO_KEY.getOrDefault(item, "");
	}
}
