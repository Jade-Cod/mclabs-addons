package dev.jade.fishbite.chem;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for identifying chem items and reading their chem key + purity.
 * A chem item is authoritatively marked by a {@code custom_data} compound with a
 * {@code "chem"} field; its purity is {@code custom_data.purity}
 * {score,value,progress}. Used by the {@code /ch} GUI scrape, the deposit diff,
 * and the withdraw command builder so every code path keys chems identically.
 */
public final class ChemItems {
	/** A chem identity: lowercase key + purity string ("s-v-p", or "" if none). */
	public record ChemKey(String chem, String purity) {
	}

	/** Trailing "-s-v-p" purity suffix in a display name like "Chowartusite-2-2-2". */
	private static final Pattern LABEL_PURITY = Pattern.compile("((?:-\\d+){2,})$");

	private ChemItems() {
	}

	/**
	 * Whether the stack is a known chem. Base crops (Wheatium, Potatium, …) carry a
	 * {@code custom_model_data} key but NOT {@code custom_data.chem}, so we resolve
	 * the key from either source and validate it against the chem registry — that
	 * way normal crops are tracked the same as combo chems, and non-chems with
	 * model data (e.g. boosters) are still excluded.
	 */
	public static boolean isChem(ItemStack stack) {
		return !stack.isEmpty() && ChemIcons.isKnown(chemKey(stack));
	}

	/** The resolved chem key for an item, or "". Tries combo NBT first
	 *  (custom_data → model data), then the plain-vanilla base-chem item map. */
	public static String chemKey(ItemStack stack) {
		String chem = chemName(stack);
		if (!chem.isEmpty()) {
			return chem;
		}
		CustomModelDataComponent model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
		if (model != null && !model.strings().isEmpty()) {
			return model.strings().get(0).toLowerCase(Locale.ROOT);
		}
		return ChemBaseItems.keyFor(stack.getItem());
	}

	/** The purity string "s-v-p" (e.g. "2-2-2"), or "" when the item has no purity. */
	public static String purity(ItemStack stack) {
		NbtCompound data = customData(stack);
		if (data == null || !data.contains("purity")) {
			return "";
		}
		NbtCompound purity = data.getCompoundOrEmpty("purity");
		int score = purity.getInt("score", -1);
		int value = purity.getInt("value", -1);
		int progress = purity.getInt("progress", -1);
		if (score < 0 && value < 0 && progress < 0) {
			return "";
		}
		return Math.max(0, score) + "-" + Math.max(0, value) + "-" + Math.max(0, progress);
	}

	public static ChemKey keyOf(ItemStack stack) {
		return new ChemKey(chemKey(stack), purity(stack));
	}

	/** Total count of each chem (by key+purity) across the whole inventory. */
	public static Map<ChemKey, Long> snapshot(PlayerInventory inventory) {
		Map<ChemKey, Long> totals = new HashMap<>();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!isChem(stack)) {
				continue;
			}
			totals.merge(keyOf(stack), (long) stack.getCount(), Long::sum);
		}
		return totals;
	}

	/** The {@code /ch withdraw} argument: "chem|s-v-p" with purity, else "chem|0-0-0". */
	public static String withdrawArg(ChemKey key) {
		return key.purity().isEmpty() ? key.chem() + "|0-0-0" : key.chem() + "|" + key.purity();
	}

	/** A display label like "Chowartusite-2-2-2" (or "Chowartusite" with no purity). */
	public static String displayLabel(ChemKey key) {
		String name = capitalize(key.chem());
		return key.purity().isEmpty() ? name : name + "-" + key.purity();
	}

	/** Parse a server display name ("Chowartusite-2-2-2") back into a ChemKey. */
	public static ChemKey parseLabel(String label) {
		String trimmed = label == null ? "" : label.trim();
		Matcher matcher = LABEL_PURITY.matcher(trimmed);
		if (matcher.find()) {
			String purity = matcher.group(1).substring(1); // drop leading '-'
			if (purity.equals("0-0-0")) {
				purity = "";
			}
			String chem = trimmed.substring(0, matcher.start()).toLowerCase(Locale.ROOT).trim();
			return new ChemKey(chem, purity);
		}
		return new ChemKey(trimmed.toLowerCase(Locale.ROOT), "");
	}

	private static String chemName(ItemStack stack) {
		NbtCompound data = customData(stack);
		return data == null ? "" : data.getString("chem", "").toLowerCase(Locale.ROOT);
	}

	private static NbtCompound customData(ItemStack stack) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		return component == null ? null : component.copyNbt();
	}

	private static String capitalize(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
