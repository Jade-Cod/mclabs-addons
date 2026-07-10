package dev.jade.labsaddons.pititem;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.EnumMap;
import java.util.Map;

/**
 * Icon stacks for Pit items, analogous to {@link dev.jade.labsaddons.mcmmo.McmmoSkillIcons}:
 * each item's real base vanilla item. Stacks are cached — no per-frame allocation.
 */
public final class PitItemIcons {
	private static final Map<PitItem, ItemStack> CACHE = new EnumMap<>(PitItem.class);

	private PitItemIcons() {
	}

	/** Icon for a tracker entry key ("pit_item:stormbreaker"), or null if unknown. */
	public static ItemStack iconFor(String key) {
		PitItem item = itemForKey(key);
		return item == null ? null : CACHE.computeIfAbsent(item, i -> new ItemStack(itemFor(i)));
	}

	private static PitItem itemForKey(String key) {
		for (PitItem item : PitItem.values()) {
			if (PitItemCooldownTracker.keyFor(item).equals(key)) {
				return item;
			}
		}
		return null;
	}

	private static Item itemFor(PitItem item) {
		return switch (item) {
			case STORMBREAKER -> Items.IRON_AXE;
			case HEAVY_STEEL_CHESTPLATE -> Items.IRON_CHESTPLATE;
			case BODY_SLAM -> Items.PLAYER_HEAD;
			case SCYTHE_SWEEP -> Items.IRON_HOE;
			case BLINK_BOOTS -> Items.DIAMOND_BOOTS;
			case EXCALIBUR -> Items.IRON_SWORD;
		};
	}
}
