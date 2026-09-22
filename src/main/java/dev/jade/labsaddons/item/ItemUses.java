package dev.jade.labsaddons.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the remaining "uses" (charges) left on a server item like Smoke Bomb, Smelling Salts
 * or Janky Jetski. Only some of these set a custom_data {@code uses} field — every one of them
 * mirrors the count into a lore line ("Charges: 3"), so that's the fallback and the only source
 * for items (XL potions, Whetstone, etc.) that skip custom_data entirely.
 *
 * <p><b>Why the memo.</b> This is asked for every slot the game draws, every frame: the hotbar
 * always, plus all fifty-four of a container's slots whenever one is open. A profile taken with
 * a chest open put it at <b>768 ms of a 17-second render thread, 4.5%</b> — 380 ms of that
 * flattening lore {@link Component} to a String, 296 ms running the regex over it, and 128 ms
 * deep-copying an NBT compound to read one integer. Every bit of it recomputed an answer that
 * had not changed since the slot was last drawn.
 *
 * <p>So the answers are remembered against the component they were read from.
 * {@link ItemLore} and {@link CustomData} are immutable, and a stack whose data changes
 * gets a <em>new</em> one rather than having its old one edited — so an unchanged slot hits the
 * memo and a changed one misses it, with no version counter to keep in step. Keyed by identity
 * on purpose: {@code ItemLore} is a record, so its {@code equals} and {@code hashCode}
 * walk the whole text tree, which is the cost being avoided.
 *
 * <p>Read from the render thread only, via {@code GuiGraphicsExtractorMixin}; a plain map is enough
 * and is why there is no locking here.
 */
public final class ItemUses {
	private static final Pattern CHARGES_LORE =
			Pattern.compile("Charges:\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);

	/** No charges to show. Remembered like any other answer — it is the common one. */
	private static final int NONE = -1;

	/**
	 * How many components to remember.
	 *
	 * <p>The working set is one screen's worth: a hotbar and a container is ninety slots. This
	 * is well clear of that, and bounds what the map can pin in memory — the keys are the
	 * server's own components, so holding them forever would hold every item ever drawn.
	 */
	private static final int MAX_REMEMBERED = 512;

	private static final Map<Object, Integer> ANSWERS = new IdentityHashMap<>();

	/** How many components have actually been parsed. Visible for testing. */
	private static int reads;

	private ItemUses() {
	}

	/** Remaining uses for a single (non-stacked) item, or -1 if not applicable. */
	public static int remaining(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1) {
			return NONE;
		}
		int fromCustomData = customDataUses(stack);
		return fromCustomData >= 0 ? fromCustomData : loreCharges(stack);
	}

	private static int customDataUses(ItemStack stack) {
		CustomData component = stack.get(DataComponents.CUSTOM_DATA);
		// isEmpty() before the memo: an empty compound cannot hold "uses", and asking costs
		// nothing next to the copy below.
		if (component == null || component.isEmpty()) {
			return NONE;
		}
		Integer known = ANSWERS.get(component);
		return known != null ? known : remember(component, readUses(component));
	}

	private static int loreCharges(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return NONE;
		}
		Integer known = ANSWERS.get(lore);
		return known != null ? known : remember(lore, scan(lore));
	}

	/** The one accessor {@link CustomData} offers is a deep copy, so this runs once. */
	private static int readUses(CustomData component) {
		reads++;
		CompoundTag data = component.copyTag();
		return data.contains("uses") ? data.getIntOr("uses", NONE) : NONE;
	}

	/** Flattens each lore line and looks for the count. Runs once per distinct lore. */
	static int scan(ItemLore lore) {
		reads++;
		for (Component line : lore.lines()) {
			Matcher matcher = CHARGES_LORE.matcher(line.getString());
			if (matcher.find()) {
				// Server-controlled lore: an over-long digit run overflows int and would
				// throw mid-render. Fail soft.
				try {
					return Integer.parseInt(matcher.group(1));
				} catch (NumberFormatException e) {
					return NONE;
				}
			}
		}
		return NONE;
	}

	/**
	 * Remembers an answer, clearing the map wholesale once it is full.
	 *
	 * <p>ponytail: cleared rather than evicted least-recently-used. A miss costs one parse, the
	 * map refills from what is on screen within a frame, and an LRU would want an access order
	 * this has no reason to track.
	 */
	private static int remember(Object component, int value) {
		if (ANSWERS.size() >= MAX_REMEMBERED) {
			ANSWERS.clear();
		}
		ANSWERS.put(component, value);
		return value;
	}

	/** Visible for testing: the charges on this lore, memo and all. */
	static int charges(ItemLore lore) {
		Integer known = ANSWERS.get(lore);
		return known != null ? known : remember(lore, scan(lore));
	}

	/** Visible for testing: how many components have been parsed rather than remembered. */
	static int reads() {
		return reads;
	}

	/** Visible for testing. */
	static void forget() {
		ANSWERS.clear();
		reads = 0;
	}

	/** Visible for testing: how many answers are being held. */
	static int remembered() {
		return ANSWERS.size();
	}
}
