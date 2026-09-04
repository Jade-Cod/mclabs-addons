package dev.jade.labsaddons.chem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
			Map.entry("betromelonide", Items.DYE.pick(DyeColor.WHITE)),
			Map.entry("cocobinide", Items.INK_SAC),
			Map.entry("cactatonate", Items.POISONOUS_POTATO),
			Map.entry("cartatonide", Items.DYE.pick(DyeColor.GRAY)),
			Map.entry("chorberrium", Items.DYE.pick(DyeColor.RED)),
			Map.entry("chorumpkinate", Items.DYE.pick(DyeColor.PURPLE)),
			Map.entry("chowartusite", Items.DYE.pick(DyeColor.ORANGE)),
			Map.entry("copaprinide", Items.BONE_MEAL),
			Map.entry("glocarronide", Items.BLAZE_POWDER),
			Map.entry("glocobinide", Items.PRISMARINE_CRYSTALS),
			Map.entry("glompkinide", Items.PRISMARINE_SHARD),
			Map.entry("glorootinide", Items.DYE.pick(DyeColor.LIGHT_BLUE)),
			Map.entry("melcobinide", Items.DYE.pick(DyeColor.BLUE)),
			Map.entry("melpotinide", Items.DYE.pick(DyeColor.YELLOW)),
			Map.entry("papcactinide", Items.DYE.pick(DyeColor.LIGHT_GRAY)),
			Map.entry("papwartinide", Items.BRICK),
			Map.entry("pumpsugrinide", Items.GLOWSTONE_DUST),
			Map.entry("pumpwartinide", Items.NETHER_BRICK),
			Map.entry("sugcarronide", Items.CLAY_BALL),
			Map.entry("sweemelonide", Items.DYE.pick(DyeColor.MAGENTA)),
			Map.entry("sweepaprinide", Items.DYE.pick(DyeColor.PINK)),
			Map.entry("wheasugrinide", Items.DYE.pick(DyeColor.BLACK)),
			Map.entry("wheacactinide", Items.DYE.pick(DyeColor.CYAN)),
			Map.entry("wheacobinide", Items.DYE.pick(DyeColor.BROWN)),
			Map.entry("whearootinide", Items.BREAD),
			// --- Base/processed chems renamed by the server texture pack ---
			Map.entry("canium", Items.SUGAR_CANE),
			Map.entry("nonowheanide", Items.HAY_BLOCK),
			Map.entry("triwheanide", Items.BREAD),
			// --- Base chems (sourced from minecraft-farm-optimizer crops.json) ---
			Map.entry("wheatium", Items.WHEAT),
			Map.entry("potatium", Items.POTATO),
			Map.entry("carrotenium", Items.CARROT),
			Map.entry("nethwartium", Items.NETHER_WART),
			Map.entry("cocobium", Items.COCOA_BEANS),
			Map.entry("betronium", Items.BEETROOT),
			Map.entry("chorufrium", Items.CHORUS_FRUIT),
			Map.entry("cactium", Items.DYE.pick(DyeColor.GREEN)),
			Map.entry("melonium", Items.MELON_SEEDS),
			Map.entry("pumpkonium", Items.PUMPKIN_SEEDS),
			Map.entry("paprium", Items.PAPER),
			Map.entry("sugrium", Items.SUGAR),
			Map.entry("globerrium", Items.GLOW_BERRIES),
			Map.entry("sweeberrium", Items.SWEET_BERRIES),
			// --- Processed single-ingredient chems ---
			Map.entry("chorufrinide", Items.POPPED_CHORUS_FRUIT),
			Map.entry("cactinide", Items.DYE.pick(DyeColor.LIME)),
			Map.entry("potatinide", Items.BAKED_POTATO));

	/** Lazily built, reused stacks (no per-frame allocation). */
	private static final Map<String, ItemStack> CACHE = new HashMap<>();

	private ChemIcons() {
	}

	/** Whether {@code chemName} is a known chem (base crop, combo, or processed). */
	public static boolean isKnown(String chemName) {
		return ITEMS.containsKey(normalize(chemName));
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

	/**
	 * Whether a booster/chem name refers to the server's "All" chem booster.
	 * Accepts the chat name ("All Chems"), the GUI model-data ("all_chem_booster"),
	 * and the bare "All".
	 */
	public static boolean isAllBooster(String name) {
		if (name == null) {
			return false;
		}
		String s = name.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
		return s.equals("all") || s.startsWith("all chem");
	}

	private static ItemStack build(String key) {
		if (isAllBooster(key)) {
			return new ItemStack(Items.END_CRYSTAL);
		}
		Item item = ITEMS.get(key);
		if (item == null) {
			return new ItemStack(Items.PAPER);
		}
		ItemStack stack = new ItemStack(item);
		// Re-attach the server's custom_model_data string so the pack skins it.
		stack.set(DataComponents.CUSTOM_MODEL_DATA,
				new CustomModelData(List.of(), List.of(), List.of(key), List.of()));
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
