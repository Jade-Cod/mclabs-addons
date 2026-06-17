package dev.jade.fishbite.chem;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps MCLabs chemical names to the vanilla item the server's resource pack
 * skins into the chem texture. In every sampled item the {@code custom_model_data}
 * string equals the lowercase chem name, so we only store name -> base item and
 * reuse the name itself as the model-data string.
 *
 * <p>Used by the booster and bounty widgets. The "All" booster is shown as an end
 * crystal; anything unmapped falls back to paper (the caller still prints the name).
 */
public final class ChemIcons {
	/** chem name (lowercase) -> base vanilla item carrying the server model. */
	private static final Map<String, Item> ITEMS = Map.ofEntries(
			// --- Compounds (exact, from the chems GUI dump) ---
			Map.entry("betromelonide", Items.WHITE_DYE),
			Map.entry("cocobinide", Items.INK_SAC),
			Map.entry("cactatonate", Items.POISONOUS_POTATO),
			Map.entry("cartatonide", Items.GRAY_DYE),
			Map.entry("chorberrium", Items.RED_DYE),
			Map.entry("chorumpkinate", Items.PURPLE_DYE),
			Map.entry("chowartusite", Items.ORANGE_DYE),
			Map.entry("copaprinide", Items.BONE_MEAL),
			Map.entry("glocarronide", Items.BLAZE_POWDER),
			Map.entry("glocobinide", Items.PRISMARINE_CRYSTALS),
			Map.entry("glompkinide", Items.PRISMARINE_SHARD),
			Map.entry("glorootinide", Items.LIGHT_BLUE_DYE),
			Map.entry("melcobinide", Items.BLUE_DYE),
			Map.entry("melpotinide", Items.YELLOW_DYE),
			Map.entry("papcactinide", Items.LIGHT_GRAY_DYE),
			Map.entry("papwartinide", Items.BRICK),
			Map.entry("pumpsugrinide", Items.GLOWSTONE_DUST),
			Map.entry("pumpwartinide", Items.NETHER_BRICK),
			Map.entry("sugcarronide", Items.CLAY_BALL),
			Map.entry("sweemelonide", Items.MAGENTA_DYE),
			Map.entry("sweepaprinide", Items.PINK_DYE),
			Map.entry("wheasugrinide", Items.BLACK_DYE),
			Map.entry("wheacactinide", Items.CYAN_DYE),
			Map.entry("wheacobinide", Items.BROWN_DYE),
			Map.entry("whearootinide", Items.BREAD),
			// --- Base chems (sourced from minecraft-farm-optimizer crops.json) ---
			Map.entry("wheatium", Items.WHEAT),
			Map.entry("potatium", Items.POTATO),
			Map.entry("carrotenium", Items.CARROT),
			Map.entry("nethwartium", Items.NETHER_WART),
			Map.entry("cocobium", Items.COCOA_BEANS),
			Map.entry("betronium", Items.BEETROOT),
			Map.entry("chorufrium", Items.CHORUS_FRUIT),
			Map.entry("cactium", Items.GREEN_DYE),
			Map.entry("melonium", Items.MELON_SEEDS),
			Map.entry("pumpkonium", Items.PUMPKIN_SEEDS),
			Map.entry("paprium", Items.PAPER),
			Map.entry("sugrium", Items.SUGAR),
			Map.entry("globerrium", Items.GLOW_BERRIES),
			Map.entry("sweeberrium", Items.SWEET_BERRIES),
			// --- Processed single-ingredient chems ---
			Map.entry("chorufrinide", Items.POPPED_CHORUS_FRUIT),
			Map.entry("cactinide", Items.LIME_DYE),
			Map.entry("potatinide", Items.BAKED_POTATO));

	/** Lazily built, reused stacks (no per-frame allocation). */
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private ChemIcons() {
	}

	/** An icon stack for the given chem/booster name; never null. */
	public static ItemStack iconFor(String chemName) {
		String key = normalize(chemName);
		ItemStack cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		ItemStack stack = build(key);
		CACHE.put(key, stack);
		return stack;
	}

	private static ItemStack build(String key) {
		if (key.equals("all")) {
			return new ItemStack(Items.END_CRYSTAL);
		}
		Item item = ITEMS.get(key);
		if (item == null) {
			return new ItemStack(Items.PAPER);
		}
		ItemStack stack = new ItemStack(item);
		// Re-attach the server's custom_model_data string so the pack skins it.
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(List.of(), List.of(), List.of(key), List.of()));
		return stack;
	}

	private static String normalize(String chemName) {
		if (chemName == null) {
			return "";
		}
		String s = chemName.toLowerCase(Locale.ROOT).trim();
		if (s.startsWith("raw ")) {
			s = s.substring(4).trim();
		}
		if (s.startsWith("heated ")) {
			s = s.substring(7).trim();
		}
		return s;
	}
}
