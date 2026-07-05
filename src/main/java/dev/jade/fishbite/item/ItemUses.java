package dev.jade.fishbite.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Reads the remaining "uses" (charges) custom_data field used by server items
 *  like Smoke Bomb / Smelling Salts / Janky Jetski. */
public final class ItemUses {
	private ItemUses() {
	}

	/** Remaining uses for a single (non-stacked) item, or -1 if not applicable. */
	public static int remaining(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1) {
			return -1;
		}
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound data = component == null ? null : component.copyNbt();
		return data != null && data.contains("uses") ? data.getInt("uses", -1) : -1;
	}
}
