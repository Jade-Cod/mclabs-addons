package dev.jade.labsaddons.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the remaining "uses" (charges) left on a server item like Smoke Bomb,
 *  Smelling Salts, or Janky Jetski. Only some of these set a custom_data
 *  "uses" field — every one of them mirrors the count into a lore line
 *  ("Charges: 3"), so that's the fallback and the only source for items
 *  (XL potions, Whetstone, etc.) that skip custom_data entirely. */
public final class ItemUses {
	private static final Pattern CHARGES_LORE = Pattern.compile("Charges:\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

	private ItemUses() {
	}

	/** Remaining uses for a single (non-stacked) item, or -1 if not applicable. */
	public static int remaining(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1) {
			return -1;
		}
		int fromCustomData = customDataUses(stack);
		return fromCustomData >= 0 ? fromCustomData : loreCharges(stack);
	}

	private static int customDataUses(ItemStack stack) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound data = component == null ? null : component.copyNbt();
		return data != null && data.contains("uses") ? data.getInt("uses", -1) : -1;
	}

	private static int loreCharges(ItemStack stack) {
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore == null) {
			return -1;
		}
		for (var line : lore.lines()) {
			Matcher matcher = CHARGES_LORE.matcher(line.getString());
			if (matcher.find()) {
				// Server-controlled lore: an over-long digit run overflows int and
				// would throw mid-render (this runs per slot, per frame). Fail soft.
				try {
					return Integer.parseInt(matcher.group(1));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}
}
